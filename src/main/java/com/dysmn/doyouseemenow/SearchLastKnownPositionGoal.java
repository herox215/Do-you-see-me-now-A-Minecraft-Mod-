package com.dysmn.doyouseemenow;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * AI Goal: mob investigates the last known player position.
 *
 * Phases:
 * 1. TURN_TOWARD - Turn toward the position (for melee hits)
 * 2. WALK_TO     - Walk to the position (for distant targets)
 * 3. LOOK_AROUND - Look around at the destination
 */
public class SearchLastKnownPositionGoal extends Goal {

	private final MobEntity mob;

	private static final int LOOK_AROUND_DURATION = 100; // 5 seconds
	private static final double ARRIVAL_DISTANCE = 1.5;
	private static final int WALK_TIMEOUT = 200; // 10 seconds
	private static final int TURN_DURATION = 15; // ~0.75 seconds to turn
	private static final int LOOK_CHANGE_INTERVAL = 25;

	// Walk instead of just turning beyond this distance
	private static final double WALK_THRESHOLD = 5.0;

	private enum Phase { TURN_TOWARD, WALK_TO, LOOK_AROUND }

	private Phase phase;
	private Vec3d targetPos;
	private int timer;
	private float lookYaw;
	private double walkSpeed;

	public SearchLastKnownPositionGoal(MobEntity mob) {
		this.mob = mob;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (mob.getTarget() != null) return false;
		Vec3d lastPos = ((LastKnownPositionAccess) mob).dysmn$getLastKnownTargetPos();
		return lastPos != null;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.targetPos = ((LastKnownPositionAccess) mob).dysmn$getLastKnownTargetPos();
		this.timer = 0;

		if (targetPos == null) return;

		double distance = mob.getPos().distanceTo(targetPos);

		if (distance < WALK_THRESHOLD) {
			// Close range (melee hit): turn first
			this.phase = Phase.TURN_TOWARD;
			this.walkSpeed = 1.3;
		} else {
			// Far away (ranged/lost target): walk directly
			this.phase = Phase.WALK_TO;
			this.walkSpeed = 1.0;
			mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, walkSpeed);
		}

		// Look toward target immediately
		mob.getLookControl().lookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);

		// Send "?" packet to nearby players
		sendSearchingPacket(true);
	}

	@Override
	public void tick() {
		timer++;

		switch (phase) {
			case TURN_TOWARD -> tickTurnToward();
			case WALK_TO -> tickWalkTo();
			case LOOK_AROUND -> tickLookAround();
		}
	}

	private void tickTurnToward() {
		if (targetPos == null) return;

		// Actively look at target position every tick (forces head+body rotation)
		mob.getLookControl().lookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);

		if (timer >= TURN_DURATION) {
			// Done turning — look around if close enough, otherwise walk there
			double distance = mob.getPos().distanceTo(targetPos);
			if (distance <= ARRIVAL_DISTANCE) {
				phase = Phase.LOOK_AROUND;
				timer = 0;
				lookYaw = mob.getHeadYaw();
			} else {
				phase = Phase.WALK_TO;
				timer = 0;
				mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, walkSpeed);
			}
		}
	}

	private void tickWalkTo() {
		if (targetPos == null) return;

		mob.getLookControl().lookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);

		double distance = mob.getPos().distanceTo(targetPos);
		if (distance <= ARRIVAL_DISTANCE || mob.getNavigation().isIdle()) {
			phase = Phase.LOOK_AROUND;
			timer = 0;
			mob.getNavigation().stop();
			lookYaw = mob.getHeadYaw();
			return;
		}

		if (timer > WALK_TIMEOUT) {
			phase = Phase.LOOK_AROUND;
			timer = 0;
			mob.getNavigation().stop();
			lookYaw = mob.getHeadYaw();
		}
	}

	private void tickLookAround() {
		if (timer % LOOK_CHANGE_INTERVAL == 0) {
			lookYaw += (mob.getRandom().nextFloat() - 0.5f) * 180.0f;
		}

		double lookX = mob.getX() - Math.sin(Math.toRadians(lookYaw)) * 5.0;
		double lookZ = mob.getZ() + Math.cos(Math.toRadians(lookYaw)) * 5.0;
		mob.getLookControl().lookAt(lookX, mob.getEyeY(), lookZ);

		if (timer % 40 == 0) {
			double offsetX = (mob.getRandom().nextDouble() - 0.5) * 4.0;
			double offsetZ = (mob.getRandom().nextDouble() - 0.5) * 4.0;
			mob.getNavigation().startMovingTo(
				mob.getX() + offsetX,
				mob.getY(),
				mob.getZ() + offsetZ,
				walkSpeed * 0.6
			);
		}
	}

	@Override
	public boolean shouldContinue() {
		if (mob.getTarget() != null) return false;
		if (phase == Phase.LOOK_AROUND && timer >= LOOK_AROUND_DURATION) return false;

		// Pick up new position (e.g. another hit while already searching)
		Vec3d newPos = ((LastKnownPositionAccess) mob).dysmn$getLastKnownTargetPos();
		if (newPos != null && !newPos.equals(targetPos)) {
			targetPos = newPos;
			phase = Phase.TURN_TOWARD;
			timer = 0;
			mob.getLookControl().lookAt(targetPos.x, targetPos.y + 1.0, targetPos.z);
		}

		return true;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		((LastKnownPositionAccess) mob).dysmn$setLastKnownTargetPos(null);
		this.targetPos = null;
		this.timer = 0;

		// Remove "?" indicator
		sendSearchingPacket(false);
	}

	private void sendSearchingPacket(boolean searching) {
		if (mob.getWorld().isClient()) return;

		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(mob.getId());
		buf.writeBoolean(searching);

		for (ServerPlayerEntity player : PlayerLookup.tracking(mob)) {
			ServerPlayNetworking.send(player, NetworkConstants.MOB_SEARCHING_PACKET, buf);
		}
	}
}
