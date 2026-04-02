package com.dysmn.doyouseemenow;

import com.dysmn.doyouseemenow.detection.DetectionTracker;
import com.dysmn.doyouseemenow.detection.InvestigatePlayerGoal;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoYouSeeMeNow implements ModInitializer {
	public static final String MOD_ID = "do_you_see_me_now";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.load();
		LOGGER.info("Do You See Me Now loaded!");

		// Register the search goal for all mobs when they load into the world
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof MobEntity mob && !ModConfig.get().isBlacklisted(mob)) {
				mob.goalSelector.add(3, new SearchLastKnownPositionGoal(mob));
				mob.goalSelector.add(2, new InvestigatePlayerGoal(mob));
			}
		});

		// Tick detection tracker every server tick
		ServerTickEvents.END_SERVER_TICK.register(server -> DetectionTracker.tick());

		// Sync hearing config to clients on join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ModConfig config = ModConfig.get();
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeString(config.hearingMode);
			buf.writeInt(config.hearingMobList.size());
			for (String id : config.hearingMobList) {
				buf.writeString(id);
			}
			ServerPlayNetworking.send(handler.player, NetworkConstants.SYNC_CONFIG_PACKET, buf);
		});
	}
}
