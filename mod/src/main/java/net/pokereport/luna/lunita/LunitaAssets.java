package net.pokereport.luna.lunita;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/** Checks the packaged resources on both dedicated servers and clients. */
public final class LunitaAssets {
    private LunitaAssets() {}
    private static InputStream resource(String name) {
        InputStream stream = LunitaAssets.class.getResourceAsStream("/assets/lunaeternal/" + name);
        if (stream == null) throw new IllegalStateException("Recurso de Lunita ausente: " + name);
        return stream;
    }
    public static void verify() throws Exception {
        Set<String> bones = new HashSet<>();
        com.google.gson.JsonObject geometry;
        try (var reader = new java.io.InputStreamReader(resource("geo/lunita.geo.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            geometry = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        }
        for (var bone : geometry.getAsJsonArray("bones")) {
            if (!bones.add(bone.getAsJsonObject().get("name").getAsString())) throw new IllegalStateException("Hueso duplicado");
        }
        for (var bone : geometry.getAsJsonArray("bones")) {
            var parent = bone.getAsJsonObject().get("parent");
            if (parent != null && !bones.contains(parent.getAsString())) throw new IllegalStateException("Padre inexistente");
        }
        try (var png = resource("textures/entity/lunita.png")) {
            byte[] header = png.readNBytes(24);
            var description = geometry.getAsJsonObject("description");
            if (header.length != 24 || ByteBuffer.wrap(header).getInt(16) != description.get("texture_width").getAsInt()
                    || ByteBuffer.wrap(header).getInt(20) != description.get("texture_height").getAsInt()) {
                throw new IllegalStateException("Dimensiones de textura incompatibles");
            }
        }
        try (var reader = new java.io.InputStreamReader(resource("animations/lunita.animation.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            var animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (String name : new String[]{"idle", "walk", "greet", "blink"}) {
                if (!animations.has("animation.lunita." + name)) throw new IllegalStateException("Animación ausente: " + name);
            }
            for (var entry : animations.entrySet()) {
                var channels = entry.getValue().getAsJsonObject().getAsJsonObject("bones");
                if (channels != null) for (String bone : channels.keySet()) {
                    if (!bones.contains(bone)) throw new IllegalStateException(entry.getKey() + ": hueso inexistente " + bone);
                }
            }
        }
    }
}
