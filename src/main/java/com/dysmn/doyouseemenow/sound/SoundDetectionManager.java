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

import java.util.*;

/**
 * Server-side manager that processes sound events and alerts nearby mobs.
 *
 * Performance optimization: instead of scanning entities on every sound event,
 * a global cooldown per player limits entity scans to at most once per
 * SCAN_COOLDOWN_TICKS. Sounds during cooldown are queued and the loudest
 * one is used when the cooldown expires.
 */
public final class SoundDetectionManager {

	/** Minimum ticks between entity scans per player. */
	private static final int SCAN_COOLDOWN_TICKS = 8;

	/** Pending sound per player — only the loudest (largest radius) is kept. */
	private static final Map<UUID, PendingSound> pendingSounds = new HashMap<>();

	/** Last scan tick per player. */
	private static final Map<UUID, Long> lastScanTick = new HashMap<>();

	private SoundDetectionManager() {}

	/**
	 * Emit a sound event. If the player is on cooldown, the sound is queued.
	 * Otherwise processes immediately and starts a new cooldown.
	 */
	public static void emitSound(ServerWorld world, SoundEvent event) {
		if (!ModConfig.get().soundDetectionEnabled) return;

		long worldTime = world.getTime();

		// Non-player sounds (projectiles) process immediately — they're infrequent
		if (event.source() == null || !(event.source() instanceof PlayerEntity player)) {
			processSound(world, event, worldTime);
			return;
		}

		UUID playerId = player.getUuid();
		Long lastScan = lastScanTick.get(playerId);

		if (lastScan == null || worldTime - lastScan >= SCAN_COOLDOWN_TICKS) {
			// Cooldown expired or first sound — process now
			processSound(world, event, worldTime);
			lastScanTick.put(playerId, worldTime);
			pendingSounds.remove(playerId);
		} else {
			// On cooldown — queue the loudest sound
			PendingSound existing = pendingSounds.get(playerId);
			if (existing == null || event.radius() > existing.event.radius()) {
				pendingSounds.put(playerId, new PendingSound(world, event));
			}
		}
	}

	/**
	 * Called every server tick to flush pending sounds whose cooldown has expired.
	 */
	public static void tick(long worldTime) {
		if (pendingSounds.isEmpty()) return;

		Iterator<Map.Entry<UUID, PendingSound>> it = pendingSounds.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingSound> entry = it.next();
			UUID playerId = entry.getKey();
			Long lastScan = lastScanTick.get(playerId);

			if (lastScan == null || worldTime - lastScan >= SCAN_COOLDOWN_TICKS) {
				PendingSound pending = entry.getValue();
				processSound(pending.world, pending.event, worldTime);
				lastScanTick.put(playerId, worldTime);
				it.remove();
			}
		}

		// Periodic cleanup of stale entries
		if (worldTime % 200 == 0) {
			lastScanTick.entrySet().removeIf(e -> worldTime - e.getValue() > 600);
			SoundFatigue.cleanup(worldTime);
		}
	}

	/**
	 * Actually scans for mobs and alerts them. This is the expensive part.
	 */
	private static void processSound(ServerWorld world, SoundEvent event, long worldTime) {
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
			double fatigue = SoundFatigue.getAndRecord(mob, event.position(), event.type(), worldTime);
			if (fatigue <= 0.0) continue;

			if (fatigue < 1.0) {
				double distance = mob.getPos().distanceTo(event.position());
				if (distance > radius * fatigue) continue;
			}

			Vec3d investigatePos = addInaccuracy(mob, event.position());
			((LastKnownPositionAccess) mob).dysmn$setLastKnownTargetPos(investigatePos);
		}
	}

	/**
	 * Applies the heavy armor radius multiplier to a base sound radius.
	 */
	public static double applyArmorBonus(PlayerEntity player, double baseRadius) {
		int heavyPieces = countHeavyArmorPieces(player);
		double multiplier = 1.0 + heavyPieces * ModConfig.get().heavyArmorBonusPerPiece;
		return baseRadius * multiplier;
	}

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
		if (mob.getTarget() != null) return false;
		if (ModConfig.get().isBlacklisted(mob)) return false;
		if (!ModConfig.get().canMobHear(mob)) return false;
		if (event.source() != null && event.source() == mob) return false;

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

	public static void clear() {
		pendingSounds.clear();
		lastScanTick.clear();
	}

	private record PendingSound(ServerWorld world, SoundEvent event) {}
}
