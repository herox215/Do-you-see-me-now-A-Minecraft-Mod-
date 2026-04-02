package com.dysmn.doyouseemenow;

import com.dysmn.doyouseemenow.client.ClientModConfig;
import com.dysmn.doyouseemenow.client.DetectionBarRenderer;
import com.dysmn.doyouseemenow.client.SpottedParticles;
import com.dysmn.doyouseemenow.client.SpottedTracker;
import com.dysmn.doyouseemenow.client.StealthHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import java.util.ArrayList;
import java.util.List;

public class DoYouSeeMeNowClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Config sync packet: server sends hearing config on join
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SYNC_CONFIG_PACKET, (client, handler, buf, responseSender) -> {
			String hearingMode = buf.readString();
			int listSize = buf.readInt();
			List<String> hearingMobList = new ArrayList<>(listSize);
			for (int i = 0; i < listSize; i++) {
				hearingMobList.add(buf.readString());
			}
			client.execute(() -> ClientModConfig.apply(hearingMode, hearingMobList));
		});

		// "!" packet: mob spotted the player
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MOB_SPOTTED_PACKET, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			client.execute(() -> {
				if (client.world != null) {
					SpottedTracker.markSpotted(entityId, client.world.getTime());
				}
			});
		});

		// "?" packet: mob searching / stopped searching
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MOB_SEARCHING_PACKET, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			boolean searching = buf.readBoolean();
			client.execute(() -> SpottedTracker.setSearching(entityId, searching));
		});

		// Detection progress packet (legacy single): server sends mob detection progress
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.DETECTION_PROGRESS_PACKET, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			float progress = buf.readFloat();
			client.execute(() -> DetectionBarRenderer.setProgress(entityId, progress));
		});

		// Detection progress batch packet: multiple updates in one packet
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.DETECTION_PROGRESS_BATCH_PACKET, (client, handler, buf, responseSender) -> {
			int count = buf.readInt();
			int[] entityIds = new int[count];
			float[] progresses = new float[count];
			for (int i = 0; i < count; i++) {
				entityIds[i] = buf.readInt();
				progresses[i] = buf.readFloat();
			}
			client.execute(() -> {
				for (int i = 0; i < entityIds.length; i++) {
					DetectionBarRenderer.setProgress(entityIds[i], progresses[i]);
				}
			});
		});

		// Tick: clean up tracker state + spawn particles + detection bar interpolation
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null) {
				SpottedTracker.tick(client.world.getTime());
				SpottedParticles.tick(client.world);
				DetectionBarRenderer.tick();
			}
		});

		// Stealth HUD (only visible while sneaking)
		HudRenderCallback.EVENT.register(StealthHudRenderer::render);

		// Detection bar rendering in world space
		WorldRenderEvents.AFTER_ENTITIES.register(DetectionBarRenderer::render);
	}
}
