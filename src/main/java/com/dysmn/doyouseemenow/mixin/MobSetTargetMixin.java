package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import com.dysmn.doyouseemenow.NetworkConstants;
import com.dysmn.doyouseemenow.VisibilityCheck;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
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

		// Ender Dragon and Wither: always normal behavior
		if (self instanceof EnderDragonEntity || self instanceof WitherEntity) return;

		// New target is a player the mob can't see
		// -> don't set target, store position for investigation instead
		if (target instanceof ServerPlayerEntity && target != oldTarget
				&& !VisibilityCheck.canMobSeeTarget(self, target)) {

			// Store position with distance-based inaccuracy
			Vec3d attackerPos = target.getPos();
			double distance = self.distanceTo(target);

			// Melee (< 5 blocks): nearly exact, ranged: less accurate
			double inaccuracy = Math.min(distance * 0.15, 10.0);
			if (inaccuracy > 0.5) {
				double offsetX = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				double offsetZ = (self.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
				attackerPos = attackerPos.add(offsetX, 0, offsetZ);
			}

			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(attackerPos);

			// Don't set target — mob investigates the position instead
			ci.cancel();
			return;
		}

		// Player is actually seen -> send "!" packet
		if (target instanceof ServerPlayerEntity player && target != oldTarget) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeInt(self.getId());
			ServerPlayNetworking.send(player, NetworkConstants.MOB_SPOTTED_PACKET, buf);
		}

		// Lost player as target -> remember last known position
		if (oldTarget instanceof ServerPlayerEntity && target == null) {
			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(oldTarget.getPos());
		}

		// New target set -> clear old search position
		if (target != null) {
			((LastKnownPositionAccess) self).dysmn$setLastKnownTargetPos(null);
		}
	}
}
