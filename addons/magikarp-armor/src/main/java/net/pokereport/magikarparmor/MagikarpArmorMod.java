package net.pokereport.magikarparmor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MagikarpArmorMod implements ModInitializer {
    public static final String MOD_ID = "magikarparmor";
    public static final MagikarpArmorItem HELMET = register("magikarp_helmet", ArmorItem.Type.HELMET, "helmet");
    public static final MagikarpArmorItem CHESTPLATE = register("magikarp_chestplate", ArmorItem.Type.CHESTPLATE, "chestplate");
    public static final MagikarpArmorItem LEGGINGS = register("magikarp_leggings", ArmorItem.Type.LEGGINGS, "leggings");
    public static final MagikarpArmorItem BOOTS = register("magikarp_boots", ArmorItem.Type.BOOTS, "boots");

    private static MagikarpArmorItem register(String id, ArmorItem.Type type, String piece) {
        var item = new MagikarpArmorItem(ArmorMaterials.NETHERITE, type,
                new Item.Settings().maxCount(1), piece);
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), item);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(HELMET); entries.add(CHESTPLATE);
            entries.add(LEGGINGS); entries.add(BOOTS);
        });
    }
}
