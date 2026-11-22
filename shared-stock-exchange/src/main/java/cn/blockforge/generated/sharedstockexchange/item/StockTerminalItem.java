package cn.blockforge.generated.sharedstockexchange.item;

import cn.blockforge.generated.sharedstockexchange.client.GuideBook;
import cn.blockforge.generated.sharedstockexchange.client.MarketScreen;
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
 * 股票交易终端：拿在手上右键就能打开交易所界面。
 */
public class StockTerminalItem extends Item {
    public StockTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            // 潜行右键不用另找手册，直接翻教程
            if (player.isShiftKeyDown()) {
                GuideBook.open();
            } else {
                MarketScreen.openMarket();
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.shared_stock_exchange.stock_terminal.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.shared_stock_exchange.stock_terminal.tip2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.shared_stock_exchange.stock_terminal.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
