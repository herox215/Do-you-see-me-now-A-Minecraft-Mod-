package com.dysmn.doyouseemenow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class VisibilityCheck {

	private VisibilityCheck() {}

	/**
	 * Checks if a mob can see its target (FOV only).
	 * Used for mobs already chasing a target — no light restriction.
	 */
	public static boolean canMobSeeTarget(MobEntity mob, Entity target) {
		if (ModConfig.get().isBlacklisted(mob)) {
			return true;
		}
		return isInFieldOfView(mob, target);
	}

	/**
	 * Checks if a mob can initially detect a target (FOV + max range).
	 * Light level does NOT affect whether a mob can see — only how fast
	 * the detection meter fills (handled in DetectionTracker).
	 */
	public static boolean canMobDetectTarget(MobEntity mob, Entity target) {
		if (ModConfig.get().isBlacklisted(mob)) {
			return true;
		}

		if (!isInFieldOfView(mob, target)) {
			return false;
		}

		double distance = mob.getEyePos().distanceTo(target.getBoundingBox().getCenter());
		return distance <= ModConfig.get().maxDetectionRange;
	}

	public static boolean isInFieldOfView(MobEntity mob, Entity target) {
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
