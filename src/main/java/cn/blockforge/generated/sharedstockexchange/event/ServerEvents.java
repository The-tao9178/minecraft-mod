package cn.blockforge.generated.sharedstockexchange.event;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.command.StockCommand;
import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import cn.blockforge.generated.sharedstockexchange.market.MarketData;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务器端事件：玩家进服先推一次行情、补发离线分红，之后定期波动、定期同步，并注册 /stock 命令。
 */
@EventBusSubscriber(modid = StockExchangeMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        MarketManager.serverTick(server);
    }

    /** 开服时在日志里写清楚：现在用的是等价交换的 EMC，还是模组自带的测试钱包。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StockExchangeMod.LOGGER.info("[共享证券交易所] 现金来源：{}", MarketManager.cashModeText());
        for (Component line : ProjectEmcBridge.diagnostics()) {
            StockExchangeMod.LOGGER.info("[共享证券交易所] 自检：{}", plainText(line));
        }
    }

    /** 专用服务器上没有客户端语言文件，直接 getString() 会炸，所以兜一层。 */
    private static String plainText(Component component) {
        try {
            return component.getString();
        } catch (Throwable throwable) {
            return component.toString();
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        MarketData data = MarketManager.data(server);
        data.setPlayerName(player.getUUID(), player.getName().getString());
        if (!MarketManager.projecteActive()) {
            String key = ProjectEmcBridge.installedButDisconnected()
                    ? "message.shared_stock_exchange.cash_hint_broken"
                    : "message.shared_stock_exchange.cash_hint_test";
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), false);
        }
        long pending = data.pendingDividend(player.getUUID());
        if (pending > 0L) {
            Component claimed = MarketManager.claimDividends(server, player);
            player.displayClientMessage(claimed, false);
        }
        // 负债系统关着的话，先把上次留下的负数 EMC 清零（离线时写不进去，只能等上线）
        MarketManager.settleDebtIfDisabled(server, player);
        // 第一次有玩家进来时顺手自检一次：转化桌到底认不认负数 EMC
        // （拿他自己的 EMC 试一下，写完立刻还原，不会动到他的钱；整个服务器只做一次）
        Boolean keepsNegative = ProjectEmcBridge.probeTable(player);
        if (keepsNegative != null) {
            StockExchangeMod.LOGGER.info("[共享证券交易所] 负数 EMC 自检：{}",
                    keepsNegative ? "转化桌可以保留负数，卖东西会正常抵扣欠款"
                            : "这个版本的等价交换会把负数夹成 0，欠款暂时只能用 /stock refund 处理");
        }
        // 欠债的玩家一上线就说清楚：EMC 是负数，以及能怎么处理
        long cash = MarketManager.balance(server, player);
        if (cash < 0L) {
            player.displayClientMessage(Component.translatable("message.shared_stock_exchange.debt_hint",
                    MarketFormat.emc(-cash)).withStyle(ChatFormatting.RED), false);
        }
        MarketManager.sendTo(player);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StockCommand.register(event.getDispatcher());
    }
}
