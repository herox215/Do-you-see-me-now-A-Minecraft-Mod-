package com.dysmn.doyouseemenow.sound;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import com.dysmn.doyouseemenow.ModConfig;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Server-side manager that processes sound events and alerts nearby mobs.
 * Mobs that hear a sound get their lastKnownTargetPos set, which triggers
 * the existing SearchLastKnownPositionGoal.
 */
public final class SoundDetectionManager {

	private SoundDetectionManager() {}

	/**
	 * Emit a sound event. Searches for all hearing mobs within radius
	 * and sets their lastKnownTargetPos so they investigate.
	 */
	public static void emitSound(ServerWorld world, SoundEvent event) {
		if (!ModConfig.get().soundDetectionEnabled) return;

		double radius = event.radius();

		Box searchBox = new Box(
			event.position().subtract(radius, radius, radius),
			event.position().add(radius, radius, radius)
		);

		List<MobEntity> nearbyMobs = world.getEntitiesByClass(
			MobEntity.class, searchBox,
			mob -> canMobHear(mob, event, radius)
		);

		for (MobEntity mob : nearbyMobs) {
			Vec3d investigatePos = addInaccuracy(mob, event.position());
			((LastKnownPositionAccess) mob).dysmn$setLastKnownTargetPos(investigatePos);
		}
	}

	/**
	 * Applies the heavy armor radius multiplier to a base sound radius.
	 * Only relevant for WALKING and SPRINTING sounds.
	 */
	public static double applyArmorBonus(PlayerEntity player, double baseRadius) {
		int heavyPieces = countHeavyArmorPieces(player);
		double multiplier = 1.0 + heavyPieces * ModConfig.get().heavyArmorBonusPerPiece;
		return baseRadius * multiplier;
	}

	/**
	 * Counts how many heavy armor pieces (iron, diamond, netherite) the player wears.
	 */
	public static int countHeavyArmorPieces(PlayerEntity player) {
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

	private static boolean canMobHear(MobEntity mob, SoundEvent event, double radius) {
		// Mob already has a target -> ignores sounds
		if (mob.getTarget() != null) return false;

		// Blacklisted mobs (vision bypass) also don't hear
		if (ModConfig.get().isBlacklisted(mob)) return false;

		// Hearing whitelist/blacklist check
		if (!ModConfig.get().canMobHear(mob)) return false;

		// Don't react to own sounds
		if (event.source() != null && event.source() == mob) return false;

		// Precise distance check (box is an approximation)
		double distance = mob.getPos().distanceTo(event.position());
		return distance <= radius;
	}

	private static Vec3d addInaccuracy(MobEntity mob, Vec3d soundPos) {
		double distance = mob.getPos().distanceTo(soundPos);
		double inaccuracy = Math.min(distance * 0.1, 4.0);
		if (inaccuracy < 0.5) return soundPos;

		double ox = (mob.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
		double oz = (mob.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
		return soundPos.add(ox, 0, oz);
	}
}
