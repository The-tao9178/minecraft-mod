package cn.blockforge.generated.sharedstockexchange.market;

import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class MarketManager {
   public static final int MAX_SHARES_PER_TRADE = 100000;
   public static final double FEE_RATE = 0.01;
   public static final int DIVIDEND_INTERVAL_TICKS = 12000;
   public static final double MAJOR_HOLDER_RATIO = 0.2;
   public static final long REFUND_EMC = 1000L;
   private static final int MARGIN_CHECK_TICKS = 20;
   private static final double MAX_SAFE_AMOUNT = 9.0E15;
   private static final Random RANDOM = new Random();
   private static final int SYNC_INTERVAL_TICKS = 100;
   private static int ticksSinceDrift;
   private static int ticksSinceDividend;
   private static int ticksSinceSync;
   private static int ticksSinceMargin;
   private static boolean primed;

   private MarketManager() {
   }

   public static MarketData data(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(MarketData.READER, MarketData::new, MarketData.NAME);
   }

   public static boolean projecteActive() {
      return ProjectEmcBridge.available();
   }

   public static int cashMode() {
      if (projecteActive()) {
         return 1;
      } else {
         return ProjectEmcBridge.installedButDisconnected() ? 2 : 0;
      }
   }

   public static String cashModeText() {
      return ProjectEmcBridge.describe();
   }

   public static long balance(MinecraftServer server, ServerPlayer player) {
      if (projecteActive()) {
         long emc = ProjectEmcBridge.getEmc(player);
         return emc == Long.MIN_VALUE ? 0L : emc;
      } else {
         return data(server).testWallet(player.getUUID());
      }
   }

   public static double anchorPrice(StockDefinition definition) {
      double emc = ProjectEmcBridge.itemEmc(definition.itemId());
      return emc > 0.0 ? emc : definition.basePrice();
   }

   private static boolean take(MinecraftServer server, ServerPlayer player, long amount) {
      if (amount <= 0L) {
         return true;
      } else if (projecteActive()) {
         long emc = ProjectEmcBridge.getEmc(player);
         return emc == Long.MIN_VALUE ? false : ProjectEmcBridge.setEmc(player, MarketMath.safeAdd(emc, -amount));
      } else {
         MarketData data = data(server);
         data.setTestWallet(player.getUUID(), MarketMath.safeAdd(data.testWallet(player.getUUID()), -amount));
         return true;
      }
   }

   private static boolean give(MinecraftServer server, ServerPlayer player, long amount) {
      if (amount <= 0L) {
         return true;
      } else if (projecteActive()) {
         long emc = ProjectEmcBridge.getEmc(player);
         return emc == Long.MIN_VALUE ? false : ProjectEmcBridge.setEmc(player, MarketMath.safeAdd(emc, amount));
      } else {
         MarketData data = data(server);
         data.setTestWallet(player.getUUID(), MarketMath.safeAdd(data.testWallet(player.getUUID()), amount));
         return true;
      }
   }

   public static long outstandingDebt(MinecraftServer server, ServerPlayer player) {
      return Math.max(-balance(server, player), 0L);
   }

   private static Component debtNote(long repaid, long stillOwed) {
      return repaid <= 0L
         ? Component.empty()
         : (stillOwed > 0L
               ? Component.translatable(
                  "message.shared_stock_exchange.paid_debt", new Object[]{MarketFormat.emc((double)repaid), MarketFormat.emc((double)stillOwed)}
               )
               : Component.translatable("message.shared_stock_exchange.paid_debt_clear", new Object[]{MarketFormat.emc((double)repaid)}))
            .withStyle(ChatFormatting.GOLD);
   }

   private static boolean setBalance(MinecraftServer server, ServerPlayer player, long value) {
      if (projecteActive()) {
         return ProjectEmcBridge.setEmc(player, value);
      } else {
         data(server).setTestWallet(player.getUUID(), value);
         return true;
      }
   }

   public static boolean debtAllowed(MinecraftServer server) {
      return data(server).debtAllowed();
   }

   public static boolean liquidationEnabled(MinecraftServer server) {
      return data(server).liquidationEnabled();
   }

   public static double positionValue(MarketData data, UUID playerId) {
      double value = 0.0;

      for (StockDefinition definition : Stocks.ALL) {
         long held = data.holding(playerId, definition.id());
         if (held > 0L) {
            value += data.proceedsForShares(definition.id(), held);
         }
      }

      return value;
   }

   public static Component trade(ServerPlayer player, String stockId, int shares, boolean buying) {
      MinecraftServer server = player.getServer();
      if (server == null) {
         return Component.translatable("message.shared_stock_exchange.no_server");
      } else {
         StockDefinition definition = Stocks.byId(stockId);
         if (definition == null) {
            return Component.translatable("message.shared_stock_exchange.unknown_stock", new Object[]{String.valueOf(stockId)});
         } else if (shares > 0 && shares <= 100000) {
            MarketData data = data(server);
            UUID playerId = player.getUUID();
            data.setPlayerName(playerId, player.getName().getString());
            Component stockName = Component.translatable(Stocks.nameKey(stockId));
            if (buying) {
               return buy(player, server, data, definition, stockName, shares);
            } else {
               long owned = data.holding(playerId, stockId);
               if (owned <= 0L) {
                  return Component.translatable("message.shared_stock_exchange.no_shares").withStyle(ChatFormatting.GRAY);
               } else {
                  long effective = Math.min((long)shares, owned);
                  Sale sale = sell(player, server, data, definition, effective);
                  if (!sale.ok()) {
                     return Component.translatable("message.shared_stock_exchange.payout_failed");
                  } else {
                     double profitRatio = sale.basisSold() <= 0.0 ? 0.0 : sale.profit() / sale.basisSold();
                     Component message = Component.translatable(
                           "message.shared_stock_exchange.sold",
                           new Object[]{
                              effective,
                              stockName,
                              MarketFormat.emc((double)sale.received()),
                              MarketFormat.emc((double)sale.fee()),
                              MarketFormat.emc(sale.profit()),
                              MarketFormat.percent(profitRatio),
                              MarketFormat.emc(data.price(stockId))
                           }
                        )
                        .withStyle(sale.profit() >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED);
                     if (effective < (long)shares) {
                        message = message.copy()
                           .append("\n")
                           .append(Component.translatable("message.shared_stock_exchange.sold_all", new Object[]{effective}).withStyle(ChatFormatting.YELLOW));
                     }

                     if (sale.repaid() > 0L) {
                        message = message.copy().append("\n").append(debtNote(sale.repaid(), outstandingDebt(server, player)));
                     }

                     return message;
                  }
               }
            }
         } else {
            return Component.translatable("message.shared_stock_exchange.bad_amount", new Object[]{100000});
         }
      }
   }

   private static Component buy(ServerPlayer player, MinecraftServer server, MarketData data, StockDefinition definition, Component stockName, int shares) {
      String stockId = definition.id();
      UUID playerId = player.getUUID();
      long buyable = data.maxBuyable(stockId);
      if (buyable <= 0L) {
         return Component.translatable("message.shared_stock_exchange.not_enough_liquidity");
      } else {
         long effective = Math.min((long)shares, buyable);
         double costExact = data.costForShares(stockId, effective);
         if (Double.isFinite(costExact) && !(costExact > 9.0E15)) {
            long cost = Math.max(1L, (long)Math.ceil(costExact));
            long fee = MarketMath.feeOf(cost);
            long total = cost + fee;
            long cash = balance(server, player);
            double positionValue = positionValue(data, playerId);
            if (data.debtAllowed()) {
               if ((double)cost - (double)cash > 9.0E15) {
                  return Component.translatable("message.shared_stock_exchange.debt_too_large", new Object[]{MarketFormat.emc(9.0E15)});
               }

               if (data.liquidationEnabled() && !MarketMath.survivesMaintenance((double)cash, positionValue, (double)cost, (double)fee)) {
                  return Component.translatable(
                        "message.shared_stock_exchange.would_liquidate",
                        new Object[]{
                           MarketFormat.emc(MarketMath.equityOf((double)cash, positionValue)),
                           MarketFormat.emc(MarketMath.maintenanceLine(positionValue + (double)cost))
                        }
                     )
                     .withStyle(ChatFormatting.RED);
               }
            } else if ((double)total > (double)Math.max(cash, 0L)) {
               return Component.translatable(
                  "message.shared_stock_exchange.not_enough_emc", new Object[]{MarketFormat.emc((double)total), MarketFormat.emc((double)cash)}
               );
            }

            if (!take(server, player, total)) {
               return Component.translatable(
                  ProjectEmcBridge.negativeRejected()
                     ? "message.shared_stock_exchange.emc_negative_rejected"
                     : "message.shared_stock_exchange.emc_write_failed"
               );
            } else {
               data.applyBuy(stockId, effective, (double)cost);
               data.addHolding(playerId, stockId, effective);
               data.addCostBasis(playerId, stockId, (double)total);
               data.addDividendPool(stockId, (double)fee);
               data.recordPrice(stockId);
               announceMajorHolder(server, player, definition);
               Component message = Component.translatable(
                     "message.shared_stock_exchange.bought",
                     new Object[]{
                        effective,
                        stockName,
                        MarketFormat.emc((double)total),
                        MarketFormat.emc((double)fee),
                        MarketFormat.emc(data.avgCost(playerId, stockId)),
                        MarketFormat.emc(data.price(stockId))
                     }
                  )
                  .withStyle(ChatFormatting.GREEN);
               if (effective < (long)shares) {
                  message = message.copy()
                     .append("\n")
                     .append(Component.translatable("message.shared_stock_exchange.bought_all", new Object[]{effective}).withStyle(ChatFormatting.YELLOW));
               }

               return message;
            }
         } else {
            return Component.translatable("message.shared_stock_exchange.not_enough_liquidity");
         }
      }
   }

   private static Sale sell(ServerPlayer player, MinecraftServer server, MarketData data, StockDefinition definition, long shares) {
      String stockId = definition.id();
      UUID playerId = player.getUUID();
      long owned = data.holding(playerId, stockId);
      if (shares > 0L && owned >= shares) {
         long gross = Math.max(0L, (long)Math.floor(data.proceedsForShares(stockId, shares)));
         long fee = MarketMath.feeOf(gross);
         long received = Math.max(gross - fee, 0L);
         long repaid = Math.min(outstandingDebt(server, player), received);
         if (!give(server, player, received)) {
            return Sale.failed();
         } else {
            double basisSold = data.costBasis(playerId, stockId) * ((double)shares / (double)owned);
            data.applySell(stockId, shares, (double)gross);
            data.addHolding(playerId, stockId, -shares);
            data.addCostBasis(playerId, stockId, -basisSold);
            data.addDividendPool(stockId, (double)fee);
            data.recordPrice(stockId);
            return new Sale(true, gross, fee, received, basisSold, repaid);
         }
      } else {
         return Sale.failed();
      }
   }

   public static Component liquidate(ServerPlayer player, String stockId) {
      MinecraftServer server = player.getServer();
      if (server == null) {
         return Component.translatable("message.shared_stock_exchange.no_server");
      } else {
         MarketData data = data(server);
         UUID playerId = player.getUUID();
         data.setPlayerName(playerId, player.getName().getString());
         boolean all = stockId == null || stockId.isEmpty();
         if (!all && Stocks.byId(stockId) == null) {
            return Component.translatable("message.shared_stock_exchange.unknown_stock", new Object[]{String.valueOf(stockId)});
         } else {
            int soldKinds = 0;
            long totalShares = 0L;
            long totalReceived = 0L;
            long totalGross = 0L;
            long totalRepaid = 0L;
            double totalBasis = 0.0;
            boolean failed = false;

            for (StockDefinition definition : Stocks.ALL) {
               if (all || definition.id().equals(stockId)) {
                  long owned = data.holding(playerId, definition.id());
                  if (owned > 0L) {
                     Sale sale = sell(player, server, data, definition, owned);
                     if (!sale.ok()) {
                        failed = true;
                     } else {
                        soldKinds++;
                        totalShares += owned;
                        totalReceived += sale.received();
                        totalGross += sale.gross();
                        totalRepaid += sale.repaid();
                        totalBasis += sale.basisSold();
                     }
                  }
               }
            }

            if (soldKinds != 0) {
               double profit = (double)totalReceived - totalBasis;
               double profitRatio = totalBasis <= 0.0 ? 0.0 : profit / totalBasis;
               Component message = failed
                  ? Component.translatable(
                        "message.shared_stock_exchange.clear_partial",
                        new Object[]{
                           soldKinds, totalShares, MarketFormat.emc((double)totalReceived), MarketFormat.emc(profit), MarketFormat.percent(profitRatio)
                        }
                     )
                     .withStyle(ChatFormatting.YELLOW)
                  : Component.translatable(
                        "message.shared_stock_exchange.cleared",
                        new Object[]{
                           soldKinds,
                           totalShares,
                           MarketFormat.emc((double)totalReceived),
                           MarketFormat.emc((double)totalGross),
                           MarketFormat.emc(profit),
                           MarketFormat.percent(profitRatio)
                        }
                     )
                     .withStyle(profit >= 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED);
               if (totalRepaid > 0L) {
                  message = message.copy().append("\n").append(debtNote(totalRepaid, outstandingDebt(server, player)));
               }

               return message;
            } else {
               return failed
                  ? Component.translatable("message.shared_stock_exchange.payout_failed").withStyle(ChatFormatting.RED)
                  : Component.translatable("message.shared_stock_exchange.clear_nothing").withStyle(ChatFormatting.GRAY);
            }
         }
      }
   }

   public static void checkMarginCalls(MinecraftServer server) {
      MarketData data = data(server);
      if (data.liquidationEnabled()) {
         for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            long cash = balance(server, online);
            double value = positionValue(data, online.getUUID());
            if (MarketMath.needsLiquidation((double)cash, value)) {
               forceLiquidate(server, data, online);
            }
         }
      }
   }

   private static void forceLiquidate(MinecraftServer server, MarketData data, ServerPlayer player) {
      UUID playerId = player.getUUID();
      int kinds = 0;
      long totalShares = 0L;
      long totalReceived = 0L;
      double totalBasis = 0.0;
      boolean failed = false;

      for (StockDefinition definition : Stocks.ALL) {
         long owned = data.holding(playerId, definition.id());
         if (owned > 0L) {
            Sale sale = sell(player, server, data, definition, owned);
            if (!sale.ok()) {
               failed = true;
            } else {
               kinds++;
               totalShares += owned;
               totalReceived += sale.received();
               totalBasis += sale.basisSold();
            }
         }
      }

      if (kinds != 0) {
         long balance = balance(server, player);
         double loss = Math.max(totalBasis - (double)totalReceived, 0.0);
         player.displayClientMessage(
            Component.translatable(
                  "message.shared_stock_exchange.forced_liquidation",
                  new Object[]{kinds, totalShares, MarketFormat.emc((double)totalReceived), MarketFormat.emc(loss), MarketFormat.emc((double)balance)}
               )
               .withStyle(ChatFormatting.RED),
            false
         );
         if (balance < 0L) {
            player.displayClientMessage(
               Component.translatable("message.shared_stock_exchange.forced_liquidation_debt", new Object[]{MarketFormat.emc((double)(-balance))})
                  .withStyle(ChatFormatting.DARK_RED),
               false
            );
         }

         if (failed) {
            player.displayClientMessage(
               Component.translatable("message.shared_stock_exchange.forced_liquidation_partial").withStyle(ChatFormatting.YELLOW), false
            );
         }
      }
   }

   public static Component refund(ServerPlayer player) {
      MinecraftServer server = player.getServer();
      if (server == null) {
         return Component.translatable("message.shared_stock_exchange.no_server");
      } else {
         MarketData data = data(server);
         UUID playerId = player.getUUID();
         long cash = balance(server, player);
         if (cash >= 0L) {
            return Component.translatable("message.shared_stock_exchange.refund_not_needed").withStyle(ChatFormatting.GRAY);
         } else {
            long debt = -cash;
            long lostShares = 0L;

            for (StockDefinition definition : Stocks.ALL) {
               lostShares += data.holding(playerId, definition.id());
            }

            if (!setBalance(server, player, 1000L)) {
               return Component.translatable("message.shared_stock_exchange.emc_write_failed").withStyle(ChatFormatting.RED);
            } else {
               data.clearPositions(playerId);
               data.setLastBalance(playerId, 1000L);
               return Component.translatable(
                     "message.shared_stock_exchange.refund_done", new Object[]{MarketFormat.emc((double)debt), lostShares, MarketFormat.emc(1000.0)}
                  )
                  .withStyle(ChatFormatting.GREEN);
            }
         }
      }
   }

   public static int disableDebt(MinecraftServer server) {
      MarketData data = data(server);
      data.setDebtAllowed(false);
      int cleared = data.clearNegativeWallets();

      // 关掉负债时，把在线玩家身上真实的负数 EMC 也一起抹平，否则他们会一直卡在负数余额上。
      for (ServerPlayer online : server.getPlayerList().getPlayers()) {
         if (projecteActive()) {
            long emc = ProjectEmcBridge.getEmc(online);
            if (emc != Long.MIN_VALUE && emc < 0L && setBalance(server, online, 0L)) {
               cleared++;
               online.displayClientMessage(
                  Component.translatable("message.shared_stock_exchange.debt_cleared_online", new Object[]{MarketFormat.emc((double)(-emc))})
                     .withStyle(ChatFormatting.YELLOW),
                  false
               );
            }
         }
      }

      return cleared;
   }

   public static void enableDebt(MinecraftServer server) {
      data(server).setDebtAllowed(true);
   }

   public static void settleDebtIfDisabled(MinecraftServer server, ServerPlayer player) {
      if (!debtAllowed(server)) {
         long cash = balance(server, player);
         if (cash < 0L && setBalance(server, player, 0L)) {
            player.displayClientMessage(
               Component.translatable("message.shared_stock_exchange.debt_cleared_online", new Object[]{MarketFormat.emc((double)(-cash))})
                  .withStyle(ChatFormatting.YELLOW),
               false
            );
         }
      }
   }

   private static void announceMajorHolder(MinecraftServer server, ServerPlayer player, StockDefinition definition) {
      MarketData data = data(server);
      List<Entry<UUID, Long>> top = data.holdersSorted(definition.id(), 1);
      if (!top.isEmpty()) {
         UUID leading = top.get(0).getKey();
         long heldByLeading = top.get(0).getValue();
         UUID recorded = data.topHolder(definition.id());
         if (!leading.equals(recorded)) {
            data.setTopHolder(definition.id(), leading);
            if (leading.equals(player.getUUID()) && definition.totalShares() > 0L) {
               double ratio = (double)heldByLeading / (double)definition.totalShares();
               if (!(ratio < 0.2)) {
                  server.getPlayerList()
                     .broadcastSystemMessage(
                        Component.translatable(
                              "message.shared_stock_exchange.major_holder",
                              new Object[]{player.getName(), Component.translatable(Stocks.nameKey(definition.id())), MarketFormat.share(ratio)}
                           )
                           .withStyle(ChatFormatting.GOLD),
                        false
                     );
               }
            }
         }
      }
   }

   public static void drift(MinecraftServer server) {
      MarketData data = data(server);

      for (StockDefinition definition : Stocks.ALL) {
         double price = data.price(definition.id());
         if (price > 0.0) {
            double noise = RANDOM.nextGaussian() * 0.02;
            double anchor = anchorPrice(definition);
            double revert = (anchor / price - 1.0) * 0.04;
            double multiplier = Mth.clamp(1.0 + noise + revert, 0.8, 1.25);
            data.setEmcPool(definition.id(), data.emcPool(definition.id()) * multiplier);
            data.recordPrice(definition.id());
         }
      }
   }

   public static void dividendCycle(MinecraftServer server) {
      MarketData data = data(server);
      Map<UUID, Long> received = new HashMap<>();

      for (StockDefinition definition : Stocks.ALL) {
         double pool = data.dividendPool(definition.id());
         if (!(pool < 1.0)) {
            List<Entry<UUID, Long>> holders = data.holders(definition.id());
            long totalHeld = data.totalHeld(definition.id());
            if (!holders.isEmpty() && totalHeld > 0L) {
               double paid = 0.0;

               for (Entry<UUID, Long> entry : holders) {
                  long amount = (long)Math.floor(pool * (double)entry.getValue().longValue() / (double)totalHeld);
                  if (amount > 0L) {
                     paid += (double)amount;
                     UUID holder = entry.getKey();
                     ServerPlayer online = server.getPlayerList().getPlayer(holder);
                     if (online != null && give(server, online, amount)) {
                        received.merge(holder, amount, Long::sum);
                     } else {
                        data.addPendingDividend(holder, amount);
                     }
                  }
               }

               if (paid > 0.0) {
                  data.addDividendPool(definition.id(), -paid);
               }
            }
         }
      }

      for (Entry<UUID, Long> entryx : received.entrySet()) {
         ServerPlayer online = server.getPlayerList().getPlayer(entryx.getKey());
         if (online != null) {
            online.displayClientMessage(
               Component.translatable("message.shared_stock_exchange.dividend_received", new Object[]{MarketFormat.emc((double)entryx.getValue().longValue())})
                  .withStyle(ChatFormatting.GOLD),
               false
            );
         }
      }
   }

   public static Component claimDividends(MinecraftServer server, ServerPlayer player) {
      MarketData data = data(server);
      long pending = data.takePendingDividend(player.getUUID());
      if (pending <= 0L) {
         return Component.translatable("message.shared_stock_exchange.dividend_none").withStyle(ChatFormatting.GRAY);
      } else if (!give(server, player, pending)) {
         data.addPendingDividend(player.getUUID(), pending);
         return Component.translatable("message.shared_stock_exchange.dividend_failed").withStyle(ChatFormatting.RED);
      } else {
         return Component.translatable("message.shared_stock_exchange.dividend_claimed", new Object[]{MarketFormat.emc((double)pending)})
            .withStyle(ChatFormatting.GOLD);
      }
   }

   public static List<Asset> ranking(MinecraftServer server) {
      MarketData data = data(server);
      Map<UUID, Double> assets = new HashMap<>();

      for (StockDefinition definition : Stocks.ALL) {
         for (Entry<UUID, Long> entry : data.holders(definition.id())) {
            assets.merge(entry.getKey(), data.proceedsForShares(definition.id(), entry.getValue()), Double::sum);
         }
      }

      for (ServerPlayer online : server.getPlayerList().getPlayers()) {
         UUID id = online.getUUID();
         data.setPlayerName(id, online.getName().getString());
         assets.merge(id, (double)balance(server, online), Double::sum);
      }

      for (UUID id : data.knownPlayers()) {
         if (!assets.containsKey(id)) {
            assets.put(id, (double)data.lastBalance(id));
         }
      }

      List<Asset> list = new ArrayList<>();

      for (Entry<UUID, Double> entry : assets.entrySet()) {
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
      float[] history = new float[size * 120];
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
         List<Entry<UUID, Long>> top = data.holdersSorted(definition.id(), 1);
         if (top.isEmpty()) {
            topNames[i] = "";
            topShares[i] = 0L;
         } else {
            topNames[i] = data.playerName(top.get(0).getKey());
            topShares[i] = top.get(0).getValue();
         }

         float[] samples = data.historyArray(definition.id());
         System.arraycopy(samples, 0, history, i * 120, 120);
      }

      long balance = balance(server, viewer);
      data.setLastBalance(viewerId, balance);
      return new MarketSnapshotPayload(
         prices,
         emcPools,
         sharePools,
         holdings,
         costs,
         history,
         dividends,
         anchors,
         topNames,
         topShares,
         balance,
         data.pendingDividend(viewerId),
         cashMode(),
         data.driftSeconds(),
         data.debtAllowed(),
         data.liquidationEnabled()
      );
   }

   public static void broadcast(MinecraftServer server) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         MarketNetworking.sendToPlayer(snapshot(server, player), player);
      }
   }

   public static void sendTo(ServerPlayer player) {
      MinecraftServer server = player.getServer();
      if (server != null) {
         MarketNetworking.sendToPlayer(snapshot(server, player), player);
      }
   }

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

      if (ticksSinceMargin >= 20) {
         ticksSinceMargin = 0;
         checkMarginCalls(server);
      }

      if (ticksSinceDividend >= 12000) {
         ticksSinceDividend = 0;
         dividendCycle(server);
      }

      if (ticksSinceSync >= 100) {
         ticksSinceSync = 0;
         broadcast(server);
      }
   }

   public static record Asset(UUID player, String name, double value) {
   }

   private static record Sale(boolean ok, long gross, long fee, long received, double basisSold, long repaid) {
      static Sale failed() {
         return new Sale(false, 0L, 0L, 0L, 0.0, 0L);
      }

      double profit() {
         return (double)this.received - this.basisSold;
      }
   }
}
