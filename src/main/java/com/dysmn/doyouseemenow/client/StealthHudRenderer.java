package com.dysmn.doyouseemenow.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Renders the stealth HUD: an eye symbol + percentage + score bar.
 * Only visible while sneaking, with a smooth fade in/out.
 *
 * Score states:
 * - 70–100% green  (well hidden)
 * - 30–70%  yellow (moderate risk)
 * - 0–30%   red    (high detection risk)
 */
public final class StealthHudRenderer {

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

		// Position: bottom-left, above the hotbar
		int x = 10;
		int y = context.getScaledWindowHeight() - 50;

		int alpha = (int) (fadeAlpha * 255);
		int color = getScoreColor(score, alpha);

		String eyeSymbol = getEyeSymbol(score);

		// Semi-transparent background
		int bgAlpha = (int) (fadeAlpha * 100);
		context.fill(x - 2, y - 2, x + 60, y + 12, (bgAlpha << 24));

		// Eye symbol + percentage
		context.drawTextWithShadow(client.textRenderer,
			eyeSymbol + " " + percentage + "%",
			x, y, color);

		// Score bar below text
		int barWidth = 56;
		int barHeight = 3;
		int barY = y + 12;
		int filledWidth = (int) (barWidth * score);

		// Bar background
		context.fill(x, barY, x + barWidth, barY + barHeight,
			(bgAlpha << 24) | 0x333333);
		// Bar fill
		context.fill(x, barY, x + filledWidth, barY + barHeight, color);
	}

	private static int getScoreColor(double score, int alpha) {
		if (score < 0.3) {
			return (alpha << 24) | 0xFF4444; // Red
		} else if (score < 0.7) {
			return (alpha << 24) | 0xFFAA00; // Yellow/Orange
		} else {
			return (alpha << 24) | 0x44FF44; // Green
		}
	}

	private static String getEyeSymbol(double score) {
		if (score >= 0.7) return "\u25C9";      // ◉ well hidden
		else if (score >= 0.3) return "\u25CE";  // ◎ moderate risk
		else return "\u2299";                    // ⊙ high risk
	}
}
