package com.dysmn.doyouseemenow;

import net.minecraft.util.Identifier;

public final class NetworkConstants {
	public static final Identifier MOB_SPOTTED_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "mob_spotted");
	public static final Identifier MOB_SEARCHING_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "mob_searching");
	public static final Identifier SYNC_CONFIG_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "sync_config");
	public static final Identifier DETECTION_PROGRESS_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "detection_progress");

	private NetworkConstants() {}
}
