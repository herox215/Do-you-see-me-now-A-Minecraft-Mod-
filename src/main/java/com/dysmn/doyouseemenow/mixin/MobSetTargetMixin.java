package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.NetworkConstants;
import com.dysmn.doyouseemenow.VisibilityCheck;
import com.dysmn.doyouseemenow.detection.DetectionTracker;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobSetTargetMixin {

	@Shadow
	@Nullable
	public abstract LivingEntity getTarget();

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void doYouSeeMeNow_onSetTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		LivingEntity oldTarget = this.getTarget();
		MobEntity self = (MobEntity) (Object) this;

		// Blacklisted mobs: always normal behavior
		if (ModConfig.get().isBlacklisted(self)) return;

		// DetectionTracker bypass: detection completed, let setTarget proceed
		if (DetectionTracker.settingDetectedTarget) {
			if (target instanceof ServerPlayerEntity) {
				sendSpottedPacket(self);
			}
			if (target != null) {
				((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(null);
			}
			return;
		}

		// Mobs with non-visual targeting (phantoms, piglins, wolves, etc.) skip FOV checks
		if (ModConfig.get().bypassesFov(self)) return;

		// New target is a player the mob can't see
		// -> don't set target, store position for investigation instead
		if (target instanceof ServerPlayerEntity && target != oldTarget
				&& !VisibilityCheck.canMobSeeTarget(self, target)) {

			Vec3d attackerPos = target.getPos();
			double distance = self.distanceTo(target);

			double inaccuracy = Math.min(distance * 0.15, 10.0);
			if (inaccuracy > 0.5) {
				double offsetX = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				double offsetZ = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				attackerPos = attackerPos.add(offsetX, 0, offsetZ);
			}

			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(attackerPos);
			ci.cancel();
			return;
		}

		// Player is actually seen — if detection meter is enabled, route through it
		if (target instanceof ServerPlayerEntity player && target != oldTarget) {
			// Skip detection meter if the mob was aggroed very recently (e.g. hit from behind)
			if (!DetectionTracker.wasRecentlyAggroed(self)) {
				ModConfig config = ModConfig.get();
				if (config.detectionEnabled && !config.isBlacklisted(self)
						&& !self.getWorld().isClient()) {
					DetectionTracker.onMobDetectsPlayer(self, player);
					ci.cancel();
					return;
				}
			}

			// Detection disabled or recently aggroed — immediate aggro with "!" packet
			sendSpottedPacket(self);
		}

		// Lost player as target -> remember last known position + mark for grace period
		if (oldTarget instanceof ServerPlayerEntity && target == null) {
			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(oldTarget.getPos());
			DetectionTracker.markLostTarget(self);
		}

		// New target set -> clear old search position
		if (target != null) {
			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(null);
		}
	}

	private static void sendSpottedPacket(MobEntity mob) {
		for (ServerPlayerEntity tracker : PlayerLookup.tracking(mob)) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeInt(mob.getId());
			ServerPlayNetworking.send(tracker, NetworkConstants.MOB_SPOTTED_PACKET, buf);
		}
	}
}
