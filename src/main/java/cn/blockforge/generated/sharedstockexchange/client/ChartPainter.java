package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.GuiGraphics;

public final class ChartPainter {
   private ChartPainter() {
   }

   public static void moves(
      GuiGraphics graphics,
      int x,
      int y,
      int width,
      int height,
      float[] values,
      int offset,
      int count,
      int upColor,
      int downColor,
      int flatColor,
      int thickness
   ) {
      ChartSteps.moves(x, y, width, height, values, offset, count, upColor, downColor, flatColor, thickness, graphics::fill);
   }

   public static void area(GuiGraphics graphics, int x, int y, int width, int height, float[] values, int offset, int count, int color) {
      if (values != null && width > 0 && height > 0 && count > 0 && color >>> 24 != 0) {
         int start = Math.max(0, offset);
         int end = Math.min(offset + count, values.length);
         int points = end - start;
         if (points > 0) {
            float[] bounds = ChartSteps.bounds(values, start, end);
            float min = bounds[0];
            float max = bounds[1];
            int bottom = y + height;
            int runStart = 0;
            int runY = ChartSteps.projectY((double)values[start], (double)min, (double)max, y, height);

            for (int column = 1; column <= width; column++) {
               int py;
               if (column == width) {
                  py = Integer.MIN_VALUE;
               } else {
                  int point = points == 1 ? 0 : (int)Math.round((double)column * (double)(points - 1) / (double)Math.max(1, width - 1));
                  py = ChartSteps.projectY((double)values[start + point], (double)min, (double)max, y, height);
               }

               if (py != runY) {
                  if (runY < bottom) {
                     graphics.fill(x + runStart, runY, x + column, bottom, color);
                  }

                  runStart = column;
                  runY = py;
               }
            }
         }
      }
   }

   public static void grid(GuiGraphics graphics, int x, int y, int width, int height, int lines, int color) {
      if (height > 2 && lines > 1) {
         for (int i = 0; i < lines; i++) {
            int py = y + (int)Math.round((double)i * (double)(height - 1) / (double)(lines - 1));
            graphics.fill(x, py, x + width, py + 1, color);
         }
      }
   }

   public static void dashedLine(GuiGraphics graphics, int x, int y, int width, int color) {
      if (width > 0) {
         for (int i = 0; i < width; i += 5) {
            int end = Math.min(i + 3, width);
            graphics.fill(x + i, y, x + end, y + 1, color);
         }
      }
   }

   public static void dot(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
      if (radius >= 0) {
         for (int dy = -radius; dy <= radius; dy++) {
            int half = (int)Math.floor(Math.sqrt((double)(radius * radius - dy * dy)));
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, color);
         }
      }
   }

   public static int projectY(float value, float min, float max, int y, int height) {
      return ChartSteps.projectY((double)value, (double)min, (double)max, y, height);
   }

   public static int tint(int argb, int alpha) {
      int clamped = Math.max(0, Math.min(255, alpha));
      return argb & 16777215 | clamped << 24;
   }

   public static float[] range(float[] values, int offset, int count) {
      if (values == null) {
         return new float[]{0.0F, 0.0F};
      } else {
         int start = Math.max(0, offset);
         int end = Math.min(offset + count, values.length);
         return start >= end ? new float[]{0.0F, 0.0F} : ChartSteps.bounds(values, start, end);
      }
   }
}
