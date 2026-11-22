package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.client.mcphone.PhoneSwitch;
import cn.blockforge.generated.sharedstockexchange.market.MarketCache;
import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import cn.blockforge.generated.sharedstockexchange.market.Stocks;
import cn.blockforge.generated.sharedstockexchange.net.MarketActionPayload;
import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class MarketScreen extends UiPanelScreen {
   private static final int[] QUICK_AMOUNTS = new int[]{1, 10, 100, 1000};
   private MarketScreen.Layout layout = wideLayout();
   private EditBox quantityBox;
   private MarketSnapshotPayload textFor;
   private String textLanguage = "";
   private boolean textCompact;
   private String[] nameText = new String[0];
   private String[] priceText = new String[0];
   private String[] changeText = new String[0];
   private String[] capText = new String[0];
   private String[] heldText = new String[0];
   private String[] pnlText = new String[0];
   private Component statusLine;
   private Component modeLine;
   private Component balanceLine;
   private int modeColor;
   private String footerLine = "";
   private double portfolioValue;
   private String labelQty = "";
   private String labelName = "";
   private String labelPrice = "";
   private String labelChange = "";
   private String labelCap = "";
   private String labelHeld = "";
   private String labelPnl = "";
   private String labelSettings = "";
   private String labelPreview = "";
   private static final int CHIP_HEIGHT = 12;

   public MarketScreen() {
      super(Component.translatable("gui.shared_stock_exchange.title"));
   }

   public static void openMarket() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) {
         minecraft.setScreen(new MarketScreen());
      }
   }

   protected void init() {
      this.layout = pickLayout(this.width, this.height);
      this.applyDesignSize(this.layout.width, this.layout.height);
      this.cacheLabels();
      MarketNetworking.sendToServer(new MarketActionPayload(0, "", 0));
      MarketScreen.Layout layout = this.layout;
      this.quantityBox = new EditBox(this.font, layout.qtyBoxX, layout.qtyBoxY, layout.qtyBoxWidth, 16, Component.translatable("gui.shared_stock_exchange.qty"));
      this.quantityBox.setMaxLength(6);
      this.quantityBox.setFilter(text -> {
         for (int ix = 0; ix < text.length(); ix++) {
            char c = text.charAt(ix);
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

      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.shared_stock_exchange.clear_all"),
               button -> MarketNetworking.sendToServer(new MarketActionPayload(4, "", 0))
            )
            .bounds(layout.clearX, layout.clearY, layout.clearWidth, layout.clearHeight)
            .build()
      );

      for (int i = 0; i < Stocks.ALL.size(); i++) {
         int index = i;
         int rowY = layout.rowStart + i * layout.rowHeight;
         int buttonY = rowY + 2;
         int buttonHeight = layout.rowHeight - 4;
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.shared_stock_exchange.buy"), button -> this.trade(index, true))
               .bounds(layout.buyX, buttonY, layout.buyWidth, buttonHeight)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.shared_stock_exchange.sell"), button -> this.trade(index, false))
               .bounds(layout.buyX + layout.buyWidth + 4, buttonY, layout.buyWidth, buttonHeight)
               .build()
         );
      }

      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.shared_stock_exchange.claim"),
               button -> MarketNetworking.sendToServer(new MarketActionPayload(3, "", 0))
            )
            .bounds(layout.claimX, layout.claimY, layout.claimWidth, layout.claimHeight)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> this.onClose())
            .bounds(layout.doneX, layout.doneY, layout.doneWidth, layout.doneHeight)
            .build()
      );
      // 从手机里的炒股 App 切过来的，大屏底部给一个「手机界面」按钮切回去。
      // 没装 MCphone、或者这个会话里没开过手机里的炒股 App，这个按钮根本不出现。
      if (PhoneSwitch.canReturnToPhone()) {
         this.addRenderableWidget(
            Button.builder(
                  Component.translatable("gui.shared_stock_exchange.phone_chip"),
                  button -> PhoneSwitch.returnToPhone()
               )
               .bounds(layout.phoneX, layout.phoneY, layout.phoneWidth, layout.phoneHeight)
               .build()
         );
      }
   }

   private static String changeLabel(float change) {
      String arrow = change > 5.0E-4F ? "▲ " : (change < -5.0E-4F ? "▼ " : "");
      return arrow + MarketFormat.percentShort((double)change);
   }

   private void trade(int index, boolean buying) {
      if (index >= 0 && index < Stocks.ALL.size()) {
         StockDefinition definition = Stocks.ALL.get(index);
         MarketNetworking.sendToServer(new MarketActionPayload(buying ? 1 : 2, definition.id(), TradeQuantity.get()));
      }
   }

   private void openDetail(int index) {
      Minecraft.getInstance().setScreen(new StockDetailScreen(index));
   }

   private static ItemStack icon(StockDefinition definition) {
      return StockIcons.of(definition);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      MarketScreen.Layout layout = this.layout;
      UiTheme.Palette palette = UiTheme.palette();
      int panelMouseX = this.panelMouseX((double)mouseX);
      int panelMouseY = this.panelMouseY((double)mouseY);
      graphics.fill(0, 0, this.width, this.height, palette.shade());
      this.pushPanel(graphics);
      graphics.fill(0, 0, layout.width, layout.height, palette.panel());
      graphics.renderOutline(0, 0, layout.width, layout.height, palette.border());
      this.renderWidgets(graphics, panelMouseX, panelMouseY, partialTick);
      MarketSnapshotPayload snapshot = MarketCache.snapshot();
      this.refreshText(snapshot);
      graphics.drawString(this.font, this.title, layout.titleX, layout.titleY, palette.title(), false);
      this.drawChip(graphics, palette, layout.settingsX, layout.settingsY, layout.settingsWidth, this.labelSettings);
      boolean chipHovered = panelMouseX >= layout.settingsX
         && panelMouseX <= layout.settingsX + layout.settingsWidth
         && panelMouseY >= layout.settingsY
         && panelMouseY <= layout.settingsY + 12;
      if (layout.statusX > 0 && this.statusLine != null) {
         this.drawClipped(graphics, this.statusLine, layout.statusX, layout.statusY, palette.dim(), layout.statusWidth);
      }

      this.drawClipped(graphics, this.modeLine, layout.modeX, layout.modeY, this.modeColor, layout.modeMaxWidth);
      graphics.drawString(this.font, this.labelQty, layout.qtyLabelX, layout.qtyLabelY, palette.dim(), false);
      graphics.drawString(this.font, this.labelName, layout.colName, layout.headerY, palette.dim(), false);
      graphics.drawString(this.font, this.labelPrice, layout.colPrice, layout.headerY, palette.dim(), false);
      graphics.drawString(this.font, this.labelChange, layout.colChange, layout.headerY, palette.dim(), false);
      if (layout.colCap > 0) {
         graphics.drawString(this.font, this.labelCap, layout.colCap, layout.headerY, palette.dim(), false);
      }

      graphics.drawString(this.font, this.labelHeld, layout.colHeld, layout.headerY, palette.dim(), false);
      graphics.drawString(this.font, this.labelPnl, layout.colPnl, layout.headerY, palette.dim(), false);
      int hoveredRow = -1;

      for (int i = 0; i < Stocks.ALL.size(); i++) {
         StockDefinition definition = Stocks.ALL.get(i);
         int rowY = layout.rowStart + i * layout.rowHeight;
         boolean hovered = panelMouseX >= 6 && panelMouseX <= layout.buyX - 4 && panelMouseY >= rowY && panelMouseY <= rowY + layout.rowHeight - 2;
         if (hovered) {
            hoveredRow = i;
         }

         graphics.fill(6, rowY, layout.buyX - 4, rowY + layout.rowHeight - 2, hovered ? palette.rowHover() : palette.rowNormal());
         graphics.renderItem(icon(definition), 6, rowY + (layout.rowHeight - 16) / 2);
         int textY = rowY + (layout.rowHeight - 8) / 2;
         graphics.drawString(this.font, this.nameText[i], layout.colName, textY, palette.text(), false);
         if (snapshot != null && i < snapshot.prices().length) {
            long held = i < snapshot.holdings().length ? snapshot.holdings()[i] : 0L;
            float change = snapshot.changeOf(i);
            int changeColor = change >= 0.0F ? palette.up() : palette.down();
            graphics.fill(2, rowY, 4, rowY + layout.rowHeight - 2, changeColor);
            graphics.drawString(this.font, this.priceText[i], layout.colPrice, textY, changeColor, false);
            graphics.drawString(this.font, this.changeText[i], layout.colChange, textY, changeColor, false);
            if (layout.colCap > 0) {
               graphics.drawString(this.font, this.capText[i], layout.colCap, textY, palette.mutedGold(), false);
            }

            graphics.drawString(this.font, this.heldText[i], layout.colHeld, textY, held > 0L ? palette.text() : palette.dim(), false);
            int pnlColor = "-".equals(this.pnlText[i]) ? palette.dim() : (this.pnlText[i].startsWith("-") ? palette.down() : palette.up());
            graphics.drawString(this.font, this.pnlText[i], layout.colPnl, textY, pnlColor, false);
            if (layout.colSpark > 0) {
               int sparkOffset = i * snapshot.samplesPerStock();
               int sparkPoints = snapshot.samplesPerStock();
               int sparkTop = rowY + 4;
               int sparkHeight = layout.rowHeight - 8;
               ChartPainter.area(
                  graphics,
                  layout.colSpark,
                  sparkTop,
                  layout.sparkWidth,
                  sparkHeight,
                  snapshot.history(),
                  sparkOffset,
                  sparkPoints,
                  ChartPainter.tint(changeColor, 82)
               );
               ChartPainter.moves(
                  graphics,
                  layout.colSpark,
                  sparkTop,
                  layout.sparkWidth,
                  sparkHeight,
                  snapshot.history(),
                  sparkOffset,
                  sparkPoints,
                  palette.up(),
                  palette.down(),
                  changeColor,
                  1
               );
               float[] sparkRange = ChartPainter.range(snapshot.history(), sparkOffset, sparkPoints);
               int lastY = ChartPainter.projectY(snapshot.priceAt(i, sparkPoints - 1), sparkRange[0], sparkRange[1], sparkTop, sparkHeight);
               ChartPainter.dot(graphics, layout.colSpark + layout.sparkWidth - 2, lastY, 1, palette.dot());
            }
         } else {
            graphics.drawString(this.font, "-", layout.colPrice, textY, palette.dim(), false);
         }
      }

      this.drawClipped(graphics, this.balanceLine, layout.titleX, layout.balanceY, palette.gold(), layout.width - layout.titleX * 2);
      int previewColor = palette.text();
      String preview = this.labelPreview;
      if (snapshot != null && hoveredRow >= 0) {
         TradePreview.BuyPlan plan = TradePreview.planBuy(snapshot, hoveredRow, TradeQuantity.get());
         preview = TradePreview.describeBuy(plan).getString();
         previewColor = TradePreview.buyColor(plan);
      }

      this.drawClipped(graphics, preview, layout.titleX, layout.previewY, previewColor, layout.width - layout.titleX * 2);
      if (layout.footerTextY > 0) {
         graphics.drawString(
            this.font, this.font.plainSubstrByWidth(this.footerLine, layout.width - 16), layout.titleX, layout.footerTextY, palette.dim(), false
         );
      }

      this.popPanel(graphics);
      boolean footerHovered = layout.footerTextY <= 0
         && panelMouseX >= 6
         && panelMouseX <= layout.width - 6
         && panelMouseY >= layout.balanceY - 2
         && panelMouseY <= layout.balanceY + 11;
      if (chipHovered) {
         graphics.renderComponentTooltip(
            this.font,
            List.of(Component.translatable("gui.shared_stock_exchange.settings_tip"), Component.translatable("gui.shared_stock_exchange.theme_tip")),
            mouseX,
            mouseY
         );
      } else if (snapshot != null && hoveredRow >= 0) {
         graphics.renderComponentTooltip(this.font, this.tooltipFor(snapshot, hoveredRow), mouseX, mouseY);
      } else if (snapshot != null && footerHovered) {
         graphics.renderComponentTooltip(this.font, List.of(Component.literal(this.footerLine)), mouseX, mouseY);
      }
   }

   private void refreshText(MarketSnapshotPayload snapshot) {
      String language = Minecraft.getInstance().getLanguageManager().getSelected();
      boolean compact = this.layout.compact;
      if (snapshot != this.textFor || !language.equals(this.textLanguage) || compact != this.textCompact) {
         this.textFor = snapshot;
         this.textLanguage = language;
         this.textCompact = compact;
         int count = Stocks.ALL.size();
         if (this.nameText.length != count) {
            this.nameText = new String[count];
            this.priceText = new String[count];
            this.changeText = new String[count];
            this.capText = new String[count];
            this.heldText = new String[count];
            this.pnlText = new String[count];
         }

         for (int i = 0; i < count; i++) {
            this.nameText[i] = Component.translatable(Stocks.nameKey(Stocks.ALL.get(i).id())).getString();
         }

         UiTheme.Palette palette = UiTheme.palette();
         this.portfolioValue = 0.0;
         if (snapshot == null) {
            Arrays.fill(this.priceText, "-");
            Arrays.fill(this.changeText, "-");
            Arrays.fill(this.capText, "-");
            Arrays.fill(this.heldText, "-");
            Arrays.fill(this.pnlText, "-");
            this.statusLine = null;
            this.modeLine = Component.translatable(compact ? "gui.shared_stock_exchange.mode_test_short" : "gui.shared_stock_exchange.mode_test");
            this.modeColor = palette.warn();
            this.balanceLine = Component.translatable("gui.shared_stock_exchange.balance_test", new Object[]{"-", "-", "-"});
            this.footerLine = Component.translatable("gui.shared_stock_exchange.footer_test", new Object[]{"-", 30}).getString();
         } else {
            for (int i = 0; i < count; i++) {
               if (i >= snapshot.prices().length) {
                  this.priceText[i] = "-";
                  this.changeText[i] = "-";
                  this.capText[i] = "-";
                  this.heldText[i] = "-";
                  this.pnlText[i] = "-";
               } else {
                  double price = snapshot.prices()[i];
                  long held = i < snapshot.holdings().length ? snapshot.holdings()[i] : 0L;
                  double avgCost = i < snapshot.costs().length ? snapshot.costs()[i] : 0.0;
                  this.priceText[i] = MarketFormat.emc(price);
                  this.changeText[i] = changeLabel(snapshot.changeOf(i));
                  this.capText[i] = MarketFormat.emc(price * (double)Stocks.ALL.get(i).totalShares());
                  this.heldText[i] = Long.toString(held);
                  if (held > 0L) {
                     double value = snapshot.proceedsOf(i, held);
                     this.pnlText[i] = MarketFormat.emc(value - avgCost * (double)held);
                     this.portfolioValue += value;
                  } else {
                     this.pnlText[i] = "-";
                  }
               }
            }

            this.statusLine = Component.translatable(
               "gui.shared_stock_exchange.switches_chip", new Object[]{onOff(snapshot.debtAllowed()), onOff(snapshot.liquidationEnabled())}
            );
            int cashMode = snapshot.cashMode();
            String modeKey = cashMode == 1
               ? (compact ? "gui.shared_stock_exchange.mode_emc_short" : "gui.shared_stock_exchange.mode_emc")
               : (
                  cashMode == 2
                     ? (compact ? "gui.shared_stock_exchange.mode_broken_short" : "gui.shared_stock_exchange.mode_broken")
                     : (compact ? "gui.shared_stock_exchange.mode_test_short" : "gui.shared_stock_exchange.mode_test")
               );
            this.modeLine = Component.translatable(modeKey);
            this.modeColor = cashMode == 1 ? palette.up() : (cashMode == 2 ? palette.down() : palette.warn());
            this.balanceLine = Component.translatable(
               snapshot.projectE() ? "gui.shared_stock_exchange.balance_emc" : "gui.shared_stock_exchange.balance_test",
               new Object[]{
                  MarketFormat.emc((double)snapshot.balance()),
                  MarketFormat.emc(this.portfolioValue),
                  MarketFormat.emc((double)snapshot.balance() + this.portfolioValue)
               }
            );
            String footerKey = snapshot.projectE()
               ? "gui.shared_stock_exchange.footer_emc"
               : (snapshot.projectEBroken() ? "gui.shared_stock_exchange.footer_broken" : "gui.shared_stock_exchange.footer_test");
            this.footerLine = Component.translatable(footerKey, new Object[]{MarketFormat.emc((double)snapshot.pendingDividend()), snapshot.driftSeconds()})
               .getString();
         }
      }
   }

   private List<Component> tooltipFor(MarketSnapshotPayload snapshot, int index) {
      StockDefinition definition = Stocks.ALL.get(index);
      int shares = TradeQuantity.get();
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable(Stocks.nameKey(definition.id())).withStyle(ChatFormatting.WHITE));
      if (index >= snapshot.prices().length) {
         return lines;
      } else {
         double price = snapshot.prices()[index];
         long total = Math.max(1L, definition.totalShares());
         lines.add(
            Component.translatable("gui.shared_stock_exchange.tip_price", new Object[]{MarketFormat.emc(price), MarketFormat.emc(snapshot.anchorOf(index))})
               .withStyle(ChatFormatting.GOLD)
         );
         UiTheme.Palette palette = UiTheme.palette();
         lines.add(
            Component.translatable("gui.shared_stock_exchange.tip_change", new Object[]{MarketFormat.percent((double)snapshot.changeOf(index))})
               .withStyle(UiColors.chatColor(snapshot.changeOf(index) >= 0.0F ? palette.up() : palette.down()))
         );
         lines.add(
            Component.translatable("gui.shared_stock_exchange.tip_cap", new Object[]{MarketFormat.emc(price * (double)definition.totalShares())})
               .withStyle(ChatFormatting.GRAY)
         );
         String topName = snapshot.topNameOf(index);
         if (topName.isEmpty()) {
            lines.add(Component.translatable("gui.shared_stock_exchange.tip_no_top").withStyle(ChatFormatting.DARK_GRAY));
         } else {
            lines.add(
               Component.translatable(
                     "gui.shared_stock_exchange.tip_top", new Object[]{topName, MarketFormat.share((double)snapshot.topSharesOf(index) / (double)total)}
                  )
                  .withStyle(ChatFormatting.AQUA)
            );
         }

         lines.add(
            Component.translatable("gui.shared_stock_exchange.tip_dividend", new Object[]{MarketFormat.emc(snapshot.dividendOf(index))})
               .withStyle(ChatFormatting.GREEN)
         );
         long held = index < snapshot.holdings().length ? snapshot.holdings()[index] : 0L;
         if (held > 0L) {
            lines.add(
               Component.translatable("gui.shared_stock_exchange.tip_mine", new Object[]{held, MarketFormat.share((double)held / (double)total)})
                  .withStyle(ChatFormatting.WHITE)
            );
         }

         lines.add(
            Component.translatable("gui.shared_stock_exchange.tip_switches", new Object[]{onOff(snapshot.debtAllowed()), onOff(snapshot.liquidationEnabled())})
               .withStyle(ChatFormatting.DARK_GRAY)
         );
         double debt = snapshot.debt();
         if (debt > 0.0) {
            lines.add(
               Component.translatable(
                     snapshot.projectE() ? "gui.shared_stock_exchange.tip_debt" : "gui.shared_stock_exchange.tip_debt_test",
                     new Object[]{MarketFormat.emc(debt)}
                  )
                  .withStyle(ChatFormatting.RED)
            );
            lines.add(Component.translatable("gui.shared_stock_exchange.tip_refund").withStyle(ChatFormatting.GRAY));
         }

         TradePreview.BuyPlan plan = TradePreview.planBuy(snapshot, index, shares);
         lines.add(TradePreview.describeBuy(plan).copy().withStyle(plan.affordable() ? ChatFormatting.GREEN : ChatFormatting.RED));
         lines.add(TradePreview.describeSell(snapshot, index, shares).copy().withStyle(ChatFormatting.YELLOW));
         lines.add(Component.translatable("gui.shared_stock_exchange.tip_click_chart").withStyle(ChatFormatting.DARK_GRAY));
         return lines;
      }
   }

   @Override
   protected boolean onPanelClicked(double panelX, double panelY, int button) {
      if (button != 0) {
         return false;
      } else if (panelX >= (double)this.layout.settingsX
         && panelX <= (double)(this.layout.settingsX + this.layout.settingsWidth)
         && panelY >= (double)this.layout.settingsY
         && panelY <= (double)(this.layout.settingsY + 12)) {
         Minecraft.getInstance().setScreen(new SettingsScreen());
         return true;
      } else {
         int row = this.rowAt(panelX, panelY);
         if (row >= 0) {
            this.openDetail(row);
            return true;
         } else {
            return false;
         }
      }
   }

   private static Component onOff(boolean enabled) {
      return Component.translatable(enabled ? "gui.shared_stock_exchange.state_on" : "gui.shared_stock_exchange.state_off");
   }

   private void cacheLabels() {
      this.labelQty = Component.translatable("gui.shared_stock_exchange.qty").getString();
      this.labelName = Component.translatable("gui.shared_stock_exchange.col_name").getString();
      this.labelPrice = Component.translatable("gui.shared_stock_exchange.col_price").getString();
      this.labelChange = Component.translatable("gui.shared_stock_exchange.col_change").getString();
      this.labelCap = Component.translatable("gui.shared_stock_exchange.col_cap").getString();
      this.labelHeld = Component.translatable("gui.shared_stock_exchange.col_held").getString();
      this.labelPnl = Component.translatable("gui.shared_stock_exchange.col_pnl").getString();
      this.labelSettings = Component.translatable("gui.shared_stock_exchange.settings_chip").getString();
      this.labelPreview = Component.translatable("gui.shared_stock_exchange.preview_hint").getString();
   }

   private void drawChip(GuiGraphics graphics, UiTheme.Palette palette, int x, int y, int maxWidth, String text) {
      int textWidth = this.font.width(text);
      int width = Math.min(maxWidth, textWidth + 8);
      graphics.fill(x, y, x + width, y + 12, palette.chip());
      graphics.renderOutline(x, y, width, 12, palette.border());
      int inner = Math.max(1, width - 6);
      float scale = textWidth > inner ? (float)inner / (float)textWidth : 1.0F;
      int drawn = Math.round((float)textWidth * scale);
      int textX = x + (width - drawn) / 2;
      if (scale >= 1.0F) {
         graphics.drawString(this.font, text, textX, y + 2, palette.chipText(), false);
      } else {
         graphics.pose().pushPose();
         graphics.pose().translate((float)textX, (float)(y + 2), 0.0F);
         graphics.pose().scale(scale, scale, 1.0F);
         graphics.drawString(this.font, text, 0, 0, palette.chipText(), false);
         graphics.pose().popPose();
      }
   }

   private int rowAt(double panelX, double panelY) {
      if (!(panelX < 6.0) && !(panelX > (double)this.layout.rowRight)) {
         for (int i = 0; i < Stocks.ALL.size(); i++) {
            int rowY = this.layout.rowStart + i * this.layout.rowHeight;
            if (panelY >= (double)rowY && panelY <= (double)(rowY + this.layout.rowHeight - 2)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static MarketScreen.Layout pickLayout(int screenWidth, int screenHeight) {
      MarketScreen.Layout wide = wideLayout();
      MarketScreen.Layout tight = tightLayout();
      float wideScale = fitScale(screenWidth, screenHeight, wide.width, wide.height);
      float tightScale = fitScale(screenWidth, screenHeight, tight.width, tight.height);
      return tightScale > wideScale + 0.02F ? tight : wide;
   }

   private static MarketScreen.Layout wideLayout() {
      MarketScreen.Layout layout = new MarketScreen.Layout();
      layout.compact = false;
      layout.width = 424;
      layout.height = 302;
      layout.rowHeight = 22;
      layout.rowStart = 58;
      layout.headerY = 44;
      layout.colName = 28;
      layout.colPrice = 84;
      layout.colChange = 120;
      layout.colCap = 168;
      layout.colHeld = 208;
      layout.colPnl = 240;
      layout.colSpark = 288;
      layout.sparkWidth = 56;
      layout.buyX = 356;
      layout.buyWidth = 26;
      layout.rowRight = 418;
      layout.balanceY = 236;
      layout.previewY = 248;
      layout.footerTextY = 260;
      layout.titleX = 8;
      layout.titleY = 9;
      layout.modeX = 220;
      layout.modeY = 9;
      layout.modeMaxWidth = 196;
      layout.settingsX = 96;
      layout.settingsY = 6;
      layout.settingsWidth = 44;
      layout.statusX = 228;
      layout.statusY = 24;
      layout.statusWidth = 104;
      layout.qtyLabelX = 8;
      layout.qtyLabelY = 27;
      layout.qtyBoxX = 32;
      layout.qtyBoxY = 23;
      layout.qtyBoxWidth = 48;
      layout.quickX = 86;
      layout.quickY = 23;
      layout.quickWidth = 30;
      layout.quickStep = 34;
      layout.clearX = 340;
      layout.clearY = 22;
      layout.clearWidth = 76;
      layout.clearHeight = 18;
      layout.claimX = 300;
      layout.claimY = 278;
      layout.claimWidth = 60;
      layout.claimHeight = 18;
      layout.doneX = 364;
      layout.doneY = 278;
      layout.doneWidth = 52;
      layout.doneHeight = 18;
      layout.phoneX = 236;
      layout.phoneY = 278;
      layout.phoneWidth = 60;
      layout.phoneHeight = 18;
      return layout;
   }

   private static MarketScreen.Layout tightLayout() {
      MarketScreen.Layout layout = new MarketScreen.Layout();
      layout.compact = true;
      layout.width = 300;
      layout.height = 240;
      layout.rowHeight = 18;
      layout.rowStart = 52;
      layout.headerY = 42;
      layout.colName = 24;
      layout.colPrice = 68;
      layout.colChange = 104;
      layout.colCap = -1;
      layout.colHeld = 150;
      layout.colPnl = 180;
      layout.colSpark = -1;
      layout.sparkWidth = 0;
      layout.buyX = 240;
      layout.buyWidth = 24;
      layout.rowRight = 294;
      layout.balanceY = 200;
      layout.previewY = 211;
      layout.footerTextY = -1;
      layout.titleX = 6;
      layout.titleY = 4;
      layout.modeX = 76;
      layout.modeY = 4;
      layout.modeMaxWidth = 63;
      layout.settingsX = 140;
      layout.settingsY = 2;
      layout.settingsWidth = 34;
      layout.statusX = -1;
      layout.statusY = 0;
      layout.statusWidth = 0;
      layout.qtyLabelX = 6;
      layout.qtyLabelY = 25;
      layout.qtyBoxX = 26;
      layout.qtyBoxY = 21;
      layout.qtyBoxWidth = 40;
      layout.quickX = 70;
      layout.quickY = 21;
      layout.quickWidth = 28;
      layout.quickStep = 32;
      layout.clearX = 208;
      layout.clearY = 21;
      layout.clearWidth = 86;
      layout.clearHeight = 16;
      layout.claimX = 176;
      layout.claimY = 2;
      layout.claimWidth = 56;
      layout.claimHeight = 16;
      layout.doneX = 236;
      layout.doneY = 2;
      layout.doneWidth = 56;
      layout.doneHeight = 16;
      layout.phoneX = 6;
      layout.phoneY = 222;
      layout.phoneWidth = 60;
      layout.phoneHeight = 16;
      return layout;
   }

   private static final class Layout {
      boolean compact;
      int width;
      int height;
      int rowHeight;
      int rowStart;
      int headerY;
      int colName;
      int colPrice;
      int colChange;
      int colCap;
      int colHeld;
      int colPnl;
      int colSpark;
      int sparkWidth;
      int buyX;
      int buyWidth;
      int rowRight;
      int balanceY;
      int previewY;
      int footerTextY;
      int titleX;
      int titleY;
      int modeX;
      int modeY;
      int modeMaxWidth;
      int settingsX;
      int settingsY;
      int settingsWidth;
      int statusX;
      int statusY;
      int statusWidth;
      int qtyLabelX;
      int qtyLabelY;
      int qtyBoxX;
      int qtyBoxY;
      int qtyBoxWidth;
      int quickX;
      int quickY;
      int quickWidth;
      int quickStep;
      int clearX;
      int clearY;
      int clearWidth;
      int clearHeight;
      int claimX;
      int claimY;
      int claimWidth;
      int claimHeight;
      int doneX;
      int doneY;
      int doneWidth;
      int doneHeight;
      int phoneX;
      int phoneY;
      int phoneWidth;
      int phoneHeight;
   }
}
