package cn.blockforge.generated.sharedstockexchange.registry;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StockExchangeMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.shared_stock_exchange.main"))
                    .icon(() -> new ItemStack(ModItems.STOCK_TERMINAL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(new ItemStack(ModItems.STOCK_TERMINAL.get()));
                        output.accept(new ItemStack(ModItems.STOCK_GUIDE.get()));
                    })
                    .build());

    private ModTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
