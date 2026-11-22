package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 走势图的画笔：把一串价格画成折线（每一小段按涨跌单独上色），下面铺一层淡色面积，
 * 最新价打个亮点。交易界面里每行的小走势图（sparkline）和详情页的大涨跌图都用它，画法一致。
 *
 * 坐标换算和「每一步用什么颜色」这种几何计算放在 {@link ChartSteps} 里（那个类不依赖游戏，
 * 能单独跑测试）；这里只负责把算出来的方块交给 {@code GuiGraphics}。
 */
public final class ChartPainter {
    private ChartPainter() {
    }

    /**
     * 逐段上色的折线：这一步比上一步高就画绿，低就画红，持平画中性色。
     *
     * 以前整条线一个颜色，只有"整段时间涨还是跌"这一种信息；现在每一小段都单独上色，
     * 图上每一次上下都能一眼看出方向。整段时间的趋势仍然由面积、边框和右上角徽标表达。
     * 填充次数和原来的单色折线一样（一段最多几列，一列一次 fill），不会因为多颜色变卡。
     */
    public static void moves(GuiGraphics graphics, int x, int y, int width, int height,
                             float[] values, int offset, int count, int upColor, int downColor,
                             int flatColor, int thickness) {
        ChartSteps.moves(x, y, width, height, values, offset, count, upColor, downColor, flatColor,
                thickness, graphics::fill);
    }

    /**
     * 折线下面的淡色面积：从折线到框底整片铺一层，走势方向更一眼看得出来。
     * 颜色自带透明度，传 {@link #tint(int, int)} 算出来的值就行。
     */
    public static void area(GuiGraphics graphics, int x, int y, int width, int height,
                            float[] values, int offset, int count, int color) {
        if (values == null || width <= 0 || height <= 0 || count <= 0 || (color >>> 24) == 0) {
            return;
        }
        int start = Math.max(0, offset);
        int end = Math.min(offset + count, values.length);
        int points = end - start;
        if (points <= 0) {
            return;
        }
        float[] bounds = ChartSteps.bounds(values, start, end);
        float min = bounds[0];
        float max = bounds[1];
        int bottom = y + height;
        // 连续几列高度一样就并成一次填充：大图 120 个点摊到 370 多像素宽，
        // 每列画一次要三百多次填充，合并后一百次就够。
        int runStart = 0;
        int runY = ChartSteps.projectY(values[start], min, max, y, height);
        for (int column = 1; column <= width; column++) {
            int py;
            if (column == width) {
                py = Integer.MIN_VALUE; // 最后一列：把还没收尾的一段强制画掉
            } else {
                int point = points == 1
                        ? 0
                        : (int) Math.round((double) column * (double) (points - 1) / (double) Math.max(1, width - 1));
                py = ChartSteps.projectY(values[start + point], min, max, y, height);
            }
            if (py == runY) {
                continue;
            }
            if (runY < bottom) {
                graphics.fill(x + runStart, runY, x + column, bottom, color);
            }
            runStart = column;
            runY = py;
        }
    }

    /** 画几条横向参考线，让涨跌图更好读。 */
    public static void grid(GuiGraphics graphics, int x, int y, int width, int height, int lines, int color) {
        if (height <= 2 || lines <= 1) {
            return;
        }
        for (int i = 0; i < lines; i++) {
            int py = y + (int) Math.round((double) i * (double) (height - 1) / (double) (lines - 1));
            graphics.fill(x, py, x + width, py + 1, color);
        }
    }

    /** 一条虚线，用来标「区间起点」这种参考价。 */
    public static void dashedLine(GuiGraphics graphics, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        for (int i = 0; i < width; i += 5) {
            int end = Math.min(i + 3, width);
            graphics.fill(x + i, y, x + end, y + 1, color);
        }
    }

    /** 在最新价那里点一个圆点，视线一眼落到「现在多少钱」。一行画一次，不用一个像素一次。 */
    public static void dot(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius < 0) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt((double) (radius * radius - dy * dy)));
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
        }
    }

    /** 把某个价格换算成框里的 y 坐标，和 {@link #moves} 用的是同一套算法。 */
    public static int projectY(float value, float min, float max, int y, int height) {
        return ChartSteps.projectY(value, min, max, y, height);
    }

    /** 给一个颜色换透明度，做淡色填充时用。alpha 是 0～255。 */
    public static int tint(int argb, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (argb & 0x00FFFFFF) | (clamped << 24);
    }

    /** 走势里的最高价、最低价，返回 {最低, 最高}。 */
    public static float[] range(float[] values, int offset, int count) {
        if (values == null) {
            return new float[]{0.0F, 0.0F};
        }
        int start = Math.max(0, offset);
        int end = Math.min(offset + count, values.length);
        if (start >= end) {
            return new float[]{0.0F, 0.0F};
        }
        return ChartSteps.bounds(values, start, end);
    }
}
