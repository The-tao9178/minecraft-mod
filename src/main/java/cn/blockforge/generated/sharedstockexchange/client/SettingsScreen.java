package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends UiPanelScreen {
   private static final int WIDTH = 384;
   private static final int HEIGHT = 272;
   private static final int LABEL_X = 10;
   private static final int CONTROL_X = 96;
   private static final int CONTROL_W = 98;
   private static final int RIGHT_X = 204;
   private static final int SWATCH_COLS = 12;
   private static final int SWATCH_ROWS = 6;
   private static final int SWATCH_W = 12;
   private static final int SWATCH_H = 9;
   private static final int UP_GRID_X = 204;
   private static final int UP_GRID_Y = 60;
   private static final int DOWN_GRID_X = 204;
   private static final int DOWN_GRID_Y = 140;
   private static final int[] SWATCHES = buildSwatches();
   private static final float[] PREVIEW = previewSeries();
   private EditBox intervalBox;
   private EditBox walletBox;
   private EditBox upHexBox;
   private EditBox downHexBox;
   private Button debtButton;
   private Button liquidationButton;
   private Button walletApplyButton;
   private Button refundButton;
   private boolean refundArmed;
   private boolean shownDebt;
   private boolean shownLiquidation;
   private boolean shownProjectE;
   private int shownDrift = Integer.MIN_VALUE;
   private long shownWallet = Long.MIN_VALUE;

   public SettingsScreen() {
      super(Component.translatable("gui.shared_stock_exchange.settings"));
   }

   protected void init() {
      this.applyDesignSize(384, 272);
      MarketNetworking.sendToServer(new MarketActionPayload(0, "", 0));
      MarketSnapshotPayload snapshot = MarketCache.snapshot();
      this.shownDebt = snapshot != null && snapshot.debtAllowed();
      this.shownLiquidation = snapshot != null && snapshot.liquidationEnabled();
      this.shownProjectE = snapshot != null && snapshot.projectE();
      this.shownDrift = snapshot != null ? snapshot.driftSeconds() : 30;
      this.shownWallet = snapshot != null ? snapshot.balance() : 0L;
      this.refundArmed = false;
      this.addRenderableWidget(new FitButton(96, 23, 98, 18, themeLabel(), button -> {
         UiTheme.toggle();
         button.setMessage(themeLabel());
         this.disarmRefund();
      }));
      this.debtButton = (Button)this.addRenderableWidget(new FitButton(96, 45, 98, 18, onOff(this.shownDebt), button -> {
         MarketSnapshotPayload current = MarketCache.snapshot();
         if (current != null) {
            this.disarmRefund();
            MarketNetworking.sendToServer(new MarketActionPayload(7, "", current.debtAllowed() ? 0 : 1));
         }
      }));
      this.liquidationButton = (Button)this.addRenderableWidget(new FitButton(96, 67, 98, 18, onOff(this.shownLiquidation), button -> {
         MarketSnapshotPayload current = MarketCache.snapshot();
         if (current != null) {
            this.disarmRefund();
            MarketNetworking.sendToServer(new MarketActionPayload(8, "", current.liquidationEnabled() ? 0 : 1));
         }
      }));
      this.intervalBox = new ThemeEditBox(this.font, 96, 89, 52, 18, Component.translatable("gui.shared_stock_exchange.settings_interval"));
      this.intervalBox.setMaxLength(4);
      this.intervalBox.setFilter(SettingsScreen::digitsOnly);
      this.intervalBox.setValue(Integer.toString(this.shownDrift));
      this.addRenderableWidget(this.intervalBox);
      this.addRenderableWidget(new FitButton(150, 89, 48, 18, Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> {
         this.disarmRefund();
         int seconds = parsePositive(this.intervalBox.getValue(), -1);
         if (seconds > 0) {
            MarketNetworking.sendToServer(new MarketActionPayload(5, "", 0, (long)seconds));
         }
      }));
      this.walletBox = new ThemeEditBox(this.font, 96, 121, 64, 18, Component.translatable("gui.shared_stock_exchange.settings_wallet"));
      this.walletBox.setMaxLength(13);
      this.walletBox.setFilter(SettingsScreen::digitsOnly);
      this.walletBox.setValue(Long.toString(Math.max(0L, this.shownWallet)));
      this.walletBox.setEditable(!this.shownProjectE);
      this.addRenderableWidget(this.walletBox);
      this.walletApplyButton = (Button)this.addRenderableWidget(
         new FitButton(162, 121, 34, 18, Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> {
            this.disarmRefund();
            long emc = parsePositive(this.walletBox.getValue(), -1L);
            if (emc >= 0L) {
               MarketNetworking.sendToServer(new MarketActionPayload(6, "", 0, emc));
            }
         })
      );
      this.walletApplyButton.active = !this.shownProjectE;
      this.refundButton = (Button)this.addRenderableWidget(
         new FitButton(10, 157, 140, 18, Component.translatable("gui.shared_stock_exchange.settings_refund"), button -> this.onRefund())
      );
      this.upHexBox = this.hexBox(246, 39);
      this.upHexBox.setValue(ColorUtil.hex(UiTheme.palette().up()));
      this.addRenderableWidget(this.upHexBox);
      this.addRenderableWidget(
         new FitButton(304, 39, 32, 18, Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> this.applyHex(this.upHexBox, true))
      );
      this.addRenderableWidget(
         new FitButton(338, 39, 38, 18, Component.translatable("gui.shared_stock_exchange.settings_default"), button -> this.resetColor(true))
      );
      this.downHexBox = this.hexBox(246, 119);
      this.downHexBox.setValue(ColorUtil.hex(UiTheme.palette().down()));
      this.addRenderableWidget(this.downHexBox);
      this.addRenderableWidget(
         new FitButton(304, 119, 32, 18, Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> this.applyHex(this.downHexBox, false))
      );
      this.addRenderableWidget(
         new FitButton(338, 119, 38, 18, Component.translatable("gui.shared_stock_exchange.settings_default"), button -> this.resetColor(false))
      );
      this.addRenderableWidget(new FitButton(306, 248, 68, 18, Component.translatable("gui.shared_stock_exchange.settings_close"), button -> this.onClose()));
   }

   private static boolean digitsOnly(String text) {
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c < '0' || c > '9') {
            return false;
         }
      }

      return true;
   }

   private static int parsePositive(String text, int fallback) {
      if (text != null && !text.isEmpty()) {
         try {
            return Integer.parseInt(text);
         } catch (NumberFormatException var3) {
            return fallback;
         }
      } else {
         return 0;
      }
   }

   private static long parsePositive(String text, long fallback) {
      if (text != null && !text.isEmpty()) {
         try {
            return Long.parseLong(text);
         } catch (NumberFormatException var4) {
            return fallback;
         }
      } else {
         return 0L;
      }
   }

   private EditBox hexBox(int x, int y) {
      EditBox box = new ThemeEditBox(this.font, x, y, 56, 18, Component.translatable("gui.shared_stock_exchange.settings_color_hex"));
      box.setMaxLength(7);
      box.setHint(Component.literal("#RRGGBB"));
      box.setFilter(text -> {
         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c == '#' && i == 0;
            if (!ok) {
               return false;
            }
         }

         return true;
      });
      return box;
   }

   private void applyHex(EditBox box, boolean up) {
      int parsed = ColorUtil.parseHex(box.getValue());
      if (parsed != -1) {
         if (up) {
            UiColors.setUp(parsed);
         } else {
            UiColors.setDown(parsed);
         }

         box.setValue(ColorUtil.hex(parsed));
         this.disarmRefund();
      }
   }

   private void resetColor(boolean up) {
      if (up) {
         UiColors.resetUp();
         this.upHexBox.setValue(ColorUtil.hex(UiTheme.palette().up()));
      } else {
         UiColors.resetDown();
         this.downHexBox.setValue(ColorUtil.hex(UiTheme.palette().down()));
      }

      this.disarmRefund();
   }

   private void onRefund() {
      if (!this.refundArmed) {
         this.refundArmed = true;
         this.refundButton.setMessage(Component.translatable("gui.shared_stock_exchange.settings_refund_confirm"));
      } else {
         this.refundArmed = false;
         this.refundButton.setMessage(Component.translatable("gui.shared_stock_exchange.settings_refund"));
         MarketNetworking.sendToServer(new MarketActionPayload(9, "", 0));
      }
   }

   private void disarmRefund() {
      if (this.refundArmed) {
         this.refundArmed = false;
         if (this.refundButton != null) {
            this.refundButton.setMessage(Component.translatable("gui.shared_stock_exchange.settings_refund"));
         }
      }
   }

   private static Component themeLabel() {
      return Component.translatable(UiTheme.isLight() ? "gui.shared_stock_exchange.theme_light_short" : "gui.shared_stock_exchange.theme_dark_short");
   }

   private static Component onOff(boolean enabled) {
      return Component.translatable(enabled ? "gui.shared_stock_exchange.state_on" : "gui.shared_stock_exchange.state_off");
   }

   public void onClose() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.screen == this) {
         minecraft.setScreen(new MarketScreen());
      }
   }

   @Override
   protected boolean onPanelClicked(double panelX, double panelY, int button) {
      if (button != 0) {
         return false;
      } else {
         int upHit = swatchAt(panelX, panelY, 204, 60);
         if (upHit >= 0) {
            UiColors.setUp(SWATCHES[upHit]);
            this.upHexBox.setValue(ColorUtil.hex(SWATCHES[upHit]));
            this.disarmRefund();
            return true;
         } else {
            int downHit = swatchAt(panelX, panelY, 204, 140);
            if (downHit >= 0) {
               UiColors.setDown(SWATCHES[downHit]);
               this.downHexBox.setValue(ColorUtil.hex(SWATCHES[downHit]));
               this.disarmRefund();
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private static int swatchAt(double panelX, double panelY, int gridX, int gridY) {
      int col = (int)Math.floor((panelX - (double)gridX) / 12.0);
      int row = (int)Math.floor((panelY - (double)gridY) / 9.0);
      return col >= 0 && col < 12 && row >= 0 && row < 6 ? row * 12 + col : -1;
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      MarketSnapshotPayload snapshot = MarketCache.snapshot();
      this.syncFromSnapshot(snapshot);
      UiTheme.Palette palette = UiTheme.palette();
      graphics.fill(0, 0, this.width, this.height, palette.shade());
      this.pushPanel(graphics);
      graphics.fill(0, 0, 384, 272, palette.panel());
      graphics.renderOutline(0, 0, 384, 272, palette.border());
      this.renderWidgets(graphics, this.panelMouseX((double)mouseX), this.panelMouseY((double)mouseY), partialTick);
      graphics.drawString(this.font, this.title, 10, 9, palette.title(), false);
      this.drawGameSettings(graphics, palette, snapshot);
      this.drawColors(graphics, palette);
      this.popPanel(graphics);
   }

   private void syncFromSnapshot(MarketSnapshotPayload snapshot) {
      if (snapshot != null) {
         if (snapshot.debtAllowed() != this.shownDebt && this.debtButton != null) {
            this.shownDebt = snapshot.debtAllowed();
            this.debtButton.setMessage(onOff(this.shownDebt));
         }

         if (snapshot.liquidationEnabled() != this.shownLiquidation && this.liquidationButton != null) {
            this.shownLiquidation = snapshot.liquidationEnabled();
            this.liquidationButton.setMessage(onOff(this.shownLiquidation));
         }

         if (snapshot.driftSeconds() != this.shownDrift) {
            this.shownDrift = snapshot.driftSeconds();
            if (this.intervalBox != null && !this.intervalBox.isFocused()) {
               this.intervalBox.setValue(Integer.toString(this.shownDrift));
            }
         }

         if (snapshot.balance() != this.shownWallet) {
            this.shownWallet = snapshot.balance();
            if (this.walletBox != null && !this.walletBox.isFocused() && !snapshot.projectE()) {
               this.walletBox.setValue(Long.toString(Math.max(0L, this.shownWallet)));
            }
         }

         if (snapshot.projectE() != this.shownProjectE) {
            this.shownProjectE = snapshot.projectE();
            if (this.walletBox != null) {
               this.walletBox.setEditable(!this.shownProjectE);
            }

            if (this.walletApplyButton != null) {
               this.walletApplyButton.active = !this.shownProjectE;
            }
         }
      }
   }

   private void drawGameSettings(GuiGraphics graphics, UiTheme.Palette palette, MarketSnapshotPayload snapshot) {
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_theme"), 10, 28, palette.text(), false);
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_debt"), 10, 50, palette.text(), false);
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_liquidation"), 10, 72, palette.text(), false);
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_interval"), 10, 94, palette.text(), false);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_interval_hint", new Object[]{5, 3600}), 10, 109, palette.dim(), 180);
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_wallet"), 10, 126, palette.text(), false);
      if (this.shownProjectE) {
         this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_wallet_emc"), 10, 141, palette.warn(), 180);
      } else {
         this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_wallet_hint"), 10, 141, palette.dim(), 180);
      }

      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_refund_hint"), 10, 178, palette.dim(), 180);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_refund_hint2"), 10, 188, palette.dim(), 180);
      if (snapshot == null) {
         this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_waiting"), 10, 261, palette.warn(), 180);
      }

      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_preview"), 10, 202, palette.dim(), false);
      this.drawPreview(graphics, palette, 10, 213, 172, 43);
   }

   private void drawPreview(GuiGraphics graphics, UiTheme.Palette palette, int x, int y, int width, int height) {
      float first = PREVIEW[0];
      float last = PREVIEW[PREVIEW.length - 1];
      int trend = last >= first ? palette.up() : palette.down();
      graphics.fill(x, y, x + width, y + height, palette.chartBg());
      ChartPainter.area(graphics, x, y, width, height, PREVIEW, 0, PREVIEW.length, ChartPainter.tint(trend, 76));
      ChartPainter.moves(graphics, x, y, width, height, PREVIEW, 0, PREVIEW.length, palette.up(), palette.down(), trend, 2);
      graphics.renderOutline(x, y, width, height, trend);
   }

   private void drawColors(GuiGraphics graphics, UiTheme.Palette palette) {
      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_colors"), 204, 9, palette.title(), false);
      this.drawColorRow(graphics, palette, true, 42, 60);
      this.drawColorRow(graphics, palette, false, 122, 140);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint"), 204, 200, palette.dim(), 172);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint2"), 204, 210, palette.dim(), 172);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint3"), 204, 220, palette.dim(), 172);
      this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_default"), 204, 230, palette.dim(), 172);
      String upText = this.upHexBox == null ? "" : this.upHexBox.getValue();
      String downText = this.downHexBox == null ? "" : this.downHexBox.getValue();
      if (!isBad(upText) && !isBad(downText)) {
         if (ColorUtil.distance(palette.up(), palette.down()) < 900) {
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_same"), 204, 240, palette.warn(), 172);
         }
      } else {
         this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_bad_hex"), 204, 240, palette.down(), 172);
      }
   }

   private void drawColorRow(GuiGraphics graphics, UiTheme.Palette palette, boolean up, int labelY, int gridY) {
      int color = up ? palette.up() : palette.down();
      graphics.drawString(
         this.font,
         Component.translatable(up ? "gui.shared_stock_exchange.settings_up" : "gui.shared_stock_exchange.settings_down"),
         204,
         labelY,
         palette.text(),
         false
      );
      int previewY = labelY - 1;
      graphics.fill(232, previewY, 244, previewY + 12, color);
      graphics.renderOutline(231, previewY - 1, 14, 14, palette.border());
      this.drawSwatches(graphics, palette, gridY, color);
   }

   private void drawSwatches(GuiGraphics graphics, UiTheme.Palette palette, int gridY, int selected) {
      for (int i = 0; i < SWATCHES.length; i++) {
         int col = i % 12;
         int row = i / 12;
         int x = 204 + col * 12;
         int y = gridY + row * 9;
         graphics.fill(x, y, x + 12 - 1, y + 9 - 1, SWATCHES[i]);
         if (SWATCHES[i] == selected) {
            graphics.renderOutline(x - 1, y - 1, 13, 10, palette.title());
         }
      }
   }

   private static boolean isBad(String text) {
      return text != null && !text.isEmpty() && ColorUtil.parseHex(text) == -1;
   }

   private static int[] buildSwatches() {
      float[][] shades = new float[][]{{0.55F, 1.0F}, {0.85F, 1.0F}, {1.0F, 0.95F}, {1.0F, 0.72F}, {0.85F, 0.52F}, {0.55F, 0.34F}};
      int[] colors = new int[72];

      for (int row = 0; row < 6; row++) {
         for (int col = 0; col < 12; col++) {
            float hue = (float)col * 360.0F / 12.0F;
            colors[row * 12 + col] = ColorUtil.fromHsv(hue, shades[row][0], shades[row][1]);
         }
      }

      return colors;
   }

   private static float[] previewSeries() {
      float[] values = new float[26];

      for (int i = 0; i < values.length; i++) {
         values[i] = (float)(100.0 + 9.0 * Math.sin((double)i * 0.55) + 4.0 * Math.cos((double)i * 0.23));
      }

      return values;
   }
}
