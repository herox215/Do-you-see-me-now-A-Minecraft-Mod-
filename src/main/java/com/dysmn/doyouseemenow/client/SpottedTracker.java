package com.dysmn.doyouseemenow.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks mob states on the client:
 * - "!" = mob spotted the player (time-limited)
 * - "?" = mob is currently searching (until stop packet)
 */
public final class SpottedTracker {

	// "!" spotted system
	private static final Map<Integer, Long> spottedMobs = new HashMap<>();
	private static final int DISPLAY_TICKS = 40;
	private static final int FADE_TICKS = 10;

	// "?" search system
	private static final Set<Integer> searchingMobs = new HashSet<>();

	private SpottedTracker() {}

	// === "!" Spotted ===

	public static void markSpotted(int entityId, long worldTick) {
		spottedMobs.put(entityId, worldTick);
	}

	public static boolean isSpotted(int entityId) {
		return spottedMobs.containsKey(entityId);
	}

	public static float getSpottedAlpha(int entityId, long currentTick) {
		Long spottedTick = spottedMobs.get(entityId);
		if (spottedTick == null) return 0f;

		long elapsed = currentTick - spottedTick;
		if (elapsed > DISPLAY_TICKS) return 0f;
		if (elapsed > DISPLAY_TICKS - FADE_TICKS) {
			return (float) (DISPLAY_TICKS - elapsed) / FADE_TICKS;
		}
		return 1.0f;
	}

	// === "?" Searching ===

	public static void setSearching(int entityId, boolean searching) {
		if (searching) {
			searchingMobs.add(entityId);
		} else {
			searchingMobs.remove(entityId);
		}
	}

	public static boolean isSearching(int entityId) {
		return searchingMobs.contains(entityId);
	}

	// === General ===

	public static void tick(long currentTick) {
		spottedMobs.entrySet().removeIf(e -> currentTick - e.getValue() > DISPLAY_TICKS);
	}

	public static void clear() {
		spottedMobs.clear();
		searchingMobs.clear();
	}
}
