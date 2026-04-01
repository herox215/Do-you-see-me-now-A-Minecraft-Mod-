package com.dysmn.doyouseemenow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

public final class VisibilityCheck {

	private VisibilityCheck() {}

	/**
	 * Checks if a mob can see its target (FOV only).
	 * Used for mobs already chasing a target — no light restriction.
	 */
	public static boolean canMobSeeTarget(MobEntity mob, Entity target) {
		if (mob instanceof EnderDragonEntity || mob instanceof WitherEntity) {
			return true;
		}
		return isInFieldOfView(mob, target);
	}

	/**
	 * Checks if a mob can initially detect a target (FOV + light-based distance).
	 * Used for first detection: range depends on the light level at the target.
	 */
	public static boolean canMobDetectTarget(MobEntity mob, Entity target) {
		if (mob instanceof EnderDragonEntity || mob instanceof WitherEntity) {
			return true;
		}

		if (!isInFieldOfView(mob, target)) {
			return false;
		}

		return isWithinLightBasedRange(mob, target);
	}

	/**
	 * Calculates the maximum detection range based on the light level at the target.
	 */
	public static double getDetectionRange(Entity target) {
		if (target.getWorld() == null) return ModConfig.get().minDetectionRange;

		BlockPos targetPos = target.getBlockPos();
		int lightLevel = target.getWorld().getLightLevel(targetPos);

		return Math.max(ModConfig.get().minDetectionRange, lightLevel * ModConfig.get().blocksPerLight);
	}

	private static boolean isWithinLightBasedRange(MobEntity mob, Entity target) {
		double maxRange = getDetectionRange(target);
		double distance = mob.getEyePos().distanceTo(target.getBoundingBox().getCenter());
		return distance <= maxRange;
	}

	private static boolean isInFieldOfView(MobEntity mob, Entity target) {
		double fovHalfCos = Math.cos(Math.toRadians(ModConfig.get().fovDegrees / 2.0));

		float headYaw = mob.getHeadYaw();
		float pitch = mob.getPitch();

		double yawRad = Math.toRadians(headYaw);
		double pitchRad = Math.toRadians(pitch);
		double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
		double lookY = -Math.sin(pitchRad);
		double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);
		Vec3d lookDir = new Vec3d(lookX, lookY, lookZ).normalize();

		Vec3d mobEyes = mob.getEyePos();
		Vec3d targetCenter = target.getBoundingBox().getCenter();
		Vec3d toTarget = targetCenter.subtract(mobEyes).normalize();

		double dot = lookDir.dotProduct(toTarget);

		return dot >= fovHalfCos;
	}
}
