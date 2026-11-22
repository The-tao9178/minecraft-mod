package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketData;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 设置面板：交易界面标题旁边的「设置」点开就是它。
 *
 * 这里把以前只能靠 /stock 命令改的东西都搬到了界面上：
 *   · 界面主题（浅色 / 深色，客户端自己的设置）
 *   · 负债系统开关、强制平仓开关（服务器设置，管理员或单人存档房主才能改）
 *   · 行情波动间隔、测试钱包（同上）
 *   · 未成年退款（谁都能点，所以要点两次确认，防手滑）
 *   · 上涨色 / 下跌色（客户端设置，默认上绿下红）
 *
 * 颜色只存在 {@link UiColors} 里，绘制的界面都按「涨 / 跌」这个角色取色，
 * 所以这里换一次颜色，小走势图、大涨跌图、涨跌数字、列表左边的色条一起变，
 * 以后新加的内容只要用这两个角色，也自动跟上，不用回来适配。
 *
 * 服务器设置走网络包改：客户端只是把按钮点亮，权限由服务器再查一遍，
 * 和命令的规矩完全一致（否则改个客户端就能随便改别人的服）。
 */
public class SettingsScreen extends UiPanelScreen {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 272;

    // 左列：一行一个设置，左边名字、右边控件
    private static final int LABEL_X = 10;
    private static final int CONTROL_X = 96;
    private static final int CONTROL_W = 98;

    // 右列：两个颜色各一排「色块预览 + 十六进制输入 + 应用 + 默认」加一块色板
    private static final int RIGHT_X = 204;
    private static final int SWATCH_COLS = 12;
    private static final int SWATCH_ROWS = 6;
    private static final int SWATCH_W = 12;
    private static final int SWATCH_H = 9;
    private static final int UP_GRID_X = RIGHT_X;
    private static final int UP_GRID_Y = 60;
    private static final int DOWN_GRID_X = RIGHT_X;
    private static final int DOWN_GRID_Y = 140;

    /** 色板：12 个色相 × 6 档深浅，点一格就换色。排一次序存起来，画的时候不用再算。 */
    private static final int[] SWATCHES = buildSwatches();

    /** 预览图用的一小段示例走势（只算一次）。 */
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

    // 界面上正显示的服务器状态，用来判断「要不要刷新按钮/输入框上的字」
    private boolean shownDebt;
    private boolean shownLiquidation;
    private boolean shownProjectE;
    private int shownDrift = Integer.MIN_VALUE;
    private long shownWallet = Long.MIN_VALUE;

    public SettingsScreen() {
        super(Component.translatable("gui.shared_stock_exchange.settings"));
    }

    @Override
    protected void init() {
        this.applyDesignSize(WIDTH, HEIGHT);
        // 进来先要一份最新行情：开关状态、波动间隔、钱包余额都在快照里
        PacketDistributor.sendToServer(new MarketActionPayload(MarketActionPayload.ACTION_SYNC, "", 0));

        MarketSnapshotPayload snapshot = MarketCache.snapshot();
        this.shownDebt = snapshot != null && snapshot.debtAllowed();
        this.shownLiquidation = snapshot != null && snapshot.liquidationEnabled();
        this.shownProjectE = snapshot != null && snapshot.projectE();
        this.shownDrift = snapshot != null ? snapshot.driftSeconds() : MarketData.DEFAULT_DRIFT_SECONDS;
        this.shownWallet = snapshot != null ? snapshot.balance() : 0L;
        this.refundArmed = false;

        this.addRenderableWidget(new FitButton(CONTROL_X, 23, CONTROL_W, 18,
                themeLabel(), button -> {
                    UiTheme.toggle();
                    button.setMessage(themeLabel());
                    this.disarmRefund();
                }));

        this.debtButton = this.addRenderableWidget(new FitButton(CONTROL_X, 45, CONTROL_W, 18,
                onOff(this.shownDebt), button -> {
                    MarketSnapshotPayload current = MarketCache.snapshot();
                    if (current == null) {
                        return;
                    }
                    this.disarmRefund();
                    PacketDistributor.sendToServer(new MarketActionPayload(
                            MarketActionPayload.ACTION_SET_DEBT, "", current.debtAllowed() ? 0 : 1));
                }));

        this.liquidationButton = this.addRenderableWidget(new FitButton(CONTROL_X, 67, CONTROL_W, 18,
                onOff(this.shownLiquidation), button -> {
                    MarketSnapshotPayload current = MarketCache.snapshot();
                    if (current == null) {
                        return;
                    }
                    this.disarmRefund();
                    PacketDistributor.sendToServer(new MarketActionPayload(
                            MarketActionPayload.ACTION_SET_LIQUIDATION, "", current.liquidationEnabled() ? 0 : 1));
                }));

        this.intervalBox = new EditBox(this.font, CONTROL_X, 89, 52, 18,
                Component.translatable("gui.shared_stock_exchange.settings_interval"));
        this.intervalBox.setMaxLength(4);
        this.intervalBox.setFilter(SettingsScreen::digitsOnly);
        this.intervalBox.setValue(Integer.toString(this.shownDrift));
        this.addRenderableWidget(this.intervalBox);
        this.addRenderableWidget(new FitButton(150, 89, 48, 18,
                Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> {
                    this.disarmRefund();
                    int seconds = parsePositive(this.intervalBox.getValue(), -1);
                    if (seconds > 0) {
                        PacketDistributor.sendToServer(new MarketActionPayload(
                                MarketActionPayload.ACTION_SET_INTERVAL, "", 0, seconds));
                    }
                }));

        this.walletBox = new EditBox(this.font, CONTROL_X, 121, 64, 18,
                Component.translatable("gui.shared_stock_exchange.settings_wallet"));
        this.walletBox.setMaxLength(13);
        this.walletBox.setFilter(SettingsScreen::digitsOnly);
        this.walletBox.setValue(Long.toString(Math.max(0L, this.shownWallet)));
        this.walletBox.setEditable(!this.shownProjectE);
        this.addRenderableWidget(this.walletBox);
        this.walletApplyButton = this.addRenderableWidget(new FitButton(162, 121, 34, 18,
                Component.translatable("gui.shared_stock_exchange.settings_apply"), button -> {
                    this.disarmRefund();
                    long emc = parsePositive(this.walletBox.getValue(), -1L);
                    if (emc >= 0L) {
                        PacketDistributor.sendToServer(new MarketActionPayload(
                                MarketActionPayload.ACTION_SET_WALLET, "", 0, emc));
                    }
                }));
        this.walletApplyButton.active = !this.shownProjectE;

        this.refundButton = this.addRenderableWidget(new FitButton(10, 157, 140, 18,
                Component.translatable("gui.shared_stock_exchange.settings_refund"), button -> this.onRefund()));

        this.upHexBox = this.hexBox(246, 39);
        this.upHexBox.setValue(ColorUtil.hex(UiTheme.palette().up()));
        this.addRenderableWidget(this.upHexBox);
        this.addRenderableWidget(new FitButton(304, 39, 32, 18,
                Component.translatable("gui.shared_stock_exchange.settings_apply"),
                button -> this.applyHex(this.upHexBox, true)));
        this.addRenderableWidget(new FitButton(338, 39, 38, 18,
                Component.translatable("gui.shared_stock_exchange.settings_default"),
                button -> this.resetColor(true)));

        this.downHexBox = this.hexBox(246, 119);
        this.downHexBox.setValue(ColorUtil.hex(UiTheme.palette().down()));
        this.addRenderableWidget(this.downHexBox);
        this.addRenderableWidget(new FitButton(304, 119, 32, 18,
                Component.translatable("gui.shared_stock_exchange.settings_apply"),
                button -> this.applyHex(this.downHexBox, false)));
        this.addRenderableWidget(new FitButton(338, 119, 38, 18,
                Component.translatable("gui.shared_stock_exchange.settings_default"),
                button -> this.resetColor(false)));

        this.addRenderableWidget(new FitButton(306, 248, 68, 18,
                Component.translatable("gui.shared_stock_exchange.settings_close"), button -> this.onClose()));
    }

    /** 只收数字：逐字符检查，避免每次按键都编译一遍正则。 */
    private static boolean digitsOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** 空串当 0；非法（超长等）返回 fallback。 */
    private static int parsePositive(String text, int fallback) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parsePositive(String text, long fallback) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private EditBox hexBox(int x, int y) {
        EditBox box = new EditBox(this.font, x, y, 56, 18,
                Component.translatable("gui.shared_stock_exchange.settings_color_hex"));
        box.setMaxLength(7);
        box.setHint(Component.literal("#RRGGBB"));
        box.setFilter(text -> {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                        || (c == '#' && i == 0);
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
        if (parsed == ColorUtil.INVALID) {
            // 格式不对就什么都不做，界面下方会用红字提示该填什么
            return;
        }
        if (up) {
            UiColors.setUp(parsed);
        } else {
            UiColors.setDown(parsed);
        }
        // 统一成 #RRGGBB 的写法，玩家打的 1fd65f 也会被整理好
        box.setValue(ColorUtil.hex(parsed));
        this.disarmRefund();
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

    /** 未成年退款要点两次：第一次只是把按钮改成「再点一次确认」，免得手滑把持仓清空。 */
    private void onRefund() {
        if (!this.refundArmed) {
            this.refundArmed = true;
            this.refundButton.setMessage(
                    Component.translatable("gui.shared_stock_exchange.settings_refund_confirm"));
            return;
        }
        this.refundArmed = false;
        this.refundButton.setMessage(Component.translatable("gui.shared_stock_exchange.settings_refund"));
        PacketDistributor.sendToServer(new MarketActionPayload(MarketActionPayload.ACTION_REFUND, "", 0));
    }

    /** 点别的按钮就把「确认退款」这一步撤掉。 */
    private void disarmRefund() {
        if (!this.refundArmed) {
            return;
        }
        this.refundArmed = false;
        if (this.refundButton != null) {
            this.refundButton.setMessage(Component.translatable("gui.shared_stock_exchange.settings_refund"));
        }
    }

    private static Component themeLabel() {
        return Component.translatable(UiTheme.isLight()
                ? "gui.shared_stock_exchange.theme_light_short"
                : "gui.shared_stock_exchange.theme_dark_short");
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.shared_stock_exchange.state_on"
                : "gui.shared_stock_exchange.state_off");
    }

    /** 关掉设置回到交易界面（从哪个界面点进来的都回交易界面，少一次点）。 */
    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(new MarketScreen());
        }
    }

    /** 鼠标点色板：换色，顺手把输入框里的十六进制同步过去。 */
    @Override
    protected boolean onPanelClicked(double panelX, double panelY, int button) {
        if (button != 0) {
            return false;
        }
        int upHit = swatchAt(panelX, panelY, UP_GRID_X, UP_GRID_Y);
        if (upHit >= 0) {
            UiColors.setUp(SWATCHES[upHit]);
            this.upHexBox.setValue(ColorUtil.hex(SWATCHES[upHit]));
            this.disarmRefund();
            return true;
        }
        int downHit = swatchAt(panelX, panelY, DOWN_GRID_X, DOWN_GRID_Y);
        if (downHit >= 0) {
            UiColors.setDown(SWATCHES[downHit]);
            this.downHexBox.setValue(ColorUtil.hex(SWATCHES[downHit]));
            this.disarmRefund();
            return true;
        }
        return false;
    }

    private static int swatchAt(double panelX, double panelY, int gridX, int gridY) {
        int col = (int) Math.floor((panelX - gridX) / SWATCH_W);
        int row = (int) Math.floor((panelY - gridY) / SWATCH_H);
        if (col < 0 || col >= SWATCH_COLS || row < 0 || row >= SWATCH_ROWS) {
            return -1;
        }
        return row * SWATCH_COLS + col;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MarketSnapshotPayload snapshot = MarketCache.snapshot();
        this.syncFromSnapshot(snapshot);
        UiTheme.Palette palette = UiTheme.palette();

        graphics.fill(0, 0, this.width, this.height, palette.shade());
        this.pushPanel(graphics);
        graphics.fill(0, 0, WIDTH, HEIGHT, palette.panel());
        graphics.renderOutline(0, 0, WIDTH, HEIGHT, palette.border());
        this.renderWidgets(graphics, this.panelMouseX(mouseX), this.panelMouseY(mouseY), partialTick);

        graphics.drawString(this.font, this.title, LABEL_X, 9, palette.title(), false);

        this.drawGameSettings(graphics, palette, snapshot);
        this.drawColors(graphics, palette);

        this.popPanel(graphics);
    }

    /** 服务器状态变了就刷新按钮上的字和输入框里的数（输入框正在打字时不抢）。 */
    private void syncFromSnapshot(MarketSnapshotPayload snapshot) {
        if (snapshot == null) {
            return;
        }
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

    private void drawGameSettings(GuiGraphics graphics, UiTheme.Palette palette, MarketSnapshotPayload snapshot) {
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_theme"),
                LABEL_X, 28, palette.text(), false);
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_debt"),
                LABEL_X, 50, palette.text(), false);
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_liquidation"),
                LABEL_X, 72, palette.text(), false);
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_interval"),
                LABEL_X, 94, palette.text(), false);
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_interval_hint",
                        MarketData.MIN_DRIFT_SECONDS, MarketData.MAX_DRIFT_SECONDS),
                LABEL_X, 109, palette.dim(), 180);

        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_wallet"),
                LABEL_X, 126, palette.text(), false);
        if (this.shownProjectE) {
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_wallet_emc"),
                    LABEL_X, 141, palette.warn(), 180);
        } else {
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_wallet_hint"),
                    LABEL_X, 141, palette.dim(), 180);
        }

        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_refund_hint"),
                LABEL_X, 178, palette.dim(), 180);
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_refund_hint2"),
                LABEL_X, 188, palette.dim(), 180);

        // 权限说明：只在拿不到快照以外的情况也写清楚，免得改了没反应以为坏了
        if (snapshot == null) {
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_waiting"),
                    LABEL_X, 261, palette.warn(), 180);
        }

        // 左下角一块小预览：换颜色时能立刻看到线和面积变成什么样
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_preview"),
                LABEL_X, 202, palette.dim(), false);
        this.drawPreview(graphics, palette, LABEL_X, 213, 172, 43);
    }

    private void drawPreview(GuiGraphics graphics, UiTheme.Palette palette, int x, int y, int width, int height) {
        float first = PREVIEW[0];
        float last = PREVIEW[PREVIEW.length - 1];
        int trend = last >= first ? palette.up() : palette.down();
        graphics.fill(x, y, x + width, y + height, palette.chartBg());
        ChartPainter.area(graphics, x, y, width, height, PREVIEW, 0, PREVIEW.length,
                ChartPainter.tint(trend, 0x4C));
        // 每一小段按涨跌单独上色，和详情页的大图是同一套画法
        ChartPainter.moves(graphics, x, y, width, height, PREVIEW, 0, PREVIEW.length,
                palette.up(), palette.down(), trend, 2);
        graphics.renderOutline(x, y, width, height, trend);
    }

    private void drawColors(GuiGraphics graphics, UiTheme.Palette palette) {
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.settings_colors"),
                RIGHT_X, 9, palette.title(), false);

        this.drawColorRow(graphics, palette, true, 42, UP_GRID_Y);
        this.drawColorRow(graphics, palette, false, 122, DOWN_GRID_Y);

        // 提示：改的是「颜色本身」，不是换贴图；以后新加的东西也自动跟着这两个角色走
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint"),
                RIGHT_X, 200, palette.dim(), 172);
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint2"),
                RIGHT_X, 210, palette.dim(), 172);
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_hint3"),
                RIGHT_X, 220, palette.dim(), 172);
        this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_default"),
                RIGHT_X, 230, palette.dim(), 172);

        String upText = this.upHexBox == null ? "" : this.upHexBox.getValue();
        String downText = this.downHexBox == null ? "" : this.downHexBox.getValue();
        if (isBad(upText) || isBad(downText)) {
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_bad_hex"),
                    RIGHT_X, 240, palette.down(), 172);
        } else if (ColorUtil.distance(palette.up(), palette.down()) < 900) {
            // 两个颜色太像，图上分不出涨跌：提醒一下，但不强行拦着（玩家可能就是想要同色）
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.settings_color_same"),
                    RIGHT_X, 240, palette.warn(), 172);
        }
    }

    private void drawColorRow(GuiGraphics graphics, UiTheme.Palette palette, boolean up, int labelY, int gridY) {
        int color = up ? palette.up() : palette.down();
        graphics.drawString(this.font, Component.translatable(up
                        ? "gui.shared_stock_exchange.settings_up"
                        : "gui.shared_stock_exchange.settings_down"),
                RIGHT_X, labelY, palette.text(), false);
        // 现在用的颜色：一个小色块，边上一圈框，深色浅色主题都看得见
        int previewY = labelY - 1;
        graphics.fill(RIGHT_X + 28, previewY, RIGHT_X + 40, previewY + 12, color);
        graphics.renderOutline(RIGHT_X + 27, previewY - 1, 14, 14, palette.border());
        this.drawSwatches(graphics, palette, gridY, color);
    }

    private void drawSwatches(GuiGraphics graphics, UiTheme.Palette palette, int gridY, int selected) {
        for (int i = 0; i < SWATCHES.length; i++) {
            int col = i % SWATCH_COLS;
            int row = i / SWATCH_COLS;
            int x = RIGHT_X + col * SWATCH_W;
            int y = gridY + row * SWATCH_H;
            graphics.fill(x, y, x + SWATCH_W - 1, y + SWATCH_H - 1, SWATCHES[i]);
            if (SWATCHES[i] == selected) {
                // 选中的那一格描个框（框比格子大一圈，看得出是「当前颜色」）
                graphics.renderOutline(x - 1, y - 1, SWATCH_W + 1, SWATCH_H + 1, palette.title());
            }
        }
    }

    private static boolean isBad(String text) {
        return text != null && !text.isEmpty() && ColorUtil.parseHex(text) == ColorUtil.INVALID;
    }

    private static int[] buildSwatches() {
        // 6 档「饱和度 / 明度」，从上到下越来越暗：亮色、正色、深色都挑得到
        float[][] shades = {
                {0.55F, 1.00F},
                {0.85F, 1.00F},
                {1.00F, 0.95F},
                {1.00F, 0.72F},
                {0.85F, 0.52F},
                {0.55F, 0.34F}
        };
        int[] colors = new int[SWATCH_COLS * SWATCH_ROWS];
        for (int row = 0; row < SWATCH_ROWS; row++) {
            for (int col = 0; col < SWATCH_COLS; col++) {
                float hue = (float) col * 360.0F / (float) SWATCH_COLS;
                colors[row * SWATCH_COLS + col] = ColorUtil.fromHsv(hue, shades[row][0], shades[row][1]);
            }
        }
        return colors;
    }

    private static float[] previewSeries() {
        float[] values = new float[26];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) (100.0D + 9.0D * Math.sin((double) i * 0.55D)
                    + 4.0D * Math.cos((double) i * 0.23D));
        }
        return values;
    }
}
