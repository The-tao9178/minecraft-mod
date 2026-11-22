package cn.blockforge.generated.sharedstockexchange.client;

public final class ChartSteps {
   private ChartSteps() {
   }

   public static int stepColor(float previousValue, float currentValue, int upColor, int downColor, int flatColor) {
      if (currentValue > previousValue) {
         return upColor;
      } else {
         return currentValue < previousValue ? downColor : flatColor;
      }
   }

   public static int projectY(double value, double min, double max, int y, int height) {
      if (height <= 1) {
         return y;
      } else if (max - min < 1.0E-6) {
         return y + height / 2;
      } else {
         double ratio = (value - min) / (max - min);
         int py = y + height - 1 - (int)Math.round(ratio * (double)(height - 1));
         return Math.max(y, Math.min(y + height - 1, py));
      }
   }

   public static int clampY(int py, int top, int height, int lineWidth) {
      return Math.max(top, Math.min(top + height - lineWidth, py));
   }

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

      return min == Float.MAX_VALUE ? new float[]{0.0F, 0.0F} : new float[]{min, max};
   }

   public static void moves(
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
      int thickness,
      ChartSteps.Sink sink
   ) {
      if (values != null && width > 2 && height > 2 && count > 0 && sink != null) {
         int lineWidth = Math.max(1, Math.min(thickness, height));
         int start = Math.max(0, offset);
         int end = Math.min(offset + count, values.length);
         int points = end - start;
         if (points > 0) {
            float[] range = bounds(values, start, end);
            float min = range[0];
            float max = range[1];
            if (points == 1) {
               int py = clampY(projectY((double)values[start], (double)min, (double)max, y, height), y, height, lineWidth);
               sink.fill(x, py, x + width, py + lineWidth, flatColor);
            } else if (points > width) {
               float prevValue = values[start];
               int prevY = clampY(projectY((double)prevValue, (double)min, (double)max, y, height), y, height, lineWidth);

               for (int column = 1; column < width; column++) {
                  int point = (int)Math.round((double)column * (double)(points - 1) / (double)(width - 1));
                  float value = values[start + point];
                  int py = clampY(projectY((double)value, (double)min, (double)max, y, height), y, height, lineWidth);
                  sink.fill(
                     x + column - 1,
                     Math.min(prevY, py),
                     x + column + 1,
                     Math.max(prevY, py) + lineWidth,
                     stepColor(prevValue, value, upColor, downColor, flatColor)
                  );
                  prevValue = value;
                  prevY = py;
               }
            } else {
               int prevX = x;
               float prevValue = values[start];
               int prevY = clampY(projectY((double)prevValue, (double)min, (double)max, y, height), y, height, lineWidth);

               for (int i = 1; i < points; i++) {
                  int px = x + (int)Math.round((double)i * (double)(width - 1) / (double)(points - 1));
                  float value = values[start + i];
                  int py = clampY(projectY((double)value, (double)min, (double)max, y, height), y, height, lineWidth);
                  int color = stepColor(prevValue, value, upColor, downColor, flatColor);
                  int dx = px - prevX;
                  if (dx <= 0) {
                     sink.fill(prevX, Math.min(prevY, py), prevX + 1, Math.max(prevY, py) + lineWidth, color);
                  } else {
                     for (int step = 0; step <= dx; step++) {
                        int columnX = prevX + step;
                        int columnY = prevY + (int)Math.round((double)(py - prevY) * (double)step / (double)dx);
                        sink.fill(columnX, columnY, columnX + 1, columnY + lineWidth, color);
                     }
                  }

                  prevX = px;
                  prevY = py;
                  prevValue = value;
               }
            }
         }
      }
   }

   @FunctionalInterface
   public interface Sink {
      void fill(int var1, int var2, int var3, int var4, int var5);
   }
}
