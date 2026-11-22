package cn.blockforge.generated.sharedstockexchange.item;

import cn.blockforge.generated.sharedstockexchange.client.GuideBook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 交易所手册：右键打开分页教程，把怎么买、怎么卖、分红、清仓、联机玩法都写清楚。
 * 交易终端潜行右键也能直接翻这本手册，不用把两样都带在身上。
 */
public class StockGuideItem extends Item {
    public StockGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            GuideBook.open();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.shared_stock_exchange.stock_guide.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.shared_stock_exchange.stock_guide.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
