package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class UiPanelScreen extends Screen {
   private static final float MIN_SCALE = 0.35F;
   private int designWidth = 1;
   private int designHeight = 1;
   private float uiScale = 1.0F;
   private int offsetX;
   private int offsetY;

   protected UiPanelScreen(Component title) {
      super(title);
   }

   protected static float fitScale(int screenWidth, int screenHeight, int designWidth, int designHeight) {
      if (designWidth > 0 && designHeight > 0) {
         float fitX = ((float)screenWidth - 4.0F) / (float)designWidth;
         float fitY = ((float)screenHeight - 4.0F) / (float)designHeight;
         return Math.max(0.35F, Math.min(1.0F, Math.min(fitX, fitY)));
      } else {
         return 0.35F;
      }
   }

   protected final void applyDesignSize(int designWidth, int designHeight) {
      this.designWidth = Math.max(1, designWidth);
      this.designHeight = Math.max(1, designHeight);
      this.uiScale = fitScale(this.width, this.height, this.designWidth, this.designHeight);
      this.offsetX = Math.round(((float)this.width - (float)this.designWidth * this.uiScale) * 0.5F);
      this.offsetY = Math.round(((float)this.height - (float)this.designHeight * this.uiScale) * 0.5F);
   }

   protected final float uiScale() {
      return this.uiScale;
   }

   protected final double toPanelX(double screenX) {
      return (screenX - (double)this.offsetX) / (double)this.uiScale;
   }

   protected final double toPanelY(double screenY) {
      return (screenY - (double)this.offsetY) / (double)this.uiScale;
   }

   protected final int panelMouseX(double screenX) {
      return (int)Math.floor(this.toPanelX(screenX));
   }

   protected final int panelMouseY(double screenY) {
      return (int)Math.floor(this.toPanelY(screenY));
   }

   protected final void pushPanel(GuiGraphics graphics) {
      graphics.pose().pushPose();
      graphics.pose().translate((float)this.offsetX, (float)this.offsetY, 0.0F);
      graphics.pose().scale(this.uiScale, this.uiScale, 1.0F);
   }

   protected final void popPanel(GuiGraphics graphics) {
      graphics.pose().popPose();
   }

   protected final void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   protected final void drawClipped(GuiGraphics graphics, Component text, int x, int y, int color, int maxWidth) {
      this.drawClipped(graphics, text.getString(), x, y, color, maxWidth);
   }

   protected final void drawClipped(GuiGraphics graphics, String text, int x, int y, int color, int maxWidth) {
      graphics.drawString(this.font, this.font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected boolean onPanelClicked(double panelX, double panelY, int button) {
      return false;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double panelX = this.toPanelX(mouseX);
      double panelY = this.toPanelY(mouseY);
      return super.mouseClicked(panelX, panelY, button) || this.onPanelClicked(panelX, panelY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return super.mouseReleased(this.toPanelX(mouseX), this.toPanelY(mouseY), button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      return super.mouseDragged(this.toPanelX(mouseX), this.toPanelY(mouseY), button, dragX / (double)this.uiScale, dragY / (double)this.uiScale);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      return super.mouseScrolled(this.toPanelX(mouseX), this.toPanelY(mouseY), amount);
   }

   public void mouseMoved(double mouseX, double mouseY) {
      super.mouseMoved(this.toPanelX(mouseX), this.toPanelY(mouseY));
   }
}
