package cn.blockforge.generated.sharedstockexchange.market;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;

public class MarketData extends SavedData {
   public static final String NAME = "shared_stock_exchange_market";
   public static final long TEST_WALLET_START = 1000000L;
   public static final int HISTORY_LENGTH = 120;
   public static final int DEFAULT_DRIFT_SECONDS = 30;
   public static final int MIN_DRIFT_SECONDS = 5;
   public static final int MAX_DRIFT_SECONDS = 3600;
   public static final Function<CompoundTag, MarketData> READER = MarketData::load;
   private final Map<String, Double> emcPools = new HashMap<>();
   private final Map<String, Double> sharePools = new HashMap<>();
   private final Map<UUID, Map<String, Long>> holdings = new HashMap<>();
   private final Map<UUID, Map<String, Double>> costBasis = new HashMap<>();
   private final Map<UUID, Long> testWallets = new HashMap<>();
   private final Map<String, Deque<Double>> histories = new HashMap<>();
   private int driftSeconds = 30;
   private boolean debtAllowed = true;
   private boolean liquidationEnabled = true;
   private final Map<String, Double> dividendPools = new HashMap<>();
   private final Map<UUID, Long> pendingDividends = new HashMap<>();
   private final Map<UUID, String> playerNames = new HashMap<>();
   private final Map<UUID, Long> lastBalances = new HashMap<>();
   private final Map<String, UUID> topHolders = new HashMap<>();

   public MarketData() {
      for (StockDefinition definition : Stocks.ALL) {
         this.emcPools.put(definition.id(), definition.initialEmcPool());
         this.sharePools.put(definition.id(), (double)definition.totalShares());
      }
   }

   public static MarketData load(CompoundTag tag) {
      MarketData data = new MarketData();
      data.driftSeconds = clampDriftSeconds(tag.getInt("drift"));
      data.debtAllowed = !tag.contains("debt") || tag.getBoolean("debt");
      data.liquidationEnabled = !tag.contains("liq") || tag.getBoolean("liq");
      CompoundTag stocksTag = tag.getCompound("stocks");

      for (String id : stocksTag.getAllKeys()) {
         if (data.emcPools.containsKey(id)) {
            CompoundTag stockTag = stocksTag.getCompound(id);
            data.emcPools.put(id, Math.max(stockTag.getDouble("emc"), 1.0));
            data.sharePools.put(id, Math.max(stockTag.getDouble("shares"), 1.0E-6));
            ListTag prices = stockTag.getList("prices", 6);
            if (!prices.isEmpty()) {
               Deque<Double> deque = data.histories.computeIfAbsent(id, keyx -> new ArrayDeque<>(120));
               int start = Math.max(0, prices.size() - 120);

               for (int i = start; i < prices.size(); i++) {
                  deque.addLast(prices.getDouble(i));
               }
            }
         }
      }

      CompoundTag playersTag = tag.getCompound("players");

      for (String key : playersTag.getAllKeys()) {
         UUID uuid;
         try {
            uuid = UUID.fromString(key);
         } catch (IllegalArgumentException var19) {
            continue;
         }

         CompoundTag playerTag = playersTag.getCompound(key);
         CompoundTag holdTag = playerTag.getCompound("holdings");
         Map<String, Long> owned = new HashMap<>();

         for (String stockId : holdTag.getAllKeys()) {
            long amount = holdTag.getLong(stockId);
            if (amount > 0L) {
               owned.put(stockId, amount);
            }
         }

         if (!owned.isEmpty()) {
            data.holdings.put(uuid, owned);
         }

         CompoundTag costTag = playerTag.getCompound("cost");
         Map<String, Double> costs = new HashMap<>();

         for (String stockIdx : costTag.getAllKeys()) {
            double cost = costTag.getDouble(stockIdx);
            if (cost > 0.0) {
               costs.put(stockIdx, cost);
            }
         }

         if (!costs.isEmpty()) {
            data.costBasis.put(uuid, costs);
         }

         long wallet = playerTag.getLong("wallet");
         if (wallet != 0L) {
            data.testWallets.put(uuid, wallet);
         }
      }

      loadDoubles(tag.getCompound("dividends"), data.dividendPools);
      loadLongs(tag.getCompound("pending"), data.pendingDividends, 1L);
      loadLongs(tag.getCompound("balances"), data.lastBalances, Long.MIN_VALUE);
      CompoundTag namesTag = tag.getCompound("names");

      for (String key : namesTag.getAllKeys()) {
         try {
            data.playerNames.put(UUID.fromString(key), namesTag.getString(key));
         } catch (IllegalArgumentException var18) {
         }
      }

      CompoundTag topTag = tag.getCompound("top");

      for (String stockIdxx : topTag.getAllKeys()) {
         try {
            data.topHolders.put(stockIdxx, UUID.fromString(topTag.getString(stockIdxx)));
         } catch (IllegalArgumentException var17) {
         }
      }

      return data;
   }

   private static void loadDoubles(CompoundTag tag, Map<String, Double> target) {
      for (String key : tag.getAllKeys()) {
         double value = tag.getDouble(key);
         if (value > 0.0) {
            target.put(key, value);
         }
      }
   }

   private static void loadLongs(CompoundTag tag, Map<UUID, Long> target, long minimum) {
      for (String key : tag.getAllKeys()) {
         long value = tag.getLong(key);
         if (value >= minimum) {
            try {
               target.put(UUID.fromString(key), value);
            } catch (IllegalArgumentException var9) {
            }
         }
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putInt("drift", this.driftSeconds);
      tag.putBoolean("debt", this.debtAllowed);
      tag.putBoolean("liq", this.liquidationEnabled);
      CompoundTag stocksTag = new CompoundTag();

      for (Entry<String, Double> entry : this.emcPools.entrySet()) {
         CompoundTag stockTag = new CompoundTag();
         stockTag.putDouble("emc", entry.getValue());
         stockTag.putDouble("shares", this.sharePools.getOrDefault(entry.getKey(), 0.0));
         Deque<Double> deque = this.histories.get(entry.getKey());
         if (deque != null && !deque.isEmpty()) {
            ListTag prices = new ListTag();

            for (double value : deque) {
               prices.add(prices.size(), DoubleTag.valueOf(value));
            }

            stockTag.put("prices", prices);
         }

         stocksTag.put(entry.getKey(), stockTag);
      }

      tag.put("stocks", stocksTag);
      CompoundTag playersTag = new CompoundTag();
      Set<UUID> known = new HashSet<>(this.holdings.keySet());
      known.addAll(this.testWallets.keySet());
      known.addAll(this.costBasis.keySet());

      for (UUID uuid : known) {
         CompoundTag playerTag = new CompoundTag();
         CompoundTag holdTag = new CompoundTag();
         Map<String, Long> owned = this.holdings.get(uuid);
         if (owned != null) {
            for (Entry<String, Long> entry : owned.entrySet()) {
               if (entry.getValue() > 0L) {
                  holdTag.putLong(entry.getKey(), entry.getValue());
               }
            }
         }

         playerTag.put("holdings", holdTag);
         CompoundTag costTag = new CompoundTag();
         Map<String, Double> costs = this.costBasis.get(uuid);
         if (costs != null) {
            for (Entry<String, Double> entryx : costs.entrySet()) {
               if (entryx.getValue() > 0.0) {
                  costTag.putDouble(entryx.getKey(), entryx.getValue());
               }
            }
         }

         playerTag.put("cost", costTag);
         playerTag.putLong("wallet", this.testWallets.getOrDefault(uuid, 0L));
         playersTag.put(uuid.toString(), playerTag);
      }

      tag.put("players", playersTag);
      CompoundTag dividendsTag = new CompoundTag();

      for (Entry<String, Double> entryxx : this.dividendPools.entrySet()) {
         if (entryxx.getValue() > 0.0) {
            dividendsTag.putDouble(entryxx.getKey(), entryxx.getValue());
         }
      }

      tag.put("dividends", dividendsTag);
      CompoundTag pendingTag = new CompoundTag();

      for (Entry<UUID, Long> entryxxx : this.pendingDividends.entrySet()) {
         if (entryxxx.getValue() > 0L) {
            pendingTag.putLong(entryxxx.getKey().toString(), entryxxx.getValue());
         }
      }

      tag.put("pending", pendingTag);
      CompoundTag balancesTag = new CompoundTag();

      for (Entry<UUID, Long> entryxxxx : this.lastBalances.entrySet()) {
         balancesTag.putLong(entryxxxx.getKey().toString(), entryxxxx.getValue());
      }

      tag.put("balances", balancesTag);
      CompoundTag namesTag = new CompoundTag();

      for (Entry<UUID, String> entryxxxx : this.playerNames.entrySet()) {
         namesTag.putString(entryxxxx.getKey().toString(), entryxxxx.getValue());
      }

      tag.put("names", namesTag);
      CompoundTag topTag = new CompoundTag();

      for (Entry<String, UUID> entryxxxx : this.topHolders.entrySet()) {
         topTag.putString(entryxxxx.getKey(), entryxxxx.getValue().toString());
      }

      tag.put("top", topTag);
      return tag;
   }

   public double emcPool(String stockId) {
      return this.emcPools.getOrDefault(stockId, 0.0);
   }

   public double sharePool(String stockId) {
      return this.sharePools.getOrDefault(stockId, 1.0E-6);
   }

   public double price(String stockId) {
      return this.emcPool(stockId) / Math.max(this.sharePool(stockId), 1.0E-6);
   }

   public double marketCap(StockDefinition definition) {
      return this.price(definition.id()) * (double)definition.totalShares();
   }

   public long holding(UUID player, String stockId) {
      Map<String, Long> owned = this.holdings.get(player);
      return owned == null ? 0L : owned.getOrDefault(stockId, 0L);
   }

   public void addHolding(UUID player, String stockId, long delta) {
      Map<String, Long> owned = this.holdings.computeIfAbsent(player, key -> new HashMap<>());
      long total = owned.getOrDefault(stockId, 0L) + delta;
      if (total <= 0L) {
         owned.remove(stockId);
      } else {
         owned.put(stockId, total);
      }

      if (owned.isEmpty()) {
         this.holdings.remove(player);
      }

      this.setDirty();
   }

   public double costBasis(UUID player, String stockId) {
      Map<String, Double> costs = this.costBasis.get(player);
      return costs == null ? 0.0 : costs.getOrDefault(stockId, 0.0);
   }

   public void addCostBasis(UUID player, String stockId, double delta) {
      Map<String, Double> costs = this.costBasis.computeIfAbsent(player, key -> new HashMap<>());
      double total = costs.getOrDefault(stockId, 0.0) + delta;
      if (total <= 1.0E-6) {
         costs.remove(stockId);
      } else {
         costs.put(stockId, total);
      }

      if (costs.isEmpty()) {
         this.costBasis.remove(player);
      }

      this.setDirty();
   }

   public double avgCost(UUID player, String stockId) {
      long owned = this.holding(player, stockId);
      return owned <= 0L ? 0.0 : this.costBasis(player, stockId) / (double)owned;
   }

   public long testWallet(UUID player) {
      return this.testWallets.getOrDefault(player, 1000000L);
   }

   public void setTestWallet(UUID player, long value) {
      this.testWallets.put(player, value);
      this.setDirty();
   }

   public boolean debtAllowed() {
      return this.debtAllowed;
   }

   public void setDebtAllowed(boolean allowed) {
      this.debtAllowed = allowed;
      this.setDirty();
   }

   public boolean liquidationEnabled() {
      return this.liquidationEnabled;
   }

   public void setLiquidationEnabled(boolean enabled) {
      this.liquidationEnabled = enabled;
      this.setDirty();
   }

   public void clearPositions(UUID player) {
      boolean removed = this.holdings.remove(player) != null;
      removed |= this.costBasis.remove(player) != null;
      if (removed) {
         this.setDirty();
      }
   }

   public int clearNegativeWallets() {
      int cleared = 0;

      for (Entry<UUID, Long> entry : this.testWallets.entrySet()) {
         if (entry.getValue() < 0L) {
            entry.setValue(0L);
            cleared++;
         }
      }

      if (cleared > 0) {
         this.setDirty();
      }

      return cleared;
   }

   public List<Entry<UUID, Long>> holders(String stockId) {
      return this.holdersSorted(stockId, Integer.MAX_VALUE);
   }

   public List<Entry<UUID, Long>> holdersSorted(String stockId, int limit) {
      List<Entry<UUID, Long>> list = new ArrayList<>();

      for (Entry<UUID, Map<String, Long>> playerEntry : this.holdings.entrySet()) {
         long amount = playerEntry.getValue().getOrDefault(stockId, 0L);
         if (amount > 0L) {
            list.add(new SimpleImmutableEntry<>(playerEntry.getKey(), amount));
         }
      }

      list.sort((first, second) -> {
         int byShares = Long.compare(second.getValue(), first.getValue());
         return byShares != 0 ? byShares : first.getKey().compareTo(second.getKey());
      });
      return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
   }

   public long totalHeld(String stockId) {
      long total = 0L;

      for (Map<String, Long> owned : this.holdings.values()) {
         total += owned.getOrDefault(stockId, 0L);
      }

      return total;
   }

   public String playerName(UUID player) {
      String name = this.playerNames.get(player);
      return name != null && !name.isEmpty() ? name : "玩家 " + player.toString().substring(0, 6);
   }

   public void setPlayerName(UUID player, String name) {
      if (name != null && !name.isEmpty()) {
         if (!name.equals(this.playerNames.put(player, name))) {
            this.setDirty();
         }
      }
   }

   public long lastBalance(UUID player) {
      return this.lastBalances.getOrDefault(player, 0L);
   }

   public void setLastBalance(UUID player, long value) {
      Long previous = this.lastBalances.get(player);
      if (previous == null || previous != value) {
         this.lastBalances.put(player, value);
         this.setDirty();
      }
   }

   public UUID topHolder(String stockId) {
      return this.topHolders.get(stockId);
   }

   public void setTopHolder(String stockId, UUID player) {
      this.topHolders.put(stockId, player);
      this.setDirty();
   }

   public Set<UUID> knownPlayers() {
      Set<UUID> known = new HashSet<>(this.holdings.keySet());
      known.addAll(this.pendingDividends.keySet());
      known.addAll(this.playerNames.keySet());
      known.addAll(this.lastBalances.keySet());
      known.addAll(this.testWallets.keySet());
      return known;
   }

   public double dividendPool(String stockId) {
      return this.dividendPools.getOrDefault(stockId, 0.0);
   }

   public void addDividendPool(String stockId, double delta) {
      double total = Math.max(this.dividendPool(stockId) + delta, 0.0);
      if (total <= 1.0E-6) {
         this.dividendPools.remove(stockId);
      } else {
         this.dividendPools.put(stockId, total);
      }

      this.setDirty();
   }

   public long pendingDividend(UUID player) {
      return this.pendingDividends.getOrDefault(player, 0L);
   }

   public void addPendingDividend(UUID player, long amount) {
      if (amount > 0L) {
         this.pendingDividends.merge(player, amount, Long::sum);
         this.setDirty();
      }
   }

   public long takePendingDividend(UUID player) {
      Long amount = this.pendingDividends.remove(player);
      if (amount != null && amount > 0L) {
         this.setDirty();
         return amount;
      } else {
         return 0L;
      }
   }

   public double costForShares(String stockId, long shares) {
      return MarketMath.costForShares(this.emcPool(stockId), this.sharePool(stockId), shares);
   }

   public double proceedsForShares(String stockId, long shares) {
      return MarketMath.proceedsForShares(this.emcPool(stockId), this.sharePool(stockId), shares);
   }

   public long maxBuyable(String stockId) {
      return MarketMath.maxBuyable(this.sharePool(stockId));
   }

   public void applyBuy(String stockId, long shares, double emcIn) {
      double emc = this.emcPool(stockId);
      double pool = this.sharePool(stockId);
      double invariant = emc * pool;
      double newEmc = emc + emcIn;
      this.emcPools.put(stockId, newEmc);
      this.sharePools.put(stockId, Math.max(invariant / newEmc, 1.0E-6));
      this.setDirty();
   }

   public void applySell(String stockId, long shares, double emcOut) {
      double emc = this.emcPool(stockId);
      double pool = this.sharePool(stockId);
      double invariant = emc * pool;
      double newEmc = Math.max(emc - emcOut, 1.0E-6);
      this.emcPools.put(stockId, newEmc);
      this.sharePools.put(stockId, Math.max(invariant / newEmc, 1.0E-6));
      this.setDirty();
   }

   public void setEmcPool(String stockId, double value) {
      this.emcPools.put(stockId, Math.max(value, 1.0E-6));
      this.setDirty();
   }

   public int driftSeconds() {
      return this.driftSeconds;
   }

   public int setDriftSeconds(int seconds) {
      this.driftSeconds = clampDriftSeconds(seconds);
      this.setDirty();
      return this.driftSeconds;
   }

   public int driftTicks() {
      return this.driftSeconds * 20;
   }

   private static int clampDriftSeconds(int seconds) {
      return seconds <= 0 ? 30 : Math.max(5, Math.min(3600, seconds));
   }

   public void recordPrice(String stockId) {
      Deque<Double> deque = this.histories.computeIfAbsent(stockId, key -> new ArrayDeque<>(120));
      deque.addLast(this.price(stockId));

      while (deque.size() > 120) {
         deque.removeFirst();
      }
   }

   public float[] historyArray(String stockId) {
      Deque<Double> deque = this.histories.get(stockId);
      float[] out = new float[120];
      double current = this.price(stockId);
      if (deque != null && !deque.isEmpty()) {
         int pad = 120 - deque.size();
         double first = deque.peekFirst() == null ? current : deque.peekFirst();

         for (int i = 0; i < pad; i++) {
            out[i] = (float)first;
         }

         int index = pad;

         for (double value : deque) {
            if (index >= 120) {
               break;
            }

            out[index++] = (float)value;
         }

         return out;
      } else {
         for (int i = 0; i < 120; i++) {
            out[i] = (float)current;
         }

         return out;
      }
   }
}
