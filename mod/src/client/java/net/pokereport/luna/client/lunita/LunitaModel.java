package net.pokereport.luna.client.lunita;

import net.minecraft.util.Identifier;
import net.pokereport.luna.lunita.LunitaEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;

/** Recursos exportados de lunitapokemon.bbmodel, sin depender de Cobblemon. */
public final class LunitaModel extends GeoModel<LunitaEntity> {
    private static final Identifier MODEL = Identifier.of("lunaeternal", "geo/lunita.geo.json");
    private static final Identifier TEXTURE = Identifier.of("lunaeternal", "textures/entity/lunita.png");
    private static final Identifier ANIMATIONS = Identifier.of("lunaeternal", "animations/lunita.animation.json");
    @Override public Identifier getModelResource(LunitaEntity ignored) { return MODEL; }
    @Override public Identifier getTextureResource(LunitaEntity ignored) { return TEXTURE; }
    @Override public Identifier getAnimationResource(LunitaEntity ignored) { return ANIMATIONS; }
    @Override public void setCustomAnimations(LunitaEntity entity, long instanceId, AnimationState<LunitaEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        var head = getAnimationProcessor().getBone("head_ai");
        var data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            head.setRotX((float)Math.toRadians(Math.max(-30, Math.min(30, data.headPitch()))));
            head.setRotY((float)Math.toRadians(Math.max(-45, Math.min(45, data.netHeadYaw()))));
        }
    }
}
