package cn.blockforge.generated.sharedstockexchange.client;

import cn.blockforge.generated.sharedstockexchange.market.StockDefinition;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class StockIcons {
   private static final Map<String, ItemStack> CACHE = new HashMap<>();

   private StockIcons() {
   }

   public static ItemStack of(StockDefinition definition) {
      ItemStack cached = CACHE.get(definition.id());
      if (cached != null) {
         return cached;
      } else {
         ItemStack built = build(definition);
         CACHE.put(definition.id(), built);
         return built;
      }
   }

   private static ItemStack build(StockDefinition definition) {
      ResourceLocation id = ResourceLocation.tryParse(definition.itemId());
      if (id == null) {
         return ItemStack.EMPTY;
      } else {
         Item item = (Item)BuiltInRegistries.ITEM.get(id);
         return item == null ? ItemStack.EMPTY : new ItemStack(item);
      }
   }
}
