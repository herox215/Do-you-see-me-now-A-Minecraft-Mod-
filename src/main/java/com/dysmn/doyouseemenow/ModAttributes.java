package com.dysmn.doyouseemenow;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom entity attributes added by the mod.
 */
public final class ModAttributes {

    /** Stealth bonus: 0.0 = no bonus, each point adds to stealth score. */
    public static final EntityAttribute STEALTH_BONUS = new ClampedEntityAttribute(
            "attribute.do_you_see_me_now.stealth_bonus", 0.0, 0.0, 1.0
    ).setTracked(true);

    public static void register() {
        Registry.register(Registries.ATTRIBUTE,
                new Identifier(DoYouSeeMeNow.MOD_ID, "stealth_bonus"),
                STEALTH_BONUS);
    }

    private ModAttributes() {}
}
