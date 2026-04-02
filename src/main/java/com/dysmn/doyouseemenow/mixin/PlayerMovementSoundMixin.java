package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.sound.SoundDetectionManager;
import com.dysmn.doyouseemenow.sound.SoundEvent;
import com.dysmn.doyouseemenow.sound.SoundType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects player movement sounds (sprinting, walking, landing) and emits
 * sound events that nearby mobs can hear.
 * Sneaking produces no sound.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerMovementSoundMixin {

	@Unique
	private int dysmn_movementSoundCooldown = 0;

	@Unique
	private float dysmn_lastFallDistance = 0.0f;

	@Inject(method = "tick", at = @At("TAIL"))
	private void doYouSeeMeNow_checkMovementSound(CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		if (player.getWorld().isClient()) return;
		if (player.isCreative() || player.isSpectator()) return;

		ModConfig config = ModConfig.get();

		// --- Landing detection ---
		if (player.isOnGround() && dysmn_lastFallDistance >= config.landingMinFallDistance) {
			double radius = dysmn_lastFallDistance * config.landingRadiusMultiplier;
			radius = Math.max(config.landingMinRadius, Math.min(radius, config.landingMaxRadius));
			SoundDetectionManager.emitSound(player.getServerWorld(),
				new SoundEvent(player.getPos(), radius, SoundType.LANDING, player));
			dysmn_lastFallDistance = 0.0f;
		}

		// Track fall distance while airborne
		if (!player.isOnGround()) {
			dysmn_lastFallDistance = player.fallDistance;
		} else {
			dysmn_lastFallDistance = 0.0f;
		}

		// --- Movement sounds (sprint / walk) ---
		if (player.isSneaking()) return;

		if (dysmn_movementSoundCooldown > 0) {
			dysmn_movementSoundCooldown--;
			return;
		}

		if (player.isSprinting() && player.isOnGround()) {
			double radius = SoundDetectionManager.applyArmorBonus(player, config.sprintSoundRadius);
			SoundDetectionManager.emitSound(player.getServerWorld(),
				new SoundEvent(player.getPos(), radius, SoundType.SPRINTING, player));
			dysmn_movementSoundCooldown = config.sprintSoundInterval;
		} else if (isWalking(player)) {
			double radius = SoundDetectionManager.applyArmorBonus(player, config.walkSoundRadius);
			SoundDetectionManager.emitSound(player.getServerWorld(),
				new SoundEvent(player.getPos(), radius, SoundType.WALKING, player));
			dysmn_movementSoundCooldown = config.walkSoundInterval;
		}
	}

	@Unique
	private static boolean isWalking(ServerPlayerEntity player) {
		if (!player.isOnGround()) return false;
		if (player.isSprinting()) return false;
		if (player.isSneaking()) return false;
		double speed = player.getVelocity().horizontalLength();
		return speed > 0.01;
	}
}
