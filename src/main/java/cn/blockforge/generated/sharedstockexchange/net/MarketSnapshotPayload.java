package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import net.minecraft.network.FriendlyByteBuf;

public record MarketSnapshotPayload(
   double[] prices,
   double[] emcPools,
   double[] sharePools,
   long[] holdings,
   double[] costs,
   float[] history,
   double[] dividends,
   double[] anchors,
   String[] topNames,
   long[] topShares,
   long balance,
   long pendingDividend,
   int cashMode,
   int driftSeconds,
   boolean debtAllowed,
   boolean liquidationEnabled
) {
   private static final int MAX_STOCKS = 64;
   private static final int MAX_HISTORY = 8192;
   private static final int MAX_NAME = 32;
   public static final int CASH_TEST = 0;
   public static final int CASH_EMC = 1;
   public static final int CASH_BROKEN = 2;

   public boolean projectE() {
      return this.cashMode == 1;
   }

   public boolean projectEBroken() {
      return this.cashMode == 2;
   }

   public int samplesPerStock() {
      return this.prices.length <= 0 ? 1 : Math.max(1, this.history.length / this.prices.length);
   }

   public float changeOf(int index) {
      int per = this.samplesPerStock();
      int base = index * per;
      if (base >= 0 && base + per <= this.history.length) {
         float first = this.history[base];
         float last = this.history[base + per - 1];
         return first <= 0.0F ? 0.0F : last / first - 1.0F;
      } else {
         return 0.0F;
      }
   }

   public double dividendOf(int index) {
      return index >= 0 && index < this.dividends.length ? this.dividends[index] : 0.0;
   }

   public double anchorOf(int index) {
      return index >= 0 && index < this.anchors.length ? this.anchors[index] : 0.0;
   }

   public double emcPoolOf(int index) {
      return index >= 0 && index < this.emcPools.length ? this.emcPools[index] : 0.0;
   }

   public double sharePoolOf(int index) {
      return index >= 0 && index < this.sharePools.length && this.sharePools[index] > 0.0 ? this.sharePools[index] : 1.0E-6;
   }

   public String topNameOf(int index) {
      return index >= 0 && index < this.topNames.length && this.topNames[index] != null ? this.topNames[index] : "";
   }

   public long topSharesOf(int index) {
      return index >= 0 && index < this.topShares.length ? this.topShares[index] : 0L;
   }

   public double costOf(int index, long shares) {
      return MarketMath.costForShares(this.emcPoolOf(index), this.sharePoolOf(index), shares);
   }

   public double proceedsOf(int index, long shares) {
      return MarketMath.proceedsForShares(this.emcPoolOf(index), this.sharePoolOf(index), shares);
   }

   public long maxBuyable(int index) {
      return MarketMath.maxBuyable(this.sharePoolOf(index));
   }

   public double positionValue() {
      int size = Math.min(this.prices.length, this.holdings.length);
      double value = 0.0;

      for (int i = 0; i < size; i++) {
         if (this.holdings[i] > 0L) {
            value += this.proceedsOf(i, this.holdings[i]);
         }
      }

      return value;
   }

   public double debt() {
      return this.balance < 0L ? (double)(-this.balance) : 0.0;
   }

   public double equity() {
      return MarketMath.equityOf((double)this.balance, this.positionValue());
   }

   public boolean marginRisk() {
      return MarketMath.needsLiquidation((double)this.balance, this.positionValue());
   }

   public float priceAt(int index, int sample) {
      int per = this.samplesPerStock();
      int base = index * per + sample;
      return base >= 0 && base < this.history.length ? this.history[base] : 0.0F;
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.prices.length);

      for (double price : this.prices) {
         buffer.writeDouble(price);
      }

      buffer.writeVarInt(this.emcPools.length);

      for (double pool : this.emcPools) {
         buffer.writeDouble(pool);
      }

      buffer.writeVarInt(this.sharePools.length);

      for (double pool : this.sharePools) {
         buffer.writeDouble(pool);
      }

      buffer.writeVarInt(this.holdings.length);

      for (long holding : this.holdings) {
         buffer.writeLong(holding);
      }

      buffer.writeVarInt(this.costs.length);

      for (double cost : this.costs) {
         buffer.writeDouble(cost);
      }

      buffer.writeVarInt(this.history.length);

      for (float sample : this.history) {
         buffer.writeFloat(sample);
      }

      buffer.writeVarInt(this.dividends.length);

      for (double pool : this.dividends) {
         buffer.writeDouble(pool);
      }

      buffer.writeVarInt(this.anchors.length);

      for (double anchor : this.anchors) {
         buffer.writeDouble(anchor);
      }

      buffer.writeVarInt(this.topNames.length);

      for (String name : this.topNames) {
         buffer.writeUtf(name == null ? "" : name, 32);
      }

      buffer.writeVarInt(this.topShares.length);

      for (long shares : this.topShares) {
         buffer.writeLong(shares);
      }

      buffer.writeLong(this.balance);
      buffer.writeLong(this.pendingDividend);
      buffer.writeVarInt(this.cashMode);
      buffer.writeVarInt(this.driftSeconds);
      buffer.writeBoolean(this.debtAllowed);
      buffer.writeBoolean(this.liquidationEnabled);
   }

   public static MarketSnapshotPayload decode(FriendlyByteBuf buffer) {
      int priceCount = Math.min(buffer.readVarInt(), 64);
      double[] prices = new double[priceCount];

      for (int i = 0; i < priceCount; i++) {
         prices[i] = buffer.readDouble();
      }

      int emcPoolCount = Math.min(buffer.readVarInt(), 64);
      double[] emcPools = new double[emcPoolCount];

      for (int i = 0; i < emcPoolCount; i++) {
         emcPools[i] = buffer.readDouble();
      }

      int sharePoolCount = Math.min(buffer.readVarInt(), 64);
      double[] sharePools = new double[sharePoolCount];

      for (int i = 0; i < sharePoolCount; i++) {
         sharePools[i] = buffer.readDouble();
      }

      int holdingCount = Math.min(buffer.readVarInt(), 64);
      long[] holdings = new long[holdingCount];

      for (int i = 0; i < holdingCount; i++) {
         holdings[i] = buffer.readLong();
      }

      int costCount = Math.min(buffer.readVarInt(), 64);
      double[] costs = new double[costCount];

      for (int i = 0; i < costCount; i++) {
         costs[i] = buffer.readDouble();
      }

      int historyCount = Math.min(buffer.readVarInt(), 8192);
      float[] history = new float[historyCount];

      for (int i = 0; i < historyCount; i++) {
         history[i] = buffer.readFloat();
      }

      int dividendCount = Math.min(buffer.readVarInt(), 64);
      double[] dividends = new double[dividendCount];

      for (int i = 0; i < dividendCount; i++) {
         dividends[i] = buffer.readDouble();
      }

      int anchorCount = Math.min(buffer.readVarInt(), 64);
      double[] anchors = new double[anchorCount];

      for (int i = 0; i < anchorCount; i++) {
         anchors[i] = buffer.readDouble();
      }

      int nameCount = Math.min(buffer.readVarInt(), 64);
      String[] topNames = new String[nameCount];

      for (int i = 0; i < nameCount; i++) {
         topNames[i] = buffer.readUtf(32);
      }

      int topShareCount = Math.min(buffer.readVarInt(), 64);
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
         pendingDividend,
         cashMode,
         driftSeconds,
         debtAllowed,
         liquidationEnabled
      );
   }
}
