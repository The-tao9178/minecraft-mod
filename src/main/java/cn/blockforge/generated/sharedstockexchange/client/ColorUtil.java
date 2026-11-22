package cn.blockforge.generated.sharedstockexchange.client;

/**
 * 颜色小工具：解析 / 输出 #RRGGBB、按比例混合两个颜色、HSV 转 RGB。
 *
 * 这一份不依赖游戏，任何界面都能用，也能单独编译跑测试（不碰 Minecraft 的类）。
 * 自定义涨跌颜色、色板、以及「深色/浅色主题下都看得清的最高/最低价颜色」都从这里算。
 */
public final class ColorUtil {
    /** 解析失败的返回值，和任何合法的 ARGB 都不会撞上。 */
    public static final int INVALID = -1;

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private ColorUtil() {
    }

    /** 把透明通道补成不透明；界面上的颜色都不该是半透明的。 */
    public static int opaque(int argb) {
        return (argb & 0x00FFFFFF) | 0xFF000000;
    }

    /**
     * 解析颜色文字，认这几种写法：#1FD65F、1FD65F、#FF1FD65F（带透明度）。
     * 玩家在输入框里打的东西一律走这里，不合法就返回 {@link #INVALID}，调用方原样保留旧颜色。
     */
    public static int parseHex(String text) {
        if (text == null) {
            return INVALID;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.length() != 6 && cleaned.length() != 8) {
            return INVALID;
        }
        int value = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int digit = Character.digit(cleaned.charAt(i), 16);
            if (digit < 0) {
                return INVALID;
            }
            value = (value << 4) | digit;
        }
        return cleaned.length() == 6 ? opaque(value) : value;
    }

    /** 输出 #RRGGBB（丢掉透明度），给输入框和颜色提示用。 */
    public static String hex(int argb) {
        char[] out = new char[7];
        out[0] = '#';
        for (int i = 0; i < 6; i++) {
            out[1 + i] = HEX[(argb >>> (20 - i * 4)) & 0xF];
        }
        return new String(out);
    }

    /**
     * 把 base 往 over 混 {@code ratio}（0 = 全 base，1 = 全 over）。
     * 「最高价 / 最低价」的文字色就是这样从涨跌色推出来的：浅色主题往深字混、深色主题往白字混，
     * 玩家换了涨跌色之后，这两个颜色自动跟着变得看得清，不用再单独配一遍。
     */
    public static int mix(int base, int over, float ratio) {
        float t = clamp01(ratio);
        int a = channel((base >>> 24) & 0xFF, (over >>> 24) & 0xFF, t);
        int r = channel((base >>> 16) & 0xFF, (over >>> 16) & 0xFF, t);
        int g = channel((base >>> 8) & 0xFF, (over >>> 8) & 0xFF, t);
        int b = channel(base & 0xFF, over & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** HSV 转 RGB。hue 是 0～360 度，sat / val 是 0～1；色板上的每一格都是这样点出来的。 */
    public static int fromHsv(float hue, float sat, float val) {
        float s = clamp01(sat);
        float v = clamp01(val);
        float h = ((hue % 360.0F) + 360.0F) % 360.0F / 60.0F;
        int sector = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return 0xFF000000 | (byte255(r) << 16) | (byte255(g) << 8) | byte255(b);
    }

    /** 两个颜色差多远（RGB 三通道差值的平方和）。用来提醒「上涨和下跌颜色太接近了」。 */
    public static int distance(int first, int second) {
        int dr = ((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF);
        int dg = ((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF);
        int db = (first & 0xFF) - (second & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    private static int byte255(float value) {
        int scaled = Math.round(value * 255.0F);
        return Math.max(0, Math.min(255, scaled));
    }

    private static int channel(int from, int to, float ratio) {
        int value = Math.round((float) from + ((float) to - (float) from) * ratio);
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }
}
