package net.pokereport.luna.client.rotom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.pokereport.luna.rotom.RotomTutorial;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.players.MediaPlayer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;

/** Each Screen owns its worker and player. Resize only rebuilds widgets. */
public final class RotomVideoScreen extends Screen {
    private volatile boolean closed;
    private boolean prepared;
    private volatile String error;
    private volatile int percent;
    private ExecutorService worker;
    private Future<?> preparation;
    private volatile java.io.InputStream download;
    private MRL media;
    private MediaPlayer player;
    private long started;
    private long fadeAt;
    private final long opened = System.nanoTime();
    private boolean muted;
    private boolean ducked;
    private net.minecraft.registry.RegistryKey<net.minecraft.world.World> origin;

    public RotomVideoScreen() { super(Text.literal("Rotom Dex — Guía de gimnasios")); }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), b -> close())
                .dimensions(Math.max(0, width - 78), 8, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(muted ? "Audio: OFF" : "Audio: ON"), b -> {
            muted = !muted;
            b.setMessage(Text.literal(muted ? "Audio: OFF" : "Audio: ON"));
        }).dimensions(8, 8, 88, 20).build());
        if (prepared) return;
        prepared = true;
        origin = client.world == null ? null : client.world.getRegistryKey();
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PokeReport-Rotom-video"); t.setDaemon(true); return t;
        });
        preparation = worker.submit(this::prepare);
        worker.shutdown();
    }
    private void prepare() {
        Path temporary = null;
        try {
            Path directory = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                    .resolve("cache/pokereport/cinematicas");
            Files.createDirectories(directory);
            Path target = directory.resolve("rotom-gimnasios-v1.mp4");
            if (!valid(target)) {
                temporary = Files.createTempFile(directory, "rotom-", ".part");
                try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
                        .followRedirects(HttpClient.Redirect.NORMAL).build()) {
                    var response = http.send(HttpRequest.newBuilder(URI.create(RotomTutorial.VIDEO))
                            .timeout(Duration.ofSeconds(90)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                    download = response.body();
                    try (var input = download; var output = Files.newOutputStream(temporary)) {
                        if (response.statusCode() != 200) throw new java.io.IOException("HTTP " + response.statusCode());
                        byte[] buffer = new byte[65536]; long total = 0;
                        for (int n; (n = input.read(buffer)) >= 0;) {
                            if (closed || Thread.currentThread().isInterrupted()) return;
                            total += n;
                            if (total > RotomTutorial.BYTES) throw new java.io.IOException("Unexpected size");
                            output.write(buffer, 0, n);
                            percent = (int)(total * 100 / RotomTutorial.BYTES);
                        }
                    } finally { download = null; }
                }
                if (closed) return;
                if (!valid(temporary)) throw new java.io.IOException("Checksum mismatch");
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            }
            if (!closed) client.execute(() -> {
                if (closed || client.currentScreen != this) return;
                try { media = MediaAPI.mrl(target.toString()); }
                catch (Exception | LinkageError e) { fail(e); }
            });
        } catch (Exception e) { if (!closed) fail(e); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) {}
        }
    }
    private boolean valid(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) != RotomTutorial.BYTES) return false;
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) >= 0;) {
                if (closed || Thread.currentThread().isInterrupted()) return false;
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equals(RotomTutorial.SHA256);
    }
    private void fail(Throwable cause) {
        if (error != null || closed) return;
        error = "No se pudo cargar la guía de gimnasios. Inténtalo nuevamente.";
        org.slf4j.LoggerFactory.getLogger("PokeReport-Rotom").warn("Tutorial playback failed", cause);
    }
    @Override public void tick() {
        if (closed) return;
        if (client.world == null || client.player == null || !client.world.getRegistryKey().equals(origin)) { close(); return; }
        try {
            if (error != null) return;
            if (media != null && player == null) {
                if (media.status().failed()) { fail(media.exception()); return; }
                if (media.status().loaded()) {
                    player = MediaAPI.createPlayer(media,
                            () -> MediaAPI.glEngine(Thread.currentThread(), client::execute), MediaAPI::alEngine);
                    if (player == null) throw new IllegalStateException("WaterMedia unavailable");
                    if (!player.start()) throw new IllegalStateException("Decoder refused playback");
                    started = System.nanoTime();
                }
            }
            if (player != null) {
                if (player.error()) { fail(new IllegalStateException("WaterMedia decoder error")); releasePlayer(); return; }
                for (var category : net.minecraft.sound.SoundCategory.values()) {
                    if (category != net.minecraft.sound.SoundCategory.MASTER)
                        client.getSoundManager().updateSoundVolume(category, client.options.getSoundVolume(category)*.2f);
                }
                ducked = true;
                player.volume(muted ? 0 : Math.round(100 * client.options.getSoundVolume(net.minecraft.sound.SoundCategory.MASTER)));
                if (player.ended() && fadeAt == 0) fadeAt = System.nanoTime();
                if (fadeAt != 0 && System.nanoTime()-fadeAt > 350_000_000L) close();
                if (started != 0 && System.nanoTime()-started > 75_000_000_000L) close();
            } else if (System.nanoTime()-opened > 100_000_000_000L) {
                fail(new TimeoutException("Preparation timeout")); cancelDownload();
            }
        } catch (Exception | LinkageError e) { fail(e); releasePlayer(); }
    }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xFA040C16);
        if (player != null && player.texture() > 0 && error == null) {
            float scale = Math.min((float)width / Math.max(1, player.width()), (float)height / Math.max(1, player.height()));
            float w = player.width()*scale, h = player.height()*scale, x = (width-w)/2, y = (height-h)/2;
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShaderTexture(0, (int)player.texture());
            var b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            var matrix = ctx.getMatrices().peek().getPositionMatrix();
            b.vertex(matrix,x,y,0).texture(0,0); b.vertex(matrix,x,y+h,0).texture(0,1);
            b.vertex(matrix,x+w,y+h,0).texture(1,1); b.vertex(matrix,x+w,y,0).texture(1,0);
            BufferRenderer.drawWithGlobalProgram(b.end());
        } else {
            int cy=height/2;
            ctx.drawCenteredTextWithShadow(textRenderer,"ROTOM DEX",width/2,cy-35,0xFF56E8FF);
            var message = Text.literal(error == null ? "Cargando guía de gimnasios..." : error);
            int line=0;
            for (var text : textRenderer.wrapLines(message, Math.max(50,width-32)))
                ctx.drawCenteredTextWithShadow(textRenderer,text,width/2,cy+line++*11,0xFFDEEDF9);
            int span=Math.min(200,Math.max(40,width-40));
            ctx.fill((width-span)/2,cy+32,(width+span)/2,cy+35,0xFF163748);
            ctx.fill((width-span)/2,cy+32,(width-span)/2+span*percent/100,cy+35,0xFF56E8FF);
            int scan=(int)((System.nanoTime()-opened)/12_000_000L%Math.max(1,height));
            ctx.fill(0,scan,width,scan+1,0x1856E8FF);
        }
        if (fadeAt!=0) ctx.fill(0,0,width,height, ((int)(255*Math.min(1,(System.nanoTime()-fadeAt)/350_000_000.0))<<24));
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause() { return false; }
    private void cancelDownload() {
        if (preparation!=null) preparation.cancel(true);
        // Closing the stream breaks a blocked network read even during cancellation.
        if (download!=null) try { download.close(); } catch (java.io.IOException ignored) {}
        if (worker!=null) worker.shutdownNow();
    }
    private void releasePlayer() {
        if (player!=null) {
            var previous=player; player=null;
            try { previous.release(); }
            catch (Exception | LinkageError e) { fail(e); }
        }
        if (ducked) {
            for (var category : net.minecraft.sound.SoundCategory.values())
                if (category != net.minecraft.sound.SoundCategory.MASTER)
                    client.getSoundManager().updateSoundVolume(category, client.options.getSoundVolume(category));
            ducked=false;
        }
    }
    @Override public void removed() {
        if (closed) return;
        closed=true;
        cancelDownload();
        releasePlayer();
        media=null;
        super.removed();
    }
}
