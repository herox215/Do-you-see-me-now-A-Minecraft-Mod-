package com.dysmn.doyouseemenow.client;

import com.dysmn.doyouseemenow.VisibilityCheck;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.function.Predicate;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Client-side stealth score calculation.
 * Combines light level, armor, mob proximity, movement, and FOV visibility
 * into a 0.0–1.0 score with smoothing to avoid visual jitter.
 */
public final class StealthCalculator {

	private static double smoothedScore = 0.0;
	private static final double SMOOTHING = 0.08;

	private static boolean cachedInFov = false;
	private static int fovCacheTick = -1;
	private static final int FOV_CACHE_INTERVAL = 10;

	private StealthCalculator() {}

	/**
	 * Calculates the current stealth score (0.0 = exposed, 1.0 = hidden).
	 * Called every client tick while the player is sneaking.
	 */
	public static double calculate(ClientPlayerEntity player, ClientWorld world) {
		int lightLevel = world.getLightLevel(player.getBlockPos());
		double lightScore = (15.0 - lightLevel) / 15.0;

		int heavyPieces = countHeavyArmorPieces(player);
		double armorScore = (4.0 - heavyPieces) / 4.0;

		double nearestDist = findNearestMobDistance(player, world, 48.0);
		double proximityScore = Math.min(nearestDist / 32.0, 1.0);

		double speed = player.getVelocity().horizontalLength();
		double movementScore = speed < 0.001 ? 1.0 : 0.3;

		int currentTick = (int) (world.getTime() % Integer.MAX_VALUE);
		if (currentTick != fovCacheTick
				&& (fovCacheTick < 0 || currentTick - fovCacheTick >= FOV_CACHE_INTERVAL)) {
			cachedInFov = isInAnyMobFov(player, world, 32.0);
			fovCacheTick = currentTick;
		}
		double fovScore = cachedInFov ? 0.0 : 1.0;

		double rawScore = (lightScore * 0.35) + (armorScore * 0.15)
						+ (proximityScore * 0.25) + (movementScore * 0.15)
						+ (fovScore * 0.10);

		smoothedScore += (rawScore - smoothedScore) * SMOOTHING;
		return smoothedScore;
	}

	public static void reset() {
		smoothedScore = 0.0;
		cachedInFov = false;
		fovCacheTick = -1;
	}

	private static int countHeavyArmorPieces(PlayerEntity player) {
		int count = 0;
		for (ItemStack stack : player.getArmorItems()) {
			if (stack.getItem() instanceof ArmorItem armor) {
				var mat = armor.getMaterial();
				if (mat == ArmorMaterials.IRON || mat == ArmorMaterials.DIAMOND
						|| mat == ArmorMaterials.NETHERITE) {
					count++;
				}
			}
		}
		return count;
	}

	private static final Predicate<Entity> HEARING_FILTER = ClientModConfig::canMobHear;

	private static double findNearestMobDistance(ClientPlayerEntity player,
												 ClientWorld world, double searchRadius) {
		Box searchBox = player.getBoundingBox().expand(searchRadius);
		double nearest = searchRadius;
		for (Entity entity : world.getEntitiesByClass(MobEntity.class, searchBox, HEARING_FILTER)) {
			double dist = player.distanceTo(entity);
			if (dist < nearest) nearest = dist;
		}
		return nearest;
	}

	private static boolean isInAnyMobFov(ClientPlayerEntity player,
										 ClientWorld world, double searchRadius) {
		Box searchBox = player.getBoundingBox().expand(searchRadius);
		Vec3d playerCenter = player.getBoundingBox().getCenter();
		for (Entity entity : world.getEntitiesByClass(MobEntity.class, searchBox, HEARING_FILTER)) {
			if (entity instanceof MobEntity mob) {
				if (VisibilityCheck.isInFieldOfView(mob, player)
						&& hasLineOfSight(world, mob, mob.getEyePos(), playerCenter)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasLineOfSight(ClientWorld world, Entity entity, Vec3d from, Vec3d to) {
		RaycastContext context = new RaycastContext(
				from, to,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				entity
		);
		BlockHitResult result = world.raycast(context);
		return result.getType() == HitResult.Type.MISS;
	}
}
