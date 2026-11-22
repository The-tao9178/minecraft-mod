package cn.blockforge.generated.sharedstockexchange.market;

public final class MarketFormat {
   private MarketFormat() {
   }

   public static String emc(double value) {
      if (!Double.isFinite(value)) {
         return "∞";
      } else if (value < 0.0) {
         String magnitude = emc(-value);
         return "0".equals(magnitude) ? magnitude : "-" + magnitude;
      } else if (value >= 1.0E9) {
         return fixed(value / 1.0E9, 2, false) + "B";
      } else if (value >= 1000000.0) {
         return fixed(value / 1000000.0, 2, false) + "M";
      } else {
         return value >= 1000.0 ? fixed(value / 1000.0, 1, false) + "K" : fixed(value, 0, false);
      }
   }

   public static String percent(double ratio) {
      return fixed(ratio * 100.0, 2, true) + "%";
   }

   public static String percentShort(double ratio) {
      double percent = ratio * 100.0;
      return Math.abs(percent) >= 100.0 ? fixed(percent, 0, true) + "%" : fixed(percent, 1, true) + "%";
   }

   public static String share(double ratio) {
      return fixed(Math.max(ratio, 0.0) * 100.0, 1, false) + "%";
   }

   private static String fixed(double value, int decimals, boolean signed) {
      boolean negative = value < 0.0;
      long scale = decimals == 2 ? 100L : (decimals == 1 ? 10L : 1L);
      long scaled = Math.round(Math.abs(value) * (double)scale);
      StringBuilder text = new StringBuilder(16);
      if (negative) {
         text.append('-');
      } else if (signed) {
         text.append('+');
      }

      text.append(scaled / scale);
      if (decimals > 0) {
         text.append('.');
         long fraction = scaled % scale;
         if (decimals == 2 && fraction < 10L) {
            text.append('0');
         }

         text.append(fraction);
      }

      return text.toString();
   }
}
