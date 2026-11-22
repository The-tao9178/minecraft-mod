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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
   modid = StockExchangeMod.MOD_ID,
   bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ServerEvents {
   private ServerEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         MarketManager.serverTick(event.getServer());
      }
   }

   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      StockExchangeMod.LOGGER.info("[共享证券交易所] 现金来源：{}", MarketManager.cashModeText());

      for (Component line : ProjectEmcBridge.diagnostics()) {
         StockExchangeMod.LOGGER.info("[共享证券交易所] 自检：{}", plainText(line));
      }
   }

   private static String plainText(Component component) {
      try {
         return component.getString();
      } catch (Throwable var2) {
         return component.toString();
      }
   }

   @SubscribeEvent
   public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         MinecraftServer server = player.getServer();
         if (server != null) {
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

            MarketManager.settleDebtIfDisabled(server, player);
            Boolean keepsNegative = ProjectEmcBridge.probeTable();
            if (keepsNegative != null) {
               StockExchangeMod.LOGGER
                  .info(
                     "[共享证券交易所] 负数 EMC 自检：{}",
                     keepsNegative ? "转化桌保留了负数，卖东西只会一点一点还欠款" : "这个版本的等价交换会把负数夹成 0，欠款暂时只能用 /stock refund 处理"
                  );
            }

            long cash = MarketManager.balance(server, player);
            if (cash < 0L) {
               player.displayClientMessage(
                  Component.translatable("message.shared_stock_exchange.debt_hint", new Object[]{MarketFormat.emc((double)(-cash))})
                     .withStyle(ChatFormatting.RED),
                  false
               );
            }

            MarketManager.sendTo(player);
         }
      }
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      StockCommand.register(event.getDispatcher());
   }
}
