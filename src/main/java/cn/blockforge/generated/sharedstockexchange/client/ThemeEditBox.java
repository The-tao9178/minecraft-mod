package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 带主题边框的输入框。
 *
 * <p>原版 {@link EditBox} 在 1.20.1 里总会画一圈亮边框：聚焦时是纯白，没聚焦时是浅灰。
 * 放在深色面板上，这一圈就成了一条刺眼的白边——设置界面里「强制平仓」按钮下面那条白边，
 * 其实就是紧贴着它的「波动间隔」输入框的上边框。
 *
 * <p>做法是让原版照常画完文字、光标、选中高亮，再用主题的边框色把那 1 像素的外圈盖掉：
 * 输入习惯、点击区域、光标闪烁全都没变，只是边框颜色跟着浅色 / 深色主题走。
 */
public final class ThemeEditBox extends EditBox {
   public ThemeEditBox(Font font, int x, int y, int width, int height, Component message) {
      super(font, x, y, width, height, message);
   }

   @Override
   public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderWidget(graphics, mouseX, mouseY, partialTick);
      if (!this.isVisible()) {
         return;
      }
      UiTheme.Palette palette = UiTheme.palette();
      // 聚焦时把边框提亮一点，但仍然是主题色，不用原版那种纯白
      int border = this.isFocused() ? ColorUtil.mix(palette.border(), palette.title(), 0.55F) : palette.border();
      int left = this.getX() - 1;
      int top = this.getY() - 1;
      int right = this.getX() + this.getWidth() + 1;
      int bottom = this.getY() + this.getHeight() + 1;
      graphics.fill(left, top, right, this.getY(), border);
      graphics.fill(left, this.getY() + this.getHeight(), right, bottom, border);
      graphics.fill(left, this.getY(), this.getX(), this.getY() + this.getHeight(), border);
      graphics.fill(this.getX() + this.getWidth(), this.getY(), right, this.getY() + this.getHeight(), border);
   }
}
