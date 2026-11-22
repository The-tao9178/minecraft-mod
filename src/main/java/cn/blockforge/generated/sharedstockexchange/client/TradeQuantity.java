package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;

public final class TradeQuantity {
   public static final int MAX = 100000;
   private static int value = 10;

   private TradeQuantity() {
   }

   public static int get() {
      return value;
   }

   public static void set(int amount) {
      value = Math.max(1, Math.min(amount, 100000));
   }

   public static int parse(String text) {
      if (text == null) {
         return 1;
      } else {
         String trimmed = text.trim();
         if (trimmed.isEmpty()) {
            return 1;
         } else {
            try {
               long raw = Long.parseLong(trimmed);
               return raw <= 0L ? 1 : (int)Math.min(raw, 100000L);
            } catch (NumberFormatException var4) {
               return 1;
            }
         }
      }
   }

   public static long buyableIn(int index, MarketSnapshotPayload snapshot) {
      if (snapshot == null) {
         return 0L;
      } else {
         long max = snapshot.maxBuyable(index);
         return max <= 0L ? 0L : Math.min(max, (long)get());
      }
   }
}
