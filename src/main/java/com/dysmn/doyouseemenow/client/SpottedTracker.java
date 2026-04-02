package com.dysmn.doyouseemenow.client;

import com.dysmn.doyouseemenow.ModConfig;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Tracks mob states on the client:
 * - "!" = mob spotted the player (time-limited)
 * - "?" = mob is currently searching (until stop packet)
 */
public final class SpottedTracker {

	// "!" spotted system
	private static final Map<Integer, Long> spottedMobs = new HashMap<>();

	// "?" search system — stores entityId → tick when searching started
	private static final Map<Integer, Long> searchingMobs = new HashMap<>();

	/** Max ticks a searching entry can live without being refreshed (30 seconds). */
	private static final long SEARCHING_TTL = 600;

	private SpottedTracker() {}

	// === "!" Spotted ===

	public static void markSpotted(int entityId, long worldTick) {
		spottedMobs.put(entityId, worldTick);
	}

	public static boolean isSpotted(int entityId) {
		return spottedMobs.containsKey(entityId);
	}

	@Nullable
	public static Long getSpottedTick(int entityId) {
		return spottedMobs.get(entityId);
	}

	public static float getSpottedAlpha(int entityId, long currentTick) {
		Long spottedTick = spottedMobs.get(entityId);
		if (spottedTick == null) return 0f;

		ModConfig config = ModConfig.get();
		long elapsed = currentTick - spottedTick;
		if (elapsed > config.spottedDisplayTicks) return 0f;
		if (elapsed > config.spottedDisplayTicks - config.spottedFadeTicks) {
			return (float) (config.spottedDisplayTicks - elapsed) / config.spottedFadeTicks;
		}
		return 1.0f;
	}

	/** Returns all entity IDs that currently have spotted or searching state. */
	public static Set<Integer> getActiveEntityIds() {
		Set<Integer> ids = new HashSet<>(spottedMobs.keySet());
		ids.addAll(searchingMobs.keySet());
		return ids;
	}

	// === "?" Searching ===

	public static void setSearching(int entityId, boolean searching) {
		if (searching) {
			searchingMobs.put(entityId, System.currentTimeMillis());
		} else {
			searchingMobs.remove(entityId);
		}
	}

	public static boolean isSearching(int entityId) {
		return searchingMobs.containsKey(entityId);
	}

	// === General ===

	public static void tick(long currentTick) {
		int displayTicks = ModConfig.get().spottedDisplayTicks;
		spottedMobs.entrySet().removeIf(e -> currentTick - e.getValue() > displayTicks);

		// Prune stale searching entries (mobs that despawned without sending stop packet)
		if (currentTick % 100 == 0 && !searchingMobs.isEmpty()) {
			long now = System.currentTimeMillis();
			searchingMobs.entrySet().removeIf(e -> now - e.getValue() > SEARCHING_TTL * 50);
		}
	}

	public static void clear() {
		spottedMobs.clear();
		searchingMobs.clear();
	}
}
