package cn.blockforge.generated.sharedstockexchange.market;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 全服唯一的交易所存档。放在世界存档里，所以服务器重启、换玩家都不会丢。
 *
 * 每只股票用"资金池 + 股份池"描述：
 *   价格 = 资金池 EMC / 股份池股数
 *   有人买入 -> 资金池变大、股份池变小 -> 价格和市值都被推高
 *   有人卖出 -> 反过来，价格和市值都被压低
 * 由于 资金池 × 股份池 保持恒定，买得越多单价越贵，没法靠一买一卖套利。
 *
 * 另外每个玩家每只股票都记着"现在还压着多少成本"，这样才能算持仓均价和盈亏。
 * 联机玩法靠这几样：每只股票的手续费分红池、离线玩家的待领分红、股东名单和最近一次看到的余额。
 */
public class MarketData extends SavedData {
    public static final String NAME = "shared_stock_exchange_market";
    public static final long TEST_WALLET_START = 1_000_000L;
    /** 每只股票在内存里保留多少个价格点，用来画走势线（涨跌图的数据量）。 */
    public static final int HISTORY_LENGTH = 120;
    /** 行情自动波动的默认间隔（秒），服务器可以用 /stock interval 改。 */
    public static final int DEFAULT_DRIFT_SECONDS = 30;
    public static final int MIN_DRIFT_SECONDS = 5;
    public static final int MAX_DRIFT_SECONDS = 3600;

    public static final SavedData.Factory<MarketData> FACTORY = new SavedData.Factory<>(MarketData::new, MarketData::load);

    private final Map<String, Double> emcPools = new HashMap<>();
    private final Map<String, Double> sharePools = new HashMap<>();
    private final Map<UUID, Map<String, Long>> holdings = new HashMap<>();
    private final Map<UUID, Map<String, Double>> costBasis = new HashMap<>();
    private final Map<UUID, Long> testWallets = new HashMap<>();
    /** 价格走势，跟着存档一起保存，重启后涨跌图还接得上。 */
    private final Map<String, Deque<Double>> histories = new HashMap<>();
    /** 行情自动波动一次要隔多少秒（可自定义）。 */
    private int driftSeconds = DEFAULT_DRIFT_SECONDS;
    /** 负债系统：打开时等价交换的 EMC 可以直接变成负数（欠债），关掉后只能花手里的现金。默认打开。 */
    private boolean debtAllowed = true;
    /** 强制平仓：打开时欠债又跌破强平线会被自动卖出。默认打开。 */
    private boolean liquidationEnabled = true;
    /** 每只股票攒着待分红的手续费（EMC）。 */
    private final Map<String, Double> dividendPools = new HashMap<>();
    /** 离线玩家没领到的分红，上线后补发。 */
    private final Map<UUID, Long> pendingDividends = new HashMap<>();
    /** 玩家名，股东榜和公告要用。 */
    private final Map<UUID, String> playerNames = new HashMap<>();
    /** 最近一次看到的现金余额，离线玩家也算得上财富榜。 */
    private final Map<UUID, Long> lastBalances = new HashMap<>();
    /** 每只股票上一次公告过的第一大股东，防止同一个人反复刷公告。 */
    private final Map<String, UUID> topHolders = new HashMap<>();

    public MarketData() {
        for (StockDefinition definition : Stocks.ALL) {
            this.emcPools.put(definition.id(), definition.initialEmcPool());
            this.sharePools.put(definition.id(), (double) definition.totalShares());
        }
    }

    public static MarketData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketData data = new MarketData();
        data.driftSeconds = clampDriftSeconds(tag.getInt("drift"));
        // 老存档里没有这两个键：缺省按「开」处理，和默认配置一致
        data.debtAllowed = !tag.contains("debt") || tag.getBoolean("debt");
        data.liquidationEnabled = !tag.contains("liq") || tag.getBoolean("liq");
        CompoundTag stocksTag = tag.getCompound("stocks");
        for (String id : stocksTag.getAllKeys()) {
            if (!data.emcPools.containsKey(id)) {
                continue;
            }
            CompoundTag stockTag = stocksTag.getCompound(id);
            data.emcPools.put(id, Math.max(stockTag.getDouble("emc"), 1.0D));
            data.sharePools.put(id, Math.max(stockTag.getDouble("shares"), 1.0E-6D));
            ListTag prices = stockTag.getList("prices", Tag.TAG_DOUBLE);
            if (!prices.isEmpty()) {
                Deque<Double> deque = data.histories.computeIfAbsent(id, key -> new ArrayDeque<>(HISTORY_LENGTH));
                int start = Math.max(0, prices.size() - HISTORY_LENGTH);
                for (int i = start; i < prices.size(); i++) {
                    deque.addLast(prices.getDouble(i));
                }
            }
        }
        CompoundTag playersTag = tag.getCompound("players");
        for (String key : playersTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
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
            for (String stockId : costTag.getAllKeys()) {
                double cost = costTag.getDouble(stockId);
                if (cost > 0.0D) {
                    costs.put(stockId, cost);
                }
            }
            if (!costs.isEmpty()) {
                data.costBasis.put(uuid, costs);
            }
            long wallet = playerTag.getLong("wallet");
            // 钱包可以是负数（透支买入欠下的债），所以只跳过 0
            if (wallet != 0L) {
                data.testWallets.put(uuid, wallet);
            }
        }
        loadDoubles(tag.getCompound("dividends"), data.dividendPools);
        loadLongs(tag.getCompound("pending"), data.pendingDividends, 1L);
        loadLongs(tag.getCompound("balances"), data.lastBalances, Long.MIN_VALUE);
        // 旧版本的欠款账本（"debts"）已经废弃：负数现在直接记在等价交换的 EMC 上，
        // 读档时直接忽略这一段，老存档里的欠款也就一笔勾销了。
        CompoundTag namesTag = tag.getCompound("names");
        for (String key : namesTag.getAllKeys()) {
            try {
                data.playerNames.put(UUID.fromString(key), namesTag.getString(key));
            } catch (IllegalArgumentException ignored) {
                // 坏存档直接跳过
            }
        }
        CompoundTag topTag = tag.getCompound("top");
        for (String stockId : topTag.getAllKeys()) {
            try {
                data.topHolders.put(stockId, UUID.fromString(topTag.getString(stockId)));
            } catch (IllegalArgumentException ignored) {
                // 坏存档直接跳过
            }
        }
        return data;
    }

    private static void loadDoubles(CompoundTag tag, Map<String, Double> target) {
        for (String key : tag.getAllKeys()) {
            double value = tag.getDouble(key);
            if (value > 0.0D) {
                target.put(key, value);
            }
        }
    }

    private static void loadLongs(CompoundTag tag, Map<UUID, Long> target, long minimum) {
        for (String key : tag.getAllKeys()) {
            long value = tag.getLong(key);
            if (value < minimum) {
                continue;
            }
            try {
                target.put(UUID.fromString(key), value);
            } catch (IllegalArgumentException ignored) {
                // 坏存档直接跳过
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("drift", this.driftSeconds);
        tag.putBoolean("debt", this.debtAllowed);
        tag.putBoolean("liq", this.liquidationEnabled);
        CompoundTag stocksTag = new CompoundTag();
        for (Map.Entry<String, Double> entry : this.emcPools.entrySet()) {
            CompoundTag stockTag = new CompoundTag();
            stockTag.putDouble("emc", entry.getValue());
            stockTag.putDouble("shares", this.sharePools.getOrDefault(entry.getKey(), 0.0D));
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
                for (Map.Entry<String, Long> entry : owned.entrySet()) {
                    if (entry.getValue() > 0L) {
                        holdTag.putLong(entry.getKey(), entry.getValue());
                    }
                }
            }
            playerTag.put("holdings", holdTag);

            CompoundTag costTag = new CompoundTag();
            Map<String, Double> costs = this.costBasis.get(uuid);
            if (costs != null) {
                for (Map.Entry<String, Double> entry : costs.entrySet()) {
                    if (entry.getValue() > 0.0D) {
                        costTag.putDouble(entry.getKey(), entry.getValue());
                    }
                }
            }
            playerTag.put("cost", costTag);

            playerTag.putLong("wallet", this.testWallets.getOrDefault(uuid, 0L));
            playersTag.put(uuid.toString(), playerTag);
        }
        tag.put("players", playersTag);

        CompoundTag dividendsTag = new CompoundTag();
        for (Map.Entry<String, Double> entry : this.dividendPools.entrySet()) {
            if (entry.getValue() > 0.0D) {
                dividendsTag.putDouble(entry.getKey(), entry.getValue());
            }
        }
        tag.put("dividends", dividendsTag);

        CompoundTag pendingTag = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : this.pendingDividends.entrySet()) {
            if (entry.getValue() > 0L) {
                pendingTag.putLong(entry.getKey().toString(), entry.getValue());
            }
        }
        tag.put("pending", pendingTag);

        CompoundTag balancesTag = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : this.lastBalances.entrySet()) {
            balancesTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("balances", balancesTag);

        CompoundTag namesTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : this.playerNames.entrySet()) {
            namesTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("names", namesTag);

        CompoundTag topTag = new CompoundTag();
        for (Map.Entry<String, UUID> entry : this.topHolders.entrySet()) {
            topTag.putString(entry.getKey(), entry.getValue().toString());
        }
        tag.put("top", topTag);
        return tag;
    }

    public double emcPool(String stockId) {
        return this.emcPools.getOrDefault(stockId, 0.0D);
    }

    public double sharePool(String stockId) {
        return this.sharePools.getOrDefault(stockId, 1.0E-6D);
    }

    /** 当前股价（EMC/股）。 */
    public double price(String stockId) {
        return this.emcPool(stockId) / Math.max(this.sharePool(stockId), 1.0E-6D);
    }

    /** 市值 = 股价 × 总股本。别人买入会把它推上去。 */
    public double marketCap(StockDefinition definition) {
        return this.price(definition.id()) * (double) definition.totalShares();
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

    /** 这个玩家在这只股票上还压着多少成本（EMC）。 */
    public double costBasis(UUID player, String stockId) {
        Map<String, Double> costs = this.costBasis.get(player);
        return costs == null ? 0.0D : costs.getOrDefault(stockId, 0.0D);
    }

    public void addCostBasis(UUID player, String stockId, double delta) {
        Map<String, Double> costs = this.costBasis.computeIfAbsent(player, key -> new HashMap<>());
        double total = costs.getOrDefault(stockId, 0.0D) + delta;
        if (total <= 1.0E-6D) {
            costs.remove(stockId);
        } else {
            costs.put(stockId, total);
        }
        if (costs.isEmpty()) {
            this.costBasis.remove(player);
        }
        this.setDirty();
    }

    /** 持仓均价（EMC/股）；没持仓返回 0。 */
    public double avgCost(UUID player, String stockId) {
        long owned = holding(player, stockId);
        return owned <= 0L ? 0.0D : costBasis(player, stockId) / (double) owned;
    }

    public long testWallet(UUID player) {
        return this.testWallets.getOrDefault(player, TEST_WALLET_START);
    }

    /** 改测试钱包；允许改成负数（透支买入欠下的债）。 */
    public void setTestWallet(UUID player, long value) {
        this.testWallets.put(player, value);
        this.setDirty();
    }

    /** 负债系统开着没有：EMC 能不能变成负数。 */
    public boolean debtAllowed() {
        return this.debtAllowed;
    }

    public void setDebtAllowed(boolean allowed) {
        this.debtAllowed = allowed;
        this.setDirty();
    }

    /** 强制平仓开着没有。 */
    public boolean liquidationEnabled() {
        return this.liquidationEnabled;
    }

    public void setLiquidationEnabled(boolean enabled) {
        this.liquidationEnabled = enabled;
        this.setDirty();
    }

    /**
     * 把某个人所有持仓和持仓成本一起清掉（股票直接作废，不结算钱）。
     * 「未成年退款」用它：债务和持仓一起归零，只留 1000 EMC 重新开始。
     */
    public void clearPositions(UUID player) {
        boolean removed = this.holdings.remove(player) != null;
        removed |= this.costBasis.remove(player) != null;
        if (removed) {
            this.setDirty();
        }
    }

    /** 关掉负债系统时用：把所有负数的测试钱包抹成 0，返回改了几个。 */
    public int clearNegativeWallets() {
        int cleared = 0;
        for (Map.Entry<UUID, Long> entry : this.testWallets.entrySet()) {
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

    // ---- 股东与联机数据 ----

    /** 这只股票现在所有的股东（快照，按股数从多到少）。 */
    public List<Map.Entry<UUID, Long>> holders(String stockId) {
        return holdersSorted(stockId, Integer.MAX_VALUE);
    }

    /** 这只股票持股最多的 limit 个玩家。 */
    public List<Map.Entry<UUID, Long>> holdersSorted(String stockId, int limit) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Long>> playerEntry : this.holdings.entrySet()) {
            long amount = playerEntry.getValue().getOrDefault(stockId, 0L);
            if (amount > 0L) {
                list.add(new AbstractMap.SimpleImmutableEntry<>(playerEntry.getKey(), amount));
            }
        }
        list.sort((first, second) -> {
            int byShares = Long.compare(second.getValue(), first.getValue());
            return byShares != 0 ? byShares : first.getKey().compareTo(second.getKey());
        });
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    /** 全服玩家在这只股票上的总持股。 */
    public long totalHeld(String stockId) {
        long total = 0L;
        for (Map<String, Long> owned : this.holdings.values()) {
            total += owned.getOrDefault(stockId, 0L);
        }
        return total;
    }

    public String playerName(UUID player) {
        String name = this.playerNames.get(player);
        if (name == null || name.isEmpty()) {
            return "玩家 " + player.toString().substring(0, 6);
        }
        return name;
    }

    public void setPlayerName(UUID player, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (!name.equals(this.playerNames.put(player, name))) {
            this.setDirty();
        }
    }

    public long lastBalance(UUID player) {
        return this.lastBalances.getOrDefault(player, 0L);
    }

    public void setLastBalance(UUID player, long value) {
        Long previous = this.lastBalances.get(player);
        // 现在 EMC 本身可以是负数，这里就照原样记，别把负数抹成 0
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

    // ---- 分红 ----

    public double dividendPool(String stockId) {
        return this.dividendPools.getOrDefault(stockId, 0.0D);
    }

    public void addDividendPool(String stockId, double delta) {
        double total = Math.max(this.dividendPool(stockId) + delta, 0.0D);
        if (total <= 1.0E-6D) {
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
        if (amount <= 0L) {
            return;
        }
        this.pendingDividends.merge(player, amount, Long::sum);
        this.setDirty();
    }

    /** 取走待领分红，返回取走的数量。 */
    public long takePendingDividend(UUID player) {
        Long amount = this.pendingDividends.remove(player);
        if (amount == null || amount <= 0L) {
            return 0L;
        }
        this.setDirty();
        return amount;
    }

    /** 买入 shares 股需要的 EMC（含价格冲击）。股份池不够时返回无穷大。 */
    public double costForShares(String stockId, long shares) {
        return MarketMath.costForShares(this.emcPool(stockId), this.sharePool(stockId), shares);
    }

    /** 卖出 shares 股能拿回的 EMC（含价格冲击）。 */
    public double proceedsForShares(String stockId, long shares) {
        return MarketMath.proceedsForShares(this.emcPool(stockId), this.sharePool(stockId), shares);
    }

    /** 这个价位最多还能买多少股。 */
    public long maxBuyable(String stockId) {
        return MarketMath.maxBuyable(this.sharePool(stockId));
    }

    /** 成交：资金池增加 emcIn，股份池减少，价格随之被推高。 */
    public void applyBuy(String stockId, long shares, double emcIn) {
        double emc = this.emcPool(stockId);
        double pool = this.sharePool(stockId);
        double invariant = emc * pool;
        double newEmc = emc + emcIn;
        this.emcPools.put(stockId, newEmc);
        this.sharePools.put(stockId, Math.max(invariant / newEmc, 1.0E-6D));
        this.setDirty();
    }

    /** 成交：资金池减少 emcOut，股份池增加，价格随之被压低。 */
    public void applySell(String stockId, long shares, double emcOut) {
        double emc = this.emcPool(stockId);
        double pool = this.sharePool(stockId);
        double invariant = emc * pool;
        double newEmc = Math.max(emc - emcOut, 1.0E-6D);
        this.emcPools.put(stockId, newEmc);
        this.sharePools.put(stockId, Math.max(invariant / newEmc, 1.0E-6D));
        this.setDirty();
    }

    /** 行情自然波动：轻微随机 + 向参考价回归。 */
    public void setEmcPool(String stockId, double value) {
        this.emcPools.put(stockId, Math.max(value, 1.0E-6D));
        this.setDirty();
    }

    /** 行情自动波动一次要隔多少秒。 */
    public int driftSeconds() {
        return this.driftSeconds;
    }

    /** 改波动间隔，自动夹在 5 秒到 1 小时之间；返回实际生效的秒数。 */
    public int setDriftSeconds(int seconds) {
        this.driftSeconds = clampDriftSeconds(seconds);
        this.setDirty();
        return this.driftSeconds;
    }

    /** 波动间隔换算成 tick（1 秒 = 20 tick）。 */
    public int driftTicks() {
        return this.driftSeconds * 20;
    }

    private static int clampDriftSeconds(int seconds) {
        if (seconds <= 0) {
            return DEFAULT_DRIFT_SECONDS;
        }
        return Math.max(MIN_DRIFT_SECONDS, Math.min(MAX_DRIFT_SECONDS, seconds));
    }

    /** 记一个价格点到走势里。 */
    public void recordPrice(String stockId) {
        Deque<Double> deque = this.histories.computeIfAbsent(stockId, key -> new ArrayDeque<>(HISTORY_LENGTH));
        deque.addLast(price(stockId));
        while (deque.size() > HISTORY_LENGTH) {
            deque.removeFirst();
        }
    }

    /** 固定长度的走势数组（点数不够时用最早的价格补前面）。 */
    public float[] historyArray(String stockId) {
        Deque<Double> deque = this.histories.get(stockId);
        float[] out = new float[HISTORY_LENGTH];
        double current = price(stockId);
        if (deque == null || deque.isEmpty()) {
            for (int i = 0; i < HISTORY_LENGTH; i++) {
                out[i] = (float) current;
            }
            return out;
        }
        int pad = HISTORY_LENGTH - deque.size();
        double first = deque.peekFirst() == null ? current : deque.peekFirst();
        for (int i = 0; i < pad; i++) {
            out[i] = (float) first;
        }
        int index = pad;
        for (double value : deque) {
            if (index >= HISTORY_LENGTH) {
                break;
            }
            out[index++] = (float) value;
        }
        return out;
    }
}
