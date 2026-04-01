package com.dysmn.doyouseemenow.detection;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Tracks the detection progress of a single mob toward a player.
 * Progress ranges from 0.0 (unaware) to 1.0 (full detection / aggro).
 */
public class DetectionState {

    private ServerPlayerEntity targetPlayer;
    private double progress; // 0.0 to 1.0
    private boolean detectedThisTick;
    private float lastSentProgress;
    private int ticksSinceLastSend;

    public DetectionState(ServerPlayerEntity player) {
        this.targetPlayer = player;
        this.progress = 0.0;
        this.detectedThisTick = true;
        this.lastSentProgress = -1.0f;
        this.ticksSinceLastSend = 0;
    }

    public ServerPlayerEntity getTargetPlayer() {
        return targetPlayer;
    }

    public void setTargetPlayer(ServerPlayerEntity player) {
        this.targetPlayer = player;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
    }

    public void addProgress(double amount) {
        setProgress(this.progress + amount);
    }

    public boolean isDetectedThisTick() {
        return detectedThisTick;
    }

    public void setDetectedThisTick(boolean detected) {
        this.detectedThisTick = detected;
    }

    public float getLastSentProgress() {
        return lastSentProgress;
    }

    public void setLastSentProgress(float progress) {
        this.lastSentProgress = progress;
    }

    public int getTicksSinceLastSend() {
        return ticksSinceLastSend;
    }

    public void incrementTicksSinceLastSend() {
        this.ticksSinceLastSend++;
    }

    public void resetTicksSinceLastSend() {
        this.ticksSinceLastSend = 0;
    }
}
