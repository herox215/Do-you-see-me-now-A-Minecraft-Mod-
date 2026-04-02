package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.sound.SoundDetectionManager;
import com.dysmn.doyouseemenow.sound.SoundEvent;
import com.dysmn.doyouseemenow.sound.SoundType;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Emits a sound when a player interacts with certain blocks —
 * opening chests, trapdoors, doors, fence gates, shulker boxes, barrels.
 *
 * Targets ServerPlayerInteractionManager.interactBlock() which is called
 * for ALL block interactions regardless of which Block subclass handles onUse().
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class BlockUseSoundMixin {

	@Shadow @Final protected ServerPlayerEntity player;

	@Inject(method = "interactBlock", at = @At("RETURN"))
	private void doYouSeeMeNow_blockUseSound(ServerPlayerEntity player, net.minecraft.world.World world,
											  net.minecraft.item.ItemStack stack, Hand hand,
											  BlockHitResult hitResult,
											  CallbackInfoReturnable<ActionResult> cir) {
		if (world.isClient()) return;

		ActionResult result = cir.getReturnValue();
		if (result == ActionResult.PASS || result == ActionResult.FAIL) return;

		Block block = world.getBlockState(hitResult.getBlockPos()).getBlock();
		if (!isNoisyBlock(block)) return;

		double radius = ModConfig.get().blockUseSoundRadius;
		if (player.isSneaking()) {
			radius *= 0.5;
		}

		Vec3d soundPos = Vec3d.ofCenter(hitResult.getBlockPos());
		SoundDetectionManager.emitSound((ServerWorld) world,
			new SoundEvent(soundPos, radius, SoundType.BLOCK_USE, player));
	}

	private static boolean isNoisyBlock(Block block) {
		return block instanceof ChestBlock
			|| block instanceof TrapdoorBlock
			|| block instanceof DoorBlock
			|| block instanceof FenceGateBlock
			|| block instanceof ShulkerBoxBlock
			|| block instanceof BarrelBlock
			|| block instanceof EnderChestBlock;
	}
}
