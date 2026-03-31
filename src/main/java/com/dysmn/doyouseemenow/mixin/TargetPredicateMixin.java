package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.VisibilityCheck;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetPredicate.class)
public abstract class TargetPredicateMixin {

	/**
	 * TargetPredicate.test() is used for target selection — always first detection.
	 * Applies FOV + light-based distance check.
	 */
	@Inject(method = "test", at = @At("RETURN"), cancellable = true)
	private void doYouSeeMeNow_checkTargetFov(
			@Nullable LivingEntity baseEntity,
			LivingEntity targetEntity,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValue() && baseEntity instanceof MobEntity mob) {
			if (!VisibilityCheck.canMobDetectTarget(mob, targetEntity)) {
				cir.setReturnValue(false);
			}
		}
	}
}
