package net.pokereport.luna.client.lunita;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.pokereport.luna.lunita.LunitaEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renderer cliente aislado: el servidor nunca carga clases gráficas. */
public final class LunitaRenderer extends GeoEntityRenderer<LunitaEntity> {
    public LunitaRenderer(EntityRendererFactory.Context context) {
        super(context, new LunitaModel());
        shadowRadius = .45f;
    }
}
