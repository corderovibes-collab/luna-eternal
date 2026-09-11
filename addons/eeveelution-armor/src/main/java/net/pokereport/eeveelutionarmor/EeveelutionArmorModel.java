package net.pokereport.eeveelutionarmor;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class EeveelutionArmorModel extends GeoModel<EeveelutionArmorItem> {
    @Override
    public Identifier getModelResource(EeveelutionArmorItem item) {
        return Identifier.of(EeveelutionArmorMod.MOD_ID,
                "geo/armor/eeveelution_" + item.piece() + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(EeveelutionArmorItem item) {
        return Identifier.of(EeveelutionArmorMod.MOD_ID,
                "textures/armor/eeveelution_" + item.piece() + ".png");
    }

    @Override
    public Identifier getAnimationResource(EeveelutionArmorItem item) {
        return Identifier.of(EeveelutionArmorMod.MOD_ID,
                "animations/armor/empty.animation.json");
    }
}
