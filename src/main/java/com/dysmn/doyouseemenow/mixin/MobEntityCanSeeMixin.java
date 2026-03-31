package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.VisibilityCheck;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobEntityCanSeeMixin {

	@Inject(method = "canSee", at = @At("RETURN"), cancellable = true)
	private void doYouSeeMeNow_checkFieldOfView(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		if (!((Object) this instanceof MobEntity mob)) return;

		// Already chasing this target — FOV only, no light restriction
		if (mob.getTarget() != null && mob.getTarget() == entity) {
			if (!VisibilityCheck.canMobSeeTarget(mob, entity)) {
				cir.setReturnValue(false);
			}
			return;
		}

		// First detection — FOV + light-based distance
		if (!VisibilityCheck.canMobDetectTarget(mob, entity)) {
			cir.setReturnValue(false);
		}
	}
}
