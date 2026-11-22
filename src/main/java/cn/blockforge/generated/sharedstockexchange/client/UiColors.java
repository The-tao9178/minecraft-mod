package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 自定义涨跌颜色的存档：玩家挑的「上涨色 / 下跌色」存在 config 目录下的小文本文件里。
 *
 * 为什么只存「覆盖值」而不是直接存两个颜色：没自定义时应该跟着主题走
 * （浅色主题的绿和深色主题的绿本来就不一样，这样才看得清）。所以字段用可空的 Integer，
 * null 表示「跟随当前主题的默认色」，玩家点「默认」就是把它清回 null。
 *
 * 这两个颜色会被 {@link UiTheme} 折进配色表里的 up / down 两个角色，
 * 界面上所有涨跌相关的颜色都从角色取，所以改了颜色，小走势图、大涨跌图、涨跌数字、
 * 列表左边的色条一起变；以后新加的东西只要用 up / down，也自动跟上，不用再回来适配。
 */
public final class UiColors {
    private static final String FILE_NAME = "shared_stock_exchange-colors.txt";

    /** null = 跟随主题默认色（上绿下红）。 */
    private static Integer upColor;
    private static Integer downColor;
    private static boolean loaded;

    private UiColors() {
    }

    /** 当前的上涨色：玩家自定义过就用他的，否则用主题默认的（参数传进来）。 */
    public static int up(int fallback) {
        ensureLoaded();
        return upColor == null ? fallback : upColor;
    }

    public static int down(int fallback) {
        ensureLoaded();
        return downColor == null ? fallback : downColor;
    }

    public static boolean customUp() {
        ensureLoaded();
        return upColor != null;
    }

    public static boolean customDown() {
        ensureLoaded();
        return downColor != null;
    }

    /** 玩家在设置面板里点了一个颜色：立刻生效（下一帧就用新颜色画），并写进配置文件。 */
    public static void setUp(int argb) {
        ensureLoaded();
        upColor = ColorUtil.opaque(argb);
        applyChange();
    }

    public static void setDown(int argb) {
        ensureLoaded();
        downColor = ColorUtil.opaque(argb);
        applyChange();
    }

    /** 恢复默认：清掉自定义，重新跟随主题。 */
    public static void resetUp() {
        ensureLoaded();
        upColor = null;
        applyChange();
    }

    public static void resetDown() {
        ensureLoaded();
        downColor = null;
        applyChange();
    }

    private static void applyChange() {
        // 配色表是缓存过的，改完要让它重算一次，否则界面还是旧颜色
        UiTheme.invalidatePalette();
        save();
    }

    /** 第一次用到时读一次文件；之后都是内存里的字段，不碰磁盘。 */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = configPath(FILE_NAME);
            if (path == null || !Files.isRegularFile(path)) {
                return;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim().toLowerCase(Locale.ROOT);
                int parsed = ColorUtil.parseHex(line.substring(split + 1));
                if (parsed == ColorUtil.INVALID) {
                    continue;
                }
                if ("up".equals(key)) {
                    upColor = parsed;
                } else if ("down".equals(key)) {
                    downColor = parsed;
                }
            }
        } catch (Throwable ignored) {
            // 读不出来就用主题默认色，不影响游戏
        }
    }

    /** 写文件：只写自定义过的那两个，没自定义就不写（等于跟随主题）。 */
    private static void save() {
        try {
            Path path = configPath(FILE_NAME);
            if (path == null) {
                return;
            }
            Files.createDirectories(path.getParent());
            StringBuilder text = new StringBuilder();
            text.append("# 涨跌颜色（留空或删掉这个文件就回到默认：上绿下红）\n");
            if (upColor != null) {
                text.append("up=").append(ColorUtil.hex(upColor)).append('\n');
            }
            if (downColor != null) {
                text.append("down=").append(ColorUtil.hex(downColor)).append('\n');
            }
            Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // 写不进去（只读目录之类）就只在这次游戏里生效
        }
    }

    /**
     * 把玩家选的颜色映射到最接近的原版聊天颜色。
     *
     * 聊天框和物品提示框只认原版那 16 种颜色，没法直接吃 ARGB，所以提示框里的「涨跌」文字
     * 用这里挑出来的、最接近自定义色的那一种——换了紫色上涨，提示框里也会跟着偏紫，不会还是绿的。
     */
    public static ChatFormatting chatColor(int argb) {
        ensureLoaded();
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < CHAT_COLORS.length; i++) {
            int distance = ColorUtil.distance(argb, CHAT_COLORS[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return CHAT_FORMATTINGS[best];
    }

    /** 原版 16 种聊天颜色的 RGB，顺序和 {@link #CHAT_FORMATTINGS} 一致。 */
    private static final int[] CHAT_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final ChatFormatting[] CHAT_FORMATTINGS = {
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };

    /** 游戏目录下 config/ 里的一个文件；拿不到游戏目录（比如服务端）就返回 null。 */
    static Path configPath(String fileName) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.gameDirectory == null) {
                return null;
            }
            return minecraft.gameDirectory.toPath().resolve("config").resolve(fileName);
        } catch (Throwable throwable) {
            return null;
        }
    }
}
