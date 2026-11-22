package cn.blockforge.generated.sharedstockexchange;

import cn.blockforge.generated.sharedstockexchange.net.MarketNetworking;
import cn.blockforge.generated.sharedstockexchange.registry.ModItems;
import cn.blockforge.generated.sharedstockexchange.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(StockExchangeMod.MOD_ID)
public final class StockExchangeMod {
   public static final String MOD_ID = "shared_stock_exchange";
   public static final Logger LOGGER = LoggerFactory.getLogger("Shared Stock Exchange");

   public StockExchangeMod() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModItems.register(modBus);
      ModTabs.register(modBus);
      MarketNetworking.register(modBus);
   }
}
