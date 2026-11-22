package cn.blockforge.generated.sharedstockexchange.compat;

import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 和「等价交换」（ProjectE）的软对接：玩家面板里的 EMC 直接就是股市现金。
 *
 * 这里全部用反射，因为本模组不硬依赖等价交换：玩家没装它也能玩（用模组自带的测试钱包），
 * 装了它就自动把玩家的 EMC 当现金用，一分钱都不另外造。
 *
 * 用到的等价交换 API（按 1.21.1 的真实源码逐个核对过）：
 * <pre>
 * 1.21.x（NeoForge 能力系统）
 *   moze_intel.projecte.api.capabilities.PECapabilities.KNOWLEDGE_CAPABILITY
 *       是 EntityCapability&lt;IKnowledgeProvider, Void&gt;
 *   玩家的 EMC 用 player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY) 拿到
 *   IKnowledgeProvider 后，getEmc() / setEmc(BigInteger) 读写玩家面板里的 EMC，
 *   改完用 syncEmc(ServerPlayer) 让等价交换把新数字发给客户端。
 *   moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(物品) 查物品的 EMC 参考价。
 *
 * 1.20.x 及更早（Forge 能力系统）
 *   moze_intel.projecte.api.capabilities.PE_CAPABILITIES.KNOWLEDGE + getCapability(Capability)
 *   moze_intel.projecte.api.ProjectEAPI.getEMCProxy() 拿 EMC 代理。
 * </pre>
 *
 * 上一版就是照着 1.20 的名字写的，在 1.21.1 上类名、字段名、EMC 的数值类型（BigInteger）
 * 全都对不上，所以永远是「没接通」。现在两套都认，认出来就用，认不出来就退回测试钱包，
 * 并把每一步的结果记下来给 /stock status 和开服日志看。
 *
 * 关于负数 EMC：等价交换的 setEmc 本身不拦负数，玩家的 EMC 可以老老实实写成 -500，
 * 面板、转化桌、指令看到的都是这个负号。唯一的坑在转化桌结算的时候——它会把
 * 「算完还是负数」的结果夹成 0，等于卖一次东西欠的钱就全没了。这个夹 0 在
 * {@link #TABLE_CLASS} 的 updateEmcAndSync 里，本模组用一个 Mixin 把它接过来
 * （见 mixin/TransmutationInventoryMixin），这里提供它要用的直写方法，
 * 外加一个开机自检 {@link #probeTable}，用来确认那个夹 0 真的被绕过去了。
 */
public final class ProjectEmcBridge {
    private static final String MOD_ID = "projecte";
    /** 能力持有类：1.21.x 叫 PECapabilities，旧版叫 PE_CAPABILITIES。 */
    private static final String[] CAPABILITY_CLASSES = {
            "moze_intel.projecte.api.capabilities.PECapabilities",
            "moze_intel.projecte.api.capabilities.PE_CAPABILITIES"
    };
    private static final String PROVIDER_CLASS = "moze_intel.projecte.api.capabilities.IKnowledgeProvider";
    private static final String PROXY_CLASS = "moze_intel.projecte.api.proxy.IEMCProxy";
    private static final String[] API_CLASSES = {"moze_intel.projecte.api.ProjectEAPI"};
    /** getEmc 读不出来时返回这个值（不能用 -1，因为 EMC 现在可以是负数）。 */
    public static final long UNAVAILABLE = Long.MIN_VALUE;
    /** 转化桌的核心类：Mixin 打的也是它，自检也用它。 */
    public static final String TABLE_CLASS = "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory";
    /** 接不上时最多重试这么多次（每次只多做几次 Class.forName，很便宜），避免开服早期顺序问题把它判死刑。 */
    private static final int MAX_ATTEMPTS = 64;

    private static int attempts;
    private static boolean connected;
    private static boolean negativeRejected;
    private static String state = "还没检查";

    private static Object knowledgeCapability;
    private static String capabilitySource = "";
    private static List<Method> capabilityGetters = List.of();
    private static Method cachedGetter;
    private static Method cachedGetEmc;
    private static Method cachedSetEmc;
    private static Method cachedSync;

    /** 转化桌认不认负数：true = 负数能留住，false = 被夹成 0（Mixin 没生效），null = 还没测过。 */
    private static Boolean tableKeepsNegative;
    /** 自检最多试几次（类名对不上时别每次进服都试一遍）。 */
    private static final int MAX_PROBE_ATTEMPTS = 3;
    private static int probeAttempts;
    /** 转化桌里存 provider / player 的两个字段，反射找一次就记住。 */
    private static Field cachedProviderField;
    private static Field cachedPlayerField;
    private static boolean tableFieldsResolved;
    /** 转化桌结算完会顺手更新记分板上的 emc 分数，这里也尽量跟着更新（找不到就算了）。 */
    private static boolean scoreResolved;
    private static Method cachedScore;
    private static Object cachedScoreCriteria;

    private static boolean proxyResolved;
    private static boolean proxyReady;
    private static Object emcProxy;
    private static Method cachedProxyGetValue;
    private static final Map<String, Double> ITEM_EMC = new HashMap<>();

    private ProjectEmcBridge() {
    }

    /** 等价交换的玩家 EMC 是否已接通（装了 + 接口认得出）。 */
    public static boolean available() {
        ensure();
        return connected;
    }

    /** 装了等价交换但没接通（版本对不上 / 接口认不出来）：界面要老实说，别假装没装。 */
    public static boolean installedButDisconnected() {
        ensure();
        return !connected && modPresent();
    }

    /** 物品 EMC 参考价接口是否可用（行情用它当参考价）。 */
    public static boolean proxyAvailable() {
        ensure();
        if (!connected) {
            return false;
        }
        resolveProxy();
        return proxyReady;
    }

    /** 装了等价交换这个模组吗（不管接口认不认得出）。 */
    public static boolean modPresent() {
        try {
            if (ModList.get().isLoaded(MOD_ID)) {
                return true;
            }
        } catch (Throwable ignored) {
            // 没跑在正常环境里，退回看类在不在
        }
        for (String name : CAPABILITY_CLASSES) {
            try {
                Class.forName(name, false, ProjectEmcBridge.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
                // 继续试下一个
            }
        }
        return false;
    }

    /** 一句人话说明当前现金从哪来，给开服日志用。 */
    public static String describe() {
        ensure();
        if (connected) {
            return "已接通等价交换（ProjectE）：你的 EMC 就是股市现金";
        }
        return state;
    }

    /** 和 {@link #describe()} 一样，但作为可翻译文本，界面和命令里用它。 */
    public static Component describeComponent() {
        ensure();
        return connected
                ? Component.translatable("command.shared_stock_exchange.cash_pe")
                : Component.translatable("command.shared_stock_exchange.cash_fallback", state);
    }

    /** 三步自检结果：装没装、玩家 EMC 接口、物品 EMC 参考价。 */
    public static List<Component> diagnostics() {
        ensure();
        boolean present = modPresent();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("command.shared_stock_exchange.diag_mod",
                present
                        ? Component.translatable("command.shared_stock_exchange.diag_loaded", modVersion())
                        : Component.translatable("command.shared_stock_exchange.diag_absent")));
        if (!present) {
            return lines;
        }
        lines.add(Component.translatable("command.shared_stock_exchange.diag_capability",
                connected
                        ? Component.translatable("command.shared_stock_exchange.diag_ok", capabilitySource)
                        : Component.translatable("command.shared_stock_exchange.diag_failed", state)));
        lines.add(Component.translatable("command.shared_stock_exchange.diag_proxy",
                proxyAvailable()
                        ? Component.translatable("command.shared_stock_exchange.diag_ok", "IEMCProxy.INSTANCE")
                        : Component.translatable("command.shared_stock_exchange.diag_no_proxy")));
        if (tableKeepsNegative != null) {
            lines.add(Component.translatable("command.shared_stock_exchange.diag_negative",
                    tableKeepsNegative
                            ? Component.translatable("command.shared_stock_exchange.diag_ok", "转化桌可写负数 EMC")
                            : Component.translatable("command.shared_stock_exchange.diag_negative_failed")));
        }
        return lines;
    }

    private static String modVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable throwable) {
            return "?";
        }
    }

    /** 只在没接通时才会真的去试，接通之后一次都不再碰反射。 */
    private static synchronized void ensure() {
        if (connected || attempts >= MAX_ATTEMPTS) {
            return;
        }
        attempts++;
        try {
            resolve();
        } catch (Throwable throwable) {
            connected = false;
            state = "连接等价交换时出错（" + throwable.getClass().getSimpleName() + "），先用测试钱包";
        }
    }

    private static void resolve() {
        if (!modPresent()) {
            state = "没装等价交换（ProjectE），正在用模组自带的测试钱包";
            return;
        }
        Class<?> capabilitiesClass = null;
        for (String name : CAPABILITY_CLASSES) {
            try {
                capabilitiesClass = Class.forName(name);
                break;
            } catch (Throwable ignored) {
                // 换下一个名字
            }
        }
        if (capabilitiesClass == null) {
            state = "检测到等价交换，但没找到能力类 PECapabilities，先用测试钱包";
            return;
        }
        Field field = knowledgeField(capabilitiesClass);
        if (field == null) {
            state = "检测到等价交换，但没找到玩家 EMC 的能力字段，先用测试钱包";
            return;
        }
        Object capability = null;
        try {
            capability = field.get(null);
        } catch (Throwable ignored) {
            // 下面统一判空
        }
        if (capability == null) {
            state = "检测到等价交换，但玩家 EMC 能力字段是空的，先用测试钱包";
            return;
        }
        knowledgeCapability = capability;
        capabilitySource = capabilitiesClass.getSimpleName() + "." + field.getName();

        List<Method> candidates = capabilityGetters(capability);
        if (candidates.isEmpty()) {
            state = "检测到等价交换，但玩家身上取不到 EMC 能力，先用测试钱包";
            return;
        }
        capabilityGetters = candidates;
        connected = true;
        state = "已接通等价交换（ProjectE）：你的 EMC 就是股市现金";
    }

    /** 找 KNOWLEDGE_CAPABILITY（1.21）或 KNOWLEDGE（1.20）这类字段。 */
    private static Field knowledgeField(Class<?> owner) {
        Field best = null;
        for (Field field : owner.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String lower = field.getName().toLowerCase();
            if (!lower.contains("knowledge")) {
                continue;
            }
            // 名字里带 CAPABILITY 的（KNOWLEDGE_CAPABILITY）优先
            if (best == null || lower.contains("capability")) {
                best = field;
            }
            if (lower.contains("capability")) {
                return field;
            }
        }
        return best;
    }

    /** 玩家身上能拿到这个能力的 getCapability 重载，参数少的（不带方向）优先。 */
    private static List<Method> capabilityGetters(Object capability) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : ServerPlayer.class.getMethods()) {
            if (!"getCapability".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length < 1 || parameters.length > 2) {
                continue;
            }
            if (method.getReturnType() == void.class) {
                continue;
            }
            if (!parameters[0].isInstance(capability)) {
                continue;
            }
            if (parameters.length == 2 && parameters[1].isPrimitive()) {
                continue;
            }
            candidates.add(method);
        }
        candidates.sort((first, second) -> Integer.compare(first.getParameterCount(), second.getParameterCount()));
        return candidates;
    }

    /** 拿某个公开类/接口上的方法；接口优先，因为实现类常常是包内私有的。 */
    private static List<Method> methodsOf(String className) {
        try {
            return List.of(Class.forName(className).getMethods());
        } catch (Throwable throwable) {
            return List.of();
        }
    }

    private static Method findMethod(List<Method> methods, String name, int parameterCount) {
        for (Method method : methods) {
            if (!name.equals(method.getName()) || method.getParameterCount() != parameterCount) {
                continue;
            }
            tryAccess(method);
            return method;
        }
        return null;
    }

    private static Method findOn(Class<?> owner, String name, int parameterCount) {
        return findMethod(List.of(owner.getMethods()), name, parameterCount);
    }

    private static void tryAccess(Method method) {
        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {
            // 模块化环境下可能不让改，public 方法通常也不需要
        }
    }

    private static Object provider(ServerPlayer player) {
        if (!available()) {
            return null;
        }
        if (cachedGetter != null) {
            Object found = invokeGetter(cachedGetter, player);
            if (found != null) {
                return found;
            }
        }
        for (Method method : capabilityGetters) {
            Object found = invokeGetter(method, player);
            if (found != null) {
                cachedGetter = method;
                return found;
            }
        }
        return null;
    }

    private static Object invokeGetter(Method method, ServerPlayer player) {
        try {
            Object raw = method.getParameterCount() == 1
                    ? method.invoke(player, knowledgeCapability)
                    : method.invoke(player, knowledgeCapability, null);
            return unwrap(raw);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** 兼容老式 LazyOptional / Optional 的返回值，拿到真正的 provider。 */
    private static Object unwrap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof java.util.Optional<?> optional) {
            return optional.orElse(null);
        }
        try {
            Method resolve = raw.getClass().getMethod("resolve");
            if (resolve.getParameterCount() == 0) {
                Object inner = resolve.invoke(raw);
                if (inner instanceof java.util.Optional<?> optional) {
                    return optional.orElse(null);
                }
            }
        } catch (Throwable ignored) {
            // 不是 LazyOptional，说明已经拿到 provider 本体了
        }
        return raw;
    }

    /** 玩家当前的 EMC；读不到返回 {@link #UNAVAILABLE}。老存档里可能读到负数（见 setEmc 的说明）。 */
    public static long getEmc(ServerPlayer player) {
        Object provider = provider(player);
        if (provider == null) {
            return UNAVAILABLE;
        }
        try {
            Method method = cachedGetEmc;
            if (method == null) {
                method = findMethod(methodsOf(PROVIDER_CLASS), "getEmc", 0);
                if (method == null) {
                    method = findOn(provider.getClass(), "getEmc", 0);
                }
                if (method == null) {
                    return UNAVAILABLE;
                }
                cachedGetEmc = method;
            }
            return toEmcLong(method.invoke(provider), UNAVAILABLE);
        } catch (Throwable throwable) {
            cachedGetEmc = null;
            return UNAVAILABLE;
        }
    }

    /**
     * 把玩家的 EMC 改成 value；失败返回 false。成功后顺手让等价交换刷新玩家面板。
     *
     * 负数照写：交易所透支买入时就把 EMC 直接扣成负数，等价交换的面板和转化桌看到的都是这个负号。
     * 写完会读回来核对一遍——万一某个版本的等价交换不接受负数（把它夹成 0），
     * 就把原来的数字写回去并返回 false，保证这一笔「没成交」时玩家一分钱都不会少。
     */
    public static boolean setEmc(ServerPlayer player, long value) {
        if (value == UNAVAILABLE) {
            return false;
        }
        Object provider = provider(player);
        if (provider == null) {
            return false;
        }
        long previous = getEmc(player);
        if (!writeEmc(provider, player, value)) {
            return false;
        }
        if (value < 0L) {
            long written = getEmc(player);
            if (written != value) {
                // 读回来对不上：这个版本的等价交换不接受负数 EMC，先把原值写回去
                negativeRejected = true;
                if (previous != UNAVAILABLE) {
                    writeEmc(provider, player, previous);
                    updateScore(player, BigInteger.valueOf(previous));
                }
                return false;
            }
            negativeRejected = false;
        }
        // 等价交换自己每次改 EMC 都会同步记分板上的 emc 分数，这里也照做，
        // 免得玩家开着记分板时看到的是旧数字
        updateScore(player, BigInteger.valueOf(value));
        return true;
    }

    /** 真正写一次 EMC（解析 setEmc 方法 + 调用 + 通知客户端刷新）。 */
    private static boolean writeEmc(Object provider, ServerPlayer player, long value) {
        try {
            Method method = cachedSetEmc;
            if (method == null) {
                method = findMethod(methodsOf(PROVIDER_CLASS), "setEmc", 1);
                if (method == null) {
                    method = findOn(provider.getClass(), "setEmc", 1);
                }
                if (method == null) {
                    return false;
                }
                cachedSetEmc = method;
            }
            Object argument = convertEmc(value, method.getParameterTypes()[0]);
            if (argument == null) {
                return false;
            }
            method.invoke(provider, argument);
        } catch (Throwable throwable) {
            cachedSetEmc = null;
            return false;
        }
        sync(provider, player);
        return true;
    }

    /** 等价交换是不是把负数 EMC 夹回了 0（是的话就不能透支买入，界面要给提示）。 */
    public static boolean negativeRejected() {
        return negativeRejected;
    }

    /** 1.21.1 的 EMC 是 BigInteger，老版本是 long，这里统一换成方法想要的类型。 */
    private static Object convertEmc(long value, Class<?> type) {
        if (type == long.class || type == Long.class) {
            return value;
        }
        if (type == int.class || type == Integer.class) {
            return (int) Math.min(value, Integer.MAX_VALUE);
        }
        if (type == double.class || type == Double.class) {
            return (double) value;
        }
        if (type == float.class || type == Float.class) {
            return (float) value;
        }
        if (type.isAssignableFrom(BigInteger.class)) {
            return BigInteger.valueOf(value);
        }
        return null;
    }

    /** 把 EMC 数值（BigInteger / 数字）换成 long，负数照原样保留；读不出来返回 fallback。 */
    private static long toEmcLong(Object value, long fallback) {
        if (value instanceof BigInteger big) {
            if (big.bitLength() < 63) {
                return big.longValue();
            }
            return big.signum() < 0 ? UNAVAILABLE + 1L : Long.MAX_VALUE;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    /** 改完 EMC 要通知等价交换，玩家面板上的数字才会跟着变。syncEmc 只发这一个数，最轻。 */
    private static void sync(Object provider, ServerPlayer player) {
        try {
            Method method = cachedSync;
            if (method == null) {
                method = findSync(provider, player, "syncEmc");
                if (method == null) {
                    method = findSync(provider, player, "sync");
                }
                if (method == null) {
                    return;
                }
                cachedSync = method;
            }
            method.invoke(provider, player);
        } catch (Throwable throwable) {
            cachedSync = null;
        }
    }

    private static Method findSync(Object provider, ServerPlayer player, String name) {
        List<Method> candidates = new ArrayList<>(methodsOf(PROVIDER_CLASS));
        candidates.addAll(List.of(provider.getClass().getMethods()));
        for (Method candidate : candidates) {
            if (!name.equals(candidate.getName()) || candidate.getParameterCount() != 1) {
                continue;
            }
            if (!candidate.getParameterTypes()[0].isInstance(player)) {
                continue;
            }
            tryAccess(candidate);
            return candidate;
        }
        return null;
    }

    // ---- 转化桌的负数直写（Mixin 和开机自检用） ----

    /** 转化桌认不认负数 EMC：true = 负数能留住，false = 被夹成 0（Mixin 没生效），null = 还没测过。 */
    public static Boolean tableKeepsNegative() {
        return tableKeepsNegative;
    }

    /**
     * 把 emc 原样写进玩家的等价交换数据（负数也照写），然后同步给客户端和记分板。
     * 由 Mixin 在转化桌结算 EMC 时调用：成功返回 true，调用方就不用再走"夹成 0"的原版逻辑。
     * 失败返回 false，让原版逻辑照跑（宁可夹 0，也不要让这笔结算卡住）。
     */
    public static boolean applyTableEmc(Object transmutationInventory, Object emc) {
        if (!(emc instanceof BigInteger value) || !available()) {
            return false;
        }
        Object provider = tableField(transmutationInventory, true);
        Object player = tableField(transmutationInventory, false);
        if (!(player instanceof ServerPlayer serverPlayer) || provider == null) {
            return false;
        }
        if (!writeEmcValue(provider, serverPlayer, value)) {
            return false;
        }
        updateScore(serverPlayer, value);
        tableKeepsNegative = Boolean.TRUE;
        return true;
    }

    /**
     * 转化桌把物品换成 EMC 时（addEmc）也会兜一层：只有玩家当前是负数才接管。
     *
     * 正常情况下 addEmc 最后会走到 updateEmcAndSync，那里已经由 {@link #applyTableEmc} 处理了；
     * 这里再兜一层是为了防止等价交换哪天把 updateEmcAndSync 改名或改成别的形态——
     * 只要 addEmc 还在，负数就照样能正常抵扣。
     * 返回 true 表示已经写进去了，调用方（Mixin）把原版逻辑跳过。
     */
    public static boolean applyTableAdd(Object transmutationInventory, Object amount) {
        if (!(amount instanceof BigInteger value) || value.signum() <= 0 || !available()) {
            return false;
        }
        // 到账金额大到 long 装不下就不掺和了，交给原版（正常人一辈子也碰不到）
        if (value.bitLength() > 62) {
            return false;
        }
        Object provider = tableField(transmutationInventory, true);
        Object player = tableField(transmutationInventory, false);
        if (!(player instanceof ServerPlayer serverPlayer) || provider == null) {
            return false;
        }
        long current = getEmc(serverPlayer);
        if (current == UNAVAILABLE || current >= 0L) {
            // 不欠钱时原版算得对，别抢它的活
            return false;
        }
        long next = MarketMath.safeAdd(current, value.longValue());
        if (!writeEmcValue(provider, serverPlayer, BigInteger.valueOf(next))) {
            return false;
        }
        updateScore(serverPlayer, BigInteger.valueOf(next));
        tableKeepsNegative = Boolean.TRUE;
        return true;
    }

    /**
     * 开机自检：转化桌到底认不认负数。
     *
     * 做法是临时造一个转化桌的内部对象，把 EMC 摆成 -2 再走一次"卖东西"的加钱流程 +1：
     *   · 结果 -1 → 负数留住了，卖物品能正常抵扣欠款；
     *   · 结果 0  → 原版把负数夹成了 0（Mixin 没生效）。
     * 试完立刻把 EMC 写成玩家原来的数，全程不超过一毫秒，不会动到玩家的钱。
     * 返回 true = 负数能留住；false = 被夹成 0；null = 这次判断不了。
     */
    public static Boolean probeTable(ServerPlayer player) {
        if (tableKeepsNegative != null) {
            return tableKeepsNegative;
        }
        if (probeAttempts >= MAX_PROBE_ATTEMPTS || !available()) {
            return null;
        }
        long before = getEmc(player);
        if (before == UNAVAILABLE) {
            return null;
        }
        probeAttempts++;
        try {
            Class<?> type = Class.forName(TABLE_CLASS);
            Object inventory = null;
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && Player.class.isAssignableFrom(parameters[0])) {
                    inventory = constructor.newInstance(player);
                    break;
                }
            }
            if (inventory == null) {
                return null;
            }
            Method addEmc = findMethod(List.of(type.getMethods()), "addEmc", 1);
            if (addEmc == null || !addEmc.getParameterTypes()[0].isAssignableFrom(BigInteger.class)) {
                return null;
            }
            // 摆成 -2，再按"卖到 1 EMC"走一遍加钱：能留在 -1 就说明负数没被吞掉
            if (!setEmc(player, -2L)) {
                return null;
            }
            try {
                addEmc.invoke(inventory, BigInteger.valueOf(1L));
                long after = getEmc(player);
                tableKeepsNegative = after == -1L ? Boolean.TRUE : after == 0L ? Boolean.FALSE : null;
            } finally {
                // 不管结果如何都要还原，玩家的 EMC 不能留在试出来的数上
                setEmc(player, before);
            }
            return tableKeepsNegative;
        } catch (Throwable throwable) {
            // 版本对不上就下次再试，别把结论记成"不支持"
            return null;
        }
    }

    /** 转化桌里存 provider / player 的字段，第一次用时找一遍就记住。 */
    private static Object tableField(Object inventory, boolean wantProvider) {
        if (inventory == null) {
            return null;
        }
        if (!tableFieldsResolved) {
            tableFieldsResolved = true;
            for (Class<?> current = inventory.getClass(); current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (cachedProviderField == null
                            && field.getType().getName().endsWith("IKnowledgeProvider")) {
                        tryAccessField(field);
                        cachedProviderField = field;
                    } else if (cachedPlayerField == null && Player.class.isAssignableFrom(field.getType())) {
                        tryAccessField(field);
                        cachedPlayerField = field;
                    }
                }
            }
        }
        Field field = wantProvider ? cachedProviderField : cachedPlayerField;
        if (field == null) {
            return null;
        }
        try {
            return field.get(inventory);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void tryAccessField(Field field) {
        try {
            field.setAccessible(true);
        } catch (Throwable ignored) {
            // public 字段不需要也照样能读
        }
    }

    /** 把 BigInteger 原样写进去（负数照写），然后让等价交换刷新客户端。 */
    private static boolean writeEmcValue(Object provider, ServerPlayer player, BigInteger value) {
        try {
            Method method = cachedSetEmc;
            if (method == null) {
                method = findMethod(methodsOf(PROVIDER_CLASS), "setEmc", 1);
                if (method == null) {
                    method = findOn(provider.getClass(), "setEmc", 1);
                }
                if (method == null) {
                    return false;
                }
                cachedSetEmc = method;
            }
            Object argument = convertEmcValue(value, method.getParameterTypes()[0]);
            if (argument == null) {
                return false;
            }
            method.invoke(provider, argument);
        } catch (Throwable throwable) {
            cachedSetEmc = null;
            return false;
        }
        sync(provider, player);
        return true;
    }

    /** BigInteger 直接传给 setEmc；老版本是 long 之类的话再换算一次。 */
    private static Object convertEmcValue(BigInteger value, Class<?> type) {
        if (type.isAssignableFrom(BigInteger.class)) {
            return value;
        }
        return convertEmc(value.longValue(), type);
    }

    /**
     * 等价交换会把 EMC 记到记分板的 emc 分数上，转化桌结算后要跟着更新；
     * 找不到这个工具类也没关系，只是记分板上的数字会旧一点。
     */
    private static void updateScore(ServerPlayer player, BigInteger emc) {
        if (!scoreResolved) {
            scoreResolved = true;
            try {
                Class<?> helper = Class.forName("moze_intel.projecte.utils.PlayerHelper");
                Field criteriaField = helper.getField("SCOREBOARD_EMC");
                Object criteria = criteriaField.get(null);
                for (Method method : helper.getMethods()) {
                    if (!"updateScore".equals(method.getName()) || method.getParameterCount() != 3) {
                        continue;
                    }
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!parameters[0].isAssignableFrom(ServerPlayer.class)
                            || !parameters[1].isInstance(criteria)
                            || !parameters[2].isAssignableFrom(BigInteger.class)) {
                        continue;
                    }
                    tryAccess(method);
                    cachedScore = method;
                    cachedScoreCriteria = criteria;
                    break;
                }
            } catch (Throwable ignored) {
                // 这个版本的记分板接口对不上，跳过
            }
        }
        if (cachedScore == null) {
            return;
        }
        try {
            cachedScore.invoke(null, player, cachedScoreCriteria, emc);
        } catch (Throwable throwable) {
            cachedScore = null;
        }
    }

    /**
     * 物品在等价交换里的 EMC 价值；查不到（没装 / 这个物品没有 EMC）返回 -1。
     * 股市用这个值当「股票的真实价值」，股价会慢慢往它靠。
     */
    public static double itemEmc(String itemId) {
        if (itemId == null) {
            return -1.0D;
        }
        synchronized (ITEM_EMC) {
            Double cached = ITEM_EMC.get(itemId);
            if (cached != null) {
                return cached;
            }
        }
        if (!proxyAvailable()) {
            return -1.0D;
        }
        Object proxy = emcProxy;
        if (proxy == null) {
            return -1.0D;
        }
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) {
                return -1.0D;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return -1.0D;
            }
            if (cachedProxyGetValue != null) {
                Double value = invokeValue(cachedProxyGetValue, proxy, item);
                if (value != null) {
                    return remember(itemId, value);
                }
                cachedProxyGetValue = null;
            }
            List<Method> candidates = new ArrayList<>(methodsOf(PROXY_CLASS));
            candidates.addAll(List.of(proxy.getClass().getMethods()));
            for (Method method : candidates) {
                if (!"getValue".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                tryAccess(method);
                Double value = invokeValue(method, proxy, item);
                if (value != null) {
                    cachedProxyGetValue = method;
                    return remember(itemId, value);
                }
            }
            return -1.0D;
        } catch (Throwable throwable) {
            return -1.0D;
        }
    }

    private static double remember(String itemId, double value) {
        if (!(value > 0.0D)) {
            return -1.0D;
        }
        synchronized (ITEM_EMC) {
            ITEM_EMC.put(itemId, value);
        }
        return value;
    }

    /**
     * EMC 代理（查物品价值用）。
     * 1.21.x 是接口上的静态字段 IEMCProxy.INSTANCE（由 ServiceLoader 塞进来），
     * 1.20.x 是 ProjectEAPI.getEMCProxy()。
     */
    private static synchronized void resolveProxy() {
        if (proxyResolved) {
            return;
        }
        proxyResolved = true;
        try {
            Class<?> proxyClass = Class.forName(PROXY_CLASS);
            for (Field field : proxyClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!"INSTANCE".equals(field.getName()) && field.getType() != proxyClass) {
                    continue;
                }
                Object value = field.get(null);
                if (value != null) {
                    emcProxy = value;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // 没有这个字段就试老写法
        }
        if (emcProxy == null) {
            for (String apiClass : API_CLASSES) {
                try {
                    Method getter = Class.forName(apiClass).getMethod("getEMCProxy");
                    Object value = getter.invoke(null);
                    if (value instanceof java.util.Optional<?> optional) {
                        value = optional.orElse(null);
                    }
                    if (value != null) {
                        emcProxy = value;
                        break;
                    }
                } catch (Throwable ignored) {
                    // 换下一个入口
                }
            }
        }
        proxyReady = emcProxy != null;
    }

    private static Double invokeValue(Method method, Object proxy, Item item) {
        try {
            Class<?> type = method.getParameterTypes()[0];
            Object result;
            if (type.isAssignableFrom(Item.class)) {
                result = method.invoke(proxy, item);
            } else if (type.isAssignableFrom(ItemStack.class)) {
                result = method.invoke(proxy, new ItemStack(item));
            } else {
                return null;
            }
            return result instanceof Number number ? number.doubleValue() : null;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
