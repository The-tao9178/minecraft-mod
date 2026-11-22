package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.network.chat.Component;

public final class TradePreview {
   public static final int STATUS_OK = 0;
   public static final int STATUS_DEBT = 1;
   public static final int STATUS_NO_CASH = 2;
   public static final int STATUS_LIQUIDATE = 3;
   public static final int STATUS_NO_LIQUIDITY = 4;

   private TradePreview() {
   }

   public static TradePreview.BuyPlan planBuy(MarketSnapshotPayload snapshot, int index, int requested) {
      long buyable = snapshot.maxBuyable(index);
      long balance = snapshot.balance();
      long shares = Math.min((long)Math.max(requested, 0), Math.max(buyable, 0L));
      if (shares <= 0L) {
         return new TradePreview.BuyPlan(0L, (long)requested, buyable, 0L, 0L, 0L, balance, 4, false);
      } else {
         double exact = snapshot.costOf(index, shares);
         if (Double.isFinite(exact) && !(exact > 9.0E15)) {
            long gross = Math.max(1L, (long)Math.ceil(exact));
            long fee = MarketMath.feeOf(gross);
            long total = gross + fee;
            long after = balance - total;
            int status;
            if (!snapshot.debtAllowed()) {
               status = (double)total <= (double)Math.max(balance, 0L) ? 0 : 2;
            } else if (snapshot.liquidationEnabled() && !MarketMath.survivesMaintenance((double)balance, snapshot.positionValue(), (double)gross, (double)fee)) {
               status = 3;
            } else {
               status = after < 0L ? 1 : 0;
            }

            return new TradePreview.BuyPlan(shares, (long)requested, buyable, gross, fee, total, after, status, shares < (long)requested);
         } else {
            return new TradePreview.BuyPlan(0L, (long)requested, buyable, 0L, 0L, 0L, balance, 4, false);
         }
      }
   }

   public static Component describeBuy(TradePreview.BuyPlan plan) {
      switch (plan.status()) {
         case 1:
            return Component.translatable(
               "gui.shared_stock_exchange.preview_buy_debt",
               new Object[]{plan.shares(), MarketFormat.emc((double)plan.total()), MarketFormat.emc((double)plan.after())}
            );
         case 2:
            return Component.translatable(
               "gui.shared_stock_exchange.preview_buy_no_cash",
               new Object[]{MarketFormat.emc((double)plan.total()), MarketFormat.emc((double)(plan.after() + plan.total()))}
            );
         case 3:
            return Component.translatable("gui.shared_stock_exchange.preview_buy_liquidate", new Object[]{MarketFormat.emc((double)plan.total())});
         case 4:
            return Component.translatable("gui.shared_stock_exchange.preview_buy_max", new Object[]{Math.max(plan.buyable(), 0L)});
         default:
            return plan.clamped()
               ? Component.translatable(
                  "gui.shared_stock_exchange.preview_buy_all",
                  new Object[]{plan.shares(), MarketFormat.emc((double)plan.total()), MarketFormat.emc((double)plan.after())}
               )
               : Component.translatable(
                  "gui.shared_stock_exchange.preview_buy",
                  new Object[]{plan.shares(), MarketFormat.emc((double)plan.total()), MarketFormat.emc((double)plan.after())}
               );
      }
   }

   public static int buyColor(TradePreview.BuyPlan plan) {
      if (!plan.affordable()) {
         return UiTheme.palette().down();
      } else {
         return plan.status() == 1 ? UiTheme.palette().warn() : UiTheme.palette().up();
      }
   }

   public static Component describeSell(MarketSnapshotPayload snapshot, int index, int requested) {
      long held = index >= 0 && index < snapshot.holdings().length ? snapshot.holdings()[index] : 0L;
      if (held <= 0L) {
         return Component.translatable("gui.shared_stock_exchange.preview_no_shares");
      } else {
         long effective = Math.min((long)Math.max(requested, 0), held);
         long gross = Math.max(0L, (long)Math.floor(snapshot.proceedsOf(index, effective)));
         long fee = MarketMath.feeOf(gross);
         long received = Math.max(gross - fee, 0L);
         return effective < (long)requested
            ? Component.translatable(
               "gui.shared_stock_exchange.preview_sell_all",
               new Object[]{held, MarketFormat.emc((double)received), MarketFormat.emc((double)(snapshot.balance() + received))}
            )
            : Component.translatable(
               "gui.shared_stock_exchange.preview_sell",
               new Object[]{effective, MarketFormat.emc((double)received), MarketFormat.emc((double)(snapshot.balance() + received))}
            );
      }
   }

   public static record BuyPlan(long shares, long requested, long buyable, long gross, long fee, long total, long after, int status, boolean clamped) {
      public boolean affordable() {
         return this.status == 0 || this.status == 1;
      }
   }
}
