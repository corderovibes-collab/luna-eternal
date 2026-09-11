package net.pokereport.magikarparmor;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class MagikarpArmorModel extends GeoModel<MagikarpArmorItem> {
    @Override
    public Identifier getModelResource(MagikarpArmorItem item) {
        return Identifier.of(MagikarpArmorMod.MOD_ID,
                "geo/armor/magikarp_" + item.piece() + ".geo.json");
    }
    @Override
    public Identifier getTextureResource(MagikarpArmorItem item) {
        return Identifier.of(MagikarpArmorMod.MOD_ID,
                "textures/armor/magikarp_" + item.piece() + ".png");
    }
    @Override
    public Identifier getAnimationResource(MagikarpArmorItem item) {
        return Identifier.of(MagikarpArmorMod.MOD_ID,
                "animations/armor/magikarp_" + item.piece() + ".animation.json");
    }
}
