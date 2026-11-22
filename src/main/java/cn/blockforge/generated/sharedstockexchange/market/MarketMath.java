package cn.blockforge.generated.sharedstockexchange.market;

public final class MarketMath {
   public static final double MIN_POOL = 1.0E-6;
   public static final double FEE_RATE = 0.01;
   public static final double SHARE_RESERVE = 0.1;
   public static final double MAINTENANCE_MARGIN = 0.1;
   public static final double MAX_DEBT = 9.0E15;

   private MarketMath() {
   }

   public static double costForShares(double emcPool, double sharePool, long shares) {
      if (shares > 0L && sharePool > 1.0E-6) {
         double remaining = sharePool - (double)shares;
         if (remaining <= 1.0E-6) {
            return Double.POSITIVE_INFINITY;
         } else {
            double invariant = emcPool * sharePool;
            return invariant / remaining - emcPool;
         }
      } else {
         return Double.POSITIVE_INFINITY;
      }
   }

   public static double proceedsForShares(double emcPool, double sharePool, long shares) {
      if (shares > 0L && emcPool > 0.0) {
         double pool = Math.max(sharePool, 1.0E-6);
         double invariant = emcPool * pool;
         double payout = emcPool - invariant / (pool + (double)shares);
         return Math.max(payout, 0.0);
      } else {
         return 0.0;
      }
   }

   public static long maxBuyable(double sharePool) {
      if (!(sharePool > 1.0E-6)) {
         return 0L;
      } else {
         long shares = (long)Math.floor(sharePool * 0.9);
         return Math.max(shares, 0L);
      }
   }

   public static long clampBuy(long requested, double sharePool) {
      return requested <= 0L ? 0L : Math.min(requested, maxBuyable(sharePool));
   }

   public static double equityOf(double cash, double positionValue) {
      return cash + Math.max(positionValue, 0.0);
   }

   public static double maintenanceLine(double positionValue) {
      return Math.max(positionValue, 0.0) * 0.1;
   }

   public static boolean needsLiquidation(double cash, double positionValue) {
      return !(cash >= 0.0) && !(positionValue <= 0.0) ? equityOf(cash, positionValue) < maintenanceLine(positionValue) : false;
   }

   public static boolean survivesMaintenance(double cash, double positionValue, double cost, double fee) {
      double equityAfter = equityOf(cash, positionValue) - Math.max(fee, 0.0);
      double positionAfter = Math.max(positionValue, 0.0) + Math.max(cost, 0.0);
      return equityAfter >= maintenanceLine(positionAfter);
   }

   public static long safeAdd(long a, long b) {
      long sum = a + b;
      if (((a ^ sum) & (b ^ sum)) < 0L) {
         return b > 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
      } else {
         return sum;
      }
   }

   public static long feeOf(long gross) {
      return (long)Math.floor((double)gross * 0.01);
   }
}
