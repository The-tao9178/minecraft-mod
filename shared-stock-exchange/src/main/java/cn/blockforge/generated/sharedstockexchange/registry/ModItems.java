package cn.blockforge.generated.sharedstockexchange.registry;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.item.StockGuideItem;
import cn.blockforge.generated.sharedstockexchange.item.StockTerminalItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StockExchangeMod.MOD_ID);

    public static final DeferredItem<Item> STOCK_TERMINAL = ITEMS.register("stock_terminal",
            () -> new StockTerminalItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> STOCK_GUIDE = ITEMS.register("stock_guide",
            () -> new StockGuideItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
