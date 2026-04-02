package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.sound.SoundDetectionManager;
import com.dysmn.doyouseemenow.sound.SoundEvent;
import com.dysmn.doyouseemenow.sound.SoundType;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a sound when a player starts hitting/mining a block.
 * Swinging a sword at stone, punching wood, etc. — all make noise.
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class BlockHitSoundMixin {

	@Shadow @Final protected ServerPlayerEntity player;

	@Unique private long dysmn$lastBlockHitTick = -1;

	@Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
	private void doYouSeeMeNow_blockHitSound(BlockPos pos, PlayerActionC2SPacket.Action action,
											  Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
		if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
				&& action != PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
			return;
		}

		if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) return;

		ServerWorld world = player.getServerWorld();
		if (world.isClient()) return;

		// Don't spam — one sound per 10 ticks
		long currentTick = world.getTime();
		if (currentTick - dysmn$lastBlockHitTick < 10) return;
		dysmn$lastBlockHitTick = currentTick;

		// Sneaking makes it quieter (half radius)
		double radius = ModConfig.get().blockHitSoundRadius;
		if (player.isSneaking()) {
			radius *= 0.5;
		}

		Vec3d soundPos = Vec3d.ofCenter(pos);
		SoundDetectionManager.emitSound(world,
			new SoundEvent(soundPos, radius, SoundType.BLOCK_HIT, player));
	}
}
