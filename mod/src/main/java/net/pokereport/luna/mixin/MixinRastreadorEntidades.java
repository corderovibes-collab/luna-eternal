package net.pokereport.luna.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.world.VisibilidadJugadores;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EL RECORTE DE JUGADORES VISIBLES.
 *
 * <p>Vanilla decide aqui, por cada pareja (entidad, jugador que mira), si esa
 * entidad debe existir en la pantalla del otro. Es el unico sitio donde se puede
 * decir «a este no».
 *
 * <h2>&#9888;&#9888;&#9888; POR QUE HACE FALTA UN MIXIN, QUE ES EL PRIMERO DEL
 * PROYECTO</h2>
 *
 * Se miraron las alternativas y ninguna sirve:
 * <ul>
 *   <li>La API de Fabric tiene {@code EntityTrackingEvents}, pero sus eventos
 *       <b>avisan, no deciden</b>: no se pueden cancelar.</li>
 *   <li>{@code entity-broadcast-range-percentage} de {@code server.properties}
 *       es un porcentaje <b>global y para todas las entidades</b>: recortarlo
 *       para que no se vean 200 jugadores en la plaza haria que los Pokemon
 *       decorativos, los lideres y los hologramas aparecieran de golpe delante
 *       de la cara. Y no distingue el lobby de la ciudadela.</li>
 *   <li>Poner a la gente invisible de verdad toca el juego (se pelea con los
 *       combates y con los cosmeticos) y ademas <b>no ahorra nada</b>: la
 *       entidad sigue viajando.</li>
 * </ul>
 *
 * <h2>&#9888;&#9888; SE LLAMA A {@code stopTracking} Y NO SOLO SE CANCELA</h2>
 *
 * Cancelar a secas solo impide <b>empezar</b> a verlo. A quien ya estuviera
 * dibujado se le quedaria el muñeco plantado para siempre --vanilla no volveria
 * a preguntar-- y el sintoma seria mucho peor que no tener el recorte: jugadores
 * fantasma que no estan ahi. {@code stopTracking} sobre alguien a quien no se
 * estaba siguiendo no hace nada, asi que sirve para los dos casos.
 *
 * <p>&#9888; La firma se verifico <b>contra el jar de Yarn 1.21.1 que compila
 * este servidor</b> ({@code javap} sobre {@code minecraft-merged}), no de
 * memoria: {@code ServerChunkLoadingManager$EntityTracker} es una clase interna
 * de paquete y su nombre cambio en 1.20.5 (antes {@code
 * ThreadedAnvilChunkStorage}). Un objetivo mal escrito no compila mal: <b>no
 * aplica</b>, y el recorte simplemente no existiria.
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class MixinRastreadorEntidades {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    public abstract void stopTracking(ServerPlayerEntity jugador);

    /**
     * &#9888; Solo se toca cuando lo seguido es <b>otro jugador</b>. Los Pokemon,
     * los objetos y los hologramas se dejan en paz: el problema que esto
     * resuelve es una plaza con doscientas personas, no el mundo.
     */
    @Inject(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void luna$recortarJugadores(ServerPlayerEntity visor, CallbackInfo ci) {
        // Deja constancia de que el mixin SI se aplico, que es lo unico que
        // distingue «el recorte funciona» de «el recorte no existe y nadie lo
        // sabe». Ver VisibilidadJugadores.vivo().
        VisibilidadJugadores.marcarVivo();
        if (!(this.entity instanceof ServerPlayerEntity objetivo)) {
            return;
        }
        if (VisibilidadJugadores.visible(visor, objetivo)) {
            return;
        }
        this.stopTracking(visor);
        ci.cancel();
    }
}
