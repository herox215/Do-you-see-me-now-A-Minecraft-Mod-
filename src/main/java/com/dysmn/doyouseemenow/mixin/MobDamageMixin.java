package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import com.dysmn.doyouseemenow.VisibilityCheck;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobDamageMixin {

	/**
	 * When a mob takes damage from a player it can't see,
	 * store the approximate attacker position for investigation.
	 *
	 * Handles:
	 * - Melee (sword etc.): player position directly
	 * - Ranged (arrow/trident): player position with more inaccuracy
	 * - Other projectiles: impact position as fallback
	 */
	@Inject(method = "damage", at = @At("HEAD"))
	private void doYouSeeMeNow_onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof MobEntity self)) return;

		// Try to find the actual attacker (for arrows: the shooter)
		Entity attacker = source.getAttacker();
		// Direct damage source (for arrows: the arrow itself)
		Entity directSource = source.getSource();

		// Case 1: attacker is a player (melee + ranged with known shooter)
		if (attacker instanceof ServerPlayerEntity) {
			if (VisibilityCheck.canMobSeeTarget(self, attacker)) return;

			Vec3d investigatePos = attacker.getPos();
			double distance = self.distanceTo(attacker);

			// Inaccuracy: higher for ranged attacks
			double inaccuracy;
			if (directSource instanceof ProjectileEntity) {
				// Ranged: mob only roughly knows where the shot came from
				inaccuracy = Math.max(3.0, Math.min(distance * 0.2, 12.0));
			} else {
				// Melee: position is fairly accurate
				inaccuracy = Math.min(distance * 0.1, 3.0);
			}

			if (inaccuracy > 0.5) {
				double offsetX = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				double offsetZ = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				investigatePos = investigatePos.add(offsetX, 0, offsetZ);
			}

			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(investigatePos);
			return;
		}

		// Case 2: no known attacker, but a projectile hit
		// -> investigate the impact point
		if (directSource instanceof ProjectileEntity projectile) {
			Vec3d impactPos = projectile.getPos();
			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(impactPos);
		}
	}
}
