package com.dysmn.doyouseemenow;

import net.minecraft.util.Identifier;

public final class NetworkConstants {
	public static final Identifier MOB_SPOTTED_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "mob_spotted");
	public static final Identifier MOB_SEARCHING_PACKET = new Identifier(DoYouSeeMeNow.MOD_ID, "mob_searching");

	private NetworkConstants() {}
}
