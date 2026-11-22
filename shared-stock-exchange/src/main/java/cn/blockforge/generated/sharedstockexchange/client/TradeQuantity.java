package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;

/**
 * 界面上「一次买/卖多少股」这个数字。
 *
 * 放在一个静态变量里，交易界面和涨跌详情界面共用同一个数量：
 * 在列表里填好 137 股，点进详情页还是 137 股，不用重填。
 */
public final class TradeQuantity {
    /** 服务器对单笔的上限，和 MarketManager.MAX_SHARES_PER_TRADE 保持一致。 */
    public static final int MAX = 100_000;
    private static int value = 10;

    private TradeQuantity() {
    }

    public static int get() {
        return value;
    }

    public static void set(int amount) {
        value = Math.max(1, Math.min(amount, MAX));
    }

    /** 把输入框里的文字变成合法股数，乱填就回落到 1。 */
    public static int parse(String text) {
        if (text == null) {
            return 1;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 1;
        }
        try {
            long raw = Long.parseLong(trimmed);
            if (raw <= 0L) {
                return 1;
            }
            return (int) Math.min(raw, MAX);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    /** 这个数量不会超过「这个价位最多能买多少股」；买不到就返回 0。 */
    public static long buyableIn(int index, MarketSnapshotPayload snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        long max = snapshot.maxBuyable(index);
        if (max <= 0L) {
            return 0L;
        }
        return Math.min(max, get());
    }
}
