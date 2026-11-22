package cn.blockforge.generated.sharedstockexchange.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 界面配色主题：浅色（默认）和深色，所有自绘界面都从这里取色。
 *
 * 为什么单独放一个类：以前每个界面自己写死颜色（0xFF121A20 这种），想换肤色就得把
 * 两个界面的每一次 fill / drawString 全改一遍，以后新加的按钮、面板、图表还得再补一次。
 * 现在颜色只有 {@link Palette} 里这一份，界面只按「角色」取色（背景、正文、涨、跌、警示……），
 * 新功能只要从这些角色里挑颜色，两个主题就自动都跟上了，不用为每个新控件再改一次。
 *
 * 主题和自定义的涨跌颜色都在「设置」面板里改：浅色 / 深色存在 config/ 下的小文件里，
 * 下次进游戏还是上次那个配色；上涨色 / 下跌色也在那里选，套进配色表的 up / down 两个角色。
 */
public final class UiTheme {
    public enum Mode { LIGHT, DARK }

    /**
     * 一套配色。加新界面时从这里挑角色，不要写死颜色值：
     *   panel     面板底色        border    面板外框
     *   text      正文            dim       次要文字
     *   title     标题            up/down   涨 / 跌
     *   warn      警告（欠债）    gold      强调（钱、均价线）
     *   high/low  图上的最高/最低 grid      图表网格
     *   chartBg   图表底          rowNormal/rowHover  列表行底色
     *   lineSoft  虚线            stroke    高对比线条
     *   dot       最新价亮点      chip      小标签底色
     */
    public record Palette(
            int shade, int panel, int border, int title, int text, int dim,
            int up, int down, int warn, int gold, int mutedGold, int high, int low,
            int grid, int chartBg, int rowNormal, int rowHover, int lineSoft, int stroke,
            int arrowIdle, int arrowBand, int dot, int chip, int chipText) {

        /**
         * 换掉涨跌两个颜色，顺手把「最高价 / 最低价」推出来。
         *
         * 玩家自定义的只有上涨和下跌两个颜色（设置面板里选），最高/最低价是它们的浅一点的版本：
         * 往当前主题的正文色混一点——浅色主题往深字混所以变深、深色主题往白字混所以变亮，
         * 两个主题下都看得清，玩家换什么颜色都不用再单独配一遍。
         */
        public Palette withUpDown(int upColor, int downColor) {
            return new Palette(shade, panel, border, title, text, dim,
                    upColor, downColor, warn, gold, mutedGold,
                    ColorUtil.mix(upColor, text, 0.38F), ColorUtil.mix(downColor, text, 0.38F),
                    grid, chartBg, rowNormal, rowHover, lineSoft, stroke,
                    arrowIdle, arrowBand, dot, chip, chipText);
        }
    }

    /** 浅色：以前的界面是深色，现在默认换成浅色；白底深字，图表底也换成浅灰。 */
    private static final Palette LIGHT = new Palette(
            0x8C0D1418, // shade
            0xFFF6F8FA, // panel
            0xFF2E8B72, // border
            0xFF16242B, // title
            0xFF23313A, // text
            0xFF5D707B, // dim
            0xFF12854A, // up
            0xFFC92C2C, // down
            0xFFA96200, // warn
            0xFF8A5B00, // gold
            0xFF8A6A14, // mutedGold
            0xFF0B7A62, // high
            0xFFB3432F, // low
            0x1F000000, // grid
            0xFFEAF0F3, // chartBg
            0x0D000000, // rowNormal
            0x24000000, // rowHover
            0x59000000, // lineSoft
            0xFF16242B, // stroke
            0x4D000000, // arrowIdle
            0x1F000000, // arrowBand
            0xFF16242B, // dot
            0x14000000, // chip
            0xFF23313A  // chipText
    );

    /** 深色：一直以来的那一套，切换回去就是原来的样子。 */
    private static final Palette DARK = new Palette(
            0xA6000000,
            0xF0121A20,
            0xFF4FD1A5,
            0xFFFFFFFF,
            0xFFE8F1F5,
            0xFF8FA3AE,
            0xFF1FD65F,
            0xFFFF4A4A,
            0xFFFFB84D,
            0xFFFFD770,
            0xFFD6C27A,
            0xFF8FE3C2,
            0xFFFF9A90,
            0x22FFFFFF,
            0xFF0B1115,
            0x331E2A33,
            0x55243038,
            0x66FFFFFF,
            0xFFFFFFFF,
            0x55FFFFFF,
            0x33FFFFFF,
            0xFFFFFFFF,
            0x33FFFFFF,
            0xFFE8F1F5
    );

    /** 选择存在游戏目录的 config 下，一个纯文本小文件，坏了大不了用默认值。 */
    private static final String CONFIG_FILE = "shared_stock_exchange-ui.txt";

    private static Mode mode = Mode.LIGHT;
    private static boolean loaded;
    /** 套上自定义涨跌色之后的成品配色；null 表示要重算。 */
    private static Palette derived;

    private UiTheme() {
    }

    /** 当前配色。每次 render 开头取一次存成局部变量，就只多一次判断，不会再读文件。 */
    public static Palette palette() {
        ensureLoaded();
        Palette cached = derived;
        if (cached != null) {
            return cached;
        }
        Palette base = mode == Mode.DARK ? DARK : LIGHT;
        // 基础配色之上套一层玩家自定义的涨跌色（没自定义就是主题默认的上绿下红）
        Palette built = base.withUpDown(UiColors.up(base.up()), UiColors.down(base.down()));
        derived = built;
        return built;
    }

    /** 改了涨跌色 / 换了主题时把缓存作废，下一帧按新配色重算。 */
    public static void invalidatePalette() {
        derived = null;
    }

    public static Mode mode() {
        ensureLoaded();
        return mode;
    }

    public static boolean isLight() {
        return mode() == Mode.LIGHT;
    }

    /** 在浅色 / 深色之间切换（设置面板里的「主题」按钮就是它）。 */
    public static void toggle() {
        apply(mode() == Mode.LIGHT ? Mode.DARK : Mode.LIGHT);
    }

    public static void apply(Mode next) {
        mode = next == null ? Mode.LIGHT : next;
        loaded = true;
        derived = null;
        save(mode);
    }

    /** 第一次用到时读一次文件；之后都是内存里的字段，不碰磁盘。 */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = configPath();
            if (path != null && Files.isRegularFile(path)) {
                String text = Files.readString(path, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if ("dark".equals(text)) {
                    mode = Mode.DARK;
                } else if ("light".equals(text)) {
                    mode = Mode.LIGHT;
                }
            }
        } catch (Throwable ignored) {
            // 读不出来就用默认的浅色，不影响游戏
        }
    }

    private static void save(Mode which) {
        try {
            Path path = configPath();
            if (path == null) {
                return;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, which.name().toLowerCase(Locale.ROOT) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // 写不进去（只读目录之类）就只在这次游戏里生效
        }
    }

    private static Path configPath() {
        // 主题和涨跌颜色都放在 config/ 里，路径算法只留一份（在 UiColors）
        return UiColors.configPath(CONFIG_FILE);
    }
}
