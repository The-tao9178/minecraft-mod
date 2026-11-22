package cn.blockforge.generated.sharedstockexchange.client;

public final class ColorUtil {
   public static final int INVALID = -1;
   private static final char[] HEX = "0123456789ABCDEF".toCharArray();

   private ColorUtil() {
   }

   public static int opaque(int argb) {
      return argb & 16777215 | 0xFF000000;
   }

   public static int parseHex(String text) {
      if (text == null) {
         return -1;
      } else {
         String cleaned = text.trim();
         if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
         }

         if (cleaned.length() != 6 && cleaned.length() != 8) {
            return -1;
         } else {
            int value = 0;

            for (int i = 0; i < cleaned.length(); i++) {
               int digit = Character.digit(cleaned.charAt(i), 16);
               if (digit < 0) {
                  return -1;
               }

               value = value << 4 | digit;
            }

            return cleaned.length() == 6 ? opaque(value) : value;
         }
      }
   }

   public static String hex(int argb) {
      char[] out = new char[7];
      out[0] = '#';

      for (int i = 0; i < 6; i++) {
         out[1 + i] = HEX[argb >>> 20 - i * 4 & 15];
      }

      return new String(out);
   }

   public static int mix(int base, int over, float ratio) {
      float t = clamp01(ratio);
      int a = channel(base >>> 24 & 0xFF, over >>> 24 & 0xFF, t);
      int r = channel(base >>> 16 & 0xFF, over >>> 16 & 0xFF, t);
      int g = channel(base >>> 8 & 0xFF, over >>> 8 & 0xFF, t);
      int b = channel(base & 0xFF, over & 0xFF, t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static int fromHsv(float hue, float sat, float val) {
      float s = clamp01(sat);
      float v = clamp01(val);
      float h = (hue % 360.0F + 360.0F) % 360.0F / 60.0F;
      int sector = (int)Math.floor((double)h) % 6;
      float f = h - (float)Math.floor((double)h);
      float p = v * (1.0F - s);
      float q = v * (1.0F - s * f);
      float t = v * (1.0F - s * (1.0F - f));
      float r;
      float g;
      float b;
      switch (sector) {
         case 0:
            r = v;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = v;
            b = p;
            break;
         case 2:
            r = p;
            g = v;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = v;
            break;
         case 4:
            r = t;
            g = p;
            b = v;
            break;
         default:
            r = v;
            g = p;
            b = q;
      }

      return 0xFF000000 | byte255(r) << 16 | byte255(g) << 8 | byte255(b);
   }

   public static int distance(int first, int second) {
      int dr = (first >>> 16 & 0xFF) - (second >>> 16 & 0xFF);
      int dg = (first >>> 8 & 0xFF) - (second >>> 8 & 0xFF);
      int db = (first & 0xFF) - (second & 0xFF);
      return dr * dr + dg * dg + db * db;
   }

   private static int byte255(float value) {
      int scaled = Math.round(value * 255.0F);
      return Math.max(0, Math.min(255, scaled));
   }

   private static int channel(int from, int to, float ratio) {
      int value = Math.round((float)from + ((float)to - (float)from) * ratio);
      return Math.max(0, Math.min(255, value));
   }

   private static float clamp01(float value) {
      return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
   }
}
