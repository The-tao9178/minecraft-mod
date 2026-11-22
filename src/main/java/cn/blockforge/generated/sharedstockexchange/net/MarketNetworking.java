package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MarketNetworking {
   private static final String PROTOCOL = "5";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(StockExchangeMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
   );

   private MarketNetworking() {
   }

   public static void register(IEventBus modBus) {
      CHANNEL.registerMessage(0, MarketActionPayload.class, MarketActionPayload::encode, MarketActionPayload::decode, (payload, context) -> {
         NetworkEvent.Context ctx = context.get();
         ctx.enqueueWork(() -> handleAction(payload, ctx.getSender()));
         ctx.setPacketHandled(true);
      });
      CHANNEL.registerMessage(1, MarketSnapshotPayload.class, MarketSnapshotPayload::encode, MarketSnapshotPayload::decode, (payload, context) -> {
         NetworkEvent.Context ctx = context.get();
         ctx.enqueueWork(() -> MarketCache.update(payload));
         ctx.setPacketHandled(true);
      });
   }

   public static void sendToServer(MarketActionPayload payload) {
      CHANNEL.sendToServer(payload);
   }

   public static void sendToPlayer(MarketSnapshotPayload payload, ServerPlayer player) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
   }

   private static void handleAction(MarketActionPayload payload, ServerPlayer player) {
      if (player == null) {
         return;
      }

      MinecraftServer server = player.getServer();
      if (server == null) {
         return;
      }

      int action = payload.action();
      if (action == 0) {
         sendToPlayer(MarketManager.snapshot(server, player), player);
      } else if (action == 3) {
         Component feedback = MarketManager.claimDividends(server, player);
         player.displayClientMessage(feedback, false);
         MarketManager.broadcast(server);
      } else if (action == 4) {
         Component feedback = MarketManager.liquidate(player, payload.stockId());
         player.displayClientMessage(feedback, false);
         MarketManager.broadcast(server);
      } else if (action >= 5) {
         handleSetting(payload, player, server);
      } else {
         boolean buying = action == 1;
         Component feedback = MarketManager.trade(player, payload.stockId(), payload.shares(), buying);
         player.displayClientMessage(feedback, false);
         MarketManager.broadcast(server);
      }
   }

   private static void handleSetting(MarketActionPayload payload, ServerPlayer player, MinecraftServer server) {
      int action = payload.action();
      if (action == 9) {
         player.displayClientMessage(MarketManager.refund(player), false);
         MarketManager.broadcast(server);
      } else if (!mayConfigure(server, player)) {
         player.displayClientMessage(Component.translatable("message.shared_stock_exchange.settings_no_permission").withStyle(ChatFormatting.RED), false);
      } else {
         switch (action) {
            case 5:
               int seconds = (int)Math.max(5L, Math.min(3600L, payload.amount()));
               int applied = MarketManager.data(server).setDriftSeconds(seconds);
               player.displayClientMessage(
                  Component.translatable("command.shared_stock_exchange.interval_set", new Object[]{applied}).withStyle(ChatFormatting.GREEN), false
               );
               break;
            case 6:
               long emc = Math.max(0L, Math.min(1000000000000L, payload.amount()));
               MarketManager.data(server).setTestWallet(player.getUUID(), emc);
               player.displayClientMessage(
                  Component.translatable("command.shared_stock_exchange.wallet_set", new Object[]{MarketFormat.emc((double)emc)}), false
               );
               break;
            case 7:
               if (payload.shares() != 0) {
                  MarketManager.enableDebt(server);
                  player.displayClientMessage(Component.translatable("command.shared_stock_exchange.debt_on").withStyle(ChatFormatting.GREEN), false);
               } else {
                  int cleared = MarketManager.disableDebt(server);
                  player.displayClientMessage(
                     Component.translatable("command.shared_stock_exchange.debt_off", new Object[]{cleared}).withStyle(ChatFormatting.YELLOW), false
                  );
               }
               break;
            case 8:
               boolean enabled = payload.shares() != 0;
               MarketManager.data(server).setLiquidationEnabled(enabled);
               player.displayClientMessage(
                  Component.translatable(enabled ? "command.shared_stock_exchange.liquidation_on" : "command.shared_stock_exchange.liquidation_off")
                     .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                  false
               );
               break;
            default:
               return;
         }

         MarketManager.broadcast(server);
      }
   }

   private static boolean mayConfigure(MinecraftServer server, ServerPlayer player) {
      return player.hasPermissions(2) || server.isSingleplayer();
   }
}
