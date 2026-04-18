package net.xiaoyu.mob_controller.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import net.minecraft.world.entity.player.Player;
import net.xiaoyu.mob_controller.util.MobControlUtil;
import net.xiaoyu.mob_controller.util.MobControlledData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 猪灵蛮兵 AI 行为注入。
 *
 * <p>受控猪灵蛮兵在寻找攻击目标时会移除与控制者相关的仇恨记忆。</p>
 */
@Mixin(PiglinBruteAi.class)
public class PiglinBruteAiMixin {
    /**
     * 注入 {@code findNearestValidAttackTarget} 头部：清除指向控制者的愤怒与目标记忆。
     */
    @Inject(method = "findNearestValidAttackTarget", at = @At("HEAD"))
    private static void excludeOwnerFromTargeting(AbstractPiglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
        PiglinBrute brute = (PiglinBrute) piglin;
        if (MobControlledData.isControlledEntity(brute)) {
            Brain<PiglinBrute> brain = brute.getBrain();

            // 清除不应继续敌对的记忆目标（玩家中立也在此覆盖）。
            Optional<LivingEntity> angerTarget = BehaviorUtils.getLivingEntityFromUUIDMemory(brute, MemoryModuleType.ANGRY_AT);
            if (angerTarget.isPresent()) {
                LivingEntity t = angerTarget.get();
                boolean shouldErase = t instanceof Player
                        ? MobControlUtil.isController(brute, t)
                        : !MobControlUtil.canKeepCombatTarget(brute, t);
                if (shouldErase) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            }

            Optional<? extends LivingEntity> attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
            if (attackTarget.isPresent()) {
                LivingEntity t = attackTarget.get();
                boolean shouldErase = t instanceof Player
                        ? MobControlUtil.isController(brute, t)
                        : !MobControlUtil.canKeepCombatTarget(brute, t);
                if (shouldErase) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }

            // 只移除控制者本人对应的可攻击玩家记忆，其余玩家按原版逻辑保留
            Optional<Player> attackablePlayer = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
            if (attackablePlayer.isPresent() && MobControlUtil.isController(brute, attackablePlayer.get())) {
                brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
            }

            Optional<? extends LivingEntity> nearestVisibleNemesis = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
            if (nearestVisibleNemesis.isPresent() && !MobControlUtil.canKeepCombatTarget(brute, nearestVisibleNemesis.get())) {
                brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
            }
        }
    }

    @Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
    private static void filterNeutralTarget(AbstractPiglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
        PiglinBrute brute = (PiglinBrute) piglin;
        if (!MobControlledData.isControlledEntity(brute)) {
            return;
        }

        Optional<? extends LivingEntity> result = cir.getReturnValue();
        if (result.isPresent()) {
            LivingEntity target = result.get();
            if (target instanceof Player) {
                // 只过滤控制者本人，其余玩家按原版逻辑保留
                if (MobControlUtil.isController(brute, target)) {
                    cir.setReturnValue(Optional.empty());
                }
            } else if (!MobControlUtil.canKeepCombatTarget(brute, target)) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }
}
