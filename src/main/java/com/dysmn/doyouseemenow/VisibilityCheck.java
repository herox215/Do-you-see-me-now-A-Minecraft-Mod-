package com.dysmn.doyouseemenow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

public final class VisibilityCheck {

	// Field of view in degrees (60° per side = 120° total)
	private static final double FOV_DEGREES = 120.0;
	private static final double FOV_HALF_COS = Math.cos(Math.toRadians(FOV_DEGREES / 2.0));

	// Light-based detection range: lightLevel * BLOCKS_PER_LIGHT
	private static final double BLOCKS_PER_LIGHT = 2.0;
	// Minimum detection range (even in total darkness)
	private static final double MIN_DETECTION_RANGE = 2.0;

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
		if (target.getWorld() == null) return MIN_DETECTION_RANGE;

		BlockPos targetPos = target.getBlockPos();

		// Combined light level (block light + sky light)
		int lightLevel = target.getWorld().getLightLevel(targetPos);

		return Math.max(MIN_DETECTION_RANGE, lightLevel * BLOCKS_PER_LIGHT);
	}

	private static boolean isWithinLightBasedRange(MobEntity mob, Entity target) {
		double maxRange = getDetectionRange(target);
		double distance = mob.getEyePos().distanceTo(target.getBoundingBox().getCenter());
		return distance <= maxRange;
	}

	private static boolean isInFieldOfView(MobEntity mob, Entity target) {
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

		return dot >= FOV_HALF_COS;
	}
}
