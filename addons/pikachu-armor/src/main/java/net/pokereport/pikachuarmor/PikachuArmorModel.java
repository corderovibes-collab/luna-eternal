package net.pokereport.pikachuarmor;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class PikachuArmorModel extends GeoModel<PikachuArmorItem> {
    @Override
    public Identifier getModelResource(PikachuArmorItem item) {
        return Identifier.of(PikachuArmorMod.MOD_ID,
                "geo/armor/pikachu_" + item.piece() + ".geo.json");
    }
    @Override
    public Identifier getTextureResource(PikachuArmorItem item) {
        return Identifier.of(PikachuArmorMod.MOD_ID,
                "textures/armor/pikachu_" + item.piece() + ".png");
    }
    @Override
    public Identifier getAnimationResource(PikachuArmorItem item) {
        return Identifier.of(PikachuArmorMod.MOD_ID,
                "animations/armor/pikachu_" + item.piece() + ".animation.json");
    }
}

