package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.network.chat.Component;

public final class FitButton extends Button {
   public FitButton(int x, int y, int width, int height, Component message, OnPress onPress) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
   }

   public void renderString(GuiGraphics graphics, Font font, int color) {
      Component message = this.getMessage();
      int textWidth = font.width(message);
      if (textWidth > 0) {
         int maxWidth = Math.max(1, this.getWidth() - 4);
         float scale = textWidth > maxWidth ? (float)maxWidth / (float)textWidth : 1.0F;
         float centerX = (float)this.getX() + (float)this.getWidth() * 0.5F;
         float centerY = (float)this.getY() + ((float)this.getHeight() - 9.0F) * 0.5F + 1.0F;
         graphics.pose().pushPose();
         graphics.pose().translate(centerX, centerY, 0.0F);
         graphics.pose().scale(scale, scale, 1.0F);
         graphics.drawString(font, message, -textWidth / 2, 0, color, false);
         graphics.pose().popPose();
      }
   }
}
