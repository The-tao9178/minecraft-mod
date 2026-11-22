package cn.blockforge.generated.sharedstockexchange.registry;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.item.StockGuideItem;
import cn.blockforge.generated.sharedstockexchange.item.StockTerminalItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StockExchangeMod.MOD_ID);
   public static final RegistryObject<Item> STOCK_TERMINAL = ITEMS.register("stock_terminal", () -> new StockTerminalItem(new Item.Properties().stacksTo(1)));
   public static final RegistryObject<Item> STOCK_GUIDE = ITEMS.register("stock_guide", () -> new StockGuideItem(new Item.Properties().stacksTo(1)));

   private ModItems() {
   }

   public static void register(IEventBus modBus) {
      ITEMS.register(modBus);
   }
}
