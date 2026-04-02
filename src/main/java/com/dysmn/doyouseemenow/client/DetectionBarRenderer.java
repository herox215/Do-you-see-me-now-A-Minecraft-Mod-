package com.dysmn.doyouseemenow.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side renderer for the detection bar above mob heads.
 * Shows a colored bar (green → yellow → red) that fills as detection increases.
 * Only visible when detection progress > 0%.
 */
public final class DetectionBarRenderer {

    /** Target progress received from server packets. */
    private static final Map<Integer, Float> targetProgress = new HashMap<>();

    /** Smoothed progress used for rendering (lerped toward target). */
    private static final Map<Integer, Float> displayProgress = new HashMap<>();

    /** Tick counter for stale entry cleanup. */
    private static final Map<Integer, Long> lastUpdateTick = new HashMap<>();
    private static long clientTick = 0;

    private static final float LERP_SPEED = 0.25f;
    private static final int STALE_TIMEOUT = 40; // 2 seconds

    private DetectionBarRenderer() {}

    /**
     * Called from the network packet receiver.
     */
    public static void setProgress(int entityId, float progress) {
        if (progress <= 0.001f) {
            targetProgress.remove(entityId);
        } else {
            targetProgress.put(entityId, progress);
        }
        lastUpdateTick.put(entityId, clientTick);
    }

    /**
     * Called every client tick to interpolate display values and clean up stale entries.
     */
    public static void tick() {
        clientTick++;

        // Lerp display toward target
        for (Map.Entry<Integer, Float> entry : targetProgress.entrySet()) {
            int id = entry.getKey();
            float target = entry.getValue();
            float current = displayProgress.getOrDefault(id, 0.0f);
            displayProgress.put(id, current + (target - current) * LERP_SPEED);
        }

        // Decay entries no longer receiving updates
        Iterator<Map.Entry<Integer, Float>> it = displayProgress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> entry = it.next();
            if (!targetProgress.containsKey(entry.getKey())) {
                float newVal = entry.getValue() - 0.05f;
                if (newVal <= 0.01f) {
                    it.remove();
                } else {
                    entry.setValue(newVal);
                }
            }
        }

        // Clean up stale entries
        lastUpdateTick.entrySet().removeIf(e -> clientTick - e.getValue() > STALE_TIMEOUT);
        targetProgress.keySet().retainAll(lastUpdateTick.keySet());
    }

    /**
     * Called from WorldRenderEvents.AFTER_ENTITIES to render detection bars in world space.
     */
    public static void render(WorldRenderContext context) {
        if (displayProgress.isEmpty()) return;
        if (context.world() == null) return;

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        float tickDelta = context.tickDelta();
        MatrixStack matrices = context.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        for (Map.Entry<Integer, Float> entry : displayProgress.entrySet()) {
            Entity entity = context.world().getEntityById(entry.getKey());
            if (!(entity instanceof MobEntity mob)) continue;

            float progress = entry.getValue();
            if (progress <= 0.01f) continue;

            // Distance culling — skip bars beyond 64 blocks
            double dx = mob.getX() - cameraPos.x;
            double dy = mob.getY() - cameraPos.y;
            double dz = mob.getZ() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) continue;

            // Interpolated position above mob head
            double x = MathHelper.lerp(tickDelta, mob.prevX, mob.getX()) - cameraPos.x;
            double y = MathHelper.lerp(tickDelta, mob.prevY, mob.getY())
                    + mob.getHeight() + 0.3 - cameraPos.y;
            double z = MathHelper.lerp(tickDelta, mob.prevZ, mob.getZ()) - cameraPos.z;

            matrices.push();
            matrices.translate(x, y, z);

            // Billboard: face camera
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            renderBar(matrices, progress);

            matrices.pop();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderBar(MatrixStack matrices, float progress) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float halfWidth = 12.0f;
        float height = 2.5f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Background (dark semi-transparent)
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, -halfWidth - 0.5f, -0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, -halfWidth - 0.5f, height + 0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, halfWidth + 0.5f, height + 0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, halfWidth + 0.5f, -0.5f, 0).color(0, 0, 0, 100).next();
        tessellator.draw();

        // Colored fill
        float fillWidth = halfWidth * 2.0f * progress;
        int[] color = getColor(progress);

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, -halfWidth, 0, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth, height, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth + fillWidth, height, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth + fillWidth, 0, 0).color(color[0], color[1], color[2], 180).next();
        tessellator.draw();
    }

    /**
     * Smooth color gradient: green (0%) → yellow (50%) → red (100%).
     */
    private static int[] getColor(float progress) {
        if (progress < 0.5f) {
            float t = progress / 0.5f;
            return new int[]{(int) (t * 255), 255, 0};
        } else {
            float t = (progress - 0.5f) / 0.5f;
            return new int[]{255, (int) ((1.0f - t) * 255), 0};
        }
    }

    public static void clear() {
        targetProgress.clear();
        displayProgress.clear();
        lastUpdateTick.clear();
    }
}
