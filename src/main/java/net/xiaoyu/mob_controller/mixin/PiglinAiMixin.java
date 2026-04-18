package net.xiaoyu.mob_controller.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.xiaoyu.mob_controller.util.MobControlUtil;
import net.xiaoyu.mob_controller.util.MobControlledData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 猪灵 AI 行为注入。
 *
 * <p>受控猪灵会过滤玩家相关仇恨记忆，避免自动敌对玩家。</p>
 */
@Mixin(PiglinAi.class)
public class PiglinAiMixin {
    @Inject(method = "findNearestValidAttackTarget", at = @At("HEAD"))
    private static void clearPlayerHostilityMemory(Piglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
        if (!MobControlledData.isControlledEntity(piglin)) {
            return;
        }

        Brain<Piglin> brain = piglin.getBrain();
        Optional<LivingEntity> angerTarget = BehaviorUtils.getLivingEntityFromUUIDMemory(piglin, MemoryModuleType.ANGRY_AT);
        if (angerTarget.isPresent()) {
            LivingEntity t = angerTarget.get();
            boolean shouldErase = t instanceof Player
                    ? MobControlUtil.isController(piglin, t)
                    : !MobControlUtil.canKeepCombatTarget(piglin, t);
            if (shouldErase) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }

        Optional<? extends LivingEntity> attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
        if (attackTarget.isPresent()) {
            LivingEntity t = attackTarget.get();
            boolean shouldErase = t instanceof Player
                    ? MobControlUtil.isController(piglin, t)
                    : !MobControlUtil.canKeepCombatTarget(piglin, t);
            if (shouldErase) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }

        Optional<? extends LivingEntity> nearestVisibleNemesis = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        if (nearestVisibleNemesis.isPresent() && !MobControlUtil.canKeepCombatTarget(piglin, nearestVisibleNemesis.get())) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        }

        // 只移除控制者本人对应的可攻击玩家记忆，其余玩家按原版逻辑保留
        Optional<Player> attackablePlayer = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
        if (attackablePlayer.isPresent() && MobControlUtil.isController(piglin, attackablePlayer.get())) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
        }
    }

    @Inject(method = "findNearestValidAttackTarget", at = @At("RETURN"), cancellable = true)
    private static void filterNeutralPlayerTarget(Piglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
        if (!MobControlledData.isControlledEntity(piglin)) {
            return;
        }

        Optional<? extends LivingEntity> result = cir.getReturnValue();
        if (result.isPresent()) {
            LivingEntity target = result.get();
            if (target instanceof Player) {
                // 只过滤控制者本人，其余玩家按原版逻辑保留
                if (MobControlUtil.isController(piglin, target)) {
                    cir.setReturnValue(Optional.empty());
                }
            } else if (!MobControlUtil.canKeepCombatTarget(piglin, target)) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }
}

