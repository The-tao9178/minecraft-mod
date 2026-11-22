package cn.blockforge.generated.sharedstockexchange.market;

public record StockDefinition(String id, String itemId, double basePrice, long totalShares) {
   public double initialEmcPool() {
      return this.basePrice * (double)this.totalShares;
   }
}
