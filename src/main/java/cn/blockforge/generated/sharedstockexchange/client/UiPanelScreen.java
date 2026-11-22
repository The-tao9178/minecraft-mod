package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 会自己缩放的界面底座。
 *
 * 做法：界面按一个固定的「设计尺寸」摆坐标（比如 400×302），画的时候整块等比缩放到刚好装得下，
 * 然后居中。鼠标坐标在交给控件之前先换算回设计尺寸，所以点哪儿都对得上。
 *
 * 这样一来：窗口拉小、或者玩家把游戏界面尺寸调大，界面都不会被切掉一半；
 * 大窗口时缩放比封顶在 100%，字还是清晰的，不会糊成一团。
 */
public abstract class UiPanelScreen extends Screen {
    /** 缩放下限：屏幕小到离谱时最多缩到这里，再小就真的看不清了。 */
    private static final float MIN_SCALE = 0.35F;

    private int designWidth = 1;
    private int designHeight = 1;
    private float uiScale = 1.0F;
    private int offsetX;
    private int offsetY;

    protected UiPanelScreen(Component title) {
        super(title);
    }

    /** 这块设计尺寸塞进这个屏幕时，最多能放大到几倍（封顶 100%）。 */
    protected static float fitScale(int screenWidth, int screenHeight, int designWidth, int designHeight) {
        if (designWidth <= 0 || designHeight <= 0) {
            return MIN_SCALE;
        }
        float fitX = ((float) screenWidth - 4.0F) / (float) designWidth;
        float fitY = ((float) screenHeight - 4.0F) / (float) designHeight;
        return Math.max(MIN_SCALE, Math.min(1.0F, Math.min(fitX, fitY)));
    }

    /**
     * 在 init() 里先调这个，定下这次要用的设计尺寸，
     * 顺手算好缩放比和居中偏移；之后的控件坐标都按设计尺寸写。
     */
    protected final void applyDesignSize(int designWidth, int designHeight) {
        this.designWidth = Math.max(1, designWidth);
        this.designHeight = Math.max(1, designHeight);
        this.uiScale = fitScale(this.width, this.height, this.designWidth, this.designHeight);
        this.offsetX = Math.round(((float) this.width - (float) this.designWidth * this.uiScale) * 0.5F);
        this.offsetY = Math.round(((float) this.height - (float) this.designHeight * this.uiScale) * 0.5F);
    }

    protected final float uiScale() {
        return this.uiScale;
    }

    /** 屏幕坐标 → 设计坐标（横）。 */
    protected final double toPanelX(double screenX) {
        return (screenX - (double) this.offsetX) / (double) this.uiScale;
    }

    /** 屏幕坐标 → 设计坐标（纵）。 */
    protected final double toPanelY(double screenY) {
        return (screenY - (double) this.offsetY) / (double) this.uiScale;
    }

    protected final int panelMouseX(double screenX) {
        return (int) Math.floor(toPanelX(screenX));
    }

    protected final int panelMouseY(double screenY) {
        return (int) Math.floor(toPanelY(screenY));
    }

    /** 进入设计坐标系：之后的绘制都按设计尺寸算，由这里统一缩放、居中。 */
    protected final void pushPanel(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate((float) this.offsetX, (float) this.offsetY, 0.0F);
        graphics.pose().scale(this.uiScale, this.uiScale, 1.0F);
    }

    protected final void popPanel(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    /** 把按钮、输入框这些控件画上（必须在 pushPanel 之后调用，坐标才跟着一起缩）。 */
    protected final void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 画一行字，太长就按宽度截断，保证塞得进设计尺寸里。 */
    protected final void drawClipped(GuiGraphics graphics, Component text, int x, int y, int color, int maxWidth) {
        this.drawClipped(graphics, text.getString(), x, y, color, maxWidth);
    }

    /** 同上，但收的已经是拼好的字符串：界面里那些固定标题不用每帧再翻译一次。 */
    protected final void drawClipped(GuiGraphics graphics, String text, int x, int y, int color, int maxWidth) {
        graphics.drawString(this.font, this.font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景自己画，不用原版的菜单背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 鼠标点在面板空白处（不是按钮上）时调用，坐标已经是设计尺寸。 */
    protected boolean onPanelClicked(double panelX, double panelY, int button) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double panelX = toPanelX(mouseX);
        double panelY = toPanelY(mouseY);
        return super.mouseClicked(panelX, panelY, button) || this.onPanelClicked(panelX, panelY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(toPanelX(mouseX), toPanelY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(toPanelX(mouseX), toPanelY(mouseY), button,
                dragX / (double) this.uiScale, dragY / (double) this.uiScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(toPanelX(mouseX), toPanelY(mouseY), scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toPanelX(mouseX), toPanelY(mouseY));
    }
}
