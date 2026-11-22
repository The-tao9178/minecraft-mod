package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MarketActionPayload(int action, String stockId, int shares, long amount) {
   public static final int ACTION_SYNC = 0;
   public static final int ACTION_BUY = 1;
   public static final int ACTION_SELL = 2;
   public static final int ACTION_CLAIM = 3;
   public static final int ACTION_LIQUIDATE = 4;
   public static final int ACTION_SET_INTERVAL = 5;
   public static final int ACTION_SET_WALLET = 6;
   public static final int ACTION_SET_DEBT = 7;
   public static final int ACTION_SET_LIQUIDATION = 8;
   public static final int ACTION_REFUND = 9;
   public static final ResourceLocation CHANNEL_ID = new ResourceLocation(StockExchangeMod.MOD_ID, "main");

   public MarketActionPayload(int action, String stockId, int shares) {
      this(action, stockId, shares, 0L);
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.action);
      buffer.writeUtf(this.stockId, 64);
      buffer.writeVarInt(this.shares);
      buffer.writeLong(this.amount);
   }

   public static MarketActionPayload decode(FriendlyByteBuf buffer) {
      int action = buffer.readVarInt();
      String stockId = buffer.readUtf(64);
      int shares = buffer.readVarInt();
      long amount = buffer.readLong();
      return new MarketActionPayload(action, stockId, shares, amount);
   }
}
