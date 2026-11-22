package cn.blockforge.generated.sharedstockexchange.net;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务器：请求刷新行情、提交一笔买卖、清仓，或者在设置面板里改服务器的设置。
 *
 * <ul>
 *   <li>买卖、清仓：stockId 是股票、shares 是股数；清仓时 stockId 留空表示「所有股票全卖」。</li>
 *   <li>设置：shares 当开关用（0 / 1），amount 当数字用（波动秒数、测试钱包金额）。
 *       设置类动作要管理员或单人存档房主才能生效，服务器收到时会自己再查一遍权限。</li>
 * </ul>
 */
public record MarketActionPayload(int action, String stockId, int shares, long amount) implements CustomPacketPayload {
    public static final int ACTION_SYNC = 0;
    public static final int ACTION_BUY = 1;
    public static final int ACTION_SELL = 2;
    public static final int ACTION_CLAIM = 3;
    public static final int ACTION_LIQUIDATE = 4;
    /** 设置面板：改行情波动间隔（amount = 秒）。 */
    public static final int ACTION_SET_INTERVAL = 5;
    /** 设置面板：重置测试钱包（amount = EMC）。 */
    public static final int ACTION_SET_WALLET = 6;
    /** 设置面板：负债系统开关（shares：1 开 / 0 关）。 */
    public static final int ACTION_SET_DEBT = 7;
    /** 设置面板：强制平仓开关（shares：1 开 / 0 关）。 */
    public static final int ACTION_SET_LIQUIDATION = 8;
    /** 设置面板：未成年退款（谁都能点，服务器判断有没有欠债）。 */
    public static final int ACTION_REFUND = 9;

    public static final Type<MarketActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StockExchangeMod.MOD_ID, "market_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketActionPayload> STREAM_CODEC = StreamCodec.of(
            MarketActionPayload::encode,
            MarketActionPayload::decode
    );

    /** 不涉及数字的动作（刷新、买卖、清仓、退款）用这个：amount 自动是 0。 */
    public MarketActionPayload(int action, String stockId, int shares) {
        this(action, stockId, shares, 0L);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MarketActionPayload payload) {
        buffer.writeVarInt(payload.action);
        buffer.writeUtf(payload.stockId, 64);
        buffer.writeVarInt(payload.shares);
        buffer.writeLong(payload.amount);
    }

    private static MarketActionPayload decode(RegistryFriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        String stockId = buffer.readUtf(64);
        int shares = buffer.readVarInt();
        long amount = buffer.readLong();
        return new MarketActionPayload(action, stockId, shares, amount);
    }

    @Override
    public Type<MarketActionPayload> type() {
        return TYPE;
    }
}
