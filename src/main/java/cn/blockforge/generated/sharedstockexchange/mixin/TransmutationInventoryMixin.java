package cn.blockforge.generated.sharedstockexchange.mixin;

import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让等价交换（ProjectE）的转化桌认得"负数 EMC"（也就是欠款）。
 *
 * <p>原版转化桌在把 EMC 写回玩家身上之前会把负数夹成 0，所以玩家欠了钱以后，
 * 只要往转化桌里卖掉任何一件东西，EMC 就会从负数直接变成 0（欠款凭空消失）。
 * 这里在它写之前接管：
 * <ul>
 *   <li>要写负数 —— 直接把负数写回玩家，取消原版那次"夹 0"的写入；</li>
 *   <li>玩家正在欠款时卖东西 —— 用"当前负数 + 卖出的钱"写回，于是欠款一点一点还清。</li>
 * </ul>
 *
 * <p>所有注入点都写了 {@code require = 0}：等价交换换了版本、改了方法名，
 * 注入不上去也只是退回原版行为，绝不会让游戏崩溃。方法名同时覆盖
 * BigInteger / long / int 三种可能的签名，哪一个在就用哪一个。
 */
@Mixin(
   targets = {"moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory"},
   remap = false
)
public class TransmutationInventoryMixin {
   // 写入最终值：updateEmcAndSync(最终值) 和 setEmc(最终值)
   @Inject(
      method = {"updateEmcAndSync(Ljava/math/BigInteger;)V", "setEmc(Ljava/math/BigInteger;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void sharedstockexchange$writeNegativeBigInteger(BigInteger emc, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableSet(this, emc)) {
         callback.cancel();
      }
   }

   @Inject(
      method = {"updateEmcAndSync(J)V", "setEmc(J)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void sharedstockexchange$writeNegativeLong(long emc, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableSet(this, BigInteger.valueOf(emc))) {
         callback.cancel();
      }
   }

   @Inject(
      method = {"updateEmcAndSync(I)V", "setEmc(I)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void sharedstockexchange$writeNegativeInt(int emc, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableSet(this, BigInteger.valueOf((long)emc))) {
         callback.cancel();
      }
   }

   // 加钱：addEmc(增加量)。欠款期间接管，让欠款慢慢归零。
   @Inject(method = {"addEmc(Ljava/math/BigInteger;)V"}, at = {@At("HEAD")}, cancellable = true, require = 0)
   private void sharedstockexchange$addOntoNegativeBigInteger(BigInteger amount, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableAdd(this, amount)) {
         callback.cancel();
      }
   }

   @Inject(method = {"addEmc(J)V"}, at = {@At("HEAD")}, cancellable = true, require = 0)
   private void sharedstockexchange$addOntoNegativeLong(long amount, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableAdd(this, BigInteger.valueOf(amount))) {
         callback.cancel();
      }
   }

   @Inject(method = {"addEmc(I)V"}, at = {@At("HEAD")}, cancellable = true, require = 0)
   private void sharedstockexchange$addOntoNegativeInt(int amount, CallbackInfo callback) {
      ProjectEmcBridge.noteTableHook();
      if (ProjectEmcBridge.applyTableAdd(this, BigInteger.valueOf((long)amount))) {
         callback.cancel();
      }
   }
}
