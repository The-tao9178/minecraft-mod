package cn.blockforge.generated.sharedstockexchange.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public final class UiColors {
   private static final String FILE_NAME = "shared_stock_exchange-colors.txt";
   private static Integer upColor;
   private static Integer downColor;
   private static boolean loaded;
   private static final int[] CHAT_COLORS = new int[]{
      0, 170, 43520, 43690, 11141120, 11141290, 16755200, 11184810, 5592405, 5592575, 5635925, 5636095, 16733525, 16733695, 16777045, 16777215
   };
   private static final ChatFormatting[] CHAT_FORMATTINGS = new ChatFormatting[]{
      ChatFormatting.BLACK,
      ChatFormatting.DARK_BLUE,
      ChatFormatting.DARK_GREEN,
      ChatFormatting.DARK_AQUA,
      ChatFormatting.DARK_RED,
      ChatFormatting.DARK_PURPLE,
      ChatFormatting.GOLD,
      ChatFormatting.GRAY,
      ChatFormatting.DARK_GRAY,
      ChatFormatting.BLUE,
      ChatFormatting.GREEN,
      ChatFormatting.AQUA,
      ChatFormatting.RED,
      ChatFormatting.LIGHT_PURPLE,
      ChatFormatting.YELLOW,
      ChatFormatting.WHITE
   };

   private UiColors() {
   }

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
      UiTheme.invalidatePalette();
      save();
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;

         try {
            Path path = configPath("shared_stock_exchange-colors.txt");
            if (path == null || !Files.isRegularFile(path)) {
               return;
            }

            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
               int split = line.indexOf(61);
               if (split > 0) {
                  String key = line.substring(0, split).trim().toLowerCase(Locale.ROOT);
                  int parsed = ColorUtil.parseHex(line.substring(split + 1));
                  if (parsed != -1) {
                     if ("up".equals(key)) {
                        upColor = parsed;
                     } else if ("down".equals(key)) {
                        downColor = parsed;
                     }
                  }
               }
            }
         } catch (Throwable var7) {
         }
      }
   }

   private static void save() {
      try {
         Path path = configPath("shared_stock_exchange-colors.txt");
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
      } catch (Throwable var2) {
      }
   }

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

   static Path configPath(String fileName) {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         return minecraft != null && minecraft.gameDirectory != null ? minecraft.gameDirectory.toPath().resolve("config").resolve(fileName) : null;
      } catch (Throwable var2) {
         return null;
      }
   }
}
