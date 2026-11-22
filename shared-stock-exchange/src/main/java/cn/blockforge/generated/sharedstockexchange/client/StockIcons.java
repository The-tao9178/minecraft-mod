package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 股票图标的缓存：列表每帧要画 8 个物品图标，以前每帧都重新解析一次
 * 「minecraft:iron_ingot」这种字符串、查一次注册表、new 一次 ItemStack，
 * 白白制造垃圾。股票表是写死的常量，第一个界面画到时建好、之后一直复用。
 */
public final class StockIcons {
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private StockIcons() {
    }

    public static ItemStack of(StockDefinition definition) {
        ItemStack cached = CACHE.get(definition.id());
        if (cached != null) {
            return cached;
        }
        ItemStack built = build(definition);
        CACHE.put(definition.id(), built);
        return built;
    }

    private static ItemStack build(StockDefinition definition) {
        ResourceLocation id = ResourceLocation.tryParse(definition.itemId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
