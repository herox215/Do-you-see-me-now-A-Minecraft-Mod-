package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.LastKnownPositionAccess;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds a last known target position field to MobEntity.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityDataMixin implements LastKnownPositionAccess {

	@Unique
	@Nullable
	private Vec3d dysmn_lastKnownTargetPos = null;

	@Override
	@Nullable
	public Vec3d dysmn$getLastKnownTargetPos() {
		return this.dysmn_lastKnownTargetPos;
	}

	@Override
	public void dysmn$setLastKnownTargetPos(@Nullable Vec3d pos) {
		this.dysmn_lastKnownTargetPos = pos;
	}
}
