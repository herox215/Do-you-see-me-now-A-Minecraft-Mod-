package com.dysmn.doyouseemenow.client;

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
 *
 * Uses VertexConsumerProvider from WorldRenderContext for Sodium compatibility
 * instead of Tessellator.getInstance() which Sodium replaces.
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

    public static void setProgress(int entityId, float progress) {
        if (progress <= 0.001f) {
            targetProgress.remove(entityId);
        } else {
            targetProgress.put(entityId, progress);
        }
        lastUpdateTick.put(entityId, clientTick);
    }

    public static void tick() {
        clientTick++;

        for (Map.Entry<Integer, Float> entry : targetProgress.entrySet()) {
            int id = entry.getKey();
            float target = entry.getValue();
            float current = displayProgress.getOrDefault(id, 0.0f);
            displayProgress.put(id, current + (target - current) * LERP_SPEED);
        }

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

        lastUpdateTick.entrySet().removeIf(e -> clientTick - e.getValue() > STALE_TIMEOUT);
        targetProgress.keySet().retainAll(lastUpdateTick.keySet());
    }

    /**
     * Called from WorldRenderEvents.AFTER_ENTITIES to render detection bars in world space.
     * Uses the context's VertexConsumerProvider for Sodium/Iris compatibility.
     */
    public static void render(WorldRenderContext context) {
        if (displayProgress.isEmpty()) return;
        if (context.world() == null) return;

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        float tickDelta = context.tickDelta();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();

        // Fallback: if no VertexConsumerProvider from context, use immediate mode
        VertexConsumerProvider.Immediate immediate = null;
        if (consumers == null) {
            immediate = VertexConsumerProvider.immediate(new BufferBuilder(256));
            consumers = immediate;
        }

        for (Map.Entry<Integer, Float> entry : displayProgress.entrySet()) {
            Entity entity = context.world().getEntityById(entry.getKey());
            if (!(entity instanceof MobEntity mob)) continue;

            float progress = entry.getValue();
            if (progress <= 0.01f) continue;

            // Distance culling
            double dx = mob.getX() - cameraPos.x;
            double dy = mob.getY() - cameraPos.y;
            double dz = mob.getZ() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) continue;

            double x = MathHelper.lerp(tickDelta, mob.prevX, mob.getX()) - cameraPos.x;
            double y = MathHelper.lerp(tickDelta, mob.prevY, mob.getY())
                    + mob.getHeight() + 0.3 - cameraPos.y;
            double z = MathHelper.lerp(tickDelta, mob.prevZ, mob.getZ()) - cameraPos.z;

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            renderBar(matrices, consumers, progress);

            matrices.pop();
        }

        // Flush if we created our own immediate provider
        if (immediate != null) {
            immediate.draw();
        }
    }

    private static void renderBar(MatrixStack matrices, VertexConsumerProvider consumers, float progress) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float halfWidth = 12.0f;
        float height = 2.5f;

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());

        // Background (dark semi-transparent)
        buffer.vertex(matrix, -halfWidth - 0.5f, -0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, -halfWidth - 0.5f, height + 0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, halfWidth + 0.5f, height + 0.5f, 0).color(0, 0, 0, 100).next();
        buffer.vertex(matrix, halfWidth + 0.5f, -0.5f, 0).color(0, 0, 0, 100).next();

        // Colored fill
        float fillWidth = halfWidth * 2.0f * progress;
        int[] color = getColor(progress);

        buffer.vertex(matrix, -halfWidth, 0, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth, height, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth + fillWidth, height, 0).color(color[0], color[1], color[2], 180).next();
        buffer.vertex(matrix, -halfWidth + fillWidth, 0, 0).color(color[0], color[1], color[2], 180).next();
    }

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
