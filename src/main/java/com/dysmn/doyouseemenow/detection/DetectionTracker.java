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

import java.util.*;

/**
 * Server-side tracker for the detection meter.
 *
 * Performance optimizations:
 * - Tick staggering: mobs are split into 3 groups, each checked every 3rd tick.
 *   Detection rates are multiplied by 3 to compensate.
 * - Batched packets: all progress updates are collected per tick and sent as
 *   a single packet per observing player instead of one packet per mob.
 */
public final class DetectionTracker {

    private static final Map<MobEntity, DetectionState> detectionMap = new HashMap<>();

    /** Tracks when a mob last had a player as target (world time). */
    private static final Map<Integer, Long> lastAggroTick = new HashMap<>();

    /** Grace period in ticks — mob skips detection meter if it had a target this recently. */
    private static final int AGGRO_GRACE_TICKS = 40;

    /** How many stagger groups to split mobs into. */
    private static final int STAGGER_GROUPS = 3;

    /** Server tick counter for staggering. */
    private static long tickCounter = 0;

    /** Set to true while the tracker is calling setTarget() to bypass mixin checks. */
    public static boolean settingDetectedTarget = false;

    /** Collects progress updates to batch-send at end of tick. */
    private static final Map<MobEntity, Float> pendingUpdates = new HashMap<>();

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
            state.setTargetPlayer(player);
            state.setProgress(0.0);
        }
        state.setDetectedThisTick(true);
    }

    /**
     * Called every server tick. Uses staggering to only process a subset of mobs
     * per tick, then batch-sends all progress updates.
     */
    public static void tick() {
        tickCounter++;
        ModConfig config = ModConfig.get();

        if (!config.detectionEnabled) {
            if (!detectionMap.isEmpty()) {
                // Send zero progress for all tracked mobs
                for (MobEntity mob : detectionMap.keySet()) {
                    pendingUpdates.put(mob, 0.0f);
                }
                detectionMap.clear();
                flushPackets();
            }
            return;
        }

        int currentGroup = (int) (tickCounter % STAGGER_GROUPS);

        Iterator<Map.Entry<MobEntity, DetectionState>> it = detectionMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MobEntity, DetectionState> entry = it.next();
            MobEntity mob = entry.getKey();
            DetectionState state = entry.getValue();

            // Cleanup dead or removed mobs (always, not staggered)
            if (!mob.isAlive() || mob.isRemoved()) {
                pendingUpdates.put(mob, 0.0f);
                it.remove();
                continue;
            }

            ServerPlayerEntity player = state.getTargetPlayer();
            if (player == null || !player.isAlive() || player.isRemoved()) {
                pendingUpdates.put(mob, 0.0f);
                it.remove();
                continue;
            }

            // Mob already has a target — no need for detection
            if (mob.getTarget() != null) {
                pendingUpdates.put(mob, 0.0f);
                it.remove();
                continue;
            }

            // Stagger: only do the expensive visibility check for this mob's group
            // But always consume the detectedThisTick flag to avoid stale data
            boolean detectedFlag = state.isDetectedThisTick();
            state.setDetectedThisTick(false);

            boolean isMyGroup = (mob.getId() % STAGGER_GROUPS) == currentGroup;

            if (isMyGroup) {
                boolean canDetect = detectedFlag
                        || VisibilityCheck.canMobDetectTarget(mob, player);

                if (canDetect) {
                    // Multiply rate by STAGGER_GROUPS to compensate for less frequent checks
                    double rate = calculateDetectionRate(mob, player, config) * STAGGER_GROUPS;
                    state.addProgress(rate);
                } else {
                    state.addProgress(-config.detectionDecayRate * STAGGER_GROUPS);
                }
            } else if (detectedFlag) {
                // Not our group's tick, but the mixin flagged detection — still apply progress
                // (use 1x rate since this is from an actual detection event, not a stagger tick)
                double rate = calculateDetectionRate(mob, player, config);
                state.addProgress(rate);
            }
            // If not our group and no flag: skip this tick entirely (progress unchanged)

            // Full detection — aggro!
            if (state.getProgress() >= 1.0) {
                state.setProgress(1.0);
                pendingUpdates.put(mob, 0.0f);
                settingDetectedTarget = true;
                mob.setTarget(player);
                settingDetectedTarget = false;
                it.remove();
                continue;
            }

            // Fully decayed — forget
            if (state.getProgress() <= 0.0) {
                pendingUpdates.put(mob, 0.0f);
                it.remove();
                continue;
            }

            // Queue progress update (throttled: every 3 ticks or significant change)
            state.incrementTicksSinceLastSend();
            float currentProgress = (float) state.getProgress();
            if (state.getTicksSinceLastSend() >= 3
                    || Math.abs(currentProgress - state.getLastSentProgress()) > 0.05f) {
                pendingUpdates.put(mob, currentProgress);
                state.setLastSentProgress(currentProgress);
                state.resetTicksSinceLastSend();
            }
        }

        // Batch-send all pending updates
        flushPackets();

        // Periodic cleanup of stale aggro entries
        if (tickCounter % 200 == 0) {
            cleanupAggroTicks();
        }
    }

    /**
     * Sends all pending progress updates as a single batched packet per observing player.
     * Format: [count (int), mobId1 (int), progress1 (float), mobId2, progress2, ...]
     */
    private static void flushPackets() {
        if (pendingUpdates.isEmpty()) return;

        // Collect all players that need updates and which mob updates they can see
        Map<ServerPlayerEntity, List<Map.Entry<MobEntity, Float>>> playerUpdates = new HashMap<>();

        for (Map.Entry<MobEntity, Float> update : pendingUpdates.entrySet()) {
            MobEntity mob = update.getKey();
            if (mob.getWorld().isClient()) continue;

            for (ServerPlayerEntity player : PlayerLookup.tracking(mob)) {
                playerUpdates.computeIfAbsent(player, k -> new ArrayList<>()).add(update);
            }
        }

        // Send one batched packet per player
        for (Map.Entry<ServerPlayerEntity, List<Map.Entry<MobEntity, Float>>> entry : playerUpdates.entrySet()) {
            List<Map.Entry<MobEntity, Float>> updates = entry.getValue();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(updates.size());
            for (Map.Entry<MobEntity, Float> update : updates) {
                buf.writeInt(update.getKey().getId());
                buf.writeFloat(update.getValue());
            }
            ServerPlayNetworking.send(entry.getKey(), NetworkConstants.DETECTION_PROGRESS_BATCH_PACKET, buf);
        }

        pendingUpdates.clear();
    }

    private static double calculateDetectionRate(MobEntity mob, ServerPlayerEntity player, ModConfig config) {
        double baseRate = 1.0 / config.baseDetectionTicks;

        double distance = mob.distanceTo(player);
        double distanceRatio = 1.0 - Math.min(distance / config.maxDetectionRange, 1.0);

        if (distance <= 3.0) {
            return 1.0;
        }

        double curved = distanceRatio * distanceRatio;
        double distanceFactor = 1.0 + curved * config.detectionDistanceMultiplier;

        int lightLevel = player.getWorld().getLightLevel(player.getBlockPos());
        double lightFactor = 0.3 + (lightLevel / 15.0) * config.detectionLightMultiplier;

        double speed = player.getVelocity().horizontalLength();
        double movementFactor = speed > 0.01 ? 1.0 + config.detectionMovementMultiplier : 1.0;

        double sneakFactor = player.isSneaking() ? config.detectionSneakMultiplier : 1.0;

        return baseRate * distanceFactor * lightFactor * movementFactor * sneakFactor;
    }

    public static ServerPlayerEntity getSuspiciousTarget(MobEntity mob) {
        DetectionState state = detectionMap.get(mob);
        if (state != null && state.getProgress() >= 0.5) {
            return state.getTargetPlayer();
        }
        return null;
    }

    public static void markLostTarget(MobEntity mob) {
        lastAggroTick.put(mob.getId(), mob.getWorld().getTime());
    }

    public static boolean wasRecentlyAggroed(MobEntity mob) {
        Long tick = lastAggroTick.get(mob.getId());
        if (tick == null) return false;
        long now = mob.getWorld().getTime();
        if (now - tick < AGGRO_GRACE_TICKS) return true;
        lastAggroTick.remove(mob.getId());
        return false;
    }

    private static void cleanupAggroTicks() {
        // Can't get world time without a mob, but entries expire naturally via wasRecentlyAggroed.
        // Just cap size to prevent unbounded growth.
        if (lastAggroTick.size() > 500) {
            Iterator<Map.Entry<Integer, Long>> it = lastAggroTick.entrySet().iterator();
            int toRemove = lastAggroTick.size() - 200;
            while (it.hasNext() && toRemove > 0) {
                it.next();
                it.remove();
                toRemove--;
            }
        }
    }

    public static void clear() {
        detectionMap.clear();
        lastAggroTick.clear();
        pendingUpdates.clear();
        tickCounter = 0;
    }
}
