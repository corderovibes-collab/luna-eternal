package net.pokereport.luna.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.players.MediaPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

/** Reproductor a pantalla completa de las cinematicas de PokeReport. */
public final class CinematicaScreen extends Screen {
    private static final String OAK_SHA256 =
            "eca79c6a08a26c9b5730f0bf772af1278e17b81d942090d890c1f1f18ffd5c9a";
    private final String url;
    private volatile MRL medio;
    private final int limiteSegundos;
    private final boolean puedeSalir;
    private MediaPlayer reproductor;
    private String error = "";
    private long inicio;

    public CinematicaScreen(String url, int limiteSegundos, boolean puedeSalir) {
        super(Text.literal("Cinematica PokeReport"));
        this.url = url;
        this.limiteSegundos = limiteSegundos;
        this.puedeSalir = puedeSalir;
    }

    @Override
    protected void init() {
        this.inicio = 0L;
        prepararLocal();
    }

    /**
     * Descarga completa y verifica antes de reproducir. Así el decodificador
     * nunca depende de que GitHub entregue el siguiente fragmento a tiempo y
     * desaparecen las pausas a mitad de la película. La segunda reproducción
     * usa directamente la copia cacheada de 15 MB.
     */
    private void prepararLocal() {
        CompletableFuture.runAsync(() -> {
            try {
                Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameDir().resolve("cache/pokereport/cinematicas");
                Files.createDirectories(dir);
                Path destino = dir.resolve("profesor-oak-intro-v2.mp4");
                if (!Files.isRegularFile(destino) || !hash(destino).equals(OAK_SHA256)) {
                    Path temporal = dir.resolve("profesor-oak-intro-v2.mp4.part");
                    Files.deleteIfExists(temporal);
                    HttpClient http = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.ALWAYS).build();
                    HttpResponse<Path> respuesta = http.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .header("User-Agent", "PokeReport-Luna/1.0")
                                    .GET().build(), HttpResponse.BodyHandlers.ofFile(temporal));
                    if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300
                            || !hash(temporal).equals(OAK_SHA256)) {
                        Files.deleteIfExists(temporal);
                        throw new IllegalStateException("descarga incompleta o checksum invalido");
                    }
                    try {
                        Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                MinecraftClient.getInstance().execute(() -> medio = MediaAPI.mrl(destino.toString()));
            } catch (Exception e) {
                error = "No se pudo preparar el video: " + e.getMessage();
            }
        });
    }

    private static String hash(Path ruta) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(ruta)) {
            byte[] bloque = new byte[1024 * 1024];
            for (int n; (n = in.read(bloque)) >= 0;) {
                if (n > 0) md.update(bloque, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    @Override
    public void tick() {
        if (medio != null && reproductor == null && error.isEmpty()) {
            if (medio.status().loaded()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                reproductor = MediaAPI.createPlayer(medio,
                        () -> MediaAPI.glEngine(Thread.currentThread(), mc::execute),
                        MediaAPI::alEngine);
                if (reproductor == null) {
                    error = "No se pudo iniciar el reproductor multimedia.";
                } else {
                    reproductor.volume(100);
                    reproductor.start();
                    inicio = System.currentTimeMillis();
                }
            } else if (medio.status().failed()) {
                Throwable causa = medio.exception();
                error = causa == null ? "No se pudo cargar el video."
                        : "No se pudo cargar el video: " + causa.getMessage();
            }
        }
        if (reproductor != null && reproductor.ended()) {
            close();
        } else if (limiteSegundos > 0 && inicio > 0
                && System.currentTimeMillis() - inicio >= limiteSegundos * 1000L) {
            close();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF000000);
        if (reproductor == null || reproductor.texture() <= 0) {
            String mensaje = error.isEmpty()
                    ? "Descargando y preparando video del Profesor Oak..." : error;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(mensaje),
                    width / 2, height / 2 - 5, error.isEmpty() ? 0xFFE3C75F : 0xFFFF6666);
            if (!error.isEmpty() && puedeSalir) {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Pulsa ESC para volver"), width / 2,
                        height / 2 + 14, 0xFFAAAAAA);
            }
            return;
        }

        int vw = Math.max(1, reproductor.width());
        int vh = Math.max(1, reproductor.height());
        float escala = Math.min((float) width / vw, (float) height / vh);
        float w = vw * escala;
        float h = vh * escala;
        float x = (width - w) * 0.5f;
        float y = (height - h) * 0.5f;

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, (int) reproductor.texture());
        BufferBuilder b = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        var matriz = ctx.getMatrices().peek().getPositionMatrix();
        b.vertex(matriz, x, y, 0).texture(0, 0);
        b.vertex(matriz, x, y + h, 0).texture(0, 1);
        b.vertex(matriz, x + w, y + h, 0).texture(1, 1);
        b.vertex(matriz, x + w, y, 0).texture(1, 0);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && puedeSalir) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        if (reproductor != null) {
            reproductor.release();
            reproductor = null;
        }
        super.removed();
    }
}
