package com.dysmn.doyouseemenow.mixin;

import com.dysmn.doyouseemenow.ModAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the custom STEALTH_BONUS attribute to the player's default attributes,
 * so equipment modifiers (e.g. Sneak Boots) can actually apply.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerAttributeMixin {

    @Inject(method = "createPlayerAttributes", at = @At("RETURN"))
    private static void addStealthAttribute(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
        cir.getReturnValue().add(ModAttributes.STEALTH_BONUS);
    }
}
