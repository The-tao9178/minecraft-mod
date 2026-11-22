package cn.blockforge.generated.sharedstockexchange.market;

/**
 * 交易所的报价公式，服务端和客户端共用同一份。
 *
 * 每只股票是一个「资金池 + 股份池」的恒定乘积市场：
 *   价格 = 资金池 / 股份池，资金池 × 股份池 永远不变。
 *   买 N 股 = 资金池变多、股份池变少，越买越贵；卖 N 股反着来。
 *
 * 这里的算式分成两块，互不依赖：
 *   · 报价：买多少花多少、卖多少拿多少（costForShares / proceedsForShares）；
 *   · 风控：EMC 可以是负数（欠债），权益 = 现金 + 持仓变现价值，
 *     权益跌破持仓变现价值的 {@link #MAINTENANCE_MARGIN} 才算"该强制平仓"。
 *
 * 注意：这里没有任何"最多能借多少钱"的额度算式——那是以后的贷款系统才需要的东西。
 * 现在负债系统只管一件事：EMC 能不能变成负数。强制平仓则是另一个独立开关，
 * 只回答一个问题：欠债又跌得太惨时，要不要把股票卖掉还债。
 */
public final class MarketMath {
    /** 股份池小到这个程度就当买不到了。 */
    public static final double MIN_POOL = 1.0E-6D;
    /** 每笔买卖的手续费（1%），全部进这只股票的分红池，按持股分给股东。 */
    public static final double FEE_RATE = 0.01D;
    /**
     * 盘面保留多少比例的股份不卖光（10%）。
     * 股份池被买到接近 0 时价格会被推到无穷大，这只股票也就再没人买得动，
     * 留出这一成，保证每只股票永远还有流通盘。
     */
    public static final double SHARE_RESERVE = 0.1D;
    /** 维持保证金：权益低于持仓变现价值的这个比例，就强制平仓。 */
    public static final double MAINTENANCE_MARGIN = 0.1D;
    /**
     * 单笔最多能欠到多少 EMC。只是防止数字撑爆 long 的安全线，
     * 不是"贷款额度"——正常玩（哪怕是 10 倍杠杆）也远远碰不到这个数。
     */
    public static final double MAX_DEBT = 9.0E15D;

    private MarketMath() {
    }

    /** 买入 shares 股需要的 EMC；买不到（超过可流通股数）返回无穷大。 */
    public static double costForShares(double emcPool, double sharePool, long shares) {
        if (shares <= 0L || !(sharePool > MIN_POOL)) {
            return Double.POSITIVE_INFINITY;
        }
        double remaining = sharePool - (double) shares;
        if (remaining <= MIN_POOL) {
            return Double.POSITIVE_INFINITY;
        }
        double invariant = emcPool * sharePool;
        return invariant / remaining - emcPool;
    }

    /** 卖出 shares 股能拿回的 EMC。 */
    public static double proceedsForShares(double emcPool, double sharePool, long shares) {
        if (shares <= 0L || !(emcPool > 0.0D)) {
            return 0.0D;
        }
        double pool = Math.max(sharePool, MIN_POOL);
        double invariant = emcPool * pool;
        double payout = emcPool - invariant / (pool + (double) shares);
        return Math.max(payout, 0.0D);
    }

    /** 这个价位最多还能买多少股（留出一成股份不卖光）。 */
    public static long maxBuyable(double sharePool) {
        if (!(sharePool > MIN_POOL)) {
            return 0L;
        }
        long shares = (long) Math.floor(sharePool * (1.0D - SHARE_RESERVE));
        return Math.max(shares, 0L);
    }

    /** 想买的数量超过盘面能卖的时候按「全部买入」算，返回真正成交的股数。 */
    public static long clampBuy(long requested, double sharePool) {
        if (requested <= 0L) {
            return 0L;
        }
        return Math.min(requested, maxBuyable(sharePool));
    }

    /** 总资产（权益）= 现金 + 持仓变现价值；欠债的时候现金是负数。 */
    public static double equityOf(double cash, double positionValue) {
        return cash + Math.max(positionValue, 0.0D);
    }

    /** 强平线：权益跌破这个数就强制平仓。 */
    public static double maintenanceLine(double positionValue) {
        return Math.max(positionValue, 0.0D) * MAINTENANCE_MARGIN;
    }

    /**
     * 要不要强制平仓：只有欠钱（现金为负）而且还有持仓的时候才可能触发。
     * 没欠钱时权益永远不低于持仓市值，不会被强平。这个判断本身不看开关，
     * 开关由调用方（MarketManager）决定要不要查。
     */
    public static boolean needsLiquidation(double cash, double positionValue) {
        if (cash >= 0.0D || positionValue <= 0.0D) {
            return false;
        }
        return equityOf(cash, positionValue) < maintenanceLine(positionValue);
    }

    /**
     * 这一笔买入之后，权益还站得住吗？
     *
     * 恒定乘积市场里，刚买到的股票立刻卖回去基本能拿回原价（只亏手续费），
     * 所以买入只让权益少掉手续费：权益(买后) = 权益(买前) − 手续费。
     * 而持仓变大了，强平线跟着抬高。两者一碰就知道"买完会不会当场爆仓"，
     * 与其让玩家买完下一秒被平掉，不如这一步就拦住。
     */
    public static boolean survivesMaintenance(double cash, double positionValue, double cost, double fee) {
        double equityAfter = equityOf(cash, positionValue) - Math.max(fee, 0.0D);
        double positionAfter = Math.max(positionValue, 0.0D) + Math.max(cost, 0.0D);
        return equityAfter >= maintenanceLine(positionAfter);
    }

    /**
     * 加金额时兜一层，别让 long 悄悄溢出成反方向的数。
     * EMC 现在可以直接是负数，透支买入/卖东西抵扣都会走加减法，所以统一放在这里。
     */
    public static long safeAdd(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0L) {
            return b > 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return sum;
    }

    /** 交易手续费（按成交额的 1%，向下取整）。 */
    public static long feeOf(long gross) {
        return (long) Math.floor((double) gross * FEE_RATE);
    }
}
