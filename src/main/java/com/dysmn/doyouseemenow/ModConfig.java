package com.dysmn.doyouseemenow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple JSON config. Loaded once on startup from config/do_you_see_me_now.json.
 * If the file doesn't exist, it gets created with default values.
 */
public class ModConfig {

	private static ModConfig INSTANCE;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// --- Blacklist (mobs that bypass all vision checks) ---

	/** Entity IDs that ignore FOV and light restrictions (e.g. "minecraft:wither") */
	public List<String> blacklistedMobs = List.of(
		"minecraft:ender_dragon",
		"minecraft:wither",
		"minecraft:warden",
		"minecraft:enderman"
	);

	// --- Vision ---

	/** Total field of view in degrees (default: 120) */
	public double fovDegrees = 120.0;

	/** Detection range multiplier per light level in blocks (default: 2.5) */
	public double blocksPerLight = 2.5;

	/** Minimum detection range in total darkness (default: 4.0) */
	public double minDetectionRange = 4.0;

	// --- Search Behavior ---

	/** How long the mob looks around at the target position, in ticks (default: 100 = 5s) */
	public int lookAroundDuration = 100;

	/** How close the mob needs to be to consider it "arrived", in blocks (default: 1.5) */
	public double arrivalDistance = 1.5;

	/** Max time the mob spends walking before giving up, in ticks (default: 200 = 10s) */
	public int walkTimeout = 200;

	/** Distance threshold: below this the mob turns in place, above it walks (default: 5.0) */
	public double walkThreshold = 5.0;

	// --- Indicators ---

	/** How long the "!" indicator is shown, in ticks (default: 40 = 2s) */
	public int spottedDisplayTicks = 40;

	/** Fade-out duration of the "!" indicator, in ticks (default: 10 = 0.5s) */
	public int spottedFadeTicks = 10;

	// --- Damage Investigation ---

	/** Max inaccuracy for ranged attacks when investigating, in blocks (default: 12.0) */
	public double rangedMaxInaccuracy = 12.0;

	/** Max inaccuracy for melee attacks when investigating, in blocks (default: 3.0) */
	public double meleeMaxInaccuracy = 3.0;

	private transient Set<EntityType<?>> blacklistedTypes;

	/**
	 * Checks if a mob is blacklisted (bypasses all vision checks).
	 */
	public boolean isBlacklisted(MobEntity mob) {
		if (blacklistedTypes == null) {
			blacklistedTypes = new HashSet<>();
			for (String id : blacklistedMobs) {
				var identifier = net.minecraft.util.Identifier.tryParse(id);
				if (identifier != null) {
					Registries.ENTITY_TYPE.getOrEmpty(identifier).ifPresent(blacklistedTypes::add);
				}
			}
		}
		return blacklistedTypes.contains(mob.getType());
	}

	public static ModConfig get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	public static void load() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve("do_you_see_me_now.json");

		if (Files.exists(configFile)) {
			try {
				String json = Files.readString(configFile);
				INSTANCE = GSON.fromJson(json, ModConfig.class);
				DoYouSeeMeNow.LOGGER.info("Config loaded from {}", configFile);
			} catch (Exception e) {
				DoYouSeeMeNow.LOGGER.error("Failed to load config, using defaults", e);
				INSTANCE = new ModConfig();
			}
		} else {
			INSTANCE = new ModConfig();
			DoYouSeeMeNow.LOGGER.info("No config found, creating defaults at {}", configFile);
		}

		// Always save (creates file if missing, updates with new fields if outdated)
		save();
	}

	public static void save() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve("do_you_see_me_now.json");

		try {
			Files.writeString(configFile, GSON.toJson(INSTANCE));
		} catch (IOException e) {
			DoYouSeeMeNow.LOGGER.error("Failed to save config", e);
		}
	}
}
