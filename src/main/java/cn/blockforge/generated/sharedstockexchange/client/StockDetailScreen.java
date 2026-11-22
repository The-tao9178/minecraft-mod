package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import cn.blockforge.generated.sharedstockexchange.market.Stocks;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 单只股票的涨跌详情页：点交易界面里的任意一行就进来了。
 *
 * 中间是一张大的涨跌图：最近 120 个价格点，每一小段单独上色（这一步涨是绿、跌是红），
 * 整段时间的趋势仍由面积、边框和右上角徽标表达；另外标了最高 / 最低 / 区间起点 / 你的持仓均价。
 * 图左右边缘鼠标划过会冒出箭头，点一下就能切到上一只 / 下一只股票，键盘左右方向键也行。
 *
 * 下面还有现价、参考价、市值、分红池、第一大股东、你的盈亏和风险情况
 * （权益 / 负债 / 强平线，快爆仓时整行变红；强制平仓关掉时如实显示不会强平），
 * 最底下可以直接按自定义股数买卖或清仓。
 *
 * 这一版和小窗口对齐：正常窗口用 400×260 的宽版，
 * 窗口小的时候自动切成 300×236 的紧凑版（图矮一点、信息行合并成两行），
 * 再整体等比缩放到刚好装下，所以小窗玩家也不会被切掉按钮。
 */
public class StockDetailScreen extends UiPanelScreen {
    private static final int[] QUICK_AMOUNTS = {1, 10, 100, 1000};
    /** 图左右两边多宽的一条算「切股票」的箭头区（像素）。 */
    private static final int ARROW_ZONE = 16;

    private int index;
    private Layout layout = wideLayout();
    private EditBox quantityBox;

    public StockDetailScreen(int index) {
        super(Component.translatable("gui.shared_stock_exchange.detail"));
        this.index = index;
    }

    private StockDefinition definition() {
        return Stocks.ALL.get(this.index);
    }

    @Override
    protected void init() {
        this.layout = pickLayout(this.width, this.height);
        this.applyDesignSize(this.layout.width, this.layout.height);
        // 进来先要一份最新行情，涨跌图才是当下的
        PacketDistributor.sendToServer(new MarketActionPayload(MarketActionPayload.ACTION_SYNC, "", 0));

        Layout layout = this.layout;
        this.quantityBox = new EditBox(this.font, layout.qtyBoxX, layout.qtyBoxY, layout.qtyBoxWidth, 16,
                Component.translatable("gui.shared_stock_exchange.qty"));
        this.quantityBox.setMaxLength(6);
        // 只让输数字：用逐字符检查代替 text.matches(正则)，后者每次按键都要重新编译模式
        this.quantityBox.setFilter(text -> {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            return true;
        });
        this.quantityBox.setValue(Integer.toString(TradeQuantity.get()));
        this.quantityBox.setResponder(text -> TradeQuantity.set(TradeQuantity.parse(text)));
        this.addRenderableWidget(this.quantityBox);

        for (int i = 0; i < QUICK_AMOUNTS.length; i++) {
            final int amount = QUICK_AMOUNTS[i];
            this.addRenderableWidget(new FitButton(layout.quickX + i * layout.quickStep, layout.quickY,
                    layout.quickWidth, 16, Component.literal(Integer.toString(amount)), button -> {
                        TradeQuantity.set(amount);
                        this.quantityBox.setValue(Integer.toString(amount));
                    }));
        }

        int actionY = layout.actionY;
        int actionWidth = layout.actionWidth;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.shared_stock_exchange.buy"),
                        button -> this.trade(true))
                .bounds(layout.buyButtonX, actionY, actionWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.shared_stock_exchange.sell"),
                        button -> this.trade(false))
                .bounds(layout.sellButtonX, actionY, actionWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.shared_stock_exchange.clear_one"),
                        button -> PacketDistributor.sendToServer(
                                new MarketActionPayload(MarketActionPayload.ACTION_LIQUIDATE, this.definition().id(), 0)))
                .bounds(layout.clearButtonX, actionY, actionWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.shared_stock_exchange.back"),
                        button -> this.onClose())
                .bounds(layout.backButtonX, actionY, actionWidth, 18).build());
    }

    /** 按 ESC 或点「返回」都回到交易列表，而不是直接关掉界面。 */
    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(new MarketScreen());
        }
    }

    /** 切到上一只 / 下一只股票（到头会绕回另一头）。 */
    private void switchStock(int delta) {
        int size = Stocks.ALL.size();
        if (size <= 1 || delta == 0) {
            return;
        }
        this.index = ((this.index + delta) % size + size) % size;
        // 重新摆一遍按钮和输入框；标题、图标、涨跌图、预览都会跟着读新的 index
        this.rebuildWidgets();
    }

    /** 鼠标在图的哪条边上：-1 上一只，1 下一只，0 不在箭头区。 */
    private int arrowAt(double panelX, double panelY) {
        Layout layout = this.layout;
        int middleY = layout.chartY + layout.chartHeight / 2;
        if (panelY < middleY - 12 || panelY > middleY + 12) {
            return 0;
        }
        if (panelX >= layout.chartX && panelX < layout.chartX + ARROW_ZONE) {
            return -1;
        }
        if (panelX > layout.chartX + layout.chartWidth - ARROW_ZONE
                && panelX <= layout.chartX + layout.chartWidth) {
            return 1;
        }
        return 0;
    }

    /** 像素箭头：一排从高到矮的竖线，5 次 fill，比字体渲染省也绝不会缺字。颜色跟主题走。 */
    private static void drawArrow(GuiGraphics graphics, int tipX, int centerY, boolean right, boolean hot) {
        int color = hot ? UiTheme.palette().stroke() : UiTheme.palette().arrowIdle();
        for (int i = 0; i < 5; i++) {
            int height = 9 - i * 2;
            int px = right ? tipX + i : tipX - i;
            graphics.fill(px, centerY - height / 2, px + 1, centerY - height / 2 + height, color);
        }
    }

    private void trade(boolean buying) {
        PacketDistributor.sendToServer(new MarketActionPayload(
                buying ? MarketActionPayload.ACTION_BUY : MarketActionPayload.ACTION_SELL,
                this.definition().id(),
                TradeQuantity.get()));
    }

    private static ItemStack icon(StockDefinition definition) {
        return StockIcons.of(definition);
    }

    /** 键盘左右方向键也能切股票（输入框正在打字时不抢按键）。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = this.quantityBox != null && this.quantityBox.isFocused();
        if (!typing && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) {
            this.switchStock(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean onPanelClicked(double panelX, double panelY, int button) {
        if (button != 0) {
            return false;
        }
        int arrow = this.arrowAt(panelX, panelY);
        if (arrow != 0) {
            this.switchStock(arrow);
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.index < 0 || this.index >= Stocks.ALL.size()) {
            // 正常不会发生，兜个底：万一股票表变了，也别在渲染途中关界面
            this.index = 0;
        }
        Layout layout = this.layout;
        int panelMouseX = this.panelMouseX(mouseX);
        int panelMouseY = this.panelMouseY(mouseY);
        int hoverArrow = this.arrowAt(panelMouseX, panelMouseY);
        // 一次渲染取一次配色（主题在内存里，取一次就够）
        UiTheme.Palette palette = UiTheme.palette();

        graphics.fill(0, 0, this.width, this.height, palette.shade());

        this.pushPanel(graphics);
        graphics.fill(0, 0, layout.width, layout.height, palette.panel());
        graphics.renderOutline(0, 0, layout.width, layout.height, palette.border());

        this.renderWidgets(graphics, panelMouseX, panelMouseY, partialTick);

        StockDefinition definition = this.definition();
        MarketSnapshotPayload snapshot = MarketCache.snapshot();

        // 标题：图标 + 名字 + 现价
        graphics.renderItem(icon(definition), layout.iconX, layout.iconY);
        graphics.drawString(this.font, Component.translatable(Stocks.nameKey(definition.id())),
                layout.nameX, layout.nameY, palette.title(), false);

        double price = snapshot != null && this.index < snapshot.prices().length ? snapshot.prices()[this.index] : 0.0D;
        float change = snapshot != null ? snapshot.changeOf(this.index) : 0.0F;
        int changeColor = change >= 0.0F ? palette.up() : palette.down();
        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_price",
                        MarketFormat.emc(price), MarketFormat.percent(change)),
                layout.priceX, layout.priceY, changeColor, false);

        // 涨跌图本体：整体涨跌色面积 + 逐段红绿折线 + 区间起点虚线 + 持仓均价金线 + 最新价亮点
        int chartLeft = layout.chartX;
        int chartTop = layout.chartY;
        int chartWidth = layout.chartWidth;
        int chartHeight = layout.chartHeight;
        graphics.fill(chartLeft, chartTop, chartLeft + chartWidth, chartTop + chartHeight, palette.chartBg());
        float[] range = new float[]{0.0F, 0.0F};
        if (snapshot != null) {
            int per = snapshot.samplesPerStock();
            int offset = this.index * per;
            range = ChartPainter.range(snapshot.history(), offset, per);
            ChartPainter.area(graphics, chartLeft, chartTop, chartWidth, chartHeight,
                    snapshot.history(), offset, per, ChartPainter.tint(changeColor, 0x4C));
            ChartPainter.grid(graphics, chartLeft, chartTop, chartWidth, chartHeight, 4, palette.grid());

            // 区间起点的价格：一条白色虚线，涨跌就是相对它算的
            int startY = ChartPainter.projectY(snapshot.priceAt(this.index, 0),
                    range[0], range[1], chartTop, chartHeight);
            ChartPainter.dashedLine(graphics, chartLeft, startY, chartWidth, palette.lineSoft());

            // 持仓均价：一条金色的参考线，看看现在浮盈还是浮亏
            long held = this.index < snapshot.holdings().length ? snapshot.holdings()[this.index] : 0L;
            double avgCost = this.index < snapshot.costs().length ? snapshot.costs()[this.index] : 0.0D;
            if (held > 0L && avgCost > 0.0D && range[1] > range[0]) {
                int lineY = ChartPainter.projectY((float) avgCost, range[0], range[1], chartTop, chartHeight);
                graphics.fill(chartLeft, lineY, chartLeft + chartWidth, lineY + 1, palette.gold());
            }

            // 折线：每一小段单独上色，涨绿跌红；持平（价格没动）用整体趋势色
            ChartPainter.moves(graphics, chartLeft, chartTop, chartWidth, chartHeight,
                    snapshot.history(), offset, per, palette.up(), palette.down(), changeColor, 3);
            // 最新价打个亮点，视线一眼落到「现在多少钱」
            int lastY = ChartPainter.projectY(snapshot.priceAt(this.index, per - 1),
                    range[0], range[1], chartTop, chartHeight);
            ChartPainter.dot(graphics, chartLeft + chartWidth - 4, lastY, 3, palette.dot());
            ChartPainter.dot(graphics, chartLeft + chartWidth - 4, lastY, 2, changeColor);

            // 最高价（浅绿）和最低价（浅红），价格区间一眼能认
            String high = MarketFormat.emc(range[1]);
            String low = MarketFormat.emc(range[0]);
            graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_high", high),
                    chartLeft + 4, chartTop + 4, palette.high(), false);
            graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_low", low),
                    chartLeft + 4, chartTop + chartHeight - 12, palette.low(), false);
            // 图例：告诉玩家每一段的颜色是什么意思
            int legendX = chartLeft + chartWidth - 46;
            int legendY = chartTop + chartHeight - 12;
            graphics.drawString(this.font, "\u25B2", legendX, legendY, palette.up(), false);
            graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_legend_up"),
                    legendX + 9, legendY, palette.up(), false);
            graphics.drawString(this.font, "\u25BC", legendX + 21, legendY, palette.down(), false);
            graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_legend_down"),
                    legendX + 30, legendY, palette.down(), false);

            // 右上角的涨跌徽标：▲ 上涨 / ▼ 下跌，颜色和整体趋势一致
            Component badge = change >= 0.0F
                    ? Component.translatable("gui.shared_stock_exchange.chart_up", MarketFormat.percent(change))
                    : Component.translatable("gui.shared_stock_exchange.chart_down", MarketFormat.percent(change));
            int badgeWidth = this.font.width(badge);
            int badgeX = chartLeft + chartWidth - badgeWidth - 8;
            graphics.fill(badgeX - 3, chartTop + 3, chartLeft + chartWidth - 2, chartTop + 14,
                    ChartPainter.tint(changeColor, 0x88));
            graphics.drawString(this.font, badge, badgeX, chartTop + 5, changeColor, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_wait"),
                    chartLeft + 8, chartTop + chartHeight / 2 - 4, palette.dim(), false);
        }
        // 整张图的边框也用涨跌色，扫一眼就知道整体是涨是跌
        graphics.renderOutline(chartLeft, chartTop, chartWidth, chartHeight, changeColor);

        // 鼠标停在图的左右边缘：冒出箭头（加一层淡淡的底，位置一看就懂）
        int arrowMiddleY = chartTop + chartHeight / 2;
        if (hoverArrow < 0) {
            graphics.fill(chartLeft, arrowMiddleY - 13, chartLeft + ARROW_ZONE - 2, arrowMiddleY + 13, palette.arrowBand());
            drawArrow(graphics, chartLeft + 5, arrowMiddleY, false, true);
        } else if (hoverArrow > 0) {
            graphics.fill(chartLeft + chartWidth - ARROW_ZONE + 2, arrowMiddleY - 13,
                    chartLeft + chartWidth, arrowMiddleY + 13, palette.arrowBand());
            drawArrow(graphics, chartLeft + chartWidth - 9, arrowMiddleY, true, true);
        } else {
            // 不悬停时留一对很淡的箭头，提示这里能点
            drawArrow(graphics, chartLeft + 5, arrowMiddleY, false, false);
            drawArrow(graphics, chartLeft + chartWidth - 9, arrowMiddleY, true, false);
        }

        if (snapshot != null) {
            long held = this.index < snapshot.holdings().length ? snapshot.holdings()[this.index] : 0L;
            double avgCost = this.index < snapshot.costs().length ? snapshot.costs()[this.index] : 0.0D;
            long total = Math.max(1L, definition.totalShares());
            double dividend = snapshot.dividendOf(this.index);
            // 盈亏按「现在卖掉能拿回多少 - 持仓成本」算，和保证金口径一致
            double pnl = held > 0L ? snapshot.proceedsOf(this.index, held) - avgCost * (double) held : 0.0D;
            String share = MarketFormat.share((double) held / (double) total);
            int infoWidth = layout.width - layout.infoX * 2;

            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.chart_range",
                            MarketFormat.emc(range[1]), MarketFormat.emc(range[0]),
                            MarketFormat.emc(snapshot.priceAt(this.index, 0)), MarketFormat.percent(change)),
                    layout.infoX, layout.rangeY, changeColor, infoWidth);

            this.drawClipped(graphics, layout.compact
                            ? Component.translatable("gui.shared_stock_exchange.detail_price_short",
                            MarketFormat.emc(price), MarketFormat.emc(snapshot.anchorOf(this.index)))
                            : Component.translatable("gui.shared_stock_exchange.detail_price",
                            MarketFormat.emc(price), MarketFormat.emc(snapshot.anchorOf(this.index)),
                            MarketFormat.emc(price * (double) definition.totalShares())),
                    layout.infoX, layout.priceLineY, palette.text(), infoWidth);

            if (layout.compact) {
                this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.detail_mine_short",
                                held, MarketFormat.emc(avgCost)),
                        layout.infoX, layout.mineY, held > 0L ? palette.text() : palette.dim(), infoWidth);
                this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.detail_pnl_short",
                                MarketFormat.emc(pnl), share),
                        layout.infoX, layout.pnlY, held > 0L ? (pnl >= 0.0D ? palette.up() : palette.down()) : palette.dim(), infoWidth);
            } else {
                this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.detail_mine",
                                held, MarketFormat.emc(avgCost), MarketFormat.emc(pnl), share),
                        layout.infoX, layout.mineY, held > 0L ? (pnl >= 0.0D ? palette.up() : palette.down()) : palette.dim(), infoWidth);
            }

            String topName = snapshot.topNameOf(this.index);
            Component top = topName.isEmpty()
                    ? Component.translatable("gui.shared_stock_exchange.tip_no_top")
                    : Component.translatable("gui.shared_stock_exchange.detail_top",
                    topName, MarketFormat.share((double) snapshot.topSharesOf(this.index) / (double) total));
            this.drawClipped(graphics, Component.translatable("gui.shared_stock_exchange.detail_dividend",
                            MarketFormat.emc(dividend), top),
                    layout.infoX, layout.dividendY, palette.dim(), infoWidth);

            // 风险那一行：欠债或有持仓时显示权益 / 负债（强制平仓关掉时如实说明），否则显示波动间隔
            if (layout.marginY > 0) {
                double positionValue = snapshot.positionValue();
                double equity = snapshot.equity();
                double debt = snapshot.debt();
                boolean risk = snapshot.marginRisk();
                if (held > 0L || debt > 0.0D) {
                    Component marginLine;
                    int marginColor;
                    if (!snapshot.liquidationEnabled()) {
                        // 强平关了：欠多少都只是挂着，不会有人替你卖股票
                        marginLine = Component.translatable("gui.shared_stock_exchange.detail_margin_off",
                                MarketFormat.emc(equity), MarketFormat.emc(debt));
                        marginColor = debt > 0.0D ? palette.warn() : palette.dim();
                    } else if (risk) {
                        marginLine = Component.translatable("gui.shared_stock_exchange.detail_margin_risk",
                                MarketFormat.emc(equity),
                                MarketFormat.emc(MarketMath.maintenanceLine(positionValue)));
                        marginColor = palette.down();
                    } else {
                        marginLine = Component.translatable("gui.shared_stock_exchange.detail_margin",
                                MarketFormat.emc(equity), MarketFormat.emc(debt),
                                MarketFormat.emc(MarketMath.maintenanceLine(positionValue)));
                        marginColor = debt > 0.0D ? palette.warn() : palette.dim();
                    }
                    this.drawClipped(graphics, marginLine, layout.infoX, layout.marginY, marginColor, infoWidth);
                } else {
                    graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.detail_drift",
                            snapshot.driftSeconds()), layout.infoX, layout.marginY, palette.dim(), false);
                }
            }

            // 买卖预览：这一步之后还剩多少 EMC（可能要借钱，负数就是欠债），写得明明白白
            int shares = TradeQuantity.get();
            TradePreview.BuyPlan plan = TradePreview.planBuy(snapshot, this.index, shares);
            this.drawClipped(graphics, TradePreview.describeBuy(plan),
                    layout.infoX, layout.buyY, TradePreview.buyColor(plan), infoWidth);
            this.drawClipped(graphics, TradePreview.describeSell(snapshot, this.index, shares),
                    layout.infoX, layout.sellY,
                    held <= 0L ? palette.dim() : (shares > held ? palette.gold() : palette.up()), infoWidth);
        }

        graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.qty"),
                layout.qtyLabelX, layout.qtyLabelY, palette.dim(), false);

        this.popPanel(graphics);
    }

    // ------------------------------------------------------------------
    // 两套排版：正常窗口用宽版；小窗口用紧凑版，图矮一点、信息行合并成两行
    // ------------------------------------------------------------------

    private static Layout pickLayout(int screenWidth, int screenHeight) {
        Layout wide = wideLayout();
        Layout tight = tightLayout();
        float wideScale = fitScale(screenWidth, screenHeight, wide.width, wide.height);
        float tightScale = fitScale(screenWidth, screenHeight, tight.width, tight.height);
        return tightScale > wideScale + 0.02F ? tight : wide;
    }

    private static Layout wideLayout() {
        Layout layout = new Layout();
        layout.compact = false;
        layout.width = 400;
        layout.height = 260;
        layout.iconX = 8;
        layout.iconY = 5;
        layout.nameX = 28;
        layout.nameY = 8;
        layout.priceX = 200;
        layout.priceY = 8;
        layout.chartX = 14;
        layout.chartY = 26;
        layout.chartWidth = 372;
        layout.chartHeight = 96;
        layout.infoX = 12;
        layout.rangeY = 128;
        layout.priceLineY = 140;
        layout.mineY = 152;
        layout.pnlY = -1;
        layout.dividendY = 164;
        layout.marginY = 176;
        layout.buyY = 190;
        layout.sellY = 202;
        layout.qtyLabelX = 12;
        layout.qtyLabelY = 218;
        layout.qtyBoxX = 34;
        layout.qtyBoxY = 214;
        layout.qtyBoxWidth = 48;
        layout.quickX = 88;
        layout.quickY = 214;
        layout.quickWidth = 30;
        layout.quickStep = 34;
        layout.buyButtonX = 156;
        layout.sellButtonX = 216;
        layout.clearButtonX = 276;
        layout.backButtonX = 336;
        layout.actionY = 236;
        layout.actionWidth = 56;
        return layout;
    }

    private static Layout tightLayout() {
        Layout layout = new Layout();
        layout.compact = true;
        layout.width = 300;
        layout.height = 236;
        layout.iconX = 6;
        layout.iconY = 2;
        layout.nameX = 26;
        layout.nameY = 6;
        layout.priceX = 168;
        layout.priceY = 6;
        layout.chartX = 12;
        layout.chartY = 22;
        layout.chartWidth = 276;
        layout.chartHeight = 76;
        layout.infoX = 10;
        layout.rangeY = 103;
        layout.priceLineY = 115;
        layout.mineY = 127;
        layout.pnlY = 139;
        layout.dividendY = 151;
        layout.marginY = 163;
        layout.buyY = 175;
        layout.sellY = 187;
        layout.qtyLabelX = 8;
        layout.qtyLabelY = 199;
        layout.qtyBoxX = 30;
        layout.qtyBoxY = 195;
        layout.qtyBoxWidth = 40;
        layout.quickX = 74;
        layout.quickY = 195;
        layout.quickWidth = 28;
        layout.quickStep = 32;
        layout.buyButtonX = 37;
        layout.sellButtonX = 95;
        layout.clearButtonX = 153;
        layout.backButtonX = 211;
        layout.actionY = 214;
        layout.actionWidth = 52;
        return layout;
    }

    /** 一套排版里所有会变的坐标。 */
    private static final class Layout {
        boolean compact;
        int width;
        int height;
        int iconX;
        int iconY;
        int nameX;
        int nameY;
        int priceX;
        int priceY;
        int chartX;
        int chartY;
        int chartWidth;
        int chartHeight;
        int infoX;
        int rangeY;
        int priceLineY;
        int mineY;
        int pnlY;
        int dividendY;
        int marginY;
        int buyY;
        int sellY;
        int qtyLabelX;
        int qtyLabelY;
        int qtyBoxX;
        int qtyBoxY;
        int qtyBoxWidth;
        int quickX;
        int quickY;
        int quickWidth;
        int quickStep;
        int buyButtonX;
        int sellButtonX;
        int clearButtonX;
        int backButtonX;
        int actionY;
        int actionWidth;
    }
}
