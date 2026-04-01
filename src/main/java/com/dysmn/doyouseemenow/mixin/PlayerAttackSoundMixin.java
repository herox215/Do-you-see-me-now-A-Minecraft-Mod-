package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.sound.SoundDetectionManager;
import com.dysmn.doyouseemenow.sound.SoundEvent;
import com.dysmn.doyouseemenow.sound.SoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits an attack sound when a player attacks an entity.
 * One-hit kills (target dies from the attack) produce no sound — stealth kill.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerAttackSoundMixin {

	@Inject(method = "attack", at = @At("TAIL"))
	private void doYouSeeMeNow_attackSound(Entity target, CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

		// One-hit kill → silent (stealth kill)
		if (target instanceof LivingEntity living && living.isDead()) {
			return;
		}

		SoundDetectionManager.emitSound(player.getServerWorld(),
			new SoundEvent(target.getPos(), ModConfig.get().attackSoundRadius,
				SoundType.ATTACK, player));
	}
}
