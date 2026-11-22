package cn.blockforge.generated.sharedstockexchange.mixin;

import cn.blockforge.generated.sharedstockexchange.compat.ProjectEmcBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

/**
 * 跟「等价交换」（ProjectE）的小补丁：让转化桌在结算 EMC 时保留负数。
 *
 * 等价交换原来的逻辑在 updateEmcAndSync 里：算完如果 EMC 小于 0，就直接改成 0 再存。
 * 本模组允许 EMC 是负数（炒股亏了会变成负数），这条夹 0 会让玩家"随便卖个东西，
 * 欠的钱就全没了"，和玩法冲突。所以这里把负数那一段接过来：
 * 按原值写进玩家的 EMC，再补上原版会做的客户端同步和记分板更新（见 ProjectEmcBridge）。
 *
 * 三点保证这个补丁不会连累没装等价交换的人：
 *   · 目标类用字符串指定（{@link ProjectEmcBridge#TABLE_CLASS}），没装等价交换时这个类根本不会加载，
 *     补丁自然也不会生效；
 *   · 注入方法是 require = 0，万一哪个版本的等价交换改名或改签名，这里会安静地跳过，游戏不会崩；
 *   · 注入的代码里不出现任何等价交换的类型，反射失败时直接放行，让原版逻辑照跑。
 *
 * 补丁到底有没有生效，服务器启动后 /stock status 的自检会如实报告。
 */
@Mixin(targets = ProjectEmcBridge.TABLE_CLASS, remap = false)
public class TransmutationInventoryMixin {
    /** 转化桌结算 EMC 的收口：算完是负数时按原值写进去，不夹 0。 */
    @Inject(method = "updateEmcAndSync(Ljava/math/BigInteger;)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void sharedstockexchange$writeNegativeEmc(BigInteger emc, CallbackInfo callback) {
        // 非负数交给等价交换自己处理（它本来就是对的），只接负数这一支
        if (emc == null || emc.signum() >= 0) {
            return;
        }
        if (ProjectEmcBridge.applyTableEmc(this, emc)) {
            // 已经按原值写进去了（包括同步和记分板），原版那段夹 0 就不用跑了
            callback.cancel();
        }
    }

    /**
     * 兜底：等价交换哪天把 updateEmcAndSync 改掉时，只要"往 EMC 里加钱"这一步还在，
     * 负数期间卖物品就照样能抵扣欠款（正数不掺和，原版照跑）。
     */
    @Inject(method = "addEmc(Ljava/math/BigInteger;)V", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void sharedstockexchange$addOntoNegative(BigInteger amount, CallbackInfo callback) {
        if (ProjectEmcBridge.applyTableAdd(this, amount)) {
            callback.cancel();
        }
    }
}
