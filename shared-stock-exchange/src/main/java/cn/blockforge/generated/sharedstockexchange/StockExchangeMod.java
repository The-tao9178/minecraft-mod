package cn.blockforge.generated.sharedstockexchange;

import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.registry.ModItems;
import cn.blockforge.generated.sharedstockexchange.registry.ModTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 共享证券交易所：所有行情都保存在服务器端的世界存档里，
 * 联机时每个玩家看到的是同一份价格，谁买谁卖都会推高/压低全服共享的市值。
 */
@Mod(StockExchangeMod.MOD_ID)
public final class StockExchangeMod {
    public static final String MOD_ID = "shared_stock_exchange";
    public static final Logger LOGGER = LoggerFactory.getLogger("SharedStockExchange");

    public StockExchangeMod(IEventBus modBus) {
        ModItems.register(modBus);
        ModTabs.register(modBus);
        modBus.addListener(MarketNetworking::register);
    }
}
