package cn.blockforge.generated.sharedstockexchange.item;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.client.GuideBook;
import cn.blockforge.generated.sharedstockexchange.client.MarketScreen;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class StockTerminalItem extends Item {
   public StockTerminalItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (level.isClientSide) {
         if (player.isShiftKeyDown()) {
            GuideBook.open();
         } else {
            MarketScreen.openMarket();
         }
      }

      return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item." + StockExchangeMod.MOD_ID + ".stock_terminal.tip1").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item." + StockExchangeMod.MOD_ID + ".stock_terminal.tip2").withStyle(ChatFormatting.DARK_GRAY));
      tooltip.add(Component.translatable("item." + StockExchangeMod.MOD_ID + ".stock_terminal.tip3").withStyle(ChatFormatting.DARK_GRAY));
   }
}
