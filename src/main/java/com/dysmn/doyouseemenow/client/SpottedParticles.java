package com.dysmn.doyouseemenow.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

/**
 * Spawns particle effects above mobs:
 * - Spotted "!": bright yellow/orange burst + trailing crit stars
 * - Searching "?": slow light blue dust floating upward
 */
public final class SpottedParticles {

	// Yellow-orange for spotted
	private static final DustParticleEffect SPOTTED_DUST = new DustParticleEffect(
			new Vector3f(1.0f, 0.85f, 0.0f), 1.2f
	);

	// Light blue for searching
	private static final DustParticleEffect SEARCH_DUST = new DustParticleEffect(
			new Vector3f(0.33f, 0.8f, 1.0f), 0.8f
	);

	private SpottedParticles() {}

	/**
	 * Called every client tick. Spawns particles for spotted/searching mobs.
	 */
	public static void tick(ClientWorld world) {
		if (world == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		long currentTick = world.getTime();

		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof MobEntity mob)) continue;

			int id = mob.getId();
			double x = mob.getX();
			double y = mob.getY() + mob.getHeight() + 0.3;
			double z = mob.getZ();

			// Spotted "!" has priority
			if (SpottedTracker.isSpotted(id)) {
				float alpha = SpottedTracker.getSpottedAlpha(id, currentTick);
				if (alpha > 0f) {
					spawnSpottedParticles(world, x, y, z, currentTick, id, alpha);
					continue;
				}
			}

			// Searching "?"
			if (SpottedTracker.isSearching(id)) {
				spawnSearchParticles(world, x, y, z, currentTick);
			}
		}
	}

	private static void spawnSpottedParticles(ClientWorld world, double x, double y, double z,
			long currentTick, int entityId, float alpha) {
		Long spottedTick = SpottedTracker.getSpottedTick(entityId);
		if (spottedTick == null) return;

		long elapsed = currentTick - spottedTick;

		if (elapsed <= 2) {
			// Initial burst: crit stars + dust
			for (int i = 0; i < 8; i++) {
				double ox = (Math.random() - 0.5) * 0.6;
				double oy = Math.random() * 0.5;
				double oz = (Math.random() - 0.5) * 0.6;
				world.addParticle(ParticleTypes.CRIT, x + ox, y + oy, z + oz,
						ox * 0.3, 0.2 + Math.random() * 0.2, oz * 0.3);
			}
			for (int i = 0; i < 4; i++) {
				double ox = (Math.random() - 0.5) * 0.4;
				double oz = (Math.random() - 0.5) * 0.4;
				world.addParticle(SPOTTED_DUST, x + ox, y + Math.random() * 0.3, z + oz,
						0, 0.05, 0);
			}
		} else if (currentTick % 3 == 0) {
			// Trailing particles: occasional dust floating up
			double ox = (Math.random() - 0.5) * 0.3;
			double oz = (Math.random() - 0.5) * 0.3;
			world.addParticle(SPOTTED_DUST, x + ox, y, z + oz, 0, 0.03, 0);

			if (alpha > 0.5f && Math.random() < 0.4) {
				world.addParticle(ParticleTypes.CRIT, x + ox, y + 0.1, z + oz,
						0, 0.1, 0);
			}
		}
	}

	private static void spawnSearchParticles(ClientWorld world, double x, double y, double z,
			long currentTick) {
		// Gentle ambient particles every few ticks
		if (currentTick % 5 != 0) return;

		double angle = (currentTick % 40) / 40.0 * Math.PI * 2;
		double radius = 0.25;
		double ox = Math.cos(angle) * radius;
		double oz = Math.sin(angle) * radius;

		world.addParticle(SEARCH_DUST, x + ox, y, z + oz, 0, 0.02, 0);

		// Extra particle orbiting the other way
		if (currentTick % 10 == 0) {
			double ox2 = (Math.random() - 0.5) * 0.4;
			double oz2 = (Math.random() - 0.5) * 0.4;
			world.addParticle(SEARCH_DUST, x + ox2, y + 0.2, z + oz2, 0, 0.04, 0);
		}
	}
}
