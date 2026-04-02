package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.sound.SoundDetectionManager;
import com.dysmn.doyouseemenow.sound.SoundEvent;
import com.dysmn.doyouseemenow.sound.SoundType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a sound when a projectile (arrow, snowball, egg, trident, etc.) impacts.
 * Tactical use: shoot an arrow into a corner to lure mobs away.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileImpactSoundMixin {

	@Inject(method = "onCollision", at = @At("TAIL"))
	private void doYouSeeMeNow_projectileImpactSound(HitResult hitResult, CallbackInfo ci) {
		ProjectileEntity projectile = (ProjectileEntity) (Object) this;
		if (projectile.getWorld().isClient()) return;

		// Only player-fired projectiles create sound events.
		// Mob projectiles (skeleton arrows, blaze fireballs) are ignored
		// to prevent mobs investigating their own projectile impacts.
		if (!(projectile.getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity owner)) return;
		if (owner.isCreative() || owner.isSpectator()) return;

		Vec3d impactPos = hitResult.getPos();
		ServerWorld world = (ServerWorld) projectile.getWorld();

		SoundDetectionManager.emitSound(world,
			new SoundEvent(impactPos, ModConfig.get().projectileImpactRadius,
				SoundType.PROJECTILE_IMPACT, projectile));
	}
}
