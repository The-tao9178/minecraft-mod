package cn.blockforge.generated.sharedstockexchange.command;

import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import cn.blockforge.generated.sharedstockexchange.market.MarketData;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketManager;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import cn.blockforge.generated.sharedstockexchange.market.Stocks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * /stock 命令：不方便开界面时也能查行情、看持仓、下单。
 *   /stock list            看全服行情
 *   /stock mine            看自己的持仓、均价和盈亏
 *   /stock buy <股票> <股数>
 *   /stock sell <股票> <股数>   （股数超过持仓就全部卖出）
 *   /stock status          看现金是不是接的等价交换 EMC、两个开关的状态、外加三步自检
 *   /stock dividend        领离线时攒下的分红
 *   /stock top             看全服财富榜
 *   /stock refund          未成年退款：EMC 是负数时清掉持仓和负数，EMC 重置为 1000
 *   /stock debt on|off     负债系统开关（EMC 能不能是负数），默认开
 *   /stock liquidation on|off  强制平仓开关，默认开
 *   /stock wallet <数额>    重置测试钱包（没装等价交换时用）
 *   /stock interval <秒>    改行情波动间隔
 *
 * 注意：每条输出都直接发 Component，不在这里拼字符串——专用服务器上没有客户端语言文件，
 * 服务端自己 .getString() 会出错，交给客户端翻译才稳。
 */
public final class StockCommand {
    private static final SuggestionProvider<CommandSourceStack> STOCK_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(Stocks.ALL.stream().map(StockDefinition::id).toList(), builder);

    private StockCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stock")
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("mine").executes(context -> portfolio(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("top").executes(context -> top(context.getSource())))
                .then(Commands.literal("dividend").executes(context -> claim(context.getSource())))
                .then(Commands.literal("claim").executes(context -> claim(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource(), ""))
                        .then(Commands.argument("stock", StringArgumentType.word()).suggests(STOCK_IDS)
                                .executes(context -> clear(context.getSource(),
                                        StringArgumentType.getString(context, "stock")))))
                .then(Commands.literal("interval")
                        // 服务器要 OP 才能改；单人存档里房主自己就能改，省得为了调快慢去开作弊
                        .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                        .then(Commands.argument("seconds",
                                        IntegerArgumentType.integer(MarketData.MIN_DRIFT_SECONDS, MarketData.MAX_DRIFT_SECONDS))
                                .executes(StockCommand::setInterval)))
                .then(Commands.literal("wallet")
                        // 和 /stock interval 一样：管理员或单人存档房主才能改
                        .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                        .then(Commands.argument("emc", LongArgumentType.longArg(0L, 1_000_000_000_000L))
                                .executes(StockCommand::setWallet)))
                .then(Commands.literal("chart")
                        .executes(context -> chart(context.getSource(), Stocks.ALL.get(0).id()))
                        .then(Commands.argument("stock", StringArgumentType.word()).suggests(STOCK_IDS)
                                .executes(context -> chart(context.getSource(),
                                        StringArgumentType.getString(context, "stock")))))
                // 未成年退款：谁欠债谁能用，不需要 OP——这是给"玩崩了"的人留的退路
                .then(Commands.literal("refund").executes(context -> refund(context.getSource())))
                // 负债系统：EMC 能不能变成负数。管理员或单人存档房主才能改
                .then(Commands.literal("debt")
                        .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                        .executes(context -> showSwitches(context.getSource()))
                        .then(Commands.literal("on").executes(context -> setDebt(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setDebt(context.getSource(), false))))
                // 强制平仓：欠债跌破强平线时要不要自动卖掉股票
                .then(Commands.literal("liquidation")
                        .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                        .executes(context -> showSwitches(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setLiquidation(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLiquidation(context.getSource(), false))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("stock", StringArgumentType.word()).suggests(STOCK_IDS)
                                .then(Commands.argument("shares", IntegerArgumentType.integer(1, MarketManager.MAX_SHARES_PER_TRADE))
                                        .executes(context -> trade(context, true)))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("stock", StringArgumentType.word()).suggests(STOCK_IDS)
                                .then(Commands.argument("shares", IntegerArgumentType.integer(1, MarketManager.MAX_SHARES_PER_TRADE))
                                        .executes(context -> trade(context, false))))));
    }

    /** 一行一条消息发出去，客户端按自己的语言显示。 */
    private static int sendLines(CommandSourceStack source, List<Component> lines) {
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static MutableComponent join(Object... parts) {
        MutableComponent line = Component.empty();
        for (Object part : parts) {
            if (part instanceof Component component) {
                line.append(component);
            } else {
                line.append(String.valueOf(part));
            }
        }
        return line;
    }

    private static int list(CommandSourceStack source) {
        MarketData data = MarketManager.data(source.getServer());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.list_header").withStyle(ChatFormatting.GOLD));
        for (StockDefinition definition : Stocks.ALL) {
            double price = data.price(definition.id());
            double anchor = MarketManager.anchorPrice(definition);
            double vsBase = anchor <= 0.0D ? 0.0D : price / anchor - 1.0D;
            lines.add(Component.translatable("command.shared_stock_exchange.list_row",
                    Component.translatable(Stocks.nameKey(definition.id())),
                    definition.id(),
                    MarketFormat.emc(price),
                    MarketFormat.percent(vsBase),
                    MarketFormat.emc(data.marketCap(definition)),
                    Component.translatable("command.shared_stock_exchange.pool"),
                    MarketFormat.emc(data.dividendPool(definition.id())))
                    .withStyle(vsBase >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return sendLines(source, lines);
    }

    private static int portfolio(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        MarketData data = MarketManager.data(source.getServer());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.portfolio_header").withStyle(ChatFormatting.GOLD));
        double totalValue = 0.0D;
        double totalCost = 0.0D;
        for (StockDefinition definition : Stocks.ALL) {
            long held = data.holding(player.getUUID(), definition.id());
            if (held <= 0L) {
                continue;
            }
            double price = data.price(definition.id());
            double avg = data.avgCost(player.getUUID(), definition.id());
            // 持仓值多少钱用「现在全部卖掉能拿回多少」，和界面、保证金同一个口径
            double value = data.proceedsForShares(definition.id(), held);
            double pnl = value - avg * (double) held;
            double cost = avg * (double) held;
            totalValue += value;
            totalCost += cost;
            lines.add(Component.translatable("command.shared_stock_exchange.portfolio_row",
                    Component.translatable(Stocks.nameKey(definition.id())),
                    held,
                    MarketFormat.emc(avg),
                    MarketFormat.emc(price),
                    MarketFormat.emc(pnl),
                    MarketFormat.percent(cost <= 0.0D ? 0.0D : pnl / cost),
                    Component.translatable("command.shared_stock_exchange.holding_share",
                            MarketFormat.share(definition.totalShares() <= 0L
                                    ? 0.0D
                                    : (double) held / (double) definition.totalShares())))
                    .withStyle(pnl >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (lines.size() == 1) {
            lines.add(Component.translatable("command.shared_stock_exchange.portfolio_empty"));
            return sendLines(source, lines);
        }
        double totalPnl = totalValue - totalCost;
        lines.add(Component.translatable("command.shared_stock_exchange.portfolio_total",
                MarketFormat.emc(totalValue),
                MarketFormat.emc(totalPnl),
                MarketFormat.percent(totalCost <= 0.0D ? 0.0D : totalPnl / totalCost))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("command.shared_stock_exchange.portfolio_cash",
                MarketFormat.emc(MarketManager.balance(source.getServer(), player)),
                MarketFormat.emc(data.pendingDividend(player.getUUID())))
                .withStyle(ChatFormatting.GOLD));
        return sendLines(source, lines);
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        MarketData data = MarketManager.data(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.status_header").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("command.shared_stock_exchange.status_cash",
                ProjectEmcBridge.describeComponent()));
        if (source.getEntity() instanceof ServerPlayer player) {
            long cash = MarketManager.balance(server, player);
            double positionValue = MarketManager.positionValue(data, player.getUUID());
            lines.add(Component.translatable("command.shared_stock_exchange.status_wallet",
                    MarketFormat.emc(cash),
                    MarketFormat.emc(data.pendingDividend(player.getUUID()))));
        }
        // 两个开关各管一件事：负债系统管 EMC 能不能为负，强制平仓管欠债了要不要卖股票
        lines.add(Component.translatable("command.shared_stock_exchange.status_switches",
                onOff(data.debtAllowed()), onOff(data.liquidationEnabled()))
                .withStyle(ChatFormatting.AQUA));
        if (source.getEntity() instanceof ServerPlayer player) {
            long cash = MarketManager.balance(server, player);
            double positionValue = MarketManager.positionValue(data, player.getUUID());
            if (cash < 0L || positionValue > 0.0D) {
                // 欠债时把权益和强平线摆出来；负债系统关着的话强平线没有意义，就不显示
                if (data.liquidationEnabled()) {
                    lines.add(Component.translatable("command.shared_stock_exchange.status_margin",
                                    MarketFormat.emc(MarketMath.equityOf(cash, positionValue)),
                                    MarketFormat.emc(Math.max(-cash, 0L)),
                                    MarketFormat.emc(MarketMath.maintenanceLine(positionValue)))
                            .withStyle(MarketMath.needsLiquidation(cash, positionValue)
                                    ? ChatFormatting.RED : ChatFormatting.GRAY));
                } else {
                    lines.add(Component.translatable("command.shared_stock_exchange.status_margin_off",
                            MarketFormat.emc(MarketMath.equityOf(cash, positionValue)),
                            MarketFormat.emc(Math.max(-cash, 0L))).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        lines.add(Component.translatable("command.shared_stock_exchange.status_fee",
                (int) Math.round(MarketManager.FEE_RATE * 100.0D),
                MarketManager.DIVIDEND_INTERVAL_TICKS / 1200));
        lines.add(Component.translatable("command.shared_stock_exchange.status_drift",
                data.driftSeconds(), MarketData.MIN_DRIFT_SECONDS, MarketData.MAX_DRIFT_SECONDS));
        for (StockDefinition definition : Stocks.ALL) {
            lines.add(Component.translatable("command.shared_stock_exchange.status_row",
                    Component.translatable(Stocks.nameKey(definition.id())),
                    MarketFormat.emc(data.price(definition.id())),
                    MarketFormat.emc(MarketManager.anchorPrice(definition)),
                    Component.translatable("command.shared_stock_exchange.pool"),
                    MarketFormat.emc(data.dividendPool(definition.id()))));
        }
        // 等价交换到底接没接上的三步自检，全绿才算真的通了
        for (Component line : ProjectEmcBridge.diagnostics()) {
            lines.add(line.copy().withStyle(ChatFormatting.GRAY));
        }
        return sendLines(source, lines);
    }

    /** 开关的显示文字：开 / 关（走语言文件，英文客户端也认）。 */
    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled
                ? "command.shared_stock_exchange.switch_on"
                : "command.shared_stock_exchange.switch_off");
    }

    /** 只看不改：/stock debt 或 /stock liquidation 不带参数时，把两个开关的状态念一遍。 */
    private static int showSwitches(CommandSourceStack source) {
        MarketData data = MarketManager.data(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.switches_now",
                onOff(data.debtAllowed()), onOff(data.liquidationEnabled())), false);
        return 1;
    }

    /** /stock debt on|off：负债系统，决定 EMC 能不能变成负数。 */
    private static int setDebt(CommandSourceStack source, boolean enabled) {
        MinecraftServer server = source.getServer();
        if (enabled) {
            MarketManager.enableDebt(server);
            source.sendSuccess(() -> Component.translatable(
                    "command.shared_stock_exchange.debt_on").withStyle(ChatFormatting.GREEN), true);
        } else {
            int cleared = MarketManager.disableDebt(server);
            source.sendSuccess(() -> Component.translatable(
                    "command.shared_stock_exchange.debt_off", cleared).withStyle(ChatFormatting.YELLOW), true);
        }
        MarketManager.broadcast(server);
        return 1;
    }

    /** /stock liquidation on|off：强制平仓，决定欠债跌破强平线时要不要自动卖股票。 */
    private static int setLiquidation(CommandSourceStack source, boolean enabled) {
        MinecraftServer server = source.getServer();
        MarketManager.data(server).setLiquidationEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(enabled
                        ? "command.shared_stock_exchange.liquidation_on"
                        : "command.shared_stock_exchange.liquidation_off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        MarketManager.broadcast(server);
        return 1;
    }

    /** /stock refund：EMC 是负数时把持仓和负数一起清掉，EMC 重置为 1000。 */
    private static int refund(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        Component feedback = MarketManager.refund(player);
        source.sendSuccess(() -> feedback, false);
        MarketManager.broadcast(source.getServer());
        return 1;
    }

    private static int claim(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        Component feedback = MarketManager.claimDividends(source.getServer(), player);
        source.sendSuccess(() -> feedback, false);
        MarketManager.broadcast(source.getServer());
        return 1;
    }

    /** /stock clear [股票]：把持仓全卖掉（不带参数是清仓所有股票）。 */
    private static int clear(CommandSourceStack source, String stockId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        Component feedback = MarketManager.liquidate(player, stockId);
        source.sendSuccess(() -> feedback, false);
        MarketManager.broadcast(source.getServer());
        return 1;
    }

    /** /stock interval <秒>：管理员改行情波动快慢，改完存进存档，重启也还在。 */
    /**
     * /stock wallet <数额>：重置模组自带的测试钱包。
     * 只在没装等价交换时有用——被强制平仓到负 EMC 以后，测试钱包没有别的收入来源，
     * 给单人存档留一个「重新开始」的口子，免得把自己玩死在里面。
     */
    private static int setWallet(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        if (MarketManager.projecteActive()) {
            source.sendFailure(Component.translatable("command.shared_stock_exchange.wallet_emc"));
            return 0;
        }
        long emc = LongArgumentType.getLong(context, "emc");
        MarketManager.data(source.getServer()).setTestWallet(player.getUUID(), emc);
        source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.wallet_set",
                MarketFormat.emc(emc)), false);
        MarketManager.broadcast(source.getServer());
        return 1;
    }

    private static int setInterval(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        MarketData data = MarketManager.data(source.getServer());
        int applied = data.setDriftSeconds(seconds);
        source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.interval_set", applied)
                .withStyle(ChatFormatting.GREEN), true);
        MarketManager.broadcast(source.getServer());
        return applied;
    }

    /** /stock chart [股票]：在聊天栏里用方块字符画一条走势线，不用开界面也能看涨跌。 */
    private static int chart(CommandSourceStack source, String stockId) {
        StockDefinition definition = Stocks.byId(stockId);
        if (definition == null) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.unknown_stock", stockId));
            return 0;
        }
        MarketData data = MarketManager.data(source.getServer());
        float[] history = data.historyArray(definition.id());
        float first = history[0];
        float last = history[history.length - 1];
        float high = first;
        float low = first;
        for (float value : history) {
            high = Math.max(high, value);
            low = Math.min(low, value);
        }
        double change = first <= 0.0F ? 0.0D : (double) last / (double) first - 1.0D;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.chart_header",
                Component.translatable(Stocks.nameKey(definition.id()))).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(sparkline(history)).withStyle(change >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("command.shared_stock_exchange.chart_stats",
                        MarketFormat.emc(last), MarketFormat.percent(change),
                        MarketFormat.emc(high), MarketFormat.emc(low),
                        MarketFormat.emc(MarketManager.anchorPrice(definition)))
                .withStyle(change >= 0.0D ? ChatFormatting.GREEN : ChatFormatting.RED));
        long held = 0L;
        double avgCost = 0.0D;
        if (source.getEntity() instanceof ServerPlayer player) {
            held = data.holding(player.getUUID(), definition.id());
            avgCost = data.avgCost(player.getUUID(), definition.id());
        }
        if (held > 0L) {
            lines.add(Component.translatable("command.shared_stock_exchange.chart_mine",
                            held, MarketFormat.emc(avgCost))
                    .withStyle(ChatFormatting.AQUA));
        }
        return sendLines(source, lines);
    }

    /** 把 120 个价格点压成一行方块字符，越低越矮。 */
    private static String sparkline(float[] values) {
        String levels = "\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588";
        int columns = 48;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        float span = max - min;
        StringBuilder builder = new StringBuilder(columns);
        for (int i = 0; i < columns; i++) {
            int index = values.length <= 1
                    ? 0
                    : (int) Math.round((double) i * (double) (values.length - 1) / (double) (columns - 1));
            double ratio = span < 1.0E-6F ? 0.5D : ((double) values[index] - (double) min) / (double) span;
            int level = (int) Math.round(ratio * (double) (levels.length() - 1));
            level = Math.max(0, Math.min(levels.length() - 1, level));
            builder.append(levels.charAt(level));
        }
        return builder.toString();
    }

    private static int top(CommandSourceStack source) {
        List<MarketManager.Asset> ranking = MarketManager.ranking(source.getServer());
        if (ranking.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.top_empty"), false);
            return 1;
        }
        String self = source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.top_header").withStyle(ChatFormatting.GOLD));
        int shown = 0;
        for (MarketManager.Asset asset : ranking) {
            if (shown >= 10) {
                break;
            }
            shown++;
            Component marker = asset.name().equals(self)
                    ? join("  ← ", Component.translatable("command.shared_stock_exchange.you").withStyle(ChatFormatting.AQUA))
                    : Component.empty();
            lines.add(Component.translatable("command.shared_stock_exchange.top_row",
                    shown, asset.name(), MarketFormat.emc(asset.value()), marker));
        }
        return sendLines(source, lines);
    }

    private static int trade(CommandContext<CommandSourceStack> context, boolean buying) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
            return 0;
        }
        String stockId = StringArgumentType.getString(context, "stock");
        int shares = IntegerArgumentType.getInteger(context, "shares");
        Component feedback = MarketManager.trade(player, stockId, shares, buying);
        source.sendSuccess(() -> feedback, false);
        MarketManager.broadcast(source.getServer());
        return 1;
    }
}
