package com.dysmn.doyouseemenow;

import com.dysmn.doyouseemenow.client.SpottedTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DoYouSeeMeNowClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
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

		// Clean up tracker state
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null) {
				SpottedTracker.tick(client.world.getTime());
			}
		});
	}
}
