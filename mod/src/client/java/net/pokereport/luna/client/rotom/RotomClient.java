package net.pokereport.luna.client.rotom;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Identifier;
import net.pokereport.luna.rotom.RotomEntity;
import net.pokereport.luna.rotom.RotomTutorial;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class RotomClient {
    public static void register() {
        EntityRendererRegistry.register(RotomTutorial.TYPE, ctx -> {
            var renderer = new GeoEntityRenderer<>(ctx, new GeoModel<RotomEntity>() {
                @Override public Identifier getModelResource(RotomEntity e) {
                    return Identifier.of("lunaeternal", "geo/rotom_dex.geo.json");
                }
                @Override public Identifier getTextureResource(RotomEntity e) {
                    return Identifier.of("lunaeternal", "textures/entity/rotom_dex_" + e.expression() + ".png");
                }
                @Override public Identifier getAnimationResource(RotomEntity e) {
                    return Identifier.of("lunaeternal", "animations/rotom_dex.animation.json");
                }
            });
            renderer.addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(renderer));
            return renderer;
        });
        ClientPlayNetworking.registerGlobalReceiver(RotomTutorial.Open.ID, (packet, ctx) ->
                ctx.client().execute(() -> {
                    if (ctx.client().world != null && !(ctx.client().currentScreen instanceof RotomVideoScreen))
                        ctx.client().setScreen(new RotomVideoScreen(packet.tutorial()));
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.currentScreen instanceof RotomVideoScreen screen) screen.removed();
        });
    }
    private RotomClient() {}
}
