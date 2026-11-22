package cn.blockforge.generated.sharedstockexchange.compat;

import cn.blockforge.generated.sharedstockexchange.StockExchangeMod;
import cn.blockforge.generated.sharedstockexchange.market.MarketMath;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class ProjectEmcBridge {
   private static final String MOD_ID = "projecte";
   private static final String[] CAPABILITY_CLASSES = new String[]{
      "moze_intel.projecte.api.capabilities.PECapabilities", "moze_intel.projecte.api.capabilities.PE_CAPABILITIES"
   };
   private static final String PROVIDER_CLASS = "moze_intel.projecte.api.capabilities.IKnowledgeProvider";
   private static final String PROXY_CLASS = "moze_intel.projecte.api.proxy.IEMCProxy";
   private static final String[] API_CLASSES = new String[]{"moze_intel.projecte.api.ProjectEAPI"};
   public static final long UNAVAILABLE = Long.MIN_VALUE;
   public static final String TABLE_CLASS = "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory";
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
   private static volatile Boolean tableKeepsNegative;
   private static volatile boolean tableHooked;
   private static final List<Field> TABLE_PLAYER_FIELDS = new ArrayList<>();
   private static boolean tablePlayerFieldsResolved;
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

   public static boolean available() {
      ensure();
      return connected;
   }

   public static boolean installedButDisconnected() {
      ensure();
      return !connected && modPresent();
   }

   public static boolean proxyAvailable() {
      ensure();
      if (!connected) {
         return false;
      } else {
         resolveProxy();
         return proxyReady;
      }
   }

   public static boolean modPresent() {
      try {
         if (ModList.get().isLoaded("projecte")) {
            return true;
         }
      } catch (Throwable var5) {
      }

      for (String name : CAPABILITY_CLASSES) {
         try {
            Class.forName(name, false, ProjectEmcBridge.class.getClassLoader());
            return true;
         } catch (Throwable var6) {
         }
      }

      return false;
   }

   public static String describe() {
      ensure();
      return connected ? "已接通等价交换（ProjectE）：你的 EMC 就是股市现金" : state;
   }

   public static Component describeComponent() {
      ensure();
      return connected
         ? Component.translatable("command.shared_stock_exchange.cash_pe")
         : Component.translatable("command.shared_stock_exchange.cash_fallback", new Object[]{state});
   }

   public static List<Component> diagnostics() {
      ensure();
      boolean present = modPresent();
      List<Component> lines = new ArrayList<>();
      lines.add(
         Component.translatable(
            "command.shared_stock_exchange.diag_mod",
            new Object[]{
               present
                  ? Component.translatable("command.shared_stock_exchange.diag_loaded", new Object[]{modVersion()})
                  : Component.translatable("command.shared_stock_exchange.diag_absent")
            }
         )
      );
      if (!present) {
         return lines;
      } else {
         lines.add(
            Component.translatable(
               "command.shared_stock_exchange.diag_capability",
               new Object[]{
                  connected
                     ? Component.translatable("command.shared_stock_exchange.diag_ok", new Object[]{capabilitySource})
                     : Component.translatable("command.shared_stock_exchange.diag_failed", new Object[]{state})
               }
            )
         );
         lines.add(
            Component.translatable(
               "command.shared_stock_exchange.diag_proxy",
               new Object[]{
                  proxyAvailable()
                     ? Component.translatable("command.shared_stock_exchange.diag_ok", new Object[]{"IEMCProxy.INSTANCE"})
                     : Component.translatable("command.shared_stock_exchange.diag_no_proxy")
               }
            )
         );
         if (tableKeepsNegative != null) {
            lines.add(
               Component.translatable(
                  "command.shared_stock_exchange.diag_negative",
                  new Object[]{
                     tableKeepsNegative
                        ? Component.translatable("command.shared_stock_exchange.diag_ok", new Object[]{"转化桌可写负数 EMC"})
                        : Component.translatable("command.shared_stock_exchange.diag_negative_failed")
                  }
               )
            );
         }

         return lines;
      }
   }

   private static String modVersion() {
      try {
         return ModList.get().getModContainerById("projecte").map(container -> container.getModInfo().getVersion().toString()).orElse("?");
      } catch (Throwable var1) {
         return "?";
      }
   }

   private static synchronized void ensure() {
      if (!connected && attempts < 64) {
         attempts++;

         try {
            resolve();
         } catch (Throwable var1) {
            connected = false;
            state = "连接等价交换时出错（" + var1.getClass().getSimpleName() + "），先用测试钱包";
         }
      }
   }

   private static void resolve() {
      if (!modPresent()) {
         state = "没装等价交换（ProjectE），正在用模组自带的测试钱包";
      } else {
         Class<?> capabilitiesClass = null;

         for (String name : CAPABILITY_CLASSES) {
            try {
               capabilitiesClass = Class.forName(name);
               break;
            } catch (Throwable var7) {
            }
         }

         if (capabilitiesClass == null) {
            state = "检测到等价交换，但没找到能力类 PECapabilities，先用测试钱包";
         } else {
            Field field = knowledgeField(capabilitiesClass);
            if (field == null) {
               state = "检测到等价交换，但没找到玩家 EMC 的能力字段，先用测试钱包";
            } else {
               Object capability = null;

               try {
                  capability = field.get(null);
               } catch (Throwable var6) {
               }

               if (capability == null) {
                  state = "检测到等价交换，但玩家 EMC 能力字段是空的，先用测试钱包";
               } else {
                  knowledgeCapability = capability;
                  capabilitySource = capabilitiesClass.getSimpleName() + "." + field.getName();
                  List<Method> candidates = capabilityGetters(capability);
                  if (candidates.isEmpty()) {
                     state = "检测到等价交换，但玩家身上取不到 EMC 能力，先用测试钱包";
                  } else {
                     capabilityGetters = candidates;
                     connected = true;
                     state = "已接通等价交换（ProjectE）：你的 EMC 就是股市现金";
                  }
               }
            }
         }
      }
   }

   private static Field knowledgeField(Class<?> owner) {
      Field best = null;

      for (Field field : owner.getFields()) {
         if (Modifier.isStatic(field.getModifiers())) {
            String lower = field.getName().toLowerCase();
            if (lower.contains("knowledge")) {
               if (best == null || lower.contains("capability")) {
                  best = field;
               }

               if (lower.contains("capability")) {
                  return field;
               }
            }
         }
      }

      return best;
   }

   private static List<Method> capabilityGetters(Object capability) {
      List<Method> candidates = new ArrayList<>();

      for (Method method : ServerPlayer.class.getMethods()) {
         if ("getCapability".equals(method.getName())) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length >= 1
               && parameters.length <= 2
               && method.getReturnType() != void.class
               && parameters[0].isInstance(capability)
               && (parameters.length != 2 || !parameters[1].isPrimitive())) {
               candidates.add(method);
            }
         }
      }

      candidates.sort((first, second) -> Integer.compare(first.getParameterCount(), second.getParameterCount()));
      return candidates;
   }

   private static List<Method> methodsOf(String className) {
      try {
         return List.of(Class.forName(className).getMethods());
      } catch (Throwable var2) {
         return List.of();
      }
   }

   private static Method findMethod(List<Method> methods, String name, int parameterCount) {
      for (Method method : methods) {
         if (name.equals(method.getName()) && method.getParameterCount() == parameterCount) {
            tryAccess(method);
            return method;
         }
      }

      return null;
   }

   private static Method findOn(Class<?> owner, String name, int parameterCount) {
      return findMethod(List.of(owner.getMethods()), name, parameterCount);
   }

   private static void tryAccess(Method method) {
      try {
         method.setAccessible(true);
      } catch (Throwable var2) {
      }
   }

   private static Object provider(ServerPlayer player) {
      if (!available()) {
         return null;
      } else {
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
   }

   private static Object invokeGetter(Method method, ServerPlayer player) {
      try {
         Object raw = method.getParameterCount() == 1 ? method.invoke(player, knowledgeCapability) : method.invoke(player, knowledgeCapability, null);
         return unwrap(raw);
      } catch (Throwable var3) {
         return null;
      }
   }

   private static Object unwrap(Object raw) {
      if (raw == null) {
         return null;
      } else if (raw instanceof Optional<?> optional) {
         return optional.orElse(null);
      } else {
         try {
            Method resolve = raw.getClass().getMethod("resolve");
            if (resolve.getParameterCount() == 0 && resolve.invoke(raw) instanceof Optional<?> optional) {
               return optional.orElse(null);
            }
         } catch (Throwable var4) {
         }

         return raw;
      }
   }

   public static long getEmc(ServerPlayer player) {
      Object provider = provider(player);
      if (provider == null) {
         return Long.MIN_VALUE;
      } else {
         try {
            Method method = cachedGetEmc;
            if (method == null) {
               method = findMethod(methodsOf("moze_intel.projecte.api.capabilities.IKnowledgeProvider"), "getEmc", 0);
               if (method == null) {
                  method = findOn(provider.getClass(), "getEmc", 0);
               }

               if (method == null) {
                  return Long.MIN_VALUE;
               }

               cachedGetEmc = method;
            }

            return toEmcLong(method.invoke(provider), Long.MIN_VALUE);
         } catch (Throwable var3) {
            cachedGetEmc = null;
            return Long.MIN_VALUE;
         }
      }
   }

   public static boolean setEmc(ServerPlayer player, long value) {
      if (value == Long.MIN_VALUE) {
         return false;
      } else {
         Object provider = provider(player);
         if (provider == null) {
            return false;
         } else {
            long previous = getEmc(player);
            if (!writeEmc(provider, player, value)) {
               return false;
            } else {
               if (value < 0L) {
                  long written = getEmc(player);
                  if (written != value) {
                     negativeRejected = true;
                     if (previous != Long.MIN_VALUE) {
                        writeEmc(provider, player, previous);
                        updateScore(player, BigInteger.valueOf(previous));
                     }

                     return false;
                  }

                  negativeRejected = false;
               }

               updateScore(player, BigInteger.valueOf(value));
               return true;
            }
         }
      }
   }

   private static boolean writeEmc(Object provider, ServerPlayer player, long value) {
      try {
         Method method = cachedSetEmc;
         if (method == null) {
            method = findMethod(methodsOf("moze_intel.projecte.api.capabilities.IKnowledgeProvider"), "setEmc", 1);
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
      } catch (Throwable var6) {
         cachedSetEmc = null;
         return false;
      }

      sync(provider, player);
      return true;
   }

   public static boolean negativeRejected() {
      return negativeRejected;
   }

   private static Object convertEmc(long value, Class<?> type) {
      if (type == long.class || type == Long.class) {
         return value;
      } else if (type == int.class || type == Integer.class) {
         return (int)Math.min(value, 2147483647L);
      } else if (type == double.class || type == Double.class) {
         return (double)value;
      } else if (type == float.class || type == Float.class) {
         return (float)value;
      } else {
         return type.isAssignableFrom(BigInteger.class) ? BigInteger.valueOf(value) : null;
      }
   }

   private static long toEmcLong(Object value, long fallback) {
      if (value instanceof BigInteger big) {
         if (big.bitLength() < 63) {
            return big.longValue();
         } else {
            return big.signum() < 0 ? -9223372036854775807L : Long.MAX_VALUE;
         }
      } else {
         return value instanceof Number number ? number.longValue() : fallback;
      }
   }

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
      } catch (Throwable var3) {
         cachedSync = null;
      }
   }

   private static Method findSync(Object provider, ServerPlayer player, String name) {
      List<Method> candidates = new ArrayList<>(methodsOf("moze_intel.projecte.api.capabilities.IKnowledgeProvider"));
      candidates.addAll(List.of(provider.getClass().getMethods()));

      for (Method candidate : candidates) {
         if (name.equals(candidate.getName()) && candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0].isInstance(player)) {
            tryAccess(candidate);
            return candidate;
         }
      }

      return null;
   }

   public static Boolean tableKeepsNegative() {
      return tableKeepsNegative;
   }

   public static boolean tableHooked() {
      return tableHooked;
   }

   /** mixin 真的挂到转化桌上时调用一次，用来在日志里确认"欠款不会被夹成 0"这条路是通的。 */
   public static void noteTableHook() {
      if (!tableHooked) {
         tableHooked = true;
         StockExchangeMod.LOGGER
            .info("[共享证券交易所] 已接管转化桌的 EMC 写入：欠款（负数 EMC）会保留，往转化桌卖东西是一点一点还，不会瞬间清零");
      }
   }

   /**
    * 转化桌准备把 EMC 写成"最终值"时调用（updateEmcAndSync / setEmc）。
    * 只有负数才接管：等价交换会把负数夹成 0，欠款就会凭空消失。
    * 返回 true 表示本模组已经写好，调用方应当取消原版那次写入。
    */
   public static boolean applyTableSet(Object transmutationInventory, Object emc) {
      if (transmutationInventory == null || !available()) {
         return false;
      } else {
         BigInteger value = toBigInteger(emc);
         if (value == null || value.signum() >= 0 || value.bitLength() > 62) {
            return false;
         } else {
            ServerPlayer player = tablePlayer(transmutationInventory);
            if (player == null) {
               return false;
            } else if (!setEmc(player, value.longValue())) {
               return false;
            } else {
               tableKeepsNegative = Boolean.TRUE;
               return true;
            }
         }
      }
   }

   /**
    * 转化桌准备给 EMC 加钱时调用（玩家在转化桌里卖东西走的就是 addEmc）。
    * 欠款期间接管：负数 + 卖出的钱写回去，欠款一点点归零；
    * 没欠款时返回 false，完全交给等价交换自己处理。
    */
   public static boolean applyTableAdd(Object transmutationInventory, Object amount) {
      if (transmutationInventory == null || !available()) {
         return false;
      } else {
         BigInteger value = toBigInteger(amount);
         if (value == null || value.signum() <= 0 || value.bitLength() > 62) {
            return false;
         } else {
            ServerPlayer player = tablePlayer(transmutationInventory);
            if (player == null) {
               return false;
            } else {
               long current = getEmc(player);
               if (current != Long.MIN_VALUE && current < 0L) {
                  long next = MarketMath.safeAdd(current, value.longValue());
                  if (!setEmc(player, next)) {
                     return false;
                  } else {
                     tableKeepsNegative = Boolean.TRUE;
                     return true;
                  }
               } else {
                  return false;
               }
            }
         }
      }
   }

   /** 只读状态：true 表示已经亲眼看到负数被保留下来，false 目前不会出现，null 表示还没验证过。 */
   public static Boolean probeTable() {
      return tableKeepsNegative;
   }

   private static BigInteger toBigInteger(Object value) {
      if (value instanceof BigInteger big) {
         return big;
      } else {
         return value instanceof Number number ? BigInteger.valueOf(number.longValue()) : null;
      }
   }

   /** 转化桌对象里存着是哪个玩家在用它；只有服务端的真玩家才能安全改 EMC。 */
   private static ServerPlayer tablePlayer(Object inventory) {
      if (!tablePlayerFieldsResolved) {
         tablePlayerFieldsResolved = true;
         for (Class<?> current = inventory.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
               if (Modifier.isStatic(field.getModifiers())) {
                  continue;
               }

               tryAccessField(field);
               if (Player.class.isAssignableFrom(field.getType())) {
                  TABLE_PLAYER_FIELDS.add(field);
               } else {
                  // 字段声明类型不一定写成 Player（有的版本用父类或泛型），直接看一眼值更保险。
                  try {
                     if (field.get(inventory) instanceof Player) {
                        TABLE_PLAYER_FIELDS.add(field);
                     }
                  } catch (Throwable var8) {
                  }
               }
            }
         }
      }

      for (Field field : TABLE_PLAYER_FIELDS) {
         try {
            if (field.get(inventory) instanceof ServerPlayer player) {
               return player;
            }
         } catch (Throwable var7) {
         }
      }

      return null;
   }

   private static void tryAccessField(Field field) {
      try {
         field.setAccessible(true);
      } catch (Throwable var2) {
      }
   }

   private static void updateScore(ServerPlayer player, BigInteger emc) {
      if (!scoreResolved) {
         scoreResolved = true;

         try {
            Class<?> helper = Class.forName("moze_intel.projecte.utils.PlayerHelper");
            Field criteriaField = helper.getField("SCOREBOARD_EMC");
            Object criteria = criteriaField.get(null);

            for (Method method : helper.getMethods()) {
               if ("updateScore".equals(method.getName()) && method.getParameterCount() == 3) {
                  Class<?>[] parameters = method.getParameterTypes();
                  if (parameters[0].isAssignableFrom(ServerPlayer.class)
                     && parameters[1].isInstance(criteria)
                     && parameters[2].isAssignableFrom(BigInteger.class)) {
                     tryAccess(method);
                     cachedScore = method;
                     cachedScoreCriteria = criteria;
                     break;
                  }
               }
            }
         } catch (Throwable var11) {
         }
      }

      if (cachedScore != null) {
         try {
            cachedScore.invoke(null, player, cachedScoreCriteria, emc);
         } catch (Throwable var10) {
            cachedScore = null;
         }
      }
   }

   public static double itemEmc(String itemId) {
      if (itemId == null) {
         return -1.0;
      } else {
         synchronized (ITEM_EMC) {
            Double cached = ITEM_EMC.get(itemId);
            if (cached != null) {
               return cached;
            }
         }

         if (!proxyAvailable()) {
            return -1.0;
         } else {
            Object proxy = emcProxy;
            if (proxy == null) {
               return -1.0;
            } else {
               try {
                  ResourceLocation id = ResourceLocation.tryParse(itemId);
                  if (id == null) {
                     return -1.0;
                  } else {
                     Item item = (Item)BuiltInRegistries.ITEM.get(id);
                     if (item == null) {
                        return -1.0;
                     } else {
                        if (cachedProxyGetValue != null) {
                           Double value = invokeValue(cachedProxyGetValue, proxy, item);
                           if (value != null) {
                              return remember(itemId, value);
                           }

                           cachedProxyGetValue = null;
                        }

                        List<Method> candidates = new ArrayList<>(methodsOf("moze_intel.projecte.api.proxy.IEMCProxy"));
                        candidates.addAll(List.of(proxy.getClass().getMethods()));

                        for (Method method : candidates) {
                           if ("getValue".equals(method.getName()) && method.getParameterCount() == 1) {
                              tryAccess(method);
                              Double value = invokeValue(method, proxy, item);
                              if (value != null) {
                                 cachedProxyGetValue = method;
                                 return remember(itemId, value);
                              }
                           }
                        }

                        return -1.0;
                     }
                  }
               } catch (Throwable var8) {
                  return -1.0;
               }
            }
         }
      }
   }

   private static double remember(String itemId, double value) {
      if (!(value > 0.0)) {
         return -1.0;
      } else {
         synchronized (ITEM_EMC) {
            ITEM_EMC.put(itemId, value);
            return value;
         }
      }
   }

   private static synchronized void resolveProxy() {
      if (!proxyResolved) {
         proxyResolved = true;

         try {
            Class<?> proxyClass = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");

            for (Field field : proxyClass.getFields()) {
               if (Modifier.isStatic(field.getModifiers()) && ("INSTANCE".equals(field.getName()) || field.getType() == proxyClass)) {
                  Object value = field.get(null);
                  if (value != null) {
                     emcProxy = value;
                     break;
                  }
               }
            }
         } catch (Throwable var8) {
         }

         if (emcProxy == null) {
            for (String apiClass : API_CLASSES) {
               try {
                  Method getter = Class.forName(apiClass).getMethod("getEMCProxy");
                  Object value = getter.invoke(null);
                  if (value instanceof Optional<?> optional) {
                     value = optional.orElse(null);
                  }

                  if (value != null) {
                     emcProxy = value;
                     break;
                  }
               } catch (Throwable var7) {
               }
            }
         }

         proxyReady = emcProxy != null;
      }
   }

   private static Double invokeValue(Method method, Object proxy, Item item) {
      try {
         Class<?> type = method.getParameterTypes()[0];
         Object result;
         if (type.isAssignableFrom(Item.class)) {
            result = method.invoke(proxy, item);
         } else {
            if (!type.isAssignableFrom(ItemStack.class)) {
               return null;
            }

            result = method.invoke(proxy, new ItemStack(item));
         }

         return result instanceof Number number ? number.doubleValue() : null;
      } catch (Throwable var6) {
         return null;
      }
   }
}
