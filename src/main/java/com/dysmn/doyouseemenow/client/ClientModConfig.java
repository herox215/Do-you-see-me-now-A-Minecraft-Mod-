package com.dysmn.doyouseemenow.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side holder for synced hearing config from the server.
 * Until the server sends a sync packet, falls back to treating all HostileEntity as hearable.
 */
public final class ClientModConfig {

	private static boolean synced = false;
	private static String hearingMode = "whitelist";
	private static Set<EntityType<?>> hearingMobTypes = new HashSet<>();

	private ClientModConfig() {}

	/**
	 * Called when the sync packet arrives from the server.
	 */
	public static void apply(String mode, List<String> mobIds) {
		hearingMode = mode;
		hearingMobTypes = new HashSet<>();
		for (String id : mobIds) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null) {
				Registries.ENTITY_TYPE.getOrEmpty(identifier).ifPresent(hearingMobTypes::add);
			}
		}
		synced = true;
	}

	/**
	 * Checks if a mob can hear sounds, using the server-synced config.
	 * Fallback (before sync): only HostileEntity can hear.
	 */
	public static boolean canMobHear(Entity entity) {
		if (!(entity instanceof MobEntity)) return false;

		if (!synced) {
			return entity instanceof HostileEntity;
		}

		boolean inList = hearingMobTypes.contains(entity.getType());
		return hearingMode.equals("whitelist") ? inList : !inList;
	}

	/**
	 * Resets synced state (e.g. on disconnect).
	 */
	public static void reset() {
		synced = false;
		hearingMode = "whitelist";
		hearingMobTypes = new HashSet<>();
	}
}
