package net.pokereport.eeveelutionarmor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class EeveelutionArmorMod implements ModInitializer {
    public static final String MOD_ID = "eeveelution";

    public static final EeveelutionArmorItem HELMET = register("eeveelution_helmet", ArmorItem.Type.HELMET, "helmet");
    public static final EeveelutionArmorItem CHESTPLATE = register("eeveelution_chestplate", ArmorItem.Type.CHESTPLATE, "chestplate");
    public static final EeveelutionArmorItem LEGGINGS = register("eeveelution_leggings", ArmorItem.Type.LEGGINGS, "leggings");
    public static final EeveelutionArmorItem BOOTS = register("eeveelution_boots", ArmorItem.Type.BOOTS, "boots");

    private static EeveelutionArmorItem register(String id, ArmorItem.Type type, String piece) {
        var item = new EeveelutionArmorItem(ArmorMaterials.NETHERITE, type,
                new Item.Settings().maxCount(1), piece);
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), item);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(HELMET);
            entries.add(CHESTPLATE);
            entries.add(LEGGINGS);
            entries.add(BOOTS);
        });
    }
}
