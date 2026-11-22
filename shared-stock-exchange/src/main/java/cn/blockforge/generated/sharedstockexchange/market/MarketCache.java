package cn.blockforge.generated.sharedstockexchange.market;

import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;

/**
 * 客户端手里最近一次收到的行情快照。所有屏幕都从这里读数据，
 * 数据本身来自服务器，所以联机时每个人看到的盘面完全一样。
 */
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
