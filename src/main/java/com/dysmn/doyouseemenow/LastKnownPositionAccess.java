package com.dysmn.doyouseemenow;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Interface injected into MobEntity via mixin.
 * Stores the last known player position for investigation.
 */
public interface LastKnownPositionAccess {
	@Nullable
	Vec3d dysmn$getLastKnownTargetPos();

	void dysmn$setLastKnownTargetPos(@Nullable Vec3d pos);
}
