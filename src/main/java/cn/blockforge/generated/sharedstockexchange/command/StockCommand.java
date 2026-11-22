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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class StockCommand {
   private static final SuggestionProvider<CommandSourceStack> STOCK_IDS = (context, builder) -> SharedSuggestionProvider.suggest(
         Stocks.ALL.stream().map(StockDefinition::id).toList(), builder
      );

   private StockCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("stock")
            .executes(context -> list(context.getSource()))
            .then(Commands.literal("list").executes(context -> list(context.getSource())))
            .then(Commands.literal("mine").executes(context -> portfolio(context.getSource())))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("top").executes(context -> top(context.getSource())))
            .then(Commands.literal("dividend").executes(context -> claim(context.getSource())))
            .then(Commands.literal("claim").executes(context -> claim(context.getSource())))
            .then(
               Commands.literal("clear")
                  .executes(context -> clear(context.getSource(), ""))
                  .then(
                     Commands.argument("stock", StringArgumentType.word())
                        .suggests(STOCK_IDS)
                        .executes(context -> clear(context.getSource(), StringArgumentType.getString(context, "stock")))
                  )
            )
            .then(
               Commands.literal("interval")
                  .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                  .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600)).executes(StockCommand::setInterval))
            )
            .then(
               Commands.literal("wallet")
                  .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                  .then(Commands.argument("emc", LongArgumentType.longArg(0L, 1000000000000L)).executes(StockCommand::setWallet))
            )
            .then(
               Commands.literal("chart")
                  .executes(context -> chart(context.getSource(), Stocks.ALL.get(0).id()))
                  .then(
                     Commands.argument("stock", StringArgumentType.word())
                        .suggests(STOCK_IDS)
                        .executes(context -> chart(context.getSource(), StringArgumentType.getString(context, "stock")))
                  )
            )
            .then(Commands.literal("refund").executes(context -> refund(context.getSource())))
            .then(
               Commands.literal("debt")
                  .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                  .executes(context -> showSwitches(context.getSource()))
                  .then(Commands.literal("on").executes(context -> setDebt(context.getSource(), true)))
                  .then(Commands.literal("off").executes(context -> setDebt(context.getSource(), false)))
            )
            .then(
               Commands.literal("liquidation")
                  .requires(source -> source.hasPermission(2) || source.getServer().isSingleplayer())
                  .executes(context -> showSwitches(context.getSource()))
                  .then(Commands.literal("on").executes(context -> setLiquidation(context.getSource(), true)))
                  .then(Commands.literal("off").executes(context -> setLiquidation(context.getSource(), false)))
            )
            .then(
               Commands.literal("buy")
                  .then(
                     Commands.argument("stock", StringArgumentType.word())
                        .suggests(STOCK_IDS)
                        .then(Commands.argument("shares", IntegerArgumentType.integer(1, 100000)).executes(context -> trade(context, true)))
                  )
            )
            .then(
               Commands.literal("sell")
                  .then(
                     Commands.argument("stock", StringArgumentType.word())
                        .suggests(STOCK_IDS)
                        .then(Commands.argument("shares", IntegerArgumentType.integer(1, 100000)).executes(context -> trade(context, false)))
                  )
            )
      );
   }

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
         double vsBase = anchor <= 0.0 ? 0.0 : price / anchor - 1.0;
         lines.add(
            Component.translatable(
                  "command.shared_stock_exchange.list_row",
                  new Object[]{
                     Component.translatable(Stocks.nameKey(definition.id())),
                     definition.id(),
                     MarketFormat.emc(price),
                     MarketFormat.percent(vsBase),
                     MarketFormat.emc(data.marketCap(definition)),
                     Component.translatable("command.shared_stock_exchange.pool"),
                     MarketFormat.emc(data.dividendPool(definition.id()))
                  }
               )
               .withStyle(vsBase >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED)
         );
      }

      return sendLines(source, lines);
   }

   private static int portfolio(CommandSourceStack source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         MarketData data = MarketManager.data(source.getServer());
         List<Component> lines = new ArrayList<>();
         lines.add(Component.translatable("command.shared_stock_exchange.portfolio_header").withStyle(ChatFormatting.GOLD));
         double totalValue = 0.0;
         double totalCost = 0.0;

         for (StockDefinition definition : Stocks.ALL) {
            long held = data.holding(player.getUUID(), definition.id());
            if (held > 0L) {
               double price = data.price(definition.id());
               double avg = data.avgCost(player.getUUID(), definition.id());
               double value = data.proceedsForShares(definition.id(), held);
               double pnl = value - avg * (double)held;
               double cost = avg * (double)held;
               totalValue += value;
               totalCost += cost;
               lines.add(
                  Component.translatable(
                        "command.shared_stock_exchange.portfolio_row",
                        new Object[]{
                           Component.translatable(Stocks.nameKey(definition.id())),
                           held,
                           MarketFormat.emc(avg),
                           MarketFormat.emc(price),
                           MarketFormat.emc(pnl),
                           MarketFormat.percent(cost <= 0.0 ? 0.0 : pnl / cost),
                           Component.translatable(
                              "command.shared_stock_exchange.holding_share",
                              new Object[]{MarketFormat.share(definition.totalShares() <= 0L ? 0.0 : (double)held / (double)definition.totalShares())}
                           )
                        }
                     )
                     .withStyle(pnl >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED)
               );
            }
         }

         if (lines.size() == 1) {
            lines.add(Component.translatable("command.shared_stock_exchange.portfolio_empty"));
            return sendLines(source, lines);
         } else {
            double totalPnl = totalValue - totalCost;
            lines.add(
               Component.translatable(
                     "command.shared_stock_exchange.portfolio_total",
                     new Object[]{
                        MarketFormat.emc(totalValue), MarketFormat.emc(totalPnl), MarketFormat.percent(totalCost <= 0.0 ? 0.0 : totalPnl / totalCost)
                     }
                  )
                  .withStyle(ChatFormatting.AQUA)
            );
            lines.add(
               Component.translatable(
                     "command.shared_stock_exchange.portfolio_cash",
                     new Object[]{
                        MarketFormat.emc((double)MarketManager.balance(source.getServer(), player)),
                        MarketFormat.emc((double)data.pendingDividend(player.getUUID()))
                     }
                  )
                  .withStyle(ChatFormatting.GOLD)
            );
            return sendLines(source, lines);
         }
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }

   private static int status(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      MarketData data = MarketManager.data(server);
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable("command.shared_stock_exchange.status_header").withStyle(ChatFormatting.GOLD));
      lines.add(Component.translatable("command.shared_stock_exchange.status_cash", new Object[]{ProjectEmcBridge.describeComponent()}));
      if (source.getEntity() instanceof ServerPlayer player) {
         long cash = MarketManager.balance(server, player);
         lines.add(
            Component.translatable(
               "command.shared_stock_exchange.status_wallet",
               new Object[]{MarketFormat.emc((double)cash), MarketFormat.emc((double)data.pendingDividend(player.getUUID()))}
            )
         );
      }

      lines.add(
         Component.translatable("command.shared_stock_exchange.status_switches", new Object[]{onOff(data.debtAllowed()), onOff(data.liquidationEnabled())})
            .withStyle(ChatFormatting.AQUA)
      );
      if (source.getEntity() instanceof ServerPlayer player) {
         long cash = MarketManager.balance(server, player);
         double positionValue = MarketManager.positionValue(data, player.getUUID());
         if (cash < 0L || positionValue > 0.0) {
            if (data.liquidationEnabled()) {
               lines.add(
                  Component.translatable(
                        "command.shared_stock_exchange.status_margin",
                        new Object[]{
                           MarketFormat.emc(MarketMath.equityOf((double)cash, positionValue)),
                           MarketFormat.emc((double)Math.max(-cash, 0L)),
                           MarketFormat.emc(MarketMath.maintenanceLine(positionValue))
                        }
                     )
                     .withStyle(MarketMath.needsLiquidation((double)cash, positionValue) ? ChatFormatting.RED : ChatFormatting.GRAY)
               );
            } else {
               lines.add(
                  Component.translatable(
                        "command.shared_stock_exchange.status_margin_off",
                        new Object[]{MarketFormat.emc(MarketMath.equityOf((double)cash, positionValue)), MarketFormat.emc((double)Math.max(-cash, 0L))}
                     )
                     .withStyle(ChatFormatting.GRAY)
               );
            }
         }
      }

      lines.add(Component.translatable("command.shared_stock_exchange.status_fee", new Object[]{(int)Math.round(1.0), 10}));
      lines.add(Component.translatable("command.shared_stock_exchange.status_drift", new Object[]{data.driftSeconds(), 5, 3600}));

      for (StockDefinition definition : Stocks.ALL) {
         lines.add(
            Component.translatable(
               "command.shared_stock_exchange.status_row",
               new Object[]{
                  Component.translatable(Stocks.nameKey(definition.id())),
                  MarketFormat.emc(data.price(definition.id())),
                  MarketFormat.emc(MarketManager.anchorPrice(definition)),
                  Component.translatable("command.shared_stock_exchange.pool"),
                  MarketFormat.emc(data.dividendPool(definition.id()))
               }
            )
         );
      }

      for (Component line : ProjectEmcBridge.diagnostics()) {
         lines.add(line.copy().withStyle(ChatFormatting.GRAY));
      }

      return sendLines(source, lines);
   }

   private static Component onOff(boolean enabled) {
      return Component.translatable(enabled ? "command.shared_stock_exchange.switch_on" : "command.shared_stock_exchange.switch_off");
   }

   private static int showSwitches(CommandSourceStack source) {
      MarketData data = MarketManager.data(source.getServer());
      source.sendSuccess(
         () -> Component.translatable("command.shared_stock_exchange.switches_now", new Object[]{onOff(data.debtAllowed()), onOff(data.liquidationEnabled())}),
         false
      );
      return 1;
   }

   private static int setDebt(CommandSourceStack source, boolean enabled) {
      MinecraftServer server = source.getServer();
      if (enabled) {
         MarketManager.enableDebt(server);
         source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.debt_on").withStyle(ChatFormatting.GREEN), true);
      } else {
         int cleared = MarketManager.disableDebt(server);
         source.sendSuccess(
            () -> Component.translatable("command.shared_stock_exchange.debt_off", new Object[]{cleared}).withStyle(ChatFormatting.YELLOW), true
         );
      }

      MarketManager.broadcast(server);
      return 1;
   }

   private static int setLiquidation(CommandSourceStack source, boolean enabled) {
      MinecraftServer server = source.getServer();
      MarketManager.data(server).setLiquidationEnabled(enabled);
      source.sendSuccess(
         () -> Component.translatable(enabled ? "command.shared_stock_exchange.liquidation_on" : "command.shared_stock_exchange.liquidation_off")
               .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
         true
      );
      MarketManager.broadcast(server);
      return 1;
   }

   private static int refund(CommandSourceStack source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         Component feedback = MarketManager.refund(player);
         source.sendSuccess(() -> feedback, false);
         MarketManager.broadcast(source.getServer());
         return 1;
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }

   private static int claim(CommandSourceStack source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         Component feedback = MarketManager.claimDividends(source.getServer(), player);
         source.sendSuccess(() -> feedback, false);
         MarketManager.broadcast(source.getServer());
         return 1;
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }

   private static int clear(CommandSourceStack source, String stockId) {
      if (source.getEntity() instanceof ServerPlayer player) {
         Component feedback = MarketManager.liquidate(player, stockId);
         source.sendSuccess(() -> feedback, false);
         MarketManager.broadcast(source.getServer());
         return 1;
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }

   private static int setWallet(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = context.getSource();
      if (source.getEntity() instanceof ServerPlayer player) {
         if (MarketManager.projecteActive()) {
            source.sendFailure(Component.translatable("command.shared_stock_exchange.wallet_emc"));
            return 0;
         }

         long emc = LongArgumentType.getLong(context, "emc");
         MarketManager.data(source.getServer()).setTestWallet(player.getUUID(), emc);
         source.sendSuccess(() -> Component.translatable("command.shared_stock_exchange.wallet_set", new Object[]{MarketFormat.emc((double)emc)}), false);
         MarketManager.broadcast(source.getServer());
         return 1;
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }

   private static int setInterval(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = context.getSource();
      int seconds = IntegerArgumentType.getInteger(context, "seconds");
      MarketData data = MarketManager.data(source.getServer());
      int applied = data.setDriftSeconds(seconds);
      source.sendSuccess(
         () -> Component.translatable("command.shared_stock_exchange.interval_set", new Object[]{applied}).withStyle(ChatFormatting.GREEN), true
      );
      MarketManager.broadcast(source.getServer());
      return applied;
   }

   private static int chart(CommandSourceStack source, String stockId) {
      StockDefinition definition = Stocks.byId(stockId);
      if (definition == null) {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.unknown_stock", new Object[]{stockId}));
         return 0;
      } else {
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

         double change = first <= 0.0F ? 0.0 : (double)last / (double)first - 1.0;
         List<Component> lines = new ArrayList<>();
         lines.add(
            Component.translatable("command.shared_stock_exchange.chart_header", new Object[]{Component.translatable(Stocks.nameKey(definition.id()))})
               .withStyle(ChatFormatting.GOLD)
         );
         lines.add(Component.literal(sparkline(history)).withStyle(change >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED));
         lines.add(
            Component.translatable(
                  "command.shared_stock_exchange.chart_stats",
                  new Object[]{
                     MarketFormat.emc((double)last),
                     MarketFormat.percent(change),
                     MarketFormat.emc((double)high),
                     MarketFormat.emc((double)low),
                     MarketFormat.emc(MarketManager.anchorPrice(definition))
                  }
               )
               .withStyle(change >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED)
         );
         long held = 0L;
         double avgCost = 0.0;
         if (source.getEntity() instanceof ServerPlayer player) {
            held = data.holding(player.getUUID(), definition.id());
            avgCost = data.avgCost(player.getUUID(), definition.id());
         }

         if (held > 0L) {
            lines.add(
               Component.translatable("command.shared_stock_exchange.chart_mine", new Object[]{held, MarketFormat.emc(avgCost)}).withStyle(ChatFormatting.AQUA)
            );
         }

         return sendLines(source, lines);
      }
   }

   private static String sparkline(float[] values) {
      String levels = "▁▂▃▄▅▆▇█";
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
         int index = values.length <= 1 ? 0 : (int)Math.round((double)i * (double)(values.length - 1) / (double)(columns - 1));
         double ratio = span < 1.0E-6F ? 0.5 : ((double)values[index] - (double)min) / (double)span;
         int level = (int)Math.round(ratio * (double)(levels.length() - 1));
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
      } else {
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
            lines.add(
               Component.translatable("command.shared_stock_exchange.top_row", new Object[]{shown, asset.name(), MarketFormat.emc(asset.value()), marker})
            );
         }

         return sendLines(source, lines);
      }
   }

   private static int trade(CommandContext<CommandSourceStack> context, boolean buying) {
      CommandSourceStack source = context.getSource();
      if (source.getEntity() instanceof ServerPlayer player) {
         String stock = StringArgumentType.getString(context, "stock");
         int shares = IntegerArgumentType.getInteger(context, "shares");
         Component feedback = MarketManager.trade(player, stock, shares, buying);
         source.sendSuccess(() -> feedback, false);
         MarketManager.broadcast(source.getServer());
         return 1;
      } else {
         source.sendFailure(Component.translatable("message.shared_stock_exchange.players_only"));
         return 0;
      }
   }
}
