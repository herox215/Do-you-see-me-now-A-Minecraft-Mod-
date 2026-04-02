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

	/** Maximum detection range in blocks — mobs can see up to this far (default: 32.0) */
	public double maxDetectionRange = 32.0;

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

	// --- Sound Detection ---

	/** Global toggle: sound detection on/off (default: true) */
	public boolean soundDetectionEnabled = true;

	/** Base radius for sprint sounds in blocks (default: 16) */
	public double sprintSoundRadius = 16.0;

	/** Interval between sprint sound events in ticks (default: 10) */
	public int sprintSoundInterval = 10;

	/** Base radius for walk sounds in blocks (default: 8) */
	public double walkSoundRadius = 8.0;

	/** Interval between walk sound events in ticks (default: 15) */
	public int walkSoundInterval = 15;

	/** Minimum fall distance to trigger a landing sound (default: 1.5) */
	public double landingMinFallDistance = 1.5;

	/** Radius multiplier for landing sounds: fallDistance * this (default: 3.0) */
	public double landingRadiusMultiplier = 3.0;

	/** Maximum radius for landing sounds (default: 48.0) */
	public double landingMaxRadius = 48.0;

	/** Minimum radius for landing sounds (default: 6.0) */
	public double landingMinRadius = 6.0;

	/** Base radius for attack sounds in blocks (default: 12) */
	public double attackSoundRadius = 12.0;

	/** Base radius for projectile impact sounds in blocks (default: 12) */
	public double projectileImpactRadius = 12.0;

	/** Radius for hitting blocks (sword against stone, punching, etc.) in blocks (default: 10) */
	public double blockHitSoundRadius = 10.0;

	/** Radius for using blocks (opening chests, doors, etc.) in blocks (default: 12) */
	public double blockUseSoundRadius = 12.0;

	/** Armor bonus per heavy piece (added to 1.0 multiplier) (default: 0.1875) */
	public double heavyArmorBonusPerPiece = 0.1875;

	/**
	 * Hearing mode: "whitelist" or "blacklist" (default: "whitelist")
	 * - "whitelist": only listed mobs can hear
	 * - "blacklist": all mobs can hear, except those listed
	 *
	 * Whitelist is the default because sound detection should only affect hostile mobs.
	 * A blacklist default would require listing every passive/neutral mob to exclude them,
	 * which breaks whenever a mod adds new entities. With a whitelist, only explicitly
	 * listed hostile mobs react to sounds — passive animals like sheep, cows, pigs etc.
	 * are unaffected without needing to be enumerated.
	 */
	public String hearingMode = "whitelist";

	/**
	 * Mob list for hearing (interpreted as whitelist or blacklist depending on hearingMode).
	 * Default: all vanilla hostile mobs (1.20.1).
	 * Excludes Enderman, Wither, Ender Dragon, Warden (on the vision blacklist / own mechanics).
	 * Excludes neutral mobs (Piglin, Bee, Wolf, Iron Golem, etc.).
	 */
	public List<String> hearingMobList = List.of(
		"minecraft:zombie",
		"minecraft:skeleton",
		"minecraft:creeper",
		"minecraft:spider",
		"minecraft:cave_spider",
		"minecraft:witch",
		"minecraft:pillager",
		"minecraft:vindicator",
		"minecraft:evoker",
		"minecraft:ravager",
		"minecraft:drowned",
		"minecraft:husk",
		"minecraft:stray",
		"minecraft:phantom",
		"minecraft:blaze",
		"minecraft:ghast",
		"minecraft:piglin_brute",
		"minecraft:hoglin",
		"minecraft:zoglin",
		"minecraft:wither_skeleton",
		"minecraft:guardian",
		"minecraft:elder_guardian",
		"minecraft:silverfish",
		"minecraft:endermite",
		"minecraft:vex",
		"minecraft:shulker"
	);

	// --- Detection Meter ---

	/** Global toggle: detection meter on/off (default: true) */
	public boolean detectionEnabled = true;

	/** Base ticks to reach full detection at neutral conditions (default: 60 = 3s) */
	public int baseDetectionTicks = 60;

	/** Detection decay per tick when player is out of sight (default: 0.02) */
	public double detectionDecayRate = 0.02;

	/** Detection rate multiplier while sneaking (default: 0.3) */
	public double detectionSneakMultiplier = 0.3;

	/** Extra detection speed bonus when player is moving (added to base 1.0, default: 0.5) */
	public double detectionMovementMultiplier = 0.5;

	/** Extra detection speed bonus from distance (closer = more bonus, default: 8.0) */
	public double detectionDistanceMultiplier = 8.0;

	/** Extra detection speed bonus from light level (brighter = more bonus, default: 1.0) */
	public double detectionLightMultiplier = 1.0;

	// --- Damage Investigation ---

	/** Max inaccuracy for ranged attacks when investigating, in blocks (default: 12.0) */
	public double rangedMaxInaccuracy = 12.0;

	/** Max inaccuracy for melee attacks when investigating, in blocks (default: 3.0) */
	public double meleeMaxInaccuracy = 3.0;

	private transient Set<EntityType<?>> blacklistedTypes;
	private transient Set<EntityType<?>> hearingMobTypes;

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

	/**
	 * Checks if a mob can hear sounds based on hearingMode and hearingMobList.
	 */
	public boolean canMobHear(MobEntity mob) {
		if (!soundDetectionEnabled) return false;

		if (hearingMobTypes == null) {
			hearingMobTypes = new HashSet<>();
			for (String id : hearingMobList) {
				var identifier = net.minecraft.util.Identifier.tryParse(id);
				if (identifier != null) {
					Registries.ENTITY_TYPE.getOrEmpty(identifier).ifPresent(hearingMobTypes::add);
				}
			}
		}

		boolean inList = hearingMobTypes.contains(mob.getType());
		return hearingMode.equals("whitelist") ? inList : !inList;
	}

	/**
	 * Clears the cached hearing mob types (call after config reload).
	 */
	public void resetHearingCache() {
		hearingMobTypes = null;
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
