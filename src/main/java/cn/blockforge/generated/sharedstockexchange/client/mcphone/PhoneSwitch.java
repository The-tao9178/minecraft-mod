package cn.blockforge.generated.sharedstockexchange.client.mcphone;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.client.MarketScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 「手机界面」和「大屏界面」之间来回切。
 *
 * <p>这里刻意<b>不</b>引用 MCphone 的任何类，只认它的 modid，外加原版 {@link Screen}：
 * <ul>
 *   <li>手机 → 大屏：从炒股页里点按钮，先记住当前这个手机界面，再打开我们自己的 {@link MarketScreen}；</li>
 *   <li>大屏 → 手机：把刚才记住的那个手机界面原样放回去（{@link Minecraft#setScreen(Screen)} 是原版公开方法）。</li>
 * </ul>
 *
 * <p>为什么这样最稳：MCphone 升级、改包名、改内部类都影响不到这两步，
 * 因为我们从头到尾只拿着一个 {@code Screen} 对象，没去碰它的手机界面实现。
 * 没装 MCphone 时没人会记住手机界面，大屏上的切换按钮也就不出现，原界面和以前完全一样。
 */
@Mod.EventBusSubscriber(modid = StockExchangeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PhoneSwitch {
    /** MCphone 的 modid。只用来判断「装没装」，不加载它的任何类 */
    public static final String MCPHONE_MODID = "mcphone";

    /** 上一次打开炒股 App 时的手机界面。只在内存里，不写存档 */
    private static Screen phoneScreen;

    private PhoneSwitch() {
    }

    /** 装了 MCphone 没有。用 modid 判断，避免加载它的类 */
    public static boolean mcphoneLoaded() {
        return ModList.get().isLoaded(MCPHONE_MODID);
    }

    /**
     * 记住当前的手机界面。炒股页每帧渲染时都会调一次，只有换了对象才真的赋值，开销就是一次引用比较。
     */
    public static void remember(Screen screen) {
        if (screen == null || screen == phoneScreen) {
            return;
        }
        if (mcphoneLoaded()) {
            phoneScreen = screen;
        }
    }

    /** 大屏上要不要显示「切回手机」的按钮：装了手机模组，并且这个会话里打开过手机里的炒股 App。 */
    public static boolean canReturnToPhone() {
        return phoneScreen != null && mcphoneLoaded();
    }

    /** 手机里的炒股页点「大屏」：记住手机界面，然后开交易界面。 */
    public static boolean openBigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        remember(minecraft.screen);
        minecraft.setScreen(new MarketScreen());
        return true;
    }

    /** 大屏上点「手机」：把之前记住的手机界面放回去。 */
    public static boolean returnToPhone() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = phoneScreen;
        if (screen == null || !mcphoneLoaded()) {
            return false;
        }
        if (minecraft.screen == screen) {
            return true;
        }
        minecraft.setScreen(screen);
        return true;
    }

    /** 退出世界时丢掉引用：那个手机界面已经不属于当前世界了，别带到下一次游戏里。 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        phoneScreen = null;
    }
}
