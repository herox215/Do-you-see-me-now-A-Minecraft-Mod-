package com.dysmn.doyouseemenow.sound;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Tracks per-mob fatigue toward sound positions. Mobs lose interest in
 * sounds that keep coming from the same spot.
 *
 * Each mob remembers the last few positions it investigated. Repeated
 * sounds near those positions get progressively ignored:
 * - 1st time: full radius
 * - 2nd time: half radius
 * - 3rd+ time: ignored
 *
 * Positions expire after a configurable timeout so mobs "forget" over time.
 * Uses UUID as key to avoid entity ID reuse issues.
 */
public final class SoundFatigue {

	private SoundFatigue() {}

	/** How close a new sound must be to a remembered position to count as "same spot". */
	private static final double SAME_SPOT_RADIUS = 5.0;

	/** How many ticks until a mob forgets a position. */
	private static final long MEMORY_DURATION_TICKS = 900; // 45 seconds

	/** Max remembered positions per mob. */
	private static final int MAX_MEMORIES = 5;

	private static final Map<UUID, List<FatigueEntry>> mobMemories = new HashMap<>();

	/**
	 * Returns the fatigue multiplier for this mob hearing a sound at the given position.
	 * 1.0 = full interest, 0.5 = reduced, 0.0 = completely bored.
	 * Also records the position so the next call will be more fatigued.
	 *
	 * Fatigue is tracked per sound category — movement sounds (walking, sprinting, landing)
	 * have separate fatigue from action sounds (block hit, block use, attack, etc.),
	 * so footsteps don't suppress reaction to block interactions.
	 */
	public static double getAndRecord(MobEntity mob, Vec3d soundPos, SoundType soundType, long worldTime) {
		UUID mobId = mob.getUuid();
		List<FatigueEntry> memories = mobMemories.computeIfAbsent(mobId, k -> new ArrayList<>());

		// Purge expired entries
		memories.removeIf(e -> worldTime - e.timestamp > MEMORY_DURATION_TICKS);

		boolean isMovement = soundType == SoundType.WALKING
				|| soundType == SoundType.SPRINTING
				|| soundType == SoundType.LANDING;

		// Find if this position overlaps with a remembered one of the same category
		FatigueEntry match = null;
		for (FatigueEntry entry : memories) {
			if (entry.movement == isMovement
					&& entry.position.distanceTo(soundPos) <= SAME_SPOT_RADIUS) {
				match = entry;
				break;
			}
		}

		double multiplier;
		if (match == null) {
			// First time — full interest, record it
			multiplier = 1.0;
			if (memories.size() >= MAX_MEMORIES) {
				memories.remove(0);
			}
			memories.add(new FatigueEntry(soundPos, 1, worldTime, isMovement));
		} else {
			// Seen this spot before
			match.hitCount++;
			match.timestamp = worldTime; // refresh the timer
			multiplier = match.hitCount >= 3 ? 0.0 : 0.5;
		}

		return multiplier;
	}

	/**
	 * Clean up data for mobs that no longer exist.
	 * Call periodically (e.g. every few hundred ticks) or on world unload.
	 */
	public static void cleanup(long worldTime) {
		mobMemories.entrySet().removeIf(entry -> {
			entry.getValue().removeIf(e -> worldTime - e.timestamp > MEMORY_DURATION_TICKS);
			return entry.getValue().isEmpty();
		});
	}

	public static void clear() {
		mobMemories.clear();
	}

	private static class FatigueEntry {
		Vec3d position;
		int hitCount;
		long timestamp;
		boolean movement; // true = walking/sprinting/landing, false = action sounds

		FatigueEntry(Vec3d position, int hitCount, long timestamp, boolean movement) {
			this.position = position;
			this.hitCount = hitCount;
			this.timestamp = timestamp;
			this.movement = movement;
		}
	}
}
