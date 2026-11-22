package cn.blockforge.generated.sharedstockexchange.client.mcphone;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.client.GuideBook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把交易所手册放进 MCphone 的「阅读」App（书城）里。
 *
 * <p>为什么是一个 txt 文件，而不是写一个书源：MCphone 的书源名单是它自己写死的
 * （{@code BookSources} 里的私有常量），没有对外的注册口；但它自带一个「本地书」书源，
 * 会扫描游戏目录下的 {@code mcphone/books/}，那里面的 .txt 就是书架上的书。
 * 我们往那个目录写一份教程，玩家在书城里就能点开看——用的还是 MCphone 自己的阅读器，
 * 翻页、目录、进度、收藏都是它的功能，不需要我们去碰它的内部类。
 *
 * <p>这样做的两个好处：<br>
 * 1. 不 Mixin 对方任何类，MCphone 升级改名也影响不到我们，最坏情况是书城看不到这本书；<br>
 * 2. 玩家还能像对待别的小说一样，自己删掉、复制、拖到别的整合包。
 *
 * <p>写文件只在内容真的变了的时候发生（比如模组更新了教程、或者玩家换了语言），
 * 平时进游戏一次磁盘写入都没有。
 */
@EventBusSubscriber(modid = StockExchangeMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class McphoneBooks {
    /** MCphone 的 modid，同时也是它书城目录的第一层 */
    private static final String MCPHONE_MODID = "mcphone";
    /** 书城把书放在 {@code <游戏目录>/mcphone/books/} 下（对应它内部的 PhonePaths.BOOKS） */
    private static final String BOOKS_DIR = "books";

    private McphoneBooks() {
    }

    /** 进世界后等这么多 tick 再写，错开「刚进世界」那段最忙的时候（单位：客户端 tick） */
    private static final int EXPORT_DELAY_TICKS = 100;

    /** 还剩多少 tick 才导出；-1 表示没有排队 */
    private static int pendingTicks = -1;

    /**
     * 玩家登录客户端时排一次导出。
     *
     * <p>这里不立刻写盘：刚进世界那一下本来就在加载资源、同步区块，
     * 再叠一次「拼整本教程 + 读写文件」会让进世界更顿。所以先记一笔，等 5 秒后客户端空闲了再做。
     * 书城是打开时才扫目录的，晚几秒完全来得及。
     */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        pendingTicks = EXPORT_DELAY_TICKS;
    }

    /** 到点了就导出一次，然后把排队标记清掉（之后不再每 tick 检查） */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingTicks < 0) {
            return;
        }
        if (pendingTicks > 0) {
            pendingTicks--;
            return;
        }
        pendingTicks = -1;
        if (Minecraft.getInstance().player != null) {
            exportGuide();
        }
    }

    /** 退出世界时把没做完的排队取消，免得在主菜单里还去写书城目录 */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingTicks = -1;
    }

    /**
     * 把教程写进书城目录。可以反复调用：内容没变就不写盘。
     *
     * <p>整套操作都包在 try/catch 里：书城是锦上添花，读不到、写不进都不该让玩家进不了游戏。
     */
    public static void exportGuide() {
        if (!ModList.get().isLoaded(MCPHONE_MODID)) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.gameDirectory == null) {
                return;
            }
            String fileName = Component.translatable("book.shared_stock_exchange.guide_file").getString();
            if (fileName.isBlank()) {
                return;
            }
            Path directory = minecraft.gameDirectory.toPath().resolve(MCPHONE_MODID).resolve(BOOKS_DIR);
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName + ".txt");

            String content = GuideBook.plainText();
            if (content.isBlank()) {
                return;
            }
            // 内容一样就什么都不做：平时进游戏这一次连写盘都没有
            boolean unchanged = false;
            if (Files.isRegularFile(target)) {
                try {
                    unchanged = content.equals(Files.readString(target, StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // 读不出来（编码被改过、文件被锁）就当它不一样，直接覆盖一次
                }
            }
            if (unchanged) {
                return;
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            StockExchangeMod.LOGGER.info("[共享证券交易所] 已把交易所手册放进 MCphone 书城：{}", target);
        } catch (Throwable throwable) {
            // 书城目录不可写（只读整合包、权限问题）不该影响游戏，记一次日志就够了
            StockExchangeMod.LOGGER.warn("[共享证券交易所] 往 MCphone 书城写交易所手册失败，这次先跳过",
                    throwable);
        }
    }
}
