package cn.blockforge.generated.sharedstockexchange.market;

import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;

public final class MarketCache {
   private static volatile MarketSnapshotPayload snapshot;

   private MarketCache() {
   }

   public static void update(MarketSnapshotPayload payload) {
      snapshot = payload;
   }

   public static MarketSnapshotPayload snapshot() {
      return snapshot;
   }
}
