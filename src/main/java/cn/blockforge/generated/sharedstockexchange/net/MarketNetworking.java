package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketData;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络层：客户端只发「要刷行情」「要买/卖」「要领分红」「要改设置」，
 * 服务器算完（顺带查权限）把最新行情推回给所有人。
 */
public final class MarketNetworking {
    private MarketNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 协议版本 5：动作包里多了 amount 字段（设置面板要传秒数 / EMC 金额），
        // 还多了改间隔、改钱包、两个开关、退款这几个动作
        PayloadRegistrar registrar = event.registrar("5");
        registrar.playToServer(MarketActionPayload.TYPE, MarketActionPayload.STREAM_CODEC, MarketNetworking::handleAction);
        registrar.playToClient(MarketSnapshotPayload.TYPE, MarketSnapshotPayload.STREAM_CODEC, MarketNetworking::handleSnapshot);
    }

    private static void handleAction(MarketActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            int action = payload.action();
            if (action == MarketActionPayload.ACTION_SYNC) {
                PacketDistributor.sendToPlayer(player, MarketManager.snapshot(server, player));
                return;
            }
            if (action == MarketActionPayload.ACTION_CLAIM) {
                Component feedback = MarketManager.claimDividends(server, player);
                player.displayClientMessage(feedback, false);
                MarketManager.broadcast(server);
                return;
            }
            if (action == MarketActionPayload.ACTION_LIQUIDATE) {
                Component feedback = MarketManager.liquidate(player, payload.stockId());
                player.displayClientMessage(feedback, false);
                MarketManager.broadcast(server);
                return;
            }
            if (action >= MarketActionPayload.ACTION_SET_INTERVAL) {
                handleSetting(payload, player, server);
                return;
            }
            boolean buying = action == MarketActionPayload.ACTION_BUY;
            Component feedback = MarketManager.trade(player, payload.stockId(), payload.shares(), buying);
            player.displayClientMessage(feedback, false);
            MarketManager.broadcast(server);
        });
    }

    /**
     * 设置面板发来的改动。和 /stock 命令一样的两条规矩：
     *   · 未成年退款谁都能点（这是给「玩崩了」的人留的退路）；
     *   · 其余设置只有管理员或单人存档房主能改，客户端那边只是不让点，服务器这里才是真正把关的。
     */
    private static void handleSetting(MarketActionPayload payload, ServerPlayer player, MinecraftServer server) {
        int action = payload.action();
        if (action == MarketActionPayload.ACTION_REFUND) {
            player.displayClientMessage(MarketManager.refund(player), false);
            MarketManager.broadcast(server);
            return;
        }
        if (!mayConfigure(server, player)) {
            player.displayClientMessage(Component.translatable(
                    "message.shared_stock_exchange.settings_no_permission").withStyle(ChatFormatting.RED), false);
            return;
        }
        switch (action) {
            case MarketActionPayload.ACTION_SET_INTERVAL -> {
                int seconds = (int) Math.max(MarketData.MIN_DRIFT_SECONDS,
                        Math.min(MarketData.MAX_DRIFT_SECONDS, payload.amount()));
                int applied = MarketManager.data(server).setDriftSeconds(seconds);
                player.displayClientMessage(Component.translatable(
                        "command.shared_stock_exchange.interval_set", applied).withStyle(ChatFormatting.GREEN), false);
            }
            case MarketActionPayload.ACTION_SET_WALLET -> {
                if (MarketManager.projecteActive()) {
                    player.displayClientMessage(Component.translatable(
                            "command.shared_stock_exchange.wallet_emc").withStyle(ChatFormatting.RED), false);
                    return;
                }
                long emc = Math.max(0L, Math.min(1_000_000_000_000L, payload.amount()));
                MarketManager.data(server).setTestWallet(player.getUUID(), emc);
                player.displayClientMessage(Component.translatable(
                        "command.shared_stock_exchange.wallet_set", MarketFormat.emc(emc)), false);
            }
            case MarketActionPayload.ACTION_SET_DEBT -> {
                if (payload.shares() != 0) {
                    MarketManager.enableDebt(server);
                    player.displayClientMessage(Component.translatable(
                            "command.shared_stock_exchange.debt_on").withStyle(ChatFormatting.GREEN), false);
                } else {
                    int cleared = MarketManager.disableDebt(server);
                    player.displayClientMessage(Component.translatable(
                            "command.shared_stock_exchange.debt_off", cleared).withStyle(ChatFormatting.YELLOW), false);
                }
            }
            case MarketActionPayload.ACTION_SET_LIQUIDATION -> {
                boolean enabled = payload.shares() != 0;
                MarketManager.data(server).setLiquidationEnabled(enabled);
                player.displayClientMessage(Component.translatable(enabled
                                ? "command.shared_stock_exchange.liquidation_on"
                                : "command.shared_stock_exchange.liquidation_off")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            }
            default -> {
                return;
            }
        }
        MarketManager.broadcast(server);
    }

    /** 管理员或单人存档房主：和 /stock interval、/stock debt 这些命令的门槛一致。 */
    private static boolean mayConfigure(MinecraftServer server, ServerPlayer player) {
        return player.hasPermissions(2) || server.isSingleplayer();
    }

    private static void handleSnapshot(MarketSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MarketCache.update(payload));
    }
}
