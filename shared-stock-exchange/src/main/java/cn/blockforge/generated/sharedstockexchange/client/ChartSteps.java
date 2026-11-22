package cn.blockforge.generated.sharedstockexchange.client;

/**
 * 走势折线的纯几何计算：算「每一步该画哪几块像素、用哪个颜色」。
 *
 * 特意不依赖 Minecraft 的绘制类，好处是能脱离游戏直接跑测试（真的把 120 个价格点
 * 铺到 372 像素、56 像素宽上，逐块核对颜色和边界），改了画法也能马上发现画歪。
 * {@link ChartPainter} 只负责把这里算出来的方块交给 {@code GuiGraphics.fill}。
 */
public final class ChartSteps {
    /** 一次填充要用到的参数收集器。 */
    @FunctionalInterface
    public interface Sink {
        void fill(int x0, int y0, int x1, int y1, int color);
    }

    private ChartSteps() {
    }

    /**
     * 这一步的颜色。直接比两次的价格（不是比像素）：价格涨了就是绿，跌了就是红。
     * 比像素更准：价格只动一点点时，投影到 96 像素高的图上可能落在同一行，
     * 那时也要按"真的涨了"上色，不然趋势里会冒出一堆本不该有的"持平"段。
     */
    public static int stepColor(float previousValue, float currentValue,
                                int upColor, int downColor, int flatColor) {
        if (currentValue > previousValue) {
            return upColor;
        }
        if (currentValue < previousValue) {
            return downColor;
        }
        return flatColor;
    }

    /** 把某个价格换算成框里的 y 坐标。 */
    public static int projectY(double value, double min, double max, int y, int height) {
        if (height <= 1) {
            return y;
        }
        if (max - min < 1.0E-6D) {
            return y + height / 2;
        }
        double ratio = (value - min) / (max - min);
        int py = y + height - 1 - (int) Math.round(ratio * (double) (height - 1));
        return Math.max(y, Math.min(y + height - 1, py));
    }

    /** 粗线不能画到框外面去：把 y 收进 [top, top + height - lineWidth]。 */
    public static int clampY(int py, int top, int height, int lineWidth) {
        return Math.max(top, Math.min(top + height - lineWidth, py));
    }

    /** 走势里的最高价、最低价，返回 {最低, 最高}。 */
    public static float[] bounds(float[] values, int start, int end) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = start; i < end; i++) {
            float value = values[i];
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        if (min == Float.MAX_VALUE) {
            return new float[]{0.0F, 0.0F};
        }
        return new float[]{min, max};
    }

    /**
     * 逐段上色的折线：这一步比上一步高画 up 色、低画 down 色、持平画 flat 色。
     *
     * 点数比宽度多（列表里的小走势图）时按像素列取样，一列一段；
     * 点数比宽度少（详情页大图）时每个点之间连一段。两种情况下每个像素列最多填一次。
     */
    public static void moves(int x, int y, int width, int height, float[] values, int offset, int count,
                             int upColor, int downColor, int flatColor, int thickness, Sink sink) {
        if (values == null || width <= 2 || height <= 2 || count <= 0 || sink == null) {
            return;
        }
        int lineWidth = Math.max(1, Math.min(thickness, height));
        int start = Math.max(0, offset);
        int end = Math.min(offset + count, values.length);
        int points = end - start;
        if (points <= 0) {
            return;
        }
        float[] range = bounds(values, start, end);
        float min = range[0];
        float max = range[1];
        if (points == 1) {
            int py = clampY(projectY(values[start], min, max, y, height), y, height, lineWidth);
            sink.fill(x, py, x + width, py + lineWidth, flatColor);
            return;
        }
        if (points > width) {
            float prevValue = values[start];
            int prevY = clampY(projectY(prevValue, min, max, y, height), y, height, lineWidth);
            for (int column = 1; column < width; column++) {
                int point = (int) Math.round((double) column * (double) (points - 1) / (double) (width - 1));
                float value = values[start + point];
                int py = clampY(projectY(value, min, max, y, height), y, height, lineWidth);
                sink.fill(x + column - 1, Math.min(prevY, py), x + column + 1,
                        Math.max(prevY, py) + lineWidth,
                        stepColor(prevValue, value, upColor, downColor, flatColor));
                prevValue = value;
                prevY = py;
            }
            return;
        }
        int prevX = x;
        float prevValue = values[start];
        int prevY = clampY(projectY(prevValue, min, max, y, height), y, height, lineWidth);
        for (int i = 1; i < points; i++) {
            int px = x + (int) Math.round((double) i * (double) (width - 1) / (double) (points - 1));
            float value = values[start + i];
            int py = clampY(projectY(value, min, max, y, height), y, height, lineWidth);
            int color = stepColor(prevValue, value, upColor, downColor, flatColor);
            int dx = px - prevX;
            if (dx <= 0) {
                sink.fill(prevX, Math.min(prevY, py), prevX + 1, Math.max(prevY, py) + lineWidth, color);
            } else {
                for (int step = 0; step <= dx; step++) {
                    int columnX = prevX + step;
                    int columnY = prevY + (int) Math.round((double) (py - prevY) * (double) step / (double) dx);
                    sink.fill(columnX, columnY, columnX + 1, columnY + lineWidth, color);
                }
            }
            prevX = px;
            prevY = py;
            prevValue = value;
        }
    }
}
