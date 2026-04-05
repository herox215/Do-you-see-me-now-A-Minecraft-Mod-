package com.dysmn.doyouseemenow;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Custom items registered by the mod.
 */
public final class ModItems {

    /** UUID used by vanilla for feet-slot armor. */
    private static final UUID FEET_ARMOR_UUID =
            UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID SNEAK_BOOTS_STEALTH_UUID =
            UUID.fromString("d6c3a354-7f5e-4e2a-9c1b-3a8f7d2e1b0c");
    private static final UUID SNEAK_BOOTS_SPEED_UUID =
            UUID.fromString("a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d");

    public static final Item SNEAK_BOOTS = new SneakBootsItem(
            SneakArmorMaterial.INSTANCE,
            ArmorItem.Type.BOOTS,
            new FabricItemSettings().maxCount(1)
    );

    public static void register() {
        Registry.register(Registries.ITEM,
                new Identifier(DoYouSeeMeNow.MOD_ID, "sneak_boots"),
                SNEAK_BOOTS);

        // Add to combat tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.addAfter(Items.LEATHER_BOOTS, SNEAK_BOOTS);
        });
    }

    /**
     * Custom armor item that grants stealth bonus and sneak speed.
     */
    private static class SneakBootsItem extends ArmorItem {

        private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

        SneakBootsItem(ArmorMaterial material, Type type, Settings settings) {
            super(material, type, settings);

            ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder =
                    ImmutableMultimap.builder();

            // Base armor attributes from leather boots
            builder.put(EntityAttributes.GENERIC_ARMOR,
                    new EntityAttributeModifier(FEET_ARMOR_UUID,
                            "Armor modifier", material.getProtection(type),
                            EntityAttributeModifier.Operation.ADDITION));
            builder.put(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                    new EntityAttributeModifier(FEET_ARMOR_UUID,
                            "Armor toughness", material.getToughness(),
                            EntityAttributeModifier.Operation.ADDITION));

            // Stealth bonus: +15% stealth
            builder.put(ModAttributes.STEALTH_BONUS,
                    new EntityAttributeModifier(SNEAK_BOOTS_STEALTH_UUID,
                            "Sneak Boots stealth bonus", 0.15,
                            EntityAttributeModifier.Operation.ADDITION));

            // Sneak speed boost: +40% movement speed while equipped
            // (actual sneaking speed effect is handled via the attribute being present)
            builder.put(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    new EntityAttributeModifier(SNEAK_BOOTS_SPEED_UUID,
                            "Sneak Boots speed bonus", 0.01,
                            EntityAttributeModifier.Operation.ADDITION));

            this.modifiers = builder.build();
        }

        @Override
        public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
            return slot == this.type.getEquipmentSlot() ? this.modifiers : super.getAttributeModifiers(slot);
        }
    }

    private ModItems() {}
}
