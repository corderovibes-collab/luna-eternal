package net.pokereport.pikachuarmor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PikachuArmorMod implements ModInitializer {
    public static final String MOD_ID = "pikachuarmor";
    public static final PikachuArmorItem HELMET = register("pikachu_helmet", ArmorItem.Type.HELMET, "helmet");
    public static final PikachuArmorItem CHESTPLATE = register("pikachu_chestplate", ArmorItem.Type.CHESTPLATE, "chestplate");
    public static final PikachuArmorItem LEGGINGS = register("pikachu_leggings", ArmorItem.Type.LEGGINGS, "leggings");
    public static final PikachuArmorItem BOOTS = register("pikachu_boots", ArmorItem.Type.BOOTS, "boots");

    private static PikachuArmorItem register(String id, ArmorItem.Type type, String piece) {
        var item = new PikachuArmorItem(ArmorMaterials.NETHERITE, type,
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

