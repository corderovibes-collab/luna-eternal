package net.pokereport.luna.client.lunita;

import net.minecraft.util.Identifier;
import net.pokereport.luna.lunita.LunitaEntity;
import software.bernie.geckolib.model.GeoModel;

/** Recursos exportados de lunitapokemon.bbmodel, sin depender de Cobblemon. */
public final class LunitaModel extends GeoModel<LunitaEntity> {
    private static final Identifier MODEL = Identifier.of("lunaeternal", "geo/lunita.geo.json");
    private static final Identifier TEXTURE = Identifier.of("lunaeternal", "textures/entity/lunita.png");
    private static final Identifier ANIMATIONS = Identifier.of("lunaeternal", "animations/lunita.animation.json");
    @Override public Identifier getModelResource(LunitaEntity ignored) { return MODEL; }
    @Override public Identifier getTextureResource(LunitaEntity ignored) { return TEXTURE; }
    @Override public Identifier getAnimationResource(LunitaEntity ignored) { return ANIMATIONS; }
}
