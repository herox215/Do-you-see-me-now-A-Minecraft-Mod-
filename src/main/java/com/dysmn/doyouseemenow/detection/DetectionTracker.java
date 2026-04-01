package com.dysmn.doyouseemenow.detection;

import com.dysmn.doyouseemenow.ModConfig;
import com.dysmn.doyouseemenow.NetworkConstants;
import com.dysmn.doyouseemenow.VisibilityCheck;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Server-side tracker for the detection meter.
 * Manages detection progress per mob and ticks every server tick.
 *
 * When a mob has a player in FOV + light range, detection builds up over time.
 * When detection reaches 1.0, the mob aggros. When the player leaves sight,
 * detection decays. At 0.0 the mob forgets the player entirely.
 */
public final class DetectionTracker {

    private static final Map<MobEntity, DetectionState> detectionMap = new HashMap<>();

    /** Set to true while the tracker is calling setTarget() to bypass mixin checks. */
    public static boolean settingDetectedTarget = false;

    private DetectionTracker() {}

    /**
     * Called from TargetPredicateMixin when a non-blacklisted mob detects a player
     * (FOV + light check passed). Registers or updates the detection state.
     */
    public static void onMobDetectsPlayer(MobEntity mob, ServerPlayerEntity player) {
        DetectionState state = detectionMap.get(mob);
        if (state == null) {
            state = new DetectionState(player);
            detectionMap.put(mob, state);
        } else if (state.getTargetPlayer() != player) {
            // Switched to a different player — reset progress
            state.setTargetPlayer(player);
            state.setProgress(0.0);
        }
        state.setDetectedThisTick(true);
    }

    /**
     * Called every server tick. Updates all detection states:
     * increases progress for mobs currently detecting, decays otherwise.
     */
    public static void tick() {
        ModConfig config = ModConfig.get();
        if (!config.detectionEnabled) {
            if (!detectionMap.isEmpty()) {
                for (Map.Entry<MobEntity, DetectionState> entry : detectionMap.entrySet()) {
                    sendProgressPacket(entry.getKey(), 0.0f);
                }
                detectionMap.clear();
            }
            return;
        }

        Iterator<Map.Entry<MobEntity, DetectionState>> it = detectionMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MobEntity, DetectionState> entry = it.next();
            MobEntity mob = entry.getKey();
            DetectionState state = entry.getValue();

            // Cleanup dead or removed mobs
            if (!mob.isAlive() || mob.isRemoved()) {
                sendProgressPacket(mob, 0.0f);
                it.remove();
                continue;
            }

            ServerPlayerEntity player = state.getTargetPlayer();
            if (player == null || !player.isAlive() || player.isRemoved()) {
                sendProgressPacket(mob, 0.0f);
                it.remove();
                continue;
            }

            // Mob already has a target (e.g. from damage) — no need for detection
            if (mob.getTarget() != null) {
                sendProgressPacket(mob, 0.0f);
                it.remove();
                continue;
            }

            if (state.isDetectedThisTick()) {
                double rate = calculateDetectionRate(mob, player, config);
                state.addProgress(rate);
                state.setDetectedThisTick(false);
            } else {
                // Player left sight — decay
                state.addProgress(-config.detectionDecayRate);
            }

            // Full detection — aggro!
            if (state.getProgress() >= 1.0) {
                state.setProgress(1.0);
                sendProgressPacket(mob, 0.0f);
                settingDetectedTarget = true;
                mob.setTarget(player);
                settingDetectedTarget = false;
                it.remove();
                continue;
            }

            // Fully decayed — forget
            if (state.getProgress() <= 0.0) {
                sendProgressPacket(mob, 0.0f);
                it.remove();
                continue;
            }

            // Send progress update (every 3 ticks or on significant change)
            state.incrementTicksSinceLastSend();
            float currentProgress = (float) state.getProgress();
            if (state.getTicksSinceLastSend() >= 3
                    || Math.abs(currentProgress - state.getLastSentProgress()) > 0.05f) {
                sendProgressPacket(mob, currentProgress);
                state.setLastSentProgress(currentProgress);
                state.resetTicksSinceLastSend();
            }
        }
    }

    private static double calculateDetectionRate(MobEntity mob, ServerPlayerEntity player, ModConfig config) {
        double baseRate = 1.0 / config.baseDetectionTicks;

        // Distance: closer = faster detection
        double maxRange = VisibilityCheck.getDetectionRange(player);
        double distance = mob.distanceTo(player);
        double distanceRatio = 1.0 - Math.min(distance / maxRange, 1.0);
        double distanceFactor = 1.0 + distanceRatio * config.detectionDistanceMultiplier;

        // Light: brighter = faster detection
        int lightLevel = player.getWorld().getLightLevel(player.getBlockPos());
        double lightFactor = 1.0 + (lightLevel / 15.0) * config.detectionLightMultiplier;

        // Movement: moving = faster detection
        double speed = player.getVelocity().horizontalLength();
        double movementFactor = speed > 0.01 ? 1.0 + config.detectionMovementMultiplier : 1.0;

        // Sneaking: slower detection
        double sneakFactor = player.isSneaking() ? config.detectionSneakMultiplier : 1.0;

        return baseRate * distanceFactor * lightFactor * movementFactor * sneakFactor;
    }

    private static void sendProgressPacket(MobEntity mob, float progress) {
        if (mob.getWorld().isClient()) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(mob.getId());
        buf.writeFloat(progress);

        for (ServerPlayerEntity player : PlayerLookup.tracking(mob)) {
            ServerPlayNetworking.send(player, NetworkConstants.DETECTION_PROGRESS_PACKET, buf);
        }
    }

    public static void clear() {
        detectionMap.clear();
    }
}
