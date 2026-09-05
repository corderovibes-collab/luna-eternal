package net.pokereport.luna.client;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.pokereport.luna.net.Red;

/**
 * Las fotos de los memoriales, de los bytes del servidor a una textura lista
 * para pintar.
 *
 * <h2>⚠⚠ EL SERVIDOR ES LA UNICA FUENTE, y el cliente solo cachea</h2>
 *
 * La foto llega troceada por {@code Red.FotoTramo} y aqui se ensambla. Se
 * guarda en disco ({@code lunaeternal/fotos/<sha1>.png}) para no pedirla dos
 * veces, y de ahi sale la textura. El cliente nunca inventa una foto: si no
 * esta en cache, la pide -- que es la regla P6 leida al reves, y tambien la
 * leccion del 23-ago (*el servidor es quien sabe que existe y si esta
 * aprobada*).
 */
public final class TexturasFoto {

    /** Una foto lista para pintar, con su proporcion real. */
    public record Foto(Identifier textura, int ancho, int alto) {}

    /** Lo mas que se guarda en memoria. Un memorial no son cien fotos. */
    private static final int CACHE_MAX = 24;

    private static final Map<String, NativeImageBackedTexture> TEXTURAS = new HashMap<>();
    private static final Map<String, Foto> PROPORCIONES = new HashMap<>();
    /** Las que ya se han pedido y aun no han llegado, para no pedirlas a coro. */
    private static final Set<String> PEDIDAS = new HashSet<>();
    /** Las que estan llegando ahora, trozo a trozo. */
    private static final Map<String, java.io.ByteArrayOutputStream> LLEGANDO =
            new HashMap<>();

    private TexturasFoto() {}

    /**
     * La foto si ya esta lista, o {@code null}.
     *
     * <p>⚠ Llamable desde el hilo de dibujado: solo lee mapas, no toca la red
     * ni el disco.
     */
    public static Foto lista(String sha1) {
        var tex = TEXTURAS.get(sha1);
        if (tex == null) {
            return null;
        }
        return PROPORCIONES.get(sha1);
    }

    /**
     * Pide la foto al servidor si no esta ni pedida ni en disco.
     *
     * <p>⚠ Se puede llamar cada fotograma: {@link #PEDIDAS} corta los repetidos.
     */
    public static void pedir(String sha1) {
        if (TEXTURAS.containsKey(sha1) || !PEDIDAS.add(sha1)) {
            return;
        }
        // ⚠ PRIMERO EL DISCO: si ya bajo una vez, no se vuelve a pedir por la
        //   red. La foto del servidor es inmutable --el fichero se llama como su
        //   sha1-- asi que la copia local no puede quedarse vieja.
        MinecraftClient.getInstance().execute(() -> {
            Path fichero = cache().resolve(sha1 + ".png");
            if (Files.exists(fichero)) {
                cargar(sha1, fichero);
                return;
            }
            ClientPlayNetworking.send(new Red.PedirFoto(sha1));
        });
    }

    /** Un trozo de foto que llega del servidor. Se ensambla y, al final, se
     *  guarda y se registra. */
    public static void alLlegarTramo(Red.FotoTramo tramo) {
        if (TEXTURAS.containsKey(tramo.sha1())) {
            return;
        }
        var ensamblando = LLEGANDO.computeIfAbsent(tramo.sha1(),
                s -> new java.io.ByteArrayOutputStream(tramo.total()));
        try {
            ensamblando.write(tramo.trozo());
        } catch (java.io.IOException e) {
            LLEGANDO.remove(tramo.sha1());
            return;
        }
        if (ensamblando.size() < tramo.total()) {
            return;
        }
        LLEGANDO.remove(tramo.sha1());
        byte[] bytes = ensamblando.toByteArray();
        MinecraftClient.getInstance().execute(() -> {
            try {
                Path fichero = cache().resolve(tramo.sha1() + ".png");
                Files.createDirectories(cache());
                Files.write(fichero, bytes);
                cargar(tramo.sha1(), fichero);
            } catch (java.io.IOException e) {
                PEDIDAS.remove(tramo.sha1());
            }
        });
    }

    /** Del fichero ya bajado a la textura registrada. Corre en el hilo del cliente. */
    private static void cargar(String sha1, Path fichero) {
        try (InputStream in = Files.newInputStream(fichero);
             NativeImage imagen = NativeImage.read(in)) {
            var textura = new NativeImageBackedTexture(imagen);
            MinecraftClient.getInstance().getTextureManager().registerTexture(
                    Identifier.of("lunaeternal", "foto_" + sha1), textura);
            TEXTURAS.put(sha1, textura);
            PROPORCIONES.put(sha1, new Foto(
                    Identifier.of("lunaeternal", "foto_" + sha1),
                    imagen.getWidth(), imagen.getHeight()));
        } catch (java.io.IOException e) {
            PEDIDAS.remove(sha1);
        }
        if (TEXTURAS.size() > CACHE_MAX) {
            // ⚠ Se suelta la mas vieja (orden de insercion). Es solo memoria:
            //   el fichero sigue en disco y se recarga si alguien vuelve a
            //   mirar ese memorial.
            String vieja = TEXTURAS.keySet().iterator().next();
            TEXTURAS.remove(vieja);
            PROPORCIONES.remove(vieja);
        }
    }

    /** Al salir del servidor se olvida todo: las fotos son de ESE mundo. */
    public static void olvidar() {
        TEXTURAS.clear();
        PROPORCIONES.clear();
        PEDIDAS.clear();
        LLEGANDO.clear();
    }

    private static Path cache() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("lunaeternal/fotos");
    }
}
