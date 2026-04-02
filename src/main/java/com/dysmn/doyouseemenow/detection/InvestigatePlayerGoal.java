package com.dysmn.doyouseemenow.detection;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;

/**
 * AI Goal: mob slowly approaches a player it is suspicious of (detection >= 50%).
 * Stops when detection decays to 0 (forget) or reaches 1.0 (full aggro takes over).
 */
public class InvestigatePlayerGoal extends Goal {

    private final MobEntity mob;
    private ServerPlayerEntity target;
    private int repathTimer;

    private static final double APPROACH_SPEED = 0.6;
    private static final int REPATH_INTERVAL = 20;

    public InvestigatePlayerGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (mob.getTarget() != null) return false;
        ServerPlayerEntity suspicious = DetectionTracker.getSuspiciousTarget(mob);
        if (suspicious != null && suspicious.isAlive() && !suspicious.isRemoved()) {
            this.target = suspicious;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
        if (mob.getTarget() != null) return false;
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        ServerPlayerEntity suspicious = DetectionTracker.getSuspiciousTarget(mob);
        return suspicious == target;
    }

    @Override
    public void start() {
        this.repathTimer = 0;
        pathToTarget();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) return;

        mob.getLookControl().lookAt(target, 30.0f, 30.0f);

        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL) {
            repathTimer = 0;
            pathToTarget();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        this.target = null;
    }

    private void pathToTarget() {
        if (target != null) {
            mob.getNavigation().startMovingTo(target, APPROACH_SPEED);
        }
    }
}
