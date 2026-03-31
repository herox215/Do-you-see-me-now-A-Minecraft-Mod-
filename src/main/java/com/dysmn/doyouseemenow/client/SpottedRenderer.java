package com.dysmn.doyouseemenow.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders indicators above mobs:
 * - "!" yellow = mob spotted the player
 * - "?" light blue = mob is searching
 */
public final class SpottedRenderer {

	private static final int ALERT_COLOR = 0xFFFF00;
	private static final int SEARCH_COLOR = 0x55CCFF;

	private SpottedRenderer() {}

	public static void renderIndicators(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;

		long currentTick = client.world.getTime();
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		Vec3d cameraPos = dispatcher.camera.getPos();

		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof MobEntity mob)) continue;

			int id = mob.getId();

			// Interpolated mob position
			double x = mob.prevX + (mob.getX() - mob.prevX) * tickDelta - cameraPos.x;
			double y = mob.prevY + (mob.getY() - mob.prevY) * tickDelta - cameraPos.y;
			double z = mob.prevZ + (mob.getZ() - mob.prevZ) * tickDelta - cameraPos.z;
			double indicatorY = y + mob.getHeight() + 0.5;

			// "!" takes priority over "?"
			if (SpottedTracker.isSpotted(id)) {
				float alpha = SpottedTracker.getSpottedAlpha(id, currentTick);
				if (alpha > 0f) {
					renderText(matrices, vertexConsumers, client.textRenderer,
							"!", ALERT_COLOR, x, indicatorY, z, alpha, dispatcher, true);
					continue;
				}
			}

			if (SpottedTracker.isSearching(id)) {
				renderText(matrices, vertexConsumers, client.textRenderer,
						"?", SEARCH_COLOR, x, indicatorY, z, 1.0f, dispatcher, false);
			}
		}
	}

	private static void renderText(
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			TextRenderer textRenderer,
			String text, int colorBase,
			double x, double y, double z,
			float alpha,
			EntityRenderDispatcher dispatcher,
			boolean bounce
	) {
		matrices.push();
		matrices.translate(x, y, z);

		// Billboard: always face the camera
		matrices.multiply(dispatcher.getRotation());

		float scale = 0.04f;
		matrices.scale(-scale, -scale, scale);

		// Bounce effect (only for "!")
		if (bounce) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null) {
				float b = (float) Math.sin((client.world.getTime() % 20) / 20.0 * Math.PI * 2) * 2.0f;
				matrices.translate(0, b, 0);
			}
		}

		// Center text
		float textWidth = textRenderer.getWidth(text);
		float textX = -textWidth / 2f;

		int alphaInt = (int) (alpha * 255) << 24;
		int color = alphaInt | colorBase;

		Matrix4f matrix = matrices.peek().getPositionMatrix();

		int bgAlpha = (int) (alpha * 0.4f * 255) << 24;
		textRenderer.draw(text, textX, 0, color, false, matrix, vertexConsumers,
				TextRenderer.TextLayerType.NORMAL, bgAlpha, 0xF000F0);

		matrices.pop();
	}
}
