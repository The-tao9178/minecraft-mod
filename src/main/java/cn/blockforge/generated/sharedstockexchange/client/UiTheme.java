package cn.blockforge.generated.sharedstockexchange.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class UiTheme {
   private static final UiTheme.Palette LIGHT = new UiTheme.Palette(
      -1945299944,
      -591622,
      -13726862,
      -15326165,
      -14470854,
      -10653573,
      -15563446,
      -3593172,
      -5676544,
      -7709952,
      -7706092,
      -16024990,
      -5029073,
      520093696,
      -1380109,
      218103808,
      603979776,
      1493172224,
      -15326165,
      1291845632,
      520093696,
      -15326165,
      335544320,
      -14470854
   );
   private static final UiTheme.Palette DARK = new UiTheme.Palette(
      -1509949440,
      -267249120,
      -11546203,
      -1,
      -1510923,
      -7363666,
      -14690721,
      -46518,
      -18355,
      -10384,
      -2702726,
      -7347262,
      -25968,
      587202559,
      -16051947,
      857614899,
      1428435000,
      1728053247,
      -1,
      1442840575,
      872415231,
      -1,
      872415231,
      -1510923
   );
   private static final String CONFIG_FILE = "shared_stock_exchange-ui.txt";
   private static UiTheme.Mode mode = UiTheme.Mode.LIGHT;
   private static boolean loaded;
   private static UiTheme.Palette derived;

   private UiTheme() {
   }

   public static UiTheme.Palette palette() {
      ensureLoaded();
      UiTheme.Palette cached = derived;
      if (cached != null) {
         return cached;
      } else {
         UiTheme.Palette base = mode == UiTheme.Mode.DARK ? DARK : LIGHT;
         UiTheme.Palette built = base.withUpDown(UiColors.up(base.up()), UiColors.down(base.down()));
         derived = built;
         return built;
      }
   }

   public static void invalidatePalette() {
      derived = null;
   }

   public static UiTheme.Mode mode() {
      ensureLoaded();
      return mode;
   }

   public static boolean isLight() {
      return mode() == UiTheme.Mode.LIGHT;
   }

   public static void toggle() {
      apply(mode() == UiTheme.Mode.LIGHT ? UiTheme.Mode.DARK : UiTheme.Mode.LIGHT);
   }

   public static void apply(UiTheme.Mode next) {
      mode = next == null ? UiTheme.Mode.LIGHT : next;
      loaded = true;
      derived = null;
      save(mode);
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;

         try {
            Path path = configPath();
            if (path != null && Files.isRegularFile(path)) {
               String text = Files.readString(path, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
               if ("dark".equals(text)) {
                  mode = UiTheme.Mode.DARK;
               } else if ("light".equals(text)) {
                  mode = UiTheme.Mode.LIGHT;
               }
            }
         } catch (Throwable var2) {
         }
      }
   }

   private static void save(UiTheme.Mode which) {
      try {
         Path path = configPath();
         if (path == null) {
            return;
         }

         Files.createDirectories(path.getParent());
         Files.writeString(path, which.name().toLowerCase(Locale.ROOT) + "\n", StandardCharsets.UTF_8);
      } catch (Throwable var2) {
      }
   }

   private static Path configPath() {
      return UiColors.configPath("shared_stock_exchange-ui.txt");
   }

   public static enum Mode {
      LIGHT,
      DARK;
   }

   public static record Palette(
      int shade,
      int panel,
      int border,
      int title,
      int text,
      int dim,
      int up,
      int down,
      int warn,
      int gold,
      int mutedGold,
      int high,
      int low,
      int grid,
      int chartBg,
      int rowNormal,
      int rowHover,
      int lineSoft,
      int stroke,
      int arrowIdle,
      int arrowBand,
      int dot,
      int chip,
      int chipText
   ) {
      public UiTheme.Palette withUpDown(int upColor, int downColor) {
         return new UiTheme.Palette(
            this.shade,
            this.panel,
            this.border,
            this.title,
            this.text,
            this.dim,
            upColor,
            downColor,
            this.warn,
            this.gold,
            this.mutedGold,
            ColorUtil.mix(upColor, this.text, 0.38F),
            ColorUtil.mix(downColor, this.text, 0.38F),
            this.grid,
            this.chartBg,
            this.rowNormal,
            this.rowHover,
            this.lineSoft,
            this.stroke,
            this.arrowIdle,
            this.arrowBand,
            this.dot,
            this.chip,
            this.chipText
         );
      }
   }
}
