package com.dysmn.doyouseemenow.sound;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a sound event at a position with a detection radius.
 *
 * @param position where the sound originates
 * @param radius   effective detection radius (after armor multipliers etc.)
 * @param type     the kind of sound
 * @param source   entity that caused the sound (player/projectile), may be null
 */
public record SoundEvent(
	Vec3d position,
	double radius,
	SoundType type,
	@Nullable Entity source
) {}
