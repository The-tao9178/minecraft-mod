package cn.blockforge.generated.sharedstockexchange.client.mcphone;

import cn.blockforge.generated.sharedstockexchange.client.ChartPainter;
import cn.blockforge.generated.sharedstockexchange.client.StockIcons;
import cn.blockforge.generated.sharedstockexchange.client.UiTheme;
import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import cn.blockforge.generated.sharedstockexchange.market.Stocks;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import com.november.mcphone.api.client.ui.IPhonePage;
import com.november.mcphone.api.client.ui.PhoneCanvas;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 手机里的炒股页 —— 画在 MCphone 的内容区里，和它自带的 App 共用状态栏、导航栏、壁纸。
 *
 * <p>这一页自己管两个视图，不新开 Screen：
 * <ul>
 *   <li><b>列表</b>：所有股票的价格、涨跌，点一行进详情；</li>
 *   <li><b>详情</b>：这只股票的走势图、持仓盈亏、数量选择、买入 / 卖出。</li>
 * </ul>
 * 导航栏的返回键 ◁ 在详情视图里退回列表，在列表视图里交还给手机（退到主屏）。
 *
 * <p>数据全部来自 {@link MarketCache}（服务器推来的快照），买卖也直接发原本的网络包，
 * 所以手机里看到的盘面、成交价、手续费与交易终端里完全一致，不是另做一套。
 *
 * <p>性能上有四处刻意的处理：<br>
 * 1. 价格、涨跌、股票名这些文字只在快照或语言变过之后重算一次，不每帧拼字符串；<br>
 * 2. 名字按当前手机宽度裁一次、价格量一次宽度，渲染循环里只画不算，别每帧对 8 行重复量字宽；<br>
 * 3. 列表只画视口里的那几行，整行在屏幕外就跳过——裁剪只是保证画不出去，不替你省绘制；<br>
 * 4. 行情同步按 tick 节流，40 tick（两秒）才向服务器要一次，不是每帧都发。
 */
public final class StockAppPage implements IPhonePage {
    /** 列表一行多高。物品图标是 16×16，行高跟它对齐 */
    private static final int ROW_H = 16;
    /** 顶部余额条多高 */
    private static final int HEADER_H = 12;
    /** 详情页顶部返回条多高 */
    private static final int BACK_H = 11;
    /** 多久向服务器要一次行情（客户端 tick）。40 tick = 2 秒 */
    private static final int SYNC_TICKS = 40;
    /** 走势图最高画多高，大屏上别拉成一片 */
    private static final int CHART_MAX_H = 48;

    /** 快捷数量按钮上的固定数字。第四个「全部」要跟着语言走，单独拼 */
    private static final String[] QUICK_LABELS = {"1", "10", "100"};

    /** 当前打开的股票下标，-1 表示在列表视图 */
    private int selected = -1;
    private int scroll;
    private int quantity = 1;
    private int syncTimer;
    /** 上一次向服务器要行情的时间（毫秒）。渲染路径和 tick 路径都会到期，靠它保证一秒内只发一次 */
    private long lastSyncMs;

    // ---- 文字缓存：只有快照对象换过（或语言变了）才重算 ----
    private MarketSnapshotPayload formattedFor;
    private String[] priceText = new String[0];
    private String[] changeText = new String[0];
    /** 股票名也跟着语言走，缓存起来省得每帧查 8 次翻译表 */
    private String[] names = new String[0];
    private String cashText = "";
    private String heldText = "";
    private String pnlText = "";
    /** 缓存这些字符串时游戏用的是哪种语言 */
    private String cachedLanguage = "";

    // ---- 行内文字的尺子缓存：名字裁到多宽、价格占多少像素，只有快照 / 语言 / 手机宽度变了才重量 ----
    /** 列表每行实际画出来的名字（已经按可用宽度裁好） */
    private String[] displayNames = new String[0];
    private int[] priceWidth = new int[0];
    private int[] changeWidth = new int[0];
    /** 这一批名字是按多宽的手机裁的；换宽度就要重裁 */
    private int measuredWidth = -1;
    private int cashWidth;
    private int pnlWidth;

    // ---- 固定在界面上的几个词（「大屏」「全部」「买入」……），语言变了才重取 ----
    private String labelBig = "";
    private String labelTap = "";
    private String labelAll = "";
    private String labelBuy = "";
    private String labelSell = "";
    private String labelBack = "";
    private String labelDebt = "";
    private String labelWaiting = "";

    /** 详情页那两行持仓文字是按「哪只股票 + 哪份快照」变的，记住算过谁 */
    private MarketSnapshotPayload detailFor;
    private int detailIndex = -1;
    private String detailLanguage = "";
    /** 详情页标题 / 持仓行按多宽裁的，以及盈亏那串数字的宽度 */
    private int detailWidth = -1;
    private String detailTitle = "";
    private String detailHeld = "";

    /** 两个固定小按钮（「大屏」「点一行看详情」）的宽度只跟文字走，量一次就够 */
    private Font measuredFont;
    private int labelBigWidth = 20;
    private int labelTapWidth;

    // ---- 这一帧各个可点区域的矩形（绝对屏幕坐标），点击时按它判定 ----
    private final int[] rowY = new int[Stocks.ALL.size()];
    private final int[] rowH = new int[Stocks.ALL.size()];
    private boolean listView = true;
    private int backX;
    private int backY;
    private int backW;
    private int backH;
    private int minusX;
    private int minusY;
    private int minusW;
    private int minusH;
    private int plusX;
    private int plusY;
    private int plusW;
    private int plusH;
    private int stepOriginX;
    private int stepY;
    private int stepW;
    private int stepH;
    private int stepGap;
    private int buyX;
    private int buyY;
    private int buyW;
    private int buyH;
    private int sellX;
    /** 「大屏」切换按钮的位置（列表页和详情页都会重新算一遍） */
    private int bigX;
    private int bigY;
    private int bigW;
    private int bigH;

    // ------------------------------------------------------------------ 渲染入口

    @Override
    public void render(PhoneCanvas canvas) {
        // 这一帧确实是画在手机界面里的：把那个 Screen 记下来，大屏上的「手机」按钮才知道该回哪儿。
        // 里面只有一次引用比较，不会每帧都赋值。
        PhoneSwitch.remember(Minecraft.getInstance().screen);
        // 兜底：万一某个版本的 MCphone 不调 tick()，光靠 onOpen 那一次，页面开着时价格就不动了。
        // 这里按真实时间补一次（两秒一次，和 tick 的节奏一致），不会额外多发包。
        if (System.currentTimeMillis() - this.lastSyncMs >= SYNC_TICKS * 50L) {
            this.requestSync();
        }
        MarketSnapshotPayload snapshot = MarketCache.snapshot();
        this.refreshText(snapshot, canvas.font());
        this.measureLabels(canvas.font());
        if (this.selected >= 0 && this.selected < Stocks.ALL.size()) {
            this.renderDetail(canvas, snapshot);
        } else {
            this.renderList(canvas, snapshot);
        }
    }

    // ------------------------------------------------------------------ 列表

    private void renderList(PhoneCanvas canvas, MarketSnapshotPayload snapshot) {
        GuiGraphics graphics = canvas.graphics();
        Font font = canvas.font();
        UiTheme.Palette palette = UiTheme.palette();
        int x = canvas.x();
        int y = canvas.y();
        int width = canvas.width();
        int height = canvas.height();

        this.listView = true;

        // 顶部余额：欠债时用警示色，一眼能看见自己是负数
        int cashColor = snapshot != null && snapshot.balance() < 0L ? palette.warn() : palette.gold();
        graphics.drawString(font, this.cashText, x + 3, y + 2, cashColor, false);

        // 右上角「大屏」：点一下换成交易所的大界面，状态栏 / 壁纸留在大屏那边
        int bigWidth = this.labelBigWidth;
        this.bigX = x + width - 3 - bigWidth;
        this.bigY = y + 1;
        this.bigW = bigWidth;
        this.bigH = 10;
        this.drawSmallButton(graphics, canvas, palette, this.bigX, this.bigY, this.bigW, this.bigH,
                this.labelBig);

        String hint = this.labelTap;
        int hintWidth = this.labelTapWidth;
        int hintX = this.bigX - 4 - hintWidth;
        if (hintWidth > 0 && hintX > x + 3 + this.cashWidth + 4) {
            graphics.drawString(font, hint, hintX, y + 2, canvas.style().subtleColor(), false);
        }

        int listTop = y + HEADER_H;
        int listHeight = Math.max(1, height - HEADER_H);
        int contentHeight = Stocks.ALL.size() * ROW_H;
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, contentHeight - listHeight)));

        final int startY = listTop - this.scroll;
        final int rowWidth = width;
        final int left = x;
        final int visibleTop = listTop;
        final int visibleBottom = listTop + listHeight;

        // 名字按当前手机宽度裁好、价格量好宽度，一帧只做一次；行的循环里直接用
        this.measureRows(font, rowWidth, snapshot != null);

        canvas.clipped(x, listTop, width, listHeight, () -> {
            for (int i = 0; i < Stocks.ALL.size(); i++) {
                StockDefinition definition = Stocks.ALL.get(i);
                int top = startY + i * ROW_H;
                // 视口外的整行直接跳过
                if (top + ROW_H <= visibleTop || top >= visibleBottom) {
                    this.rowH[i] = 0;
                    continue;
                }
                boolean hovered = canvas.hovered(left, top, rowWidth, ROW_H);
                if (hovered) {
                    graphics.fill(left, top, left + rowWidth, top + ROW_H - 1, palette.rowHover());
                }

                float change = snapshot == null ? 0.0F : snapshot.changeOf(i);
                int changeColor = change >= 0.0F ? palette.up() : palette.down();
                // 行首色条：绿涨红跌，和交易界面一致
                graphics.fill(left, top + 2, left + 2, top + ROW_H - 2, changeColor);

                graphics.renderItem(StockIcons.of(definition), left + 4, top);
                int textY = top + (ROW_H - 8) / 2;

                // 名字的右边界跟着价格列的实际宽度走：价格数字变长时先裁名字，绝不叠在一起
                if (snapshot != null) {
                    String price = i < this.priceText.length ? this.priceText[i] : "-";
                    String changeLabel = i < this.changeText.length ? this.changeText[i] : "-";
                    graphics.drawString(font, price, left + rowWidth - 30 - this.priceWidthAt(i),
                            textY, changeColor, false);
                    graphics.drawString(font, changeLabel,
                            left + rowWidth - 3 - this.changeWidthAt(i), textY, changeColor, false);
                } else {
                    graphics.drawString(font, "--", left + rowWidth - 24, textY, palette.dim(), false);
                }
                graphics.drawString(font, this.displayNameAt(i),
                        left + 22, textY, canvas.style().bodyColor(), false);

                this.rowY[i] = top;
                this.rowH[i] = ROW_H;
            }
        });

        // 欠债时底部再提醒一句，列表页也能看见
        if (snapshot != null && snapshot.balance() < 0L) {
            graphics.drawString(font, font.plainSubstrByWidth(this.labelDebt, width - 6),
                    x + 3, y + height - 9, palette.warn(), false);
        }
    }

    // ------------------------------------------------------------------ 详情

    private void renderDetail(PhoneCanvas canvas, MarketSnapshotPayload snapshot) {
        GuiGraphics graphics = canvas.graphics();
        Font font = canvas.font();
        UiTheme.Palette palette = UiTheme.palette();
        int x = canvas.x();
        int y = canvas.y();
        int width = canvas.width();
        int height = canvas.height();
        int index = this.selected;

        this.listView = false;
        this.ensureDetailText(snapshot, index, font, width);

        // 返回条：左边「‹ 列表」，右边「大屏」。两块各点各的，返回区让开按钮
        int bigWidth = this.labelBigWidth;
        this.bigX = x + width - 3 - bigWidth;
        this.bigY = y + 1;
        this.bigW = bigWidth;
        this.bigH = 10;
        this.backX = x;
        this.backY = y;
        this.backW = Math.max(1, this.bigX - x - 4);
        this.backH = BACK_H;
        boolean backHovered = canvas.hovered(this.backX, this.backY, this.backW, BACK_H);
        if (backHovered) {
            graphics.fill(x, y, x + this.backW, y + BACK_H, palette.rowHover());
        }
        graphics.drawString(font, this.labelBack, x + 3, y + 1,
                backHovered ? palette.title() : canvas.style().bodyColor(), false);
        this.drawSmallButton(graphics, canvas, palette, this.bigX, this.bigY, this.bigW, this.bigH,
                this.labelBig);

        float change = snapshot == null ? 0.0F : snapshot.changeOf(index);
        int changeColor = change >= 0.0F ? palette.up() : palette.down();
        int titleY = y + BACK_H + 2;
        graphics.drawString(font, this.detailTitle, x + 3, titleY, palette.title(), false);
        if (snapshot != null) {
            String label = index < this.changeText.length ? this.changeText[index] : "-";
            graphics.drawString(font, label, x + width - 3 - this.changeWidthAt(index), titleY,
                    changeColor, false);
            String price = index < this.priceText.length ? this.priceText[index] : "-";
            graphics.drawString(font, price, x + 3, titleY + 10, changeColor, false);
        }

        int infoY = titleY + 21;
        int buttonsY = y + height - 16;
        int quantityY = buttonsY - 16;
        int stepsY = quantityY - 14;
        int chartTop = infoY;
        int chartHeight = Math.max(12, Math.min(CHART_MAX_H, stepsY - 3 - chartTop));

        // 走势图：逐段上色，和交易界面的详情图同一套画法
        if (snapshot != null) {
            int chartWidth = Math.max(1, width - 4);
            graphics.fill(x + 2, chartTop, x + 2 + chartWidth, chartTop + chartHeight, palette.chartBg());
            int offset = index * snapshot.samplesPerStock();
            int points = snapshot.samplesPerStock();
            ChartPainter.area(graphics, x + 2, chartTop, chartWidth, chartHeight,
                    snapshot.history(), offset, points, ChartPainter.tint(changeColor, 0x3A));
            ChartPainter.moves(graphics, x + 2, chartTop, chartWidth, chartHeight,
                    snapshot.history(), offset, points, palette.up(), palette.down(), changeColor, 1);
            graphics.renderOutline(x + 2, chartTop, chartWidth, chartHeight, palette.border());
        } else {
            graphics.drawString(font, this.labelWaiting, x + 4, chartTop + chartHeight / 2 - 4,
                    palette.dim(), false);
        }

        // 持仓 / 盈亏
        int infoLineY = chartTop + chartHeight + 3;
        graphics.drawString(font, this.detailHeld, x + 3, infoLineY, canvas.style().bodyColor(), false);
        if (!this.pnlText.isEmpty()) {
            int pnlColor = this.pnlText.startsWith("-") ? palette.down() : palette.up();
            graphics.drawString(font, this.pnlText, x + width - 3 - this.pnlWidth,
                    infoLineY, pnlColor, false);
        }

        // 数量：− 数字 +
        this.minusX = x + 3;
        this.minusY = quantityY;
        this.minusW = 16;
        this.minusH = 12;
        this.plusW = 16;
        this.plusH = 12;
        this.plusX = x + width - 3 - this.plusW;
        this.plusY = quantityY;
        this.drawSmallButton(graphics, canvas, palette, this.minusX, this.minusY, this.minusW,
                this.minusH, "\u2212");
        this.drawSmallButton(graphics, canvas, palette, this.plusX, this.plusY, this.plusW,
                this.plusH, "+");
        String quantityLabel = Integer.toString(this.quantity);
        graphics.drawString(font, quantityLabel, x + (width - font.width(quantityLabel)) / 2,
                quantityY + 2, palette.title(), false);

        // 快捷数量：1 / 10 / 100 / 全部
        this.stepOriginX = x + 3;
        this.stepY = stepsY;
        this.stepH = 12;
        this.stepGap = 2;
        this.stepW = Math.max(10, (width - 6 - this.stepGap * 3) / 4);
        for (int i = 0; i < 4; i++) {
            String quick = i == 3 ? this.labelAll : QUICK_LABELS[i];
            int buttonX = this.stepX(i);
            boolean hovered = canvas.hovered(buttonX, stepsY, this.stepW, this.stepH);
            boolean active = this.quickAmountMatches(i);
            int fill = active ? palette.chip()
                    : hovered ? canvas.style().buttonHoverColor() : canvas.style().buttonColor();
            graphics.fill(buttonX, stepsY, buttonX + this.stepW, stepsY + this.stepH, fill);
            graphics.drawString(font, quick, buttonX + (this.stepW - font.width(quick)) / 2,
                    stepsY + 2, canvas.style().bodyColor(), false);
        }

        // 买 / 卖
        int buttonGap = 4;
        this.buyW = (width - 6 - buttonGap) / 2;
        this.buyH = 14;
        this.buyX = x + 3;
        this.buyY = buttonsY;
        this.sellX = x + 3 + this.buyW + buttonGap;
        this.drawActionButton(graphics, canvas, this.buyX, this.buyY, this.buyW, this.buyH,
                this.labelBuy, palette.up());
        this.drawActionButton(graphics, canvas, this.sellX, this.buyY, this.buyW, this.buyH,
                this.labelSell, palette.down());
    }

    private int stepX(int index) {
        return this.stepOriginX + index * (this.stepW + this.stepGap);
    }

    /** 快捷数量按钮当前是不是就是选中的那个 */
    private boolean quickAmountMatches(int index) {
        return index < 3
                ? this.quantity == switch (index) {
                    case 0 -> 1;
                    case 1 -> 10;
                    default -> 100;
                }
                : this.quantity == this.bestQuantity();
    }

    private void drawSmallButton(GuiGraphics graphics, PhoneCanvas canvas, UiTheme.Palette palette,
                                 int x, int y, int width, int height, String label) {
        boolean hovered = canvas.hovered(x, y, width, height);
        graphics.fill(x, y, x + width, y + height,
                hovered ? canvas.style().buttonHoverColor() : canvas.style().buttonColor());
        graphics.drawString(canvas.font(), label, x + (width - canvas.font().width(label)) / 2,
                y + (height - 8) / 2, hovered ? palette.title() : canvas.style().bodyColor(), false);
    }

    /** 买卖那种带底色的按钮。底色用当前主题的涨 / 跌色，和走势图对得上 */
    private void drawActionButton(GuiGraphics graphics, PhoneCanvas canvas,
                                  int x, int y, int width, int height, String label, int accent) {
        boolean hovered = canvas.hovered(x, y, width, height);
        graphics.fill(x, y, x + width, y + height, ChartPainter.tint(accent, hovered ? 0xE6 : 0xB4));
        graphics.renderOutline(x, y, width, height, ChartPainter.tint(accent, 0xFF));
        int textColor = isLight(accent) ? 0xFF10201A : 0xFFFFFFFF;
        graphics.drawString(canvas.font(), label, x + (width - canvas.font().width(label)) / 2,
                y + (height - 8) / 2, textColor, false);
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150;
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        // 「大屏」按钮：列表页和详情页都有，先判它，免得被返回条 / 列表行抢走点击
        if (this.bigH > 0 && inside(mouseX, mouseY, this.bigX, this.bigY, this.bigW, this.bigH)) {
            PhoneSwitch.openBigScreen();
            return true;
        }
        if (this.listView) {
            for (int i = 0; i < Stocks.ALL.size(); i++) {
                if (this.rowH[i] > 0 && inside(mouseX, mouseY, this.rowY[i], this.rowH[i])) {
                    this.selected = i;
                    return true;
                }
            }
            return true;
        }

        if (inside(mouseX, mouseY, this.backX, this.backY, this.backW, this.backH)) {
            this.selected = -1;
            this.listView = true;
            return true;
        }
        if (inside(mouseX, mouseY, this.minusX, this.minusY, this.minusW, this.minusH)) {
            this.quantity = Math.max(1, this.quantity - 1);
            return true;
        }
        if (inside(mouseX, mouseY, this.plusX, this.plusY, this.plusW, this.plusH)) {
            this.quantity = Math.min(1_000_000, this.quantity + 1);
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (inside(mouseX, mouseY, this.stepX(i), this.stepY, this.stepW, this.stepH)) {
                this.quantity = switch (i) {
                    case 0 -> 1;
                    case 1 -> 10;
                    case 2 -> 100;
                    default -> this.bestQuantity();
                };
                return true;
            }
        }
        if (inside(mouseX, mouseY, this.buyX, this.buyY, this.buyW, this.buyH)) {
            this.trade(true);
            return true;
        }
        if (inside(mouseX, mouseY, this.sellX, this.buyY, this.buyW, this.buyH)) {
            this.trade(false);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.listView) {
            this.scroll -= (int) (amount * ROW_H);
            return true;
        }
        return false;
    }

    @Override
    public boolean onBack() {
        if (!this.listView) {
            this.selected = -1;
            this.listView = true;
            return true;
        }
        return false;
    }

    @Override
    public void onOpen() {
        this.syncTimer = 0;
        this.requestSync();
        // 手册的导出只在登录时做一次（见 McphoneBooks），打开 App 不再碰磁盘：
        // 以前每次打开都要拼一遍整本教程再读一次文件，这是「点开 App 卡一下」的主要原因。
    }

    @Override
    public void tick() {
        if (++this.syncTimer >= SYNC_TICKS) {
            this.syncTimer = 0;
            this.requestSync();
        }
    }

    @Override
    public boolean capturesKeyboard() {
        // 手机里这一页没有输入框；返回 false 玩家才能用背包键关掉手机
        return false;
    }

    private void requestSync() {
        // tick 与渲染两条路可能同时到期；一秒内只发一次，别让服务器每帧都收到同步请求
        long now = System.currentTimeMillis();
        if (this.lastSyncMs != 0L && now - this.lastSyncMs < 1000L) {
            return;
        }
        this.lastSyncMs = now;
        if (Minecraft.getInstance().getConnection() != null) {
            MarketNetworking.sendToServer(new MarketActionPayload(MarketActionPayload.ACTION_SYNC, "", 0));
        }
    }

    private void trade(boolean buying) {
        if (this.selected < 0 || this.selected >= Stocks.ALL.size()) {
            return;
        }
        StockDefinition definition = Stocks.ALL.get(this.selected);
        MarketNetworking.sendToServer(new MarketActionPayload(
                buying ? MarketActionPayload.ACTION_BUY : MarketActionPayload.ACTION_SELL,
                definition.id(), Math.max(1, this.quantity)));
    }

    /** 「全部」：自己手里有这只股票就按持仓算，否则按盘面还能买多少算 */
    private int bestQuantity() {
        MarketSnapshotPayload snapshot = MarketCache.snapshot();
        if (snapshot == null || this.selected < 0 || this.selected >= Stocks.ALL.size()) {
            return 1;
        }
        long held = this.selected < snapshot.holdings().length ? snapshot.holdings()[this.selected] : 0L;
        if (held > 0L) {
            return (int) Math.max(1L, Math.min(1_000_000L, held));
        }
        return (int) Math.max(1L, Math.min(1_000_000L, snapshot.maxBuyable(this.selected)));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static boolean inside(double mouseX, double mouseY, int rowY, int rowHeight) {
        // 列表行没有左边界——整行都是热区（与交易界面里「点行看详情」一致）
        return mouseY >= rowY && mouseY < rowY + rowHeight;
    }

    // ------------------------------------------------------------------ 文字缓存

    private void refreshText(MarketSnapshotPayload snapshot, Font font) {
        String language = currentLanguage();
        if (snapshot == this.formattedFor && language.equals(this.cachedLanguage)) {
            return;
        }
        this.formattedFor = snapshot;
        this.cachedLanguage = language;
        this.cacheLabels();

        int count = Stocks.ALL.size();
        if (this.priceText.length != count) {
            this.priceText = new String[count];
            this.changeText = new String[count];
            this.names = new String[count];
            this.priceWidth = new int[count];
            this.changeWidth = new int[count];
        }
        for (int i = 0; i < count; i++) {
            this.names[i] = stockName(i);
        }
        if (snapshot == null) {
            this.cashText = this.labelWaiting;
            Arrays.fill(this.priceText, "-");
            Arrays.fill(this.changeText, "-");
        } else {
            this.cashText = Component.translatable("app.shared_stock_exchange.stock_market.cash",
                    MarketFormat.emc(snapshot.balance())).getString();
            for (int i = 0; i < count; i++) {
                // 防御一下：快照长度理论上和股票表一致，真对不上也别让界面抛数组越界
                if (i < snapshot.prices().length) {
                    this.priceText[i] = MarketFormat.emc(snapshot.prices()[i]);
                    this.changeText[i] = MarketFormat.percentShort(snapshot.changeOf(i));
                } else {
                    this.priceText[i] = "-";
                    this.changeText[i] = "-";
                }
            }
        }
        // 价格 / 涨跌的像素宽也只量这一次，列表和详情页都直接用
        this.cashWidth = font.width(this.cashText);
        for (int i = 0; i < count; i++) {
            this.priceWidth[i] = font.width(this.priceText[i]);
            this.changeWidth[i] = font.width(this.changeText[i]);
        }
        // 快照 / 语言变了，按宽度裁好的名字和详情页的文字作废，下一帧重算
        this.measuredWidth = -1;
        this.detailWidth = -1;
    }

    /**
     * 列表每行要画的名字按当前宽度裁一次。
     *
     * <p>以前是每帧对 8 行各量一遍字宽、再裁一遍字符串；名字和价格的宽度其实只在
     * 「快照换了 / 换了语言 / 手机宽度变了」时才变，量一次存起来，渲染循环里只管画。
     */
    private void measureRows(Font font, int width, boolean hasSnapshot) {
        if (this.measuredWidth == width) {
            return;
        }
        this.measuredWidth = width;
        int count = this.names.length;
        if (this.displayNames.length != count) {
            this.displayNames = new String[count];
        }
        for (int i = 0; i < count; i++) {
            int nameMax = hasSnapshot ? width - 30 - this.priceWidthAt(i) - 22 - 2 : width - 22 - 26;
            this.displayNames[i] = font.plainSubstrByWidth(this.names[i], Math.max(8, nameMax));
        }
    }

    /** 「大屏」「点一行看详情」两处固定文字的宽度，换字体或换语言才重量 */
    private void measureLabels(Font font) {
        if (this.measuredFont == font) {
            return;
        }
        this.measuredFont = font;
        this.labelBigWidth = Math.max(20, font.width(this.labelBig) + 8);
        this.labelTapWidth = font.width(this.labelTap);
    }

    private int priceWidthAt(int index) {
        return index >= 0 && index < this.priceWidth.length ? this.priceWidth[index] : 0;
    }

    private int changeWidthAt(int index) {
        return index >= 0 && index < this.changeWidth.length ? this.changeWidth[index] : 0;
    }

    private String displayNameAt(int index) {
        return index >= 0 && index < this.displayNames.length
                ? this.displayNames[index]
                : stockName(index);
    }

    /** 界面上固定不动的那些词，只在语言变了之后重取一次 */
    private void cacheLabels() {
        this.labelBig = text("big_screen");
        this.labelTap = text("tap");
        this.labelAll = text("all");
        this.labelBuy = text("buy");
        this.labelSell = text("sell");
        this.labelBack = text("back");
        this.labelDebt = text("debt");
        this.labelWaiting = text("waiting");
        // 词换了，按词量出来的宽度也跟着作废
        this.measuredFont = null;
    }

    /**
     * 详情页那几行文字，跟着「选中哪只 + 哪份快照 + 手机宽度」变。
     * 以前是每帧都拼一遍翻译、再量一遍字宽；现在只在这几个东西换过（或语言变了）时算一次。
     */
    private void ensureDetailText(MarketSnapshotPayload snapshot, int index, Font font, int width) {
        String language = this.cachedLanguage;
        if (snapshot == this.detailFor && index == this.detailIndex && width == this.detailWidth
                && language.equals(this.detailLanguage)) {
            return;
        }
        this.detailFor = snapshot;
        this.detailIndex = index;
        this.detailWidth = width;
        this.detailLanguage = language;
        String name = index >= 0 && index < this.names.length ? this.names[index] : "";
        this.detailTitle = font.plainSubstrByWidth(name, width - 48);
        if (snapshot == null || index < 0 || index >= snapshot.holdings().length) {
            this.heldText = "";
            this.pnlText = "";
            this.detailHeld = "";
            this.pnlWidth = 0;
            return;
        }
        long held = snapshot.holdings()[index];
        this.heldText = Component.translatable("app.shared_stock_exchange.stock_market.holding", held).getString();
        if (held > 0L) {
            double cost = index < snapshot.costs().length ? snapshot.costs()[index] : 0.0D;
            double pnl = snapshot.proceedsOf(index, held) - cost * (double) held;
            this.pnlText = (pnl >= 0.0D ? "+" : "") + MarketFormat.emc(pnl);
        } else {
            this.pnlText = "";
        }
        this.pnlWidth = this.pnlText.isEmpty() ? 0 : font.width(this.pnlText);
        int pnlOffset = this.pnlText.isEmpty() ? 0 : this.pnlWidth + 6;
        this.detailHeld = font.plainSubstrByWidth(this.heldText, Math.max(8, width - 6 - pnlOffset));
    }

    private static String stockName(int index) {
        return Component.translatable(Stocks.nameKey(Stocks.ALL.get(index).id())).getString();
    }

    private static String text(String key) {
        return Component.translatable("app.shared_stock_exchange.stock_market." + key).getString();
    }

    private static String currentLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }
}
