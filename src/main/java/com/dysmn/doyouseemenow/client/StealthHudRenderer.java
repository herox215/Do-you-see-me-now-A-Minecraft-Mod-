package com.dysmn.doyouseemenow.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Renders the stealth HUD: a large pixel-art eye icon with percentage beneath.
 * Only visible while sneaking, with a smooth fade in/out.
 * The eye is tinted green → yellow → red based on stealth score.
 */
public final class StealthHudRenderer {

	private static final Identifier EYE_TEXTURE = new Identifier("do_you_see_me_now", "textures/gui/detection_eye.png");

	/** Eye icon display size — large, percentage sits neatly below. */
	private static final int EYE_WIDTH = 48;
	private static final int EYE_HEIGHT = 24;

	private static float fadeAlpha = 0.0f;
	private static final float FADE_SPEED = 0.05f;

	private StealthHudRenderer() {}

	/**
	 * Called by HudRenderCallback every frame.
	 */
	public static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) return;

		boolean sneaking = player.isSneaking();

		// Fade in/out
		if (sneaking) {
			fadeAlpha = Math.min(fadeAlpha + FADE_SPEED, 1.0f);
		} else {
			fadeAlpha = Math.max(fadeAlpha - FADE_SPEED, 0.0f);
			if (fadeAlpha <= 0.0f) {
				StealthCalculator.reset();
				return;
			}
		}

		double score = StealthCalculator.calculate(player, client.world);
		int percentage = (int) (score * 100);

		int alpha = (int) (fadeAlpha * 255);
		int color = getScoreColor(score, alpha);
		int[] rgb = getScoreRgb(score);

		// Layout: large eye with percentage centered below, bottom-left above hotbar
		String text = percentage + "%";
		int textWidth = client.textRenderer.getWidth(text);

		int eyeX = 10;
		int eyeY = context.getScaledWindowHeight() - 58;
		int textX = eyeX + (EYE_WIDTH - textWidth) / 2;
		int textY = eyeY + EYE_HEIGHT + 2;

		// Draw eye texture (tinted by score color)
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(rgb[0] / 255.0f, rgb[1] / 255.0f, rgb[2] / 255.0f, fadeAlpha);
		context.drawTexture(EYE_TEXTURE, eyeX, eyeY, 0, 0, EYE_WIDTH, EYE_HEIGHT, EYE_WIDTH, EYE_HEIGHT);
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

		// Percentage centered below the eye
		context.drawTextWithShadow(client.textRenderer, text, textX, textY, color);
	}

	private static int getScoreColor(double score, int alpha) {
		int[] rgb = getScoreRgb(score);
		return (alpha << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
	}

	private static int[] getScoreRgb(double score) {
		if (score < 0.3) {
			return new int[]{255, 68, 68};   // Red
		} else if (score < 0.7) {
			return new int[]{255, 170, 0};    // Yellow/Orange
		} else {
			return new int[]{68, 255, 68};    // Green
		}
	}
}
