package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.VisibilityCheck;
import com.dysmn.doyouseemenow.detection.DetectionTracker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
	 * When the detection meter is enabled, delays targeting until detection reaches 100%.
	 */
	@Inject(method = "test", at = @At("RETURN"), cancellable = true)
	private void doYouSeeMeNow_checkTargetFov(
			@Nullable LivingEntity baseEntity,
			LivingEntity targetEntity,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValue() && baseEntity instanceof MobEntity mob) {
			// Skip FOV checks for mobs with non-visual targeting
			if (ModConfig.get().bypassesFov(mob)) return;
			// Already chasing this target — FOV only, skip detection meter
			if (mob.getTarget() == targetEntity) {
				if (!VisibilityCheck.canMobSeeTarget(mob, targetEntity)) {
					cir.setReturnValue(false);
				}
				return;
			}

			if (!VisibilityCheck.canMobDetectTarget(mob, targetEntity)) {
				cir.setReturnValue(false);
				return;
			}

			// Detection meter: delay targeting for non-blacklisted mobs
			// Skip if mob was aggroed recently (e.g. briefly lost FOV during combat)
			ModConfig config = ModConfig.get();
			if (config.detectionEnabled && !config.isBlacklisted(mob)
					&& !DetectionTracker.wasRecentlyAggroed(mob)
					&& targetEntity instanceof ServerPlayerEntity player
					&& !mob.getWorld().isClient()) {
				DetectionTracker.onMobDetectsPlayer(mob, player);
				cir.setReturnValue(false);
			}
		}
	}
}
