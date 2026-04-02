package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.VisibilityCheck;
import com.dysmn.doyouseemenow.detection.DetectionTracker;
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

		Entity attacker = source.getAttacker();
		Entity directSource = source.getSource();
		ModConfig config = ModConfig.get();

		// Case 1: attacker is a player (melee + ranged with known shooter)
		if (attacker instanceof ServerPlayerEntity player) {
			if (player.isCreative() || player.isSpectator()) return;

			// Taking damage boosts detection meter — prevents infinite kite exploit.
			// Melee from behind: large boost (0.4). Ranged from outside FOV: moderate boost (0.25).
			// This way repeated hits will eventually cause full detection + aggro.
			if (config.detectionEnabled && !config.isBlacklisted(self) && !self.getWorld().isClient()) {
				double damageDetectionBoost = (directSource instanceof ProjectileEntity) ? 0.25 : 0.4;
				DetectionTracker.addDamageDetection(self, player, damageDetectionBoost);
			}

			if (VisibilityCheck.canMobSeeTarget(self, attacker)) return;

			Vec3d investigatePos = attacker.getPos();
			double distance = self.distanceTo(attacker);

			double inaccuracy;
			if (directSource instanceof ProjectileEntity) {
				inaccuracy = Math.max(3.0, Math.min(distance * 0.2, config.rangedMaxInaccuracy));
			} else {
				inaccuracy = Math.min(distance * 0.1, config.meleeMaxInaccuracy);
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
