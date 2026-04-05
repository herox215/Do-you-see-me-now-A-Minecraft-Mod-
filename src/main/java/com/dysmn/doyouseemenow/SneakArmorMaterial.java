package com.dysmn.doyouseemenow;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Custom armor material for Sneak Boots.
 * Same stats as leather, but uses the "sneak" texture name so
 * Minecraft loads textures/models/armor/sneak_layer_1.png.
 */
public class SneakArmorMaterial implements ArmorMaterial {

    public static final SneakArmorMaterial INSTANCE = new SneakArmorMaterial();

    // Leather protection values: boots=1, leggings=2, chestplate=3, helmet=1
    private static final int[] PROTECTION = {1, 2, 3, 1};
    private static final int DURABILITY_MULTIPLIER = 5; // same as leather

    // Base durabilities per slot (vanilla values)
    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};

    @Override
    public int getDurability(ArmorItem.Type type) {
        return BASE_DURABILITY[type.ordinal()] * DURABILITY_MULTIPLIER;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return PROTECTION[type.ordinal()];
    }

    @Override
    public int getEnchantability() {
        return 15; // same as leather
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.PHANTOM_MEMBRANE);
    }

    @Override
    public String getName() {
        // This determines the texture path: textures/models/armor/sneak_layer_1.png
        return DoYouSeeMeNow.MOD_ID + ":sneak";
    }

    @Override
    public float getToughness() {
        return 0.0f; // same as leather
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f; // same as leather
    }
}
