package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.MarketFormat;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import cn.blockforge.generated.sharedstockexchange.net.MarketSnapshotPayload;
import net.minecraft.network.chat.Component;

/**
 * 买卖预览：这笔大概花多少 / 到手多少、成交后账上还剩多少。
 *
 * 交易界面和涨跌详情页都用这里算，而且和服务器用同一套 {@link MarketMath}，
 * 界面上看到的就是服务器真正会扣/发的数字。
 *
 * 这里也体现本轮的三条规则：
 *   · 填的股数超过盘面还能卖的，按「能买的全部」算；卖出的超过持仓的，按「全部卖出」算；
 *   · 负债系统开着时可以先花成负数（成交后 EMC 是负数 = 欠债），这一行会变成橙色；
 *   · 负债系统关着时只能花手里的现金；如果这一笔买完会立刻跌破强平线，也会提前拦住并说明。
 */
public final class TradePreview {
    /** 这一笔的状态：能买、买完欠债、钱不够（负债系统关着）、买完立刻爆仓、盘面没货。 */
    public static final int STATUS_OK = 0;
    public static final int STATUS_DEBT = 1;
    public static final int STATUS_NO_CASH = 2;
    public static final int STATUS_LIQUIDATE = 3;
    public static final int STATUS_NO_LIQUIDITY = 4;

    private TradePreview() {
    }

    /** 一笔买入的账单。{@code after} 是成交后的余额，负数就是欠债。 */
    public record BuyPlan(long shares, long requested, long buyable, long gross, long fee, long total,
                          long after, int status, boolean clamped) {
        /** 这一笔能不能真的成交（服务器会放行）。 */
        public boolean affordable() {
            return this.status == STATUS_OK || this.status == STATUS_DEBT;
        }
    }

    public static BuyPlan planBuy(MarketSnapshotPayload snapshot, int index, int requested) {
        long buyable = snapshot.maxBuyable(index);
        long balance = snapshot.balance();
        long shares = Math.min(Math.max(requested, 0), Math.max(buyable, 0L));
        if (shares <= 0L) {
            return new BuyPlan(0L, requested, buyable, 0L, 0L, 0L, balance, STATUS_NO_LIQUIDITY, false);
        }
        double exact = snapshot.costOf(index, shares);
        if (!Double.isFinite(exact) || exact > 9.0E15D) {
            // 价格被推到离谱的量级：当作买不起，别让金额溢出成负数
            return new BuyPlan(0L, requested, buyable, 0L, 0L, 0L, balance, STATUS_NO_LIQUIDITY, false);
        }
        long gross = Math.max(1L, (long) Math.ceil(exact));
        long fee = MarketMath.feeOf(gross);
        long total = gross + fee;
        long after = balance - total;
        int status;
        if (!snapshot.debtAllowed()) {
            // 负债系统关着：只能花手里的现金
            status = (double) total <= (double) Math.max(balance, 0L) ? STATUS_OK : STATUS_NO_CASH;
        } else if (snapshot.liquidationEnabled()
                && !MarketMath.survivesMaintenance(balance, snapshot.positionValue(), gross, fee)) {
            // 负债系统开着，但这一笔买完就会跌破强平线：服务器会直接拒绝，界面先说清楚
            status = STATUS_LIQUIDATE;
        } else {
            status = after < 0L ? STATUS_DEBT : STATUS_OK;
        }
        return new BuyPlan(shares, requested, buyable, gross, fee, total, after, status, shares < requested);
    }

    public static Component describeBuy(BuyPlan plan) {
        switch (plan.status()) {
            case STATUS_NO_LIQUIDITY:
                return Component.translatable("gui.shared_stock_exchange.preview_buy_max",
                        Math.max(plan.buyable(), 0L));
            case STATUS_NO_CASH:
                return Component.translatable("gui.shared_stock_exchange.preview_buy_no_cash",
                        MarketFormat.emc(plan.total()), MarketFormat.emc(plan.after() + plan.total()));
            case STATUS_LIQUIDATE:
                return Component.translatable("gui.shared_stock_exchange.preview_buy_liquidate",
                        MarketFormat.emc(plan.total()));
            case STATUS_DEBT:
                return Component.translatable("gui.shared_stock_exchange.preview_buy_debt",
                        plan.shares(), MarketFormat.emc(plan.total()), MarketFormat.emc(plan.after()));
            default:
                if (plan.clamped()) {
                    return Component.translatable("gui.shared_stock_exchange.preview_buy_all",
                            plan.shares(), MarketFormat.emc(plan.total()), MarketFormat.emc(plan.after()));
                }
                return Component.translatable("gui.shared_stock_exchange.preview_buy",
                        plan.shares(), MarketFormat.emc(plan.total()), MarketFormat.emc(plan.after()));
        }
    }

    /** 这一行的颜色：买不起 / 会爆仓红，要欠债橙，正常绿。颜色跟着当前主题走。 */
    public static int buyColor(BuyPlan plan) {
        if (!plan.affordable()) {
            return UiTheme.palette().down();
        }
        return plan.status() == STATUS_DEBT ? UiTheme.palette().warn() : UiTheme.palette().up();
    }

    /** 卖出的提示：没持仓、超过持仓（按全部卖出）、正常卖出三种。 */
    public static Component describeSell(MarketSnapshotPayload snapshot, int index, int requested) {
        long held = index >= 0 && index < snapshot.holdings().length ? snapshot.holdings()[index] : 0L;
        if (held <= 0L) {
            return Component.translatable("gui.shared_stock_exchange.preview_no_shares");
        }
        // 卖得比手上多：按「全部卖出」算，和服务器一致
        long effective = Math.min(Math.max(requested, 0), held);
        long gross = Math.max(0L, (long) Math.floor(snapshot.proceedsOf(index, effective)));
        long fee = MarketMath.feeOf(gross);
        long received = Math.max(gross - fee, 0L);
        if (effective < requested) {
            return Component.translatable("gui.shared_stock_exchange.preview_sell_all",
                    held, MarketFormat.emc(received), MarketFormat.emc(snapshot.balance() + received));
        }
        return Component.translatable("gui.shared_stock_exchange.preview_sell",
                effective, MarketFormat.emc(received), MarketFormat.emc(snapshot.balance() + received));
    }
}
