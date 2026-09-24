package net.pokereport.luna.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Tablist;

/**
 * Registro de items del mod Luna Eternal.
 */
public final class LunaItems {

    private LunaItems() {}

    // ---- ÉLITE ----
    public static final ArmaduraRangoItem ELITE_HELMET = register("elite_helmet",
            ArmorItem.Type.HELMET, Tablist.Rank.ELITE, "elite", "helmet");
    public static final ArmaduraRangoItem ELITE_CHESTPLATE = register("elite_chestplate",
            ArmorItem.Type.CHESTPLATE, Tablist.Rank.ELITE, "elite", "chestplate");
    public static final ArmaduraRangoItem ELITE_LEGGINGS = register("elite_leggings",
            ArmorItem.Type.LEGGINGS, Tablist.Rank.ELITE, "elite", "leggings");
    public static final ArmaduraRangoItem ELITE_BOOTS = register("elite_boots",
            ArmorItem.Type.BOOTS, Tablist.Rank.ELITE, "elite", "boots");

    // ---- CAMPEÓN ----
    public static final ArmaduraRangoItem CAMPEON_HELMET = register("campeon_helmet",
            ArmorItem.Type.HELMET, Tablist.Rank.CAMPEON, "campeon", "helmet");
    public static final ArmaduraRangoItem CAMPEON_CHESTPLATE = register("campeon_chestplate",
            ArmorItem.Type.CHESTPLATE, Tablist.Rank.CAMPEON, "campeon", "chestplate");
    public static final ArmaduraRangoItem CAMPEON_LEGGINGS = register("campeon_leggings",
            ArmorItem.Type.LEGGINGS, Tablist.Rank.CAMPEON, "campeon", "leggings");
    public static final ArmaduraRangoItem CAMPEON_BOOTS = register("campeon_boots",
            ArmorItem.Type.BOOTS, Tablist.Rank.CAMPEON, "campeon", "boots");

    // ---- MAESTRO ----
    public static final ArmaduraRangoItem MAESTRO_HELMET = register("maestro_helmet",
            ArmorItem.Type.HELMET, Tablist.Rank.MAESTRO, "maestro", "helmet");
    public static final ArmaduraRangoItem MAESTRO_CHESTPLATE = register("maestro_chestplate",
            ArmorItem.Type.CHESTPLATE, Tablist.Rank.MAESTRO, "maestro", "chestplate");
    public static final ArmaduraRangoItem MAESTRO_LEGGINGS = register("maestro_leggings",
            ArmorItem.Type.LEGGINGS, Tablist.Rank.MAESTRO, "maestro", "leggings");
    public static final ArmaduraRangoItem MAESTRO_BOOTS = register("maestro_boots",
            ArmorItem.Type.BOOTS, Tablist.Rank.MAESTRO, "maestro", "boots");

    // ---- LEYENDA ----
    public static final ArmaduraRangoItem LEYENDA_HELMET = register("leyenda_helmet",
            ArmorItem.Type.HELMET, Tablist.Rank.LEYENDA, "leyenda", "helmet");
    public static final ArmaduraRangoItem LEYENDA_CHESTPLATE = register("leyenda_chestplate",
            ArmorItem.Type.CHESTPLATE, Tablist.Rank.LEYENDA, "leyenda", "chestplate");
    public static final ArmaduraRangoItem LEYENDA_LEGGINGS = register("leyenda_leggings",
            ArmorItem.Type.LEGGINGS, Tablist.Rank.LEYENDA, "leyenda", "leggings");
    public static final ArmaduraRangoItem LEYENDA_BOOTS = register("leyenda_boots",
            ArmorItem.Type.BOOTS, Tablist.Rank.LEYENDA, "leyenda", "boots");

    public static final ArmaduraRangoItem[] TODOS = {
        ELITE_HELMET, ELITE_CHESTPLATE, ELITE_LEGGINGS, ELITE_BOOTS,
        CAMPEON_HELMET, CAMPEON_CHESTPLATE, CAMPEON_LEGGINGS, CAMPEON_BOOTS,
        MAESTRO_HELMET, MAESTRO_CHESTPLATE, MAESTRO_LEGGINGS, MAESTRO_BOOTS,
        LEYENDA_HELMET, LEYENDA_CHESTPLATE, LEYENDA_LEGGINGS, LEYENDA_BOOTS
    };

    private static ArmaduraRangoItem register(String id, ArmorItem.Type type,
                                             Tablist.Rank rank, String suitId, String piece) {
        var item = new ArmaduraRangoItem(ArmorMaterials.NETHERITE, type,
                new Item.Settings().maxDamage(type.getMaxDamage(37)).fireproof(), rank, suitId, piece);
        return Registry.register(Registries.ITEM, Identifier.of(LunaEternal.MOD_ID, id), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            for (var item : TODOS) {
                entries.add(item);
            }
        });
        LunaEternal.LOG.info("LunaItems: 16 piezas de armadura de rango registradas");
    }
}
