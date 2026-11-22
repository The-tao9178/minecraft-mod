package cn.blockforge.generated.sharedstockexchange.market;

/**
 * 一只股票的固定定义。价格是 EMC/股，总股本是这个市场上流通的总股数。
 */
public record StockDefinition(String id, String itemId, double basePrice, long totalShares) {

    /** 初始资金池（EMC）：发行价 × 总股本。 */
    public double initialEmcPool() {
        return basePrice * (double) totalShares;
    }
}
