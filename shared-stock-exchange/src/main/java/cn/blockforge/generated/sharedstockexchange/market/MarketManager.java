package cn.blockforge.generated.sharedstockexchange.market;

import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 交易所的服务器端逻辑：成交、行情波动、手续费分红、股东公告、强制平仓，并把行情同步给每个联机玩家。
 * 所有判断都在服务器上做，客户端只发出"我要买/卖/清仓"的请求，防作弊。
 *
 * 钱就是等价交换（ProjectE）的 EMC：买股票直接扣玩家面板里的 EMC，卖股票直接加回去。
 * 没装等价交换时退回模组自带的测试钱包，方便单机自己试。
 *
 * EMC 本身就可以是负数：买股票把钱花超了，等价交换里的 EMC（包括转化桌里显示的那个数）
 * 就直接变成负的，没有另开一本账。负数期间往转化桌里卖物品，卖到的 EMC 会先把负数填回 0，
 * 卖够等量的物品才会回正——这就是炒亏了的惩罚。
 * 等价交换自己的转化桌原来会把负数夹成 0（卖一次东西欠款就凭空消失），
 * 本模组用一个小 Mixin 把这个夹 0 去掉；万一哪个版本对不上、Mixin 没生效，
 * /stock status 的自检会如实说明。
 *
 * 这里有两套互相独立的机制，各有各的开关：
 *   · 负债系统（/stock debt on|off，默认开）：只管一件事——EMC 能不能变成负数。
 *     开着时钱花光了还能接着买，EMC 直接扣成负的；关掉后只能花手里的现金。
 *   · 强制平仓（/stock liquidation on|off，默认开）：欠债又跌破强平线时，把股票按现价卖掉还债。
 *     关了就欠多少都只是挂在那里，不会有人替你卖股票。
 * 两者互不依赖：只开负债不开强平，就是"可以欠钱，但没人管你"；
 * 只开强平不开负债，则根本不会产生新债务（原有的负数 EMC 会被清零）。
 *
 * 波动间隔存在存档里（默认 30 秒一次），服务器管理员可以随时用 /stock interval 改。
 */
public final class MarketManager {
    public static final int MAX_SHARES_PER_TRADE = 100_000;
    /** 每笔买卖的手续费（1%），全部进这只股票的分红池，按持股分给股东。 */
    public static final double FEE_RATE = MarketMath.FEE_RATE;
    /** 每 10 分钟分一次红。 */
    public static final int DIVIDEND_INTERVAL_TICKS = 12_000;
    /** 持股超过 20% 且是第一大股东，就全服公告一次。 */
    public static final double MAJOR_HOLDER_RATIO = 0.2D;
    /** 未成年退款：欠债时把持仓和债务一起清掉，EMC 重置成这个数。 */
    public static final long REFUND_EMC = 1_000L;
    /** 强制平仓多久检查一次（tick）：1 秒一次，只看在线玩家，几乎不花性能。 */
    private static final int MARGIN_CHECK_TICKS = 20;
    /** 单笔金额上限：再往上 long 就要溢出，正常游玩一辈子也用不到这个数。 */
    private static final double MAX_SAFE_AMOUNT = 9.0E15D;

    private static final Random RANDOM = new Random();
    /** 行情多久推一次给客户端（5 秒），界面上看到的价格一直是新的。 */
    private static final int SYNC_INTERVAL_TICKS = 100;
    private static int ticksSinceDrift;
    private static int ticksSinceDividend;
    private static int ticksSinceSync;
    private static int ticksSinceMargin;
    private static boolean primed;

    /** 财富榜上的一行。 */
    public record Asset(UUID player, String name, double value) {
    }

    /** 一笔卖出的结果，清仓时要把多只股票的结果加起来。repaid 是到账里先抵扣欠款的那部分（只用来生成提示）。 */
    private record Sale(boolean ok, long gross, long fee, long received, double basisSold, long repaid) {
        static Sale failed() {
            return new Sale(false, 0L, 0L, 0L, 0.0D, 0L);
        }

        double profit() {
            return (double) this.received - this.basisSold;
        }
    }

    private MarketManager() {
    }

    public static MarketData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MarketData.FACTORY, MarketData.NAME);
    }

    /** 等价交换可用时，现金就是玩家面板里的 EMC。 */
    public static boolean projecteActive() {
        return ProjectEmcBridge.available();
    }

    /** 现金模式：0 = 测试钱包，1 = 等价交换 EMC，2 = 装了等价交换但没接通。 */
    public static int cashMode() {
        if (projecteActive()) {
            return MarketSnapshotPayload.CASH_EMC;
        }
        return ProjectEmcBridge.installedButDisconnected()
                ? MarketSnapshotPayload.CASH_BROKEN
                : MarketSnapshotPayload.CASH_TEST;
    }

    /** 这句会在启动日志和 /stock status 里出现，方便确认 EMC 到底接上没有。 */
    public static String cashModeText() {
        return ProjectEmcBridge.describe();
    }

    /**
     * 玩家现在的现金 = 等价交换面板里的 EMC（可以是负数，负数就是欠着钱）。
     * 没装等价交换时就是模组自带的测试钱包。买不买得起一律看这个数。
     */
    public static long balance(MinecraftServer server, ServerPlayer player) {
        if (projecteActive()) {
            long emc = ProjectEmcBridge.getEmc(player);
            // 读不到就当 0：宁愿买不了，也不能凭空多出钱
            return emc == ProjectEmcBridge.UNAVAILABLE ? 0L : emc;
        }
        return data(server).testWallet(player.getUUID());
    }

    /** 股票对标的价格：等价交换里这个物品的 EMC 价值；没装就用手写的发行价。 */
    public static double anchorPrice(StockDefinition definition) {
        double emc = ProjectEmcBridge.itemEmc(definition.itemId());
        return emc > 0.0D ? emc : definition.basePrice();
    }

    /**
     * 从等价交换的 EMC 里扣钱。这里不拦"钱不够"：这一笔能不能买，{@link #buy} 会按两个开关
     * （负债系统 / 强制平仓）先判一遍，走到这一步就是要扣——扣成负数也照扣，
     * 负数就是欠债，等价交换面板和转化桌里看到的就是这个负号。
     */
    private static boolean take(MinecraftServer server, ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (projecteActive()) {
            long emc = ProjectEmcBridge.getEmc(player);
            if (emc == ProjectEmcBridge.UNAVAILABLE) {
                return false;
            }
            return ProjectEmcBridge.setEmc(player, MarketMath.safeAdd(emc, -amount));
        }
        MarketData data = data(server);
        data.setTestWallet(player.getUUID(), MarketMath.safeAdd(data.testWallet(player.getUUID()), -amount));
        return true;
    }

    /**
     * 发钱：直接加到等价交换的 EMC 上。EMC 是负数时，这笔钱会先把负数往 0 上填（还债）。
     * 接的是等价交换时，EMC 写不进去就返回 false（这笔钱不能凭空消失）。
     */
    private static boolean give(MinecraftServer server, ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (projecteActive()) {
            long emc = ProjectEmcBridge.getEmc(player);
            if (emc == ProjectEmcBridge.UNAVAILABLE) {
                return false;
            }
            return ProjectEmcBridge.setEmc(player, MarketMath.safeAdd(emc, amount));
        }
        MarketData data = data(server);
        data.setTestWallet(player.getUUID(), MarketMath.safeAdd(data.testWallet(player.getUUID()), amount));
        return true;
    }

    /**
     * 现在还欠多少钱（EMC 是负数时就是 −EMC）。
     * 卖出到账时会先拿这个数判断「这笔钱有多少是去还债的」，好跟玩家说清楚。
     */
    public static long outstandingDebt(MinecraftServer server, ServerPlayer player) {
        return Math.max(-balance(server, player), 0L);
    }

    /** 卖出到账后补一句「这笔钱有多少去还债了」；没还债就返回空行。 */
    private static Component debtNote(long repaid, long stillOwed) {
        if (repaid <= 0L) {
            return Component.empty();
        }
        return (stillOwed > 0L
                ? Component.translatable("message.shared_stock_exchange.paid_debt",
                        MarketFormat.emc(repaid), MarketFormat.emc(stillOwed))
                : Component.translatable("message.shared_stock_exchange.paid_debt_clear",
                        MarketFormat.emc(repaid)))
                .withStyle(ChatFormatting.GOLD);
    }

    /** 把等价交换的 EMC 直接改成 value（退款、关负债系统时的清零都走这里，负数也照写）。 */
    private static boolean setBalance(MinecraftServer server, ServerPlayer player, long value) {
        if (projecteActive()) {
            return ProjectEmcBridge.setEmc(player, value);
        }
        data(server).setTestWallet(player.getUUID(), value);
        return true;
    }

    /** 负债系统开着没有：EMC 能不能变成负数。 */
    public static boolean debtAllowed(MinecraftServer server) {
        return data(server).debtAllowed();
    }

    /** 强制平仓开着没有。 */
    public static boolean liquidationEnabled(MinecraftServer server) {
        return data(server).liquidationEnabled();
    }

    /**
     * 这个玩家的持仓值多少钱（EMC）。
     *
     * 用的是「现在全部卖掉实际能拿回多少」，不是「现价 × 股数」：
     * 股份池是恒定乘积的，自己把价格买上去以后，"现价 × 股数"会比真正能卖出的钱大好几倍。
     * 保证金、权益、财富榜都用这个口径，界面上的持仓金额也才和真能拿到手的钱对得上。
     */
    public static double positionValue(MarketData data, UUID playerId) {
        double value = 0.0D;
        for (StockDefinition definition : Stocks.ALL) {
            long held = data.holding(playerId, definition.id());
            if (held > 0L) {
                value += data.proceedsForShares(definition.id(), held);
            }
        }
        return value;
    }

    /**
     * 一次买卖。返回给玩家看的提示（成功或失败原因）。
     * 买入时额外收 1% 手续费，卖出时从到账里扣 1%，手续费进这只股票的分红池。
     */
    public static Component trade(ServerPlayer player, String stockId, int shares, boolean buying) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Component.translatable("message.shared_stock_exchange.no_server");
        }
        StockDefinition definition = Stocks.byId(stockId);
        if (definition == null) {
            return Component.translatable("message.shared_stock_exchange.unknown_stock", String.valueOf(stockId));
        }
        if (shares <= 0 || shares > MAX_SHARES_PER_TRADE) {
            return Component.translatable("message.shared_stock_exchange.bad_amount", MAX_SHARES_PER_TRADE);
        }
        MarketData data = data(server);
        UUID playerId = player.getUUID();
        data.setPlayerName(playerId, player.getName().getString());
        Component stockName = Component.translatable(Stocks.nameKey(stockId));

        if (buying) {
            return buy(player, server, data, definition, stockName, shares);
        }

        long owned = data.holding(playerId, stockId);
        if (owned <= 0L) {
            return Component.translatable("message.shared_stock_exchange.no_shares")
                    .withStyle(ChatFormatting.GRAY);
        }
        // 想卖的比手上有的多：直接按「全部卖出」成交，不报错（和买入超过盘面一样）
        long effective = Math.min((long) shares, owned);
        Sale sale = sell(player, server, data, definition, effective);
        if (!sale.ok()) {
            // 钱打不过去就先别卖，股票还留在账上
            return Component.translatable("message.shared_stock_exchange.payout_failed");
        }
        double profitRatio = sale.basisSold() <= 0.0D ? 0.0D : sale.profit() / sale.basisSold();
        Component message = Component.translatable("message.shared_stock_exchange.sold",
                        effective,
                        stockName,
                        MarketFormat.emc(sale.received()),
                        MarketFormat.emc(sale.fee()),
                        MarketFormat.emc(sale.profit()),
                        MarketFormat.percent(profitRatio),
                        MarketFormat.emc(data.price(stockId)))
                .withStyle(sale.profit() >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (effective < shares) {
            message = message.copy().append("\n").append(Component.translatable(
                    "message.shared_stock_exchange.sold_all", effective).withStyle(ChatFormatting.YELLOW));
        }
        if (sale.repaid() > 0L) {
            // 到账的钱先拿去还债了：说清楚，不然玩家只看到 EMC 没涨，会以为钱被吞了
            message = message.copy().append("\n")
                    .append(debtNote(sale.repaid(), outstandingDebt(server, player)));
        }
        return message;
    }

    private static Component buy(ServerPlayer player, MinecraftServer server, MarketData data,
                                 StockDefinition definition, Component stockName, int shares) {
        String stockId = definition.id();
        UUID playerId = player.getUUID();
        // 想买的比盘面还能卖的多，就按「能买的全部」成交，而不是报错
        long buyable = data.maxBuyable(stockId);
        if (buyable <= 0L) {
            return Component.translatable("message.shared_stock_exchange.not_enough_liquidity");
        }
        long effective = Math.min((long) shares, buyable);
        double costExact = data.costForShares(stockId, effective);
        // 超过 9e15 这种量级就不再算 long 了，免得金额溢出成负数
        if (!Double.isFinite(costExact) || costExact > MAX_SAFE_AMOUNT) {
            return Component.translatable("message.shared_stock_exchange.not_enough_liquidity");
        }
        long cost = Math.max(1L, (long) Math.ceil(costExact));
        long fee = MarketMath.feeOf(cost);
        long total = cost + fee;
        long cash = balance(server, player);
        double positionValue = positionValue(data, playerId);
        if (data.debtAllowed()) {
            // 负债系统开着：钱不够也能买，买完 EMC 就是负数（欠债）。
            // 只挡两种买法：会把数字撑爆的、以及买完立刻跌破强平线（下一秒就被卖掉）的。
            if ((double) cost - (double) cash > MarketMath.MAX_DEBT) {
                return Component.translatable("message.shared_stock_exchange.debt_too_large",
                        MarketFormat.emc(MarketMath.MAX_DEBT));
            }
            if (data.liquidationEnabled()
                    && !MarketMath.survivesMaintenance(cash, positionValue, cost, fee)) {
                return Component.translatable("message.shared_stock_exchange.would_liquidate",
                                MarketFormat.emc(MarketMath.equityOf(cash, positionValue)),
                                MarketFormat.emc(MarketMath.maintenanceLine(positionValue + cost)))
                        .withStyle(ChatFormatting.RED);
            }
        } else if ((double) total > Math.max(cash, 0L)) {
            // 负债系统关着：只能花手里的现金，说清楚差多少
            return Component.translatable("message.shared_stock_exchange.not_enough_emc",
                    MarketFormat.emc((double) total), MarketFormat.emc(cash));
        }
        if (!take(server, player, total)) {
            return Component.translatable(ProjectEmcBridge.negativeRejected()
                    ? "message.shared_stock_exchange.emc_negative_rejected"
                    : "message.shared_stock_exchange.emc_write_failed");
        }
        data.applyBuy(stockId, effective, cost);
        data.addHolding(playerId, stockId, effective);
        data.addCostBasis(playerId, stockId, total);
        data.addDividendPool(stockId, fee);
        data.recordPrice(stockId);
        announceMajorHolder(server, player, definition);
        Component message = Component.translatable("message.shared_stock_exchange.bought",
                        effective,
                        stockName,
                        MarketFormat.emc(total),
                        MarketFormat.emc(fee),
                        MarketFormat.emc(data.avgCost(playerId, stockId)),
                        MarketFormat.emc(data.price(stockId)))
                .withStyle(ChatFormatting.GREEN);
        if (effective < shares) {
            message = message.copy().append("\n").append(Component.translatable(
                    "message.shared_stock_exchange.bought_all", effective).withStyle(ChatFormatting.YELLOW));
        }
        return message;
    }

    /** 真正把股票卖掉并把 EMC 打过去。清仓和普通卖出都走这里，保证两边的算法一致。 */
    private static Sale sell(ServerPlayer player, MinecraftServer server, MarketData data,
                             StockDefinition definition, long shares) {
        String stockId = definition.id();
        UUID playerId = player.getUUID();
        long owned = data.holding(playerId, stockId);
        if (shares <= 0L || owned < shares) {
            return Sale.failed();
        }
        long gross = Math.max(0L, (long) Math.floor(data.proceedsForShares(stockId, shares)));
        long fee = MarketMath.feeOf(gross);
        long received = Math.max(gross - fee, 0L);
        // EMC 是负数时这笔钱会先把负数填回 0；这里先算出来，好让提示说清楚「有多少去还债了」
        long repaid = Math.min(outstandingDebt(server, player), received);
        if (!give(server, player, received)) {
            return Sale.failed();
        }
        double basisSold = data.costBasis(playerId, stockId) * ((double) shares / (double) owned);
        data.applySell(stockId, shares, gross);
        data.addHolding(playerId, stockId, -shares);
        data.addCostBasis(playerId, stockId, -basisSold);
        data.addDividendPool(stockId, fee);
        data.recordPrice(stockId);
        return new Sale(true, gross, fee, received, basisSold, repaid);
    }

    /**
     * 清仓：把这只股票（stockId 为空表示所有股票）的持仓全部卖掉。
     * 逐只结算，钱直接打进账上，最后给一条汇总提示。
     */
    public static Component liquidate(ServerPlayer player, String stockId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Component.translatable("message.shared_stock_exchange.no_server");
        }
        MarketData data = data(server);
        UUID playerId = player.getUUID();
        data.setPlayerName(playerId, player.getName().getString());
        boolean all = stockId == null || stockId.isEmpty();
        if (!all && Stocks.byId(stockId) == null) {
            return Component.translatable("message.shared_stock_exchange.unknown_stock", String.valueOf(stockId));
        }
        int soldKinds = 0;
        long totalShares = 0L;
        long totalReceived = 0L;
        long totalGross = 0L;
        long totalRepaid = 0L;
        double totalBasis = 0.0D;
        boolean failed = false;
        for (StockDefinition definition : Stocks.ALL) {
            if (!all && !definition.id().equals(stockId)) {
                continue;
            }
            long owned = data.holding(playerId, definition.id());
            if (owned <= 0L) {
                continue;
            }
            Sale sale = sell(player, server, data, definition, owned);
            if (!sale.ok()) {
                failed = true;
                continue;
            }
            soldKinds++;
            totalShares += owned;
            totalReceived += sale.received();
            totalGross += sale.gross();
            totalRepaid += sale.repaid();
            totalBasis += sale.basisSold();
        }
        if (soldKinds == 0) {
            if (failed) {
                return Component.translatable("message.shared_stock_exchange.payout_failed")
                        .withStyle(ChatFormatting.RED);
            }
            return Component.translatable("message.shared_stock_exchange.clear_nothing")
                    .withStyle(ChatFormatting.GRAY);
        }
        double profit = (double) totalReceived - totalBasis;
        double profitRatio = totalBasis <= 0.0D ? 0.0D : profit / totalBasis;
        Component message = failed
                ? Component.translatable("message.shared_stock_exchange.clear_partial",
                        soldKinds, totalShares, MarketFormat.emc(totalReceived),
                        MarketFormat.emc(profit), MarketFormat.percent(profitRatio))
                    .withStyle(ChatFormatting.YELLOW)
                : Component.translatable("message.shared_stock_exchange.cleared",
                        soldKinds, totalShares, MarketFormat.emc(totalReceived),
                        MarketFormat.emc(totalGross), MarketFormat.emc(profit),
                        MarketFormat.percent(profitRatio))
                    .withStyle(profit >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (totalRepaid > 0L) {
            message = message.copy().append("\n")
                    .append(debtNote(totalRepaid, outstandingDebt(server, player)));
        }
        return message;
    }

    /**
     * 每秒巡一次：谁欠着钱、权益又跌破了维持保证金线，就把他强制平仓。
     * 只看在线玩家——离线玩家的 EMC 写不进去，强平会变成"钱凭空消失"，所以不在离线时结算。
     * 强制平仓关掉的时候直接不巡（一个玩家都不看，几乎不花性能）。
     */
    public static void checkMarginCalls(MinecraftServer server) {
        MarketData data = data(server);
        if (!data.liquidationEnabled()) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            long cash = balance(server, online);
            double value = positionValue(data, online.getUUID());
            if (!MarketMath.needsLiquidation(cash, value)) {
                continue;
            }
            forceLiquidate(server, data, online);
        }
    }

    /**
     * 强制平仓：把这个人的股票全部按现价卖掉（照样收 1% 手续费），钱先去填欠的账。
     * 卖完还不够还的部分，EMC 就停在负数上——那就是他欠交易所的钱，要靠自己赚 EMC 还。
     */
    private static void forceLiquidate(MinecraftServer server, MarketData data, ServerPlayer player) {
        UUID playerId = player.getUUID();
        int kinds = 0;
        long totalShares = 0L;
        long totalReceived = 0L;
        double totalBasis = 0.0D;
        boolean failed = false;
        for (StockDefinition definition : Stocks.ALL) {
            long owned = data.holding(playerId, definition.id());
            if (owned <= 0L) {
                continue;
            }
            Sale sale = sell(player, server, data, definition, owned);
            if (!sale.ok()) {
                failed = true;
                continue;
            }
            kinds++;
            totalShares += owned;
            totalReceived += sale.received();
            totalBasis += sale.basisSold();
        }
        if (kinds == 0) {
            // 一股都没卖掉（多半是等价交换没响应），这一轮先算了，下次再查
            return;
        }
        long balance = balance(server, player);
        double loss = Math.max(totalBasis - (double) totalReceived, 0.0D);
        player.displayClientMessage(Component.translatable("message.shared_stock_exchange.forced_liquidation",
                        kinds, totalShares, MarketFormat.emc(totalReceived),
                        MarketFormat.emc(loss), MarketFormat.emc(balance))
                .withStyle(ChatFormatting.RED), false);
        if (balance < 0L) {
            player.displayClientMessage(Component.translatable("message.shared_stock_exchange.forced_liquidation_debt",
                    MarketFormat.emc(-balance)).withStyle(ChatFormatting.DARK_RED), false);
        }
        if (failed) {
            player.displayClientMessage(Component.translatable(
                    "message.shared_stock_exchange.forced_liquidation_partial")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /**
     * 未成年退款：欠债（EMC 是负数）时输入一次，把持仓和欠账一起清掉，EMC 重置成 1000。
     *
     * 为什么要连带清掉持仓：不然玩家可以用负 EMC 买一堆股票、退款拿到 1000 EMC 之后
     * 股票还在手上，等于凭空印钱。清掉持仓就没有这个漏洞——退款是"重新开始"，不是"领补贴"。
     */
    public static Component refund(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Component.translatable("message.shared_stock_exchange.no_server");
        }
        MarketData data = data(server);
        UUID playerId = player.getUUID();
        long cash = balance(server, player);
        if (cash >= 0L) {
            return Component.translatable("message.shared_stock_exchange.refund_not_needed")
                    .withStyle(ChatFormatting.GRAY);
        }
        long debt = -cash;
        long lostShares = 0L;
        for (StockDefinition definition : Stocks.ALL) {
            lostShares += data.holding(playerId, definition.id());
        }
        if (!setBalance(server, player, REFUND_EMC)) {
            return Component.translatable("message.shared_stock_exchange.emc_write_failed")
                    .withStyle(ChatFormatting.RED);
        }
        data.clearPositions(playerId);
        data.setLastBalance(playerId, REFUND_EMC);
        return Component.translatable("message.shared_stock_exchange.refund_done",
                        MarketFormat.emc(debt), lostShares, MarketFormat.emc(REFUND_EMC))
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * 关掉负债系统：之后买入只能花手里的现金，EMC 不会再被扣成负数。
     * 已经在负数上的人，EMC 直接清零（测试钱包和等价交换的负数都清）。
     * 离线的等价交换玩家手里的负数 EMC 写不进去，等他们上线时由 {@link #settleDebtIfDisabled} 补上。
     */
    public static int disableDebt(MinecraftServer server) {
        MarketData data = data(server);
        data.setDebtAllowed(false);
        int cleared = data.clearNegativeWallets();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!projecteActive()) {
                // 测试钱包的负数已经被 clearNegativeWallets 清掉了，这里只管等价交换
                continue;
            }
            long emc = ProjectEmcBridge.getEmc(online);
            // UNAVAILABLE 是 Long.MIN_VALUE，别拿来比大小
            if (emc == ProjectEmcBridge.UNAVAILABLE || emc >= 0L) {
                continue;
            }
            if (setBalance(server, online, 0L)) {
                cleared++;
                online.displayClientMessage(Component.translatable(
                        "message.shared_stock_exchange.debt_cleared_online",
                        MarketFormat.emc(-emc)).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return cleared;
    }

    /** 打开负债系统：之后钱不够也能买，等价交换的 EMC 会被扣成负数。 */
    public static void enableDebt(MinecraftServer server) {
        data(server).setDebtAllowed(true);
    }

    /** 玩家上线时补一次：负债系统关着的话，把他留下的负数 EMC 清零。 */
    public static void settleDebtIfDisabled(MinecraftServer server, ServerPlayer player) {
        if (debtAllowed(server)) {
            return;
        }
        long cash = balance(server, player);
        if (cash < 0L && setBalance(server, player, 0L)) {
            player.displayClientMessage(Component.translatable(
                    "message.shared_stock_exchange.debt_cleared_online",
                    MarketFormat.emc(-cash)).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /**
     * 有人成了某只股票的第一大股东（≥20%）就在全服说一声，联机时挺有排面。
     * 只在"第一大股东换人"的时候播报，同一个人反复加仓不会刷屏。
     */
    private static void announceMajorHolder(MinecraftServer server, ServerPlayer player, StockDefinition definition) {
        MarketData data = data(server);
        List<Map.Entry<UUID, Long>> top = data.holdersSorted(definition.id(), 1);
        if (top.isEmpty()) {
            return;
        }
        UUID leading = top.get(0).getKey();
        long heldByLeading = top.get(0).getValue();
        UUID recorded = data.topHolder(definition.id());
        if (leading.equals(recorded)) {
            return;
        }
        data.setTopHolder(definition.id(), leading);
        if (!leading.equals(player.getUUID()) || definition.totalShares() <= 0L) {
            return;
        }
        double ratio = (double) heldByLeading / (double) definition.totalShares();
        if (ratio < MAJOR_HOLDER_RATIO) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shared_stock_exchange.major_holder",
                        player.getName(),
                        Component.translatable(Stocks.nameKey(definition.id())),
                        MarketFormat.share(ratio)).withStyle(ChatFormatting.GOLD), false);
    }

    /** 每波动一次：轻微随机，同时慢慢往等价交换的 EMC 参考价回归。 */
    public static void drift(MinecraftServer server) {
        MarketData data = data(server);
        for (StockDefinition definition : Stocks.ALL) {
            double price = data.price(definition.id());
            if (!(price > 0.0D)) {
                continue;
            }
            double noise = RANDOM.nextGaussian() * 0.02D;
            double anchor = anchorPrice(definition);
            double revert = (anchor / price - 1.0D) * 0.04D;
            double multiplier = Mth.clamp(1.0D + noise + revert, 0.8D, 1.25D);
            data.setEmcPool(definition.id(), data.emcPool(definition.id()) * multiplier);
            data.recordPrice(definition.id());
        }
    }

    /**
     * 分红：把每只股票攒下的手续费按持股比例分给股东。
     * 在线的直接进 EMC，离线记一笔，上线时补发——这样联机服务器 24 小时开着也公平。
     */
    public static void dividendCycle(MinecraftServer server) {
        MarketData data = data(server);
        Map<UUID, Long> received = new HashMap<>();
        for (StockDefinition definition : Stocks.ALL) {
            double pool = data.dividendPool(definition.id());
            if (pool < 1.0D) {
                continue;
            }
            List<Map.Entry<UUID, Long>> holders = data.holders(definition.id());
            long totalHeld = data.totalHeld(definition.id());
            if (holders.isEmpty() || totalHeld <= 0L) {
                continue;
            }
            double paid = 0.0D;
            for (Map.Entry<UUID, Long> entry : holders) {
                long amount = (long) Math.floor(pool * (double) entry.getValue() / (double) totalHeld);
                if (amount <= 0L) {
                    continue;
                }
                paid += amount;
                UUID holder = entry.getKey();
                ServerPlayer online = server.getPlayerList().getPlayer(holder);
                if (online != null && give(server, online, amount)) {
                    received.merge(holder, amount, Long::sum);
                } else {
                    data.addPendingDividend(holder, amount);
                }
            }
            if (paid > 0.0D) {
                data.addDividendPool(definition.id(), -paid);
            }
        }
        for (Map.Entry<UUID, Long> entry : received.entrySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            if (online != null) {
                online.displayClientMessage(Component.translatable(
                        "message.shared_stock_exchange.dividend_received",
                        MarketFormat.emc(entry.getValue())).withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    /** 领走离线时攒下的分红（上线自动领，也可以 /stock claim 手动领）。 */
    public static Component claimDividends(MinecraftServer server, ServerPlayer player) {
        MarketData data = data(server);
        long pending = data.takePendingDividend(player.getUUID());
        if (pending <= 0L) {
            return Component.translatable("message.shared_stock_exchange.dividend_none").withStyle(ChatFormatting.GRAY);
        }
        if (!give(server, player, pending)) {
            data.addPendingDividend(player.getUUID(), pending);
            return Component.translatable("message.shared_stock_exchange.dividend_failed")
                    .withStyle(ChatFormatting.RED);
        }
        return Component.translatable("message.shared_stock_exchange.dividend_claimed",
                MarketFormat.emc(pending)).withStyle(ChatFormatting.GOLD);
    }

    /** 全服财富榜：现金 + 持仓市值，按总资产从高到低。 */
    public static List<Asset> ranking(MinecraftServer server) {
        MarketData data = data(server);
        Map<UUID, Double> assets = new HashMap<>();
        for (StockDefinition definition : Stocks.ALL) {
            for (Map.Entry<UUID, Long> entry : data.holders(definition.id())) {
                // 同样按「现在全部卖掉能拿回多少」算，财富榜才不会因为自己把价格买高而虚高
                assets.merge(entry.getKey(), data.proceedsForShares(definition.id(), entry.getValue()),
                        Double::sum);
            }
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            UUID id = online.getUUID();
            data.setPlayerName(id, online.getName().getString());
            assets.merge(id, (double) balance(server, online), Double::sum);
        }
        for (UUID id : data.knownPlayers()) {
            if (assets.containsKey(id)) {
                continue;
            }
            assets.put(id, (double) data.lastBalance(id));
        }
        List<Asset> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : assets.entrySet()) {
            list.add(new Asset(entry.getKey(), data.playerName(entry.getKey()), entry.getValue()));
        }
        list.sort((first, second) -> Double.compare(second.value(), first.value()));
        return list;
    }

    public static MarketSnapshotPayload snapshot(MinecraftServer server, ServerPlayer viewer) {
        MarketData data = data(server);
        int size = Stocks.ALL.size();
        double[] prices = new double[size];
        double[] emcPools = new double[size];
        double[] sharePools = new double[size];
        long[] holdings = new long[size];
        double[] costs = new double[size];
        double[] dividends = new double[size];
        double[] anchors = new double[size];
        String[] topNames = new String[size];
        long[] topShares = new long[size];
        float[] history = new float[size * MarketData.HISTORY_LENGTH];
        UUID viewerId = viewer.getUUID();
        data.setPlayerName(viewerId, viewer.getName().getString());
        for (int i = 0; i < size; i++) {
            StockDefinition definition = Stocks.ALL.get(i);
            prices[i] = data.price(definition.id());
            emcPools[i] = data.emcPool(definition.id());
            sharePools[i] = data.sharePool(definition.id());
            holdings[i] = data.holding(viewerId, definition.id());
            costs[i] = data.avgCost(viewerId, definition.id());
            dividends[i] = data.dividendPool(definition.id());
            anchors[i] = anchorPrice(definition);
            List<Map.Entry<UUID, Long>> top = data.holdersSorted(definition.id(), 1);
            if (top.isEmpty()) {
                topNames[i] = "";
                topShares[i] = 0L;
            } else {
                topNames[i] = data.playerName(top.get(0).getKey());
                topShares[i] = top.get(0).getValue();
            }
            float[] samples = data.historyArray(definition.id());
            System.arraycopy(samples, 0, history, i * MarketData.HISTORY_LENGTH, MarketData.HISTORY_LENGTH);
        }
        long balance = balance(server, viewer);
        data.setLastBalance(viewerId, balance);
        return new MarketSnapshotPayload(prices, emcPools, sharePools, holdings, costs, history, dividends, anchors,
                topNames, topShares, balance, data.pendingDividend(viewerId), cashMode(), data.driftSeconds(),
                data.debtAllowed(), data.liquidationEnabled());
    }

    /** 把最新的行情推给全服所有玩家，让每个人看到的盘面都是同一份。 */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, snapshot(server, player));
        }
    }

    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(server, player));
    }

    /** 服务器每 tick 的节奏控制：定期波动 + 定期强平检查 + 定期同步 + 定期分红。间隔都从存档里读，可自定义。 */
    public static void serverTick(MinecraftServer server) {
        MarketData data = data(server);
        if (!primed) {
            primed = true;
            for (StockDefinition definition : Stocks.ALL) {
                data.recordPrice(definition.id());
            }
        }
        ticksSinceDrift++;
        ticksSinceDividend++;
        ticksSinceSync++;
        ticksSinceMargin++;
        if (ticksSinceDrift >= data.driftTicks()) {
            ticksSinceDrift = 0;
            drift(server);
        }
        // 价格变了才可能有人爆仓，所以紧跟波动之后再查一次，另外每秒也查一次（交易也会推价）
        if (ticksSinceMargin >= MARGIN_CHECK_TICKS) {
            ticksSinceMargin = 0;
            checkMarginCalls(server);
        }
        if (ticksSinceDividend >= DIVIDEND_INTERVAL_TICKS) {
            ticksSinceDividend = 0;
            dividendCycle(server);
        }
        if (ticksSinceSync >= SYNC_INTERVAL_TICKS) {
            ticksSinceSync = 0;
            broadcast(server);
        }
    }
}
