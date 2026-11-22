package cn.blockforge.generated.sharedstockexchange.market;

/**
 * 数字格式化：EMC 动辄上百万，界面上用 K / M / B 缩写更清楚。
 *
 * 这里刻意不用 {@code String.format}：界面每帧要格式几十次数字，
 * 而 String.format 每次都要按语言环境编译一遍格式串，是这类自绘界面最常见的卡顿来源。
 * 改成整数四舍五入 + 手工拼串，输出和原来一样（如 "1.25M"、"+3.2%"、"-523"）。
 */
public final class MarketFormat {
    private MarketFormat() {
    }

    /**
     * EMC 金额。会变成负数：透支买入时玩家面板里的 EMC 就是负数（欠债），
     * 这里按绝对值选 K / M / B 单位，前面补个减号，不然后面会显示成一大串数字。
     */
    public static String emc(double value) {
        if (!Double.isFinite(value)) {
            return "∞";
        }
        if (value < 0.0D) {
            String magnitude = emc(-value);
            // 四舍五入到 0 的小负数别显示成 "-0"
            return "0".equals(magnitude) ? magnitude : "-" + magnitude;
        }
        if (value >= 1_000_000_000.0D) {
            return fixed(value / 1_000_000_000.0D, 2, false) + "B";
        }
        if (value >= 1_000_000.0D) {
            return fixed(value / 1_000_000.0D, 2, false) + "M";
        }
        if (value >= 1_000.0D) {
            return fixed(value / 1_000.0D, 1, false) + "K";
        }
        return fixed(value, 0, false);
    }

    public static String percent(double ratio) {
        return fixed(ratio * 100.0D, 2, true) + "%";
    }

    /**
     * 列表里用的短涨跌幅：涨得多时不带小数（+1710%），涨得少时留一位（+2.4%）。
     * 这一列很窄，两位数小数会被挤到隔壁「市值」列里，所以表格用短的、详情页用精确的。
     */
    public static String percentShort(double ratio) {
        double percent = ratio * 100.0D;
        if (Math.abs(percent) >= 100.0D) {
            return fixed(percent, 0, true) + "%";
        }
        return fixed(percent, 1, true) + "%";
    }

    /** 持股占比：不带正负号，像「12.3%」。 */
    public static String share(double ratio) {
        return fixed(Math.max(ratio, 0.0D) * 100.0D, 1, false) + "%";
    }

    /**
     * 四舍五入到 decimals 位小数后拼成字符串。
     * signed 为 true 时正数也带 + 号（涨跌幅习惯写法）。
     */
    private static String fixed(double value, int decimals, boolean signed) {
        boolean negative = value < 0.0D;
        long scale = decimals == 2 ? 100L : decimals == 1 ? 10L : 1L;
        long scaled = Math.round(Math.abs(value) * (double) scale);
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
