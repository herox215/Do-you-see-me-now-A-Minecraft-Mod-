package com.dysmn.doyouseemenow.mixin.client;

import com.dysmn.doyouseemenow.client.SpottedRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

	/**
	 * Render "!" and "?" indicators after the entity rendering pass.
	 */
	@Inject(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;drawCurrentLayer()V",
			ordinal = 0
		)
	)
	private void doYouSeeMeNow_renderAlerts(
			MatrixStack matrices,
			float tickDelta,
			long limitTime,
			boolean renderBlockOutline,
			net.minecraft.client.render.Camera camera,
			net.minecraft.client.render.GameRenderer gameRenderer,
			net.minecraft.client.render.LightmapTextureManager lightmapTextureManager,
			Matrix4f projectionMatrix,
			CallbackInfo ci
	) {
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
		SpottedRenderer.renderIndicators(matrices, immediate, tickDelta);
	}
}
