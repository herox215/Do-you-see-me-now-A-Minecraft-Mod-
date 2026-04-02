package com.dysmn.doyouseemenow;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class VisibilityCheck {

	private VisibilityCheck() {}

	/**
	 * Per-tick FOV result cache. Key = (mobId << 32) | targetId.
	 * Cleared at the end of each server tick via clearCache().
	 */
	private static final Map<Long, Boolean> fovCache = new HashMap<>();

	/**
	 * Checks if a mob can see its target (FOV only).
	 * Used for mobs already chasing a target — no range restriction.
	 */
	public static boolean canMobSeeTarget(MobEntity mob, Entity target) {
		if (ModConfig.get().isBlacklisted(mob)) {
			return true;
		}
		return isInFieldOfViewCached(mob, target);
	}

	/**
	 * Checks if a mob can initially detect a target (FOV + max range).
	 */
	public static boolean canMobDetectTarget(MobEntity mob, Entity target) {
		if (ModConfig.get().isBlacklisted(mob)) {
			return true;
		}

		if (!isInFieldOfViewCached(mob, target)) {
			return false;
		}

		double distance = mob.getEyePos().distanceTo(target.getBoundingBox().getCenter());
		return distance <= ModConfig.get().maxDetectionRange;
	}

	/**
	 * Cached FOV check. Same mob+target pair within a tick returns the cached result.
	 */
	private static boolean isInFieldOfViewCached(MobEntity mob, Entity target) {
		long key = ((long) mob.getId() << 32) | (target.getId() & 0xFFFFFFFFL);
		Boolean cached = fovCache.get(key);
		if (cached != null) return cached;

		boolean result = isInFieldOfView(mob, target);
		fovCache.put(key, result);
		return result;
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

	/**
	 * Clear the FOV cache. Call at end of each server tick.
	 */
	public static void clearCache() {
		fovCache.clear();
	}
}
