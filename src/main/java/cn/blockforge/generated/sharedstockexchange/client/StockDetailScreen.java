package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import cn.blockforge.generated.sharedstockexchange.market.Stocks;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class StockDetailScreen extends UiPanelScreen {
   private static final int[] QUICK_AMOUNTS = new int[]{1, 10, 100, 1000};
   private static final int ARROW_ZONE = 16;
   private int index;
   private StockDetailScreen.Layout layout = wideLayout();
   private EditBox quantityBox;

   public StockDetailScreen(int index) {
      super(Component.translatable("gui.shared_stock_exchange.detail"));
      this.index = index;
   }

   private StockDefinition definition() {
      return Stocks.ALL.get(this.index);
   }

   protected void init() {
      this.layout = pickLayout(this.width, this.height);
      this.applyDesignSize(this.layout.width, this.layout.height);
      MarketNetworking.sendToServer(new MarketActionPayload(0, "", 0));
      StockDetailScreen.Layout layout = this.layout;
      this.quantityBox = new ThemeEditBox(this.font, layout.qtyBoxX, layout.qtyBoxY, layout.qtyBoxWidth, 16, Component.translatable("gui.shared_stock_exchange.qty"));
      this.quantityBox.setMaxLength(6);
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
         int amount = QUICK_AMOUNTS[i];
         this.addRenderableWidget(
            new FitButton(layout.quickX + i * layout.quickStep, layout.quickY, layout.quickWidth, 16, Component.literal(Integer.toString(amount)), button -> {
               TradeQuantity.set(amount);
               this.quantityBox.setValue(Integer.toString(amount));
            })
         );
      }

      int actionY = layout.actionY;
      int actionWidth = layout.actionWidth;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.shared_stock_exchange.buy"), button -> this.trade(true))
            .bounds(layout.buyButtonX, actionY, actionWidth, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.shared_stock_exchange.sell"), button -> this.trade(false))
            .bounds(layout.sellButtonX, actionY, actionWidth, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.shared_stock_exchange.clear_one"),
               button -> MarketNetworking.sendToServer(new MarketActionPayload(4, this.definition().id(), 0))
            )
            .bounds(layout.clearButtonX, actionY, actionWidth, 18)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.shared_stock_exchange.back"), button -> this.onClose())
            .bounds(layout.backButtonX, actionY, actionWidth, 18)
            .build()
      );
   }

   public void onClose() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.screen == this) {
         minecraft.setScreen(new MarketScreen());
      }
   }

   private void switchStock(int delta) {
      int size = Stocks.ALL.size();
      if (size > 1 && delta != 0) {
         this.index = ((this.index + delta) % size + size) % size;
         this.rebuildWidgets();
      }
   }

   private int arrowAt(double panelX, double panelY) {
      StockDetailScreen.Layout layout = this.layout;
      int middleY = layout.chartY + layout.chartHeight / 2;
      if (panelY < (double)(middleY - 12) || panelY > (double)(middleY + 12)) {
         return 0;
      } else if (panelX >= (double)layout.chartX && panelX < (double)(layout.chartX + 16)) {
         return -1;
      } else {
         return panelX > (double)(layout.chartX + layout.chartWidth - 16) && panelX <= (double)(layout.chartX + layout.chartWidth) ? 1 : 0;
      }
   }

   private static void drawArrow(GuiGraphics graphics, int tipX, int centerY, boolean right, boolean hot) {
      int color = hot ? UiTheme.palette().stroke() : UiTheme.palette().arrowIdle();

      for (int i = 0; i < 5; i++) {
         int height = 9 - i * 2;
         int px = right ? tipX + i : tipX - i;
         graphics.fill(px, centerY - height / 2, px + 1, centerY - height / 2 + height, color);
      }
   }

   private void trade(boolean buying) {
      MarketNetworking.sendToServer(new MarketActionPayload(buying ? 1 : 2, this.definition().id(), TradeQuantity.get()));
   }

   private static ItemStack icon(StockDefinition definition) {
      return StockIcons.of(definition);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      boolean typing = this.quantityBox != null && this.quantityBox.isFocused();
      if (!typing && (keyCode == 263 || keyCode == 262)) {
         this.switchStock(keyCode == 263 ? -1 : 1);
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   @Override
   protected boolean onPanelClicked(double panelX, double panelY, int button) {
      if (button != 0) {
         return false;
      } else {
         int arrow = this.arrowAt(panelX, panelY);
         if (arrow != 0) {
            this.switchStock(arrow);
            return true;
         } else {
            return false;
         }
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.index < 0 || this.index >= Stocks.ALL.size()) {
         this.index = 0;
      }

      StockDetailScreen.Layout layout = this.layout;
      int panelMouseX = this.panelMouseX((double)mouseX);
      int panelMouseY = this.panelMouseY((double)mouseY);
      int hoverArrow = this.arrowAt((double)panelMouseX, (double)panelMouseY);
      UiTheme.Palette palette = UiTheme.palette();
      graphics.fill(0, 0, this.width, this.height, palette.shade());
      this.pushPanel(graphics);
      graphics.fill(0, 0, layout.width, layout.height, palette.panel());
      graphics.renderOutline(0, 0, layout.width, layout.height, palette.border());
      this.renderWidgets(graphics, panelMouseX, panelMouseY, partialTick);
      StockDefinition definition = this.definition();
      MarketSnapshotPayload snapshot = MarketCache.snapshot();
      graphics.renderItem(icon(definition), layout.iconX, layout.iconY);
      graphics.drawString(this.font, Component.translatable(Stocks.nameKey(definition.id())), layout.nameX, layout.nameY, palette.title(), false);
      double price = snapshot != null && this.index < snapshot.prices().length ? snapshot.prices()[this.index] : 0.0;
      float change = snapshot != null ? snapshot.changeOf(this.index) : 0.0F;
      int changeColor = change >= 0.0F ? palette.up() : palette.down();
      graphics.drawString(
         this.font,
         Component.translatable("gui.shared_stock_exchange.chart_price", new Object[]{MarketFormat.emc(price), MarketFormat.percent((double)change)}),
         layout.priceX,
         layout.priceY,
         changeColor,
         false
      );
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
         ChartPainter.area(graphics, chartLeft, chartTop, chartWidth, chartHeight, snapshot.history(), offset, per, ChartPainter.tint(changeColor, 76));
         ChartPainter.grid(graphics, chartLeft, chartTop, chartWidth, chartHeight, 4, palette.grid());
         int startY = ChartPainter.projectY(snapshot.priceAt(this.index, 0), range[0], range[1], chartTop, chartHeight);
         ChartPainter.dashedLine(graphics, chartLeft, startY, chartWidth, palette.lineSoft());
         long held = this.index < snapshot.holdings().length ? snapshot.holdings()[this.index] : 0L;
         double avgCost = this.index < snapshot.costs().length ? snapshot.costs()[this.index] : 0.0;
         if (held > 0L && avgCost > 0.0 && range[1] > range[0]) {
            int lineY = ChartPainter.projectY((float)avgCost, range[0], range[1], chartTop, chartHeight);
            graphics.fill(chartLeft, lineY, chartLeft + chartWidth, lineY + 1, palette.gold());
         }

         ChartPainter.moves(
            graphics, chartLeft, chartTop, chartWidth, chartHeight, snapshot.history(), offset, per, palette.up(), palette.down(), changeColor, 3
         );
         int lastY = ChartPainter.projectY(snapshot.priceAt(this.index, per - 1), range[0], range[1], chartTop, chartHeight);
         ChartPainter.dot(graphics, chartLeft + chartWidth - 4, lastY, 3, palette.dot());
         ChartPainter.dot(graphics, chartLeft + chartWidth - 4, lastY, 2, changeColor);
         String high = MarketFormat.emc((double)range[1]);
         String low = MarketFormat.emc((double)range[0]);
         graphics.drawString(
            this.font, Component.translatable("gui.shared_stock_exchange.chart_high", new Object[]{high}), chartLeft + 4, chartTop + 4, palette.high(), false
         );
         graphics.drawString(
            this.font,
            Component.translatable("gui.shared_stock_exchange.chart_low", new Object[]{low}),
            chartLeft + 4,
            chartTop + chartHeight - 12,
            palette.low(),
            false
         );
         int legendX = chartLeft + chartWidth - 46;
         int legendY = chartTop + chartHeight - 12;
         graphics.drawString(this.font, "▲", legendX, legendY, palette.up(), false);
         graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_legend_up"), legendX + 9, legendY, palette.up(), false);
         graphics.drawString(this.font, "▼", legendX + 21, legendY, palette.down(), false);
         graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.chart_legend_down"), legendX + 30, legendY, palette.down(), false);
         Component badge = change >= 0.0F
            ? Component.translatable("gui.shared_stock_exchange.chart_up", new Object[]{MarketFormat.percent((double)change)})
            : Component.translatable("gui.shared_stock_exchange.chart_down", new Object[]{MarketFormat.percent((double)change)});
         int badgeWidth = this.font.width(badge);
         int badgeX = chartLeft + chartWidth - badgeWidth - 8;
         graphics.fill(badgeX - 3, chartTop + 3, chartLeft + chartWidth - 2, chartTop + 14, ChartPainter.tint(changeColor, 136));
         graphics.drawString(this.font, badge, badgeX, chartTop + 5, changeColor, false);
      } else {
         graphics.drawString(
            this.font, Component.translatable("gui.shared_stock_exchange.chart_wait"), chartLeft + 8, chartTop + chartHeight / 2 - 4, palette.dim(), false
         );
      }

      graphics.renderOutline(chartLeft, chartTop, chartWidth, chartHeight, changeColor);
      int arrowMiddleY = chartTop + chartHeight / 2;
      if (hoverArrow < 0) {
         graphics.fill(chartLeft, arrowMiddleY - 13, chartLeft + 16 - 2, arrowMiddleY + 13, palette.arrowBand());
         drawArrow(graphics, chartLeft + 5, arrowMiddleY, false, true);
      } else if (hoverArrow > 0) {
         graphics.fill(chartLeft + chartWidth - 16 + 2, arrowMiddleY - 13, chartLeft + chartWidth, arrowMiddleY + 13, palette.arrowBand());
         drawArrow(graphics, chartLeft + chartWidth - 9, arrowMiddleY, true, true);
      } else {
         drawArrow(graphics, chartLeft + 5, arrowMiddleY, false, false);
         drawArrow(graphics, chartLeft + chartWidth - 9, arrowMiddleY, true, false);
      }

      if (snapshot != null) {
         long held = this.index < snapshot.holdings().length ? snapshot.holdings()[this.index] : 0L;
         double avgCost = this.index < snapshot.costs().length ? snapshot.costs()[this.index] : 0.0;
         long total = Math.max(1L, definition.totalShares());
         double dividend = snapshot.dividendOf(this.index);
         double pnl = held > 0L ? snapshot.proceedsOf(this.index, held) - avgCost * (double)held : 0.0;
         String share = MarketFormat.share((double)held / (double)total);
         int infoWidth = layout.width - layout.infoX * 2;
         this.drawClipped(
            graphics,
            Component.translatable(
               "gui.shared_stock_exchange.chart_range",
               new Object[]{
                  MarketFormat.emc((double)range[1]),
                  MarketFormat.emc((double)range[0]),
                  MarketFormat.emc((double)snapshot.priceAt(this.index, 0)),
                  MarketFormat.percent((double)change)
               }
            ),
            layout.infoX,
            layout.rangeY,
            changeColor,
            infoWidth
         );
         this.drawClipped(
            graphics,
            layout.compact
               ? Component.translatable(
                  "gui.shared_stock_exchange.detail_price_short", new Object[]{MarketFormat.emc(price), MarketFormat.emc(snapshot.anchorOf(this.index))}
               )
               : Component.translatable(
                  "gui.shared_stock_exchange.detail_price",
                  new Object[]{
                     MarketFormat.emc(price), MarketFormat.emc(snapshot.anchorOf(this.index)), MarketFormat.emc(price * (double)definition.totalShares())
                  }
               ),
            layout.infoX,
            layout.priceLineY,
            palette.text(),
            infoWidth
         );
         if (layout.compact) {
            this.drawClipped(
               graphics,
               Component.translatable("gui.shared_stock_exchange.detail_mine_short", new Object[]{held, MarketFormat.emc(avgCost)}),
               layout.infoX,
               layout.mineY,
               held > 0L ? palette.text() : palette.dim(),
               infoWidth
            );
            this.drawClipped(
               graphics,
               Component.translatable("gui.shared_stock_exchange.detail_pnl_short", new Object[]{MarketFormat.emc(pnl), share}),
               layout.infoX,
               layout.pnlY,
               held > 0L ? (pnl >= 0.0 ? palette.up() : palette.down()) : palette.dim(),
               infoWidth
            );
         } else {
            this.drawClipped(
               graphics,
               Component.translatable("gui.shared_stock_exchange.detail_mine", new Object[]{held, MarketFormat.emc(avgCost), MarketFormat.emc(pnl), share}),
               layout.infoX,
               layout.mineY,
               held > 0L ? (pnl >= 0.0 ? palette.up() : palette.down()) : palette.dim(),
               infoWidth
            );
         }

         String topName = snapshot.topNameOf(this.index);
         Component top = topName.isEmpty()
            ? Component.translatable("gui.shared_stock_exchange.tip_no_top")
            : Component.translatable(
               "gui.shared_stock_exchange.detail_top", new Object[]{topName, MarketFormat.share((double)snapshot.topSharesOf(this.index) / (double)total)}
            );
         this.drawClipped(
            graphics,
            Component.translatable("gui.shared_stock_exchange.detail_dividend", new Object[]{MarketFormat.emc(dividend), top}),
            layout.infoX,
            layout.dividendY,
            palette.dim(),
            infoWidth
         );
         if (layout.marginY > 0) {
            double positionValue = snapshot.positionValue();
            double equity = snapshot.equity();
            double debt = snapshot.debt();
            boolean risk = snapshot.marginRisk();
            if (held <= 0L && !(debt > 0.0)) {
               graphics.drawString(
                  this.font,
                  Component.translatable("gui.shared_stock_exchange.detail_drift", new Object[]{snapshot.driftSeconds()}),
                  layout.infoX,
                  layout.marginY,
                  palette.dim(),
                  false
               );
            } else {
               Component marginLine;
               int marginColor;
               if (!snapshot.liquidationEnabled()) {
                  marginLine = Component.translatable(
                     "gui.shared_stock_exchange.detail_margin_off", new Object[]{MarketFormat.emc(equity), MarketFormat.emc(debt)}
                  );
                  marginColor = debt > 0.0 ? palette.warn() : palette.dim();
               } else if (risk) {
                  marginLine = Component.translatable(
                     "gui.shared_stock_exchange.detail_margin_risk",
                     new Object[]{MarketFormat.emc(equity), MarketFormat.emc(MarketMath.maintenanceLine(positionValue))}
                  );
                  marginColor = palette.down();
               } else {
                  marginLine = Component.translatable(
                     "gui.shared_stock_exchange.detail_margin",
                     new Object[]{MarketFormat.emc(equity), MarketFormat.emc(debt), MarketFormat.emc(MarketMath.maintenanceLine(positionValue))}
                  );
                  marginColor = debt > 0.0 ? palette.warn() : palette.dim();
               }

               this.drawClipped(graphics, marginLine, layout.infoX, layout.marginY, marginColor, infoWidth);
            }
         }

         int shares = TradeQuantity.get();
         TradePreview.BuyPlan plan = TradePreview.planBuy(snapshot, this.index, shares);
         this.drawClipped(graphics, TradePreview.describeBuy(plan), layout.infoX, layout.buyY, TradePreview.buyColor(plan), infoWidth);
         this.drawClipped(
            graphics,
            TradePreview.describeSell(snapshot, this.index, shares),
            layout.infoX,
            layout.sellY,
            held <= 0L ? palette.dim() : ((long)shares > held ? palette.gold() : palette.up()),
            infoWidth
         );
      }

      graphics.drawString(this.font, Component.translatable("gui.shared_stock_exchange.qty"), layout.qtyLabelX, layout.qtyLabelY, palette.dim(), false);
      this.popPanel(graphics);
   }

   private static StockDetailScreen.Layout pickLayout(int screenWidth, int screenHeight) {
      StockDetailScreen.Layout wide = wideLayout();
      StockDetailScreen.Layout tight = tightLayout();
      float wideScale = fitScale(screenWidth, screenHeight, wide.width, wide.height);
      float tightScale = fitScale(screenWidth, screenHeight, tight.width, tight.height);
      return tightScale > wideScale + 0.02F ? tight : wide;
   }

   private static StockDetailScreen.Layout wideLayout() {
      StockDetailScreen.Layout layout = new StockDetailScreen.Layout();
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

   private static StockDetailScreen.Layout tightLayout() {
      StockDetailScreen.Layout layout = new StockDetailScreen.Layout();
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
