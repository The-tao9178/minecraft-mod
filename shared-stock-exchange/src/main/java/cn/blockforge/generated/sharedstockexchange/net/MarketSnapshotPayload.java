package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务器 -> 客户端：一份完整行情快照。
 * prices / emcPools / sharePools / holdings / costs / history 的顺序和 {@code Stocks.ALL} 一致，
 * 两边是同一份常量表。
 *
 * emcPools / sharePools 是每只股票的资金池和股份池：客户端拿它们就能用
 * {@link MarketMath} 算出"买 N 股要多少 EMC、成交后还剩多少"，和服务端扣钱完全一致。
 *
 * history 是一维数组，第 i 只股票的走势在 [i * 每只点数, (i + 1) * 每只点数)。
 * dividends 是这只股票攒着待分红的手续费，anchors 是等价交换里该物品的 EMC 参考价，
 * driftSeconds 是服务器设置的行情波动间隔（秒）。
 *
 * debtAllowed / liquidationEnabled 是服务器那两个开关（负债系统、强制平仓），
 * 客户端要用它们画状态、算「这样买会不会立刻爆仓」，两边看到的规则必须一致。
 */
public record MarketSnapshotPayload(double[] prices, double[] emcPools, double[] sharePools,
                                    long[] holdings, double[] costs, float[] history,
                                    double[] dividends, double[] anchors, String[] topNames, long[] topShares,
                                    long balance, long pendingDividend, int cashMode,
                                    int driftSeconds, boolean debtAllowed, boolean liquidationEnabled)
        implements CustomPacketPayload {
    private static final int MAX_STOCKS = 64;
    private static final int MAX_HISTORY = 8192;
    private static final int MAX_NAME = 32;

    public static final Type<MarketSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StockExchangeMod.MOD_ID, "market_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            MarketSnapshotPayload::encode,
            MarketSnapshotPayload::decode
    );

    /** 现金模式：0 = 测试钱包（没装等价交换），1 = 已接通等价交换的 EMC，2 = 装了但没接通。 */
    public static final int CASH_TEST = 0;
    public static final int CASH_EMC = 1;
    public static final int CASH_BROKEN = 2;

    /** 现金是不是等价交换的 EMC。 */
    public boolean projectE() {
        return this.cashMode == CASH_EMC;
    }

    /** 装了等价交换但没接通：界面要老实提示，别让人以为「没装」。 */
    public boolean projectEBroken() {
        return this.cashMode == CASH_BROKEN;
    }

    /** 每只股票在 history 里占多少个点。 */
    public int samplesPerStock() {
        return this.prices.length <= 0 ? 1 : Math.max(1, this.history.length / this.prices.length);
    }

    /** 第 index 只股票最近一次的价格，用来算涨跌。 */
    public float changeOf(int index) {
        int per = samplesPerStock();
        int base = index * per;
        if (base < 0 || base + per > this.history.length) {
            return 0.0F;
        }
        float first = this.history[base];
        float last = this.history[base + per - 1];
        return first <= 0.0F ? 0.0F : last / first - 1.0F;
    }

    public double dividendOf(int index) {
        return index >= 0 && index < this.dividends.length ? this.dividends[index] : 0.0D;
    }

    public double anchorOf(int index) {
        return index >= 0 && index < this.anchors.length ? this.anchors[index] : 0.0D;
    }

    public double emcPoolOf(int index) {
        return index >= 0 && index < this.emcPools.length ? this.emcPools[index] : 0.0D;
    }

    public double sharePoolOf(int index) {
        return index >= 0 && index < this.sharePools.length && this.sharePools[index] > 0.0D
                ? this.sharePools[index]
                : MarketMath.MIN_POOL;
    }

    public String topNameOf(int index) {
        return index >= 0 && index < this.topNames.length && this.topNames[index] != null ? this.topNames[index] : "";
    }

    public long topSharesOf(int index) {
        return index >= 0 && index < this.topShares.length ? this.topShares[index] : 0L;
    }

    /** 买 shares 股要花多少 EMC（含价格冲击，不含手续费）；买不到返回无穷大。 */
    public double costOf(int index, long shares) {
        return MarketMath.costForShares(emcPoolOf(index), sharePoolOf(index), shares);
    }

    /** 卖 shares 股能拿回多少 EMC（含价格冲击，扣手续费之前）。 */
    public double proceedsOf(int index, long shares) {
        return MarketMath.proceedsForShares(emcPoolOf(index), sharePoolOf(index), shares);
    }

    /** 这个价位最多还能买多少股。 */
    public long maxBuyable(int index) {
        return MarketMath.maxBuyable(sharePoolOf(index));
    }

    /**
     * 我的持仓值多少钱：每只股票都按「现在全部卖掉实际能拿回多少」算。
     *
     * 不能用「现价 × 股数」——股份池是恒定乘积的，自己把价格买上去之后，
     * 乘出来的账面数字会比真能卖出的钱大好几倍，保证金和强平就全失真了。
     */
    public double positionValue() {
        int size = Math.min(this.prices.length, this.holdings.length);
        double value = 0.0D;
        for (int i = 0; i < size; i++) {
            if (this.holdings[i] > 0L) {
                value += proceedsOf(i, this.holdings[i]);
            }
        }
        return value;
    }

    /** 欠的钱；没欠债就是 0。 */
    public double debt() {
        return this.balance < 0L ? (double) (-this.balance) : 0.0D;
    }

    /** 总资产 = 现金 + 持仓市值（欠债时现金是负数）。 */
    public double equity() {
        return MarketMath.equityOf(this.balance, positionValue());
    }

    /** 权益已经跌破维持保证金线：下一次检查就会被强制平仓。 */
    public boolean marginRisk() {
        return MarketMath.needsLiquidation(this.balance, positionValue());
    }

    /** 按这只股票第 index 段的走势，取出「起点价」算涨跌幅。 */
    public float priceAt(int index, int sample) {
        int per = samplesPerStock();
        int base = index * per + sample;
        return base >= 0 && base < this.history.length ? this.history[base] : 0.0F;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MarketSnapshotPayload payload) {
        buffer.writeVarInt(payload.prices.length);
        for (double price : payload.prices) {
            buffer.writeDouble(price);
        }
        buffer.writeVarInt(payload.emcPools.length);
        for (double pool : payload.emcPools) {
            buffer.writeDouble(pool);
        }
        buffer.writeVarInt(payload.sharePools.length);
        for (double pool : payload.sharePools) {
            buffer.writeDouble(pool);
        }
        buffer.writeVarInt(payload.holdings.length);
        for (long holding : payload.holdings) {
            buffer.writeLong(holding);
        }
        buffer.writeVarInt(payload.costs.length);
        for (double cost : payload.costs) {
            buffer.writeDouble(cost);
        }
        buffer.writeVarInt(payload.history.length);
        for (float sample : payload.history) {
            buffer.writeFloat(sample);
        }
        buffer.writeVarInt(payload.dividends.length);
        for (double pool : payload.dividends) {
            buffer.writeDouble(pool);
        }
        buffer.writeVarInt(payload.anchors.length);
        for (double anchor : payload.anchors) {
            buffer.writeDouble(anchor);
        }
        buffer.writeVarInt(payload.topNames.length);
        for (String name : payload.topNames) {
            buffer.writeUtf(name == null ? "" : name, MAX_NAME);
        }
        buffer.writeVarInt(payload.topShares.length);
        for (long shares : payload.topShares) {
            buffer.writeLong(shares);
        }
        buffer.writeLong(payload.balance);
        buffer.writeLong(payload.pendingDividend);
        buffer.writeVarInt(payload.cashMode);
        buffer.writeVarInt(payload.driftSeconds);
        buffer.writeBoolean(payload.debtAllowed);
        buffer.writeBoolean(payload.liquidationEnabled);
    }

    private static MarketSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        int priceCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] prices = new double[priceCount];
        for (int i = 0; i < priceCount; i++) {
            prices[i] = buffer.readDouble();
        }
        int emcPoolCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] emcPools = new double[emcPoolCount];
        for (int i = 0; i < emcPoolCount; i++) {
            emcPools[i] = buffer.readDouble();
        }
        int sharePoolCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] sharePools = new double[sharePoolCount];
        for (int i = 0; i < sharePoolCount; i++) {
            sharePools[i] = buffer.readDouble();
        }
        int holdingCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        long[] holdings = new long[holdingCount];
        for (int i = 0; i < holdingCount; i++) {
            holdings[i] = buffer.readLong();
        }
        int costCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] costs = new double[costCount];
        for (int i = 0; i < costCount; i++) {
            costs[i] = buffer.readDouble();
        }
        int historyCount = Math.min(buffer.readVarInt(), MAX_HISTORY);
        float[] history = new float[historyCount];
        for (int i = 0; i < historyCount; i++) {
            history[i] = buffer.readFloat();
        }
        int dividendCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] dividends = new double[dividendCount];
        for (int i = 0; i < dividendCount; i++) {
            dividends[i] = buffer.readDouble();
        }
        int anchorCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        double[] anchors = new double[anchorCount];
        for (int i = 0; i < anchorCount; i++) {
            anchors[i] = buffer.readDouble();
        }
        int nameCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        String[] topNames = new String[nameCount];
        for (int i = 0; i < nameCount; i++) {
            topNames[i] = buffer.readUtf(MAX_NAME);
        }
        int topShareCount = Math.min(buffer.readVarInt(), MAX_STOCKS);
        long[] topShares = new long[topShareCount];
        for (int i = 0; i < topShareCount; i++) {
            topShares[i] = buffer.readLong();
        }
        long balance = buffer.readLong();
        long pendingDividend = buffer.readLong();
        int cashMode = buffer.readVarInt();
        int driftSeconds = buffer.readVarInt();
        boolean debtAllowed = buffer.readBoolean();
        boolean liquidationEnabled = buffer.readBoolean();
        return new MarketSnapshotPayload(prices, emcPools, sharePools, holdings, costs, history,
                dividends, anchors, topNames, topShares, balance, pendingDividend, cashMode, driftSeconds,
                debtAllowed, liquidationEnabled);
    }

    @Override
    public Type<MarketSnapshotPayload> type() {
        return TYPE;
    }
}
