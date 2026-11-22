package cn.blockforge.generated.sharedstockexchange.client.mcphone;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.client.MarketScreen;
import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.ui.IPhonePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * 手机里的「炒股」App —— 装了 MCphone 才会被它扫到。
 *
 * 注册方式用的是 MCphone 公开的 SPI：本模组 jar 里有一份
 * {@code META-INF/services/com.november.mcphone.api.client.app.IPhoneApp}，
 * 里面写着这个类的全名。没装 MCphone 时没有人去读那个文件，这个类也不会被加载，
 * 所以不会在没装 MCphone 的整合包里报 NoClassDefFoundError。
 *
 * 界面走 {@link #openPage()}，也就是说炒股页画在手机屏幕【里】，
 * 状态栏、导航栏、壁纸、返回键都还是 MCphone 那一套，和它自带的 App 一样。
 */
public final class StockPhoneApp implements IPhoneApp {
    /** App 的稳定标识，命名空间用我们自己的 modid。改了这个玩家的图标位置就重置了 */
    public static final ResourceLocation APP_ID =
            new ResourceLocation(StockExchangeMod.MOD_ID, "stock_market");

    private static final ResourceLocation ICON =
            new ResourceLocation(StockExchangeMod.MOD_ID, "textures/app/stock_market.png");

    /** 版本号和描述：手机界面可能每帧都来问一次，查一遍 ModList / 翻译表太浪费，读到就存下来 */
    private static String cachedVersion;
    private static String cachedDescription;
    private static String cachedDescriptionLanguage;
    /** App 名字同理：主屏每帧给每个图标取一次名字，按语言缓存翻译结果 */
    private static String cachedNameText;
    private static String cachedNameLanguage;

    /**
     * 复用同一个页面对象。
     *
     * <p>玩家从手机切到大屏、再切回手机时，MCphone 有可能重新向我们要一次页面；
     * 如果每次都给新对象，刚才看的是哪只股票、选了多大数量就全丢了。
     * 这个页面只存界面状态（选中哪只、数量、滚动位置），复用不会串数据。
     */
    private static StockAppPage cachedPage;

    @Override
    public ResourceLocation getId() {
        return APP_ID;
    }

    /**
     * 手机主屏每帧都会给每个图标取一次名字（{@code getDisplayName().getString()}），
     * 这里按语言缓存翻译结果；返回的是新建的 literal 组件，调用方想给它加样式也改不到缓存上。
     */
    @Override
    public Component getDisplayName() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cachedNameText == null || !language.equals(cachedNameLanguage)) {
            cachedNameLanguage = language;
            cachedNameText = Component.translatable("app.shared_stock_exchange.stock_market").getString();
        }
        return Component.literal(cachedNameText);
    }

    @Override
    public ResourceLocation getIconTexture() {
        return ICON;
    }

    /**
     * 手机主屏画到我们图标的那一刻，说明玩家正开着手机，顺手把这个手机界面记下来。
     *
     * <p>这样即使玩家还没点进炒股 App，交易终端那边也能看到他有个手机可以切回去。
     * 画法还是用 MCphone 自己的默认实现（{@code IPhoneApp.super}），图标长什么样由它决定，
     * 我们只是借这一帧拿到当前界面，不碰它的任何内部类。
     */
    @Override
    public void renderIcon(GuiGraphics graphics, int x, int y, int size, float partialTick) {
        PhoneSwitch.remember(Minecraft.getInstance().screen);
        IPhoneApp.super.renderIcon(graphics, x, y, size, partialTick);
    }

    /** 覆盖了 openPage()，点图标不会走这里；留着是给不支持手机内页面的旧版 MCphone 兜底 */
    @Override
    public void onPress() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        // 旧版 MCphone 没有 openPage 这条路，只能跳出手机，开我们自己的交易界面。
        // 至少「点开能用」，而不是点了没反应
        minecraft.setScreen(new MarketScreen());
    }

    @Override
    public IPhonePage openPage() {
        if (cachedPage == null) {
            cachedPage = new StockAppPage();
        }
        return cachedPage;
    }

    /**
     * 明确告诉手机：这个 App 是画在手机屏幕里的。
     *
     * <p>MCphone 的接口里这个方法有默认实现（有页面就在手机里开），这里自己写一遍是为了
     * 不吃默认值的亏：以后它改了默认行为，我们的 App 还是照旧在手机里打开。
     */
    @Override
    public boolean opensInsidePhone() {
        return true;
    }

    /** 版本号从模组自己的元数据里读，以后换版本不用回来改这里；读一次就缓存 */
    @Override
    public String getVersion() {
        if (cachedVersion == null) {
            cachedVersion = ModList.get().getModContainerById(StockExchangeMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("1.0.0");
        }
        return cachedVersion;
    }

    @Override
    public String getAuthor() {
        return "AutoMods";
    }

    /** 描述跟着语言走，但只在语言真的变过之后重新拼一次 */
    @Override
    public String getDescription() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cachedDescription == null || !language.equals(cachedDescriptionLanguage)) {
            cachedDescriptionLanguage = language;
            cachedDescription = Component.translatable("app.shared_stock_exchange.stock_market.desc").getString();
        }
        return cachedDescription;
    }
}
