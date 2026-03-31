package com.dysmn.doyouseemenow;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoYouSeeMeNow implements ModInitializer {
	public static final String MOD_ID = "do_you_see_me_now";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Do You See Me Now loaded!");

		// Register the search goal for all mobs when they load into the world
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof MobEntity mob
					&& !(mob instanceof EnderDragonEntity)
					&& !(mob instanceof WitherEntity)) {
				mob.goalSelector.add(3, new SearchLastKnownPositionGoal(mob));
			}
		});
	}
}
