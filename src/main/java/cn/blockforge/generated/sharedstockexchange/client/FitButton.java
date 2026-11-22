package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 窄按钮上的字会自动缩小，保证「1000」这种四位数也完整显示。
 *
 * 原版按钮遇到放不下的字会走「滚动文字」那条路，里面用了一块裁剪区域；
 * 我们整个界面是缩放后画的，裁剪区域不会跟着缩放，结果字就被裁没了（之前「1000」按钮空白就是这个原因）。
 * 这里干脆自己画字：放得下就居中画，放不下就先把字缩小再居中画，不用裁剪。
 */
public final class FitButton extends Button {
    public FitButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        Component message = this.getMessage();
        int textWidth = font.width(message);
        if (textWidth <= 0) {
            return;
        }
        int maxWidth = Math.max(1, this.getWidth() - 4);
        float scale = textWidth > maxWidth ? (float) maxWidth / (float) textWidth : 1.0F;
        float centerX = (float) this.getX() + (float) this.getWidth() * 0.5F;
        float centerY = (float) this.getY() + ((float) this.getHeight() - (float) font.lineHeight) * 0.5F + 1.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, message, -textWidth / 2, 0, color, false);
        graphics.pose().popPose();
    }
}
