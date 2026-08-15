package net.pokereport.luna.pokedex;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.net.Red;

/**
 * Cuando alguien escanea un Pokémon, le suena su descripción.
 *
 * <p><b>Solo a quien escanea.</b> El evento de Cobblemon trae el
 * {@code ServerPlayer} que ha hecho el escaneo, así que el paquete se manda por
 * su conexión y por ninguna otra. Si dos personas escanean a la vez —o la misma
 * especie— cada una oye la suya y ninguna oye la de la otra. No hay difusión a
 * la zona ni sonido colocado en el mundo.
 *
 * <p>No toca la base de datos, así que se resuelve entero en el hilo del
 * servidor: una consulta a un conjunto en memoria y un paquete.
 */
public final class ScanListener {

    /**
     * ⚠️ COBBLEMON DISPARA ESTE EVENTO VARIAS VECES POR ESCANEO, y hay que
     * tragárselo aquí.
     *
     * <p>Su cliente hace esto, en {@code PokedexUsageContext} (1.7.3):
     *
     * <pre>
     * if (scanningProgress &lt; (MAX_SCAN_PROGRESS + CENTER_INFO_DISPLAY_INTERVALS))
     *     scanningProgress += updateInterval
     * if (scanningProgress &gt;= MAX_SCAN_PROGRESS)
     *     targetId?.let { FinishScanningPacket(it, ...).sendToServer() }
     * </pre>
     *
     * <p>El progreso sigue subiendo por encima del máximo, así que manda un
     * paquete de «he terminado» <b>en cada tick</b> hasta que el servidor le
     * confirma el registro y el cliente se resetea. Y el manejador del servidor
     * no limpia {@code PlayerScanningDetails}, así que cada uno de esos paquetes
     * vuelve a pasar la comprobación y vuelve a disparar el evento.
     *
     * <p>Resultado sin esto: la voz se oye <b>duplicada</b>, que es exactamente
     * como se detectó. Un segundo se traga la ráfaga entera —que dura unos pocos
     * ticks— y deja pasar un reescaneo deliberado.
     */
    private static final Cooldown RAFAGA = new Cooldown(1_000);

    /**
     * Y este es el freno de verdad, por si alguien insiste.
     *
     * <p>La descripción dura varios segundos; volver a lanzarla cada dos por
     * tres no es información, es ruido. Se mide <b>por jugador</b>, no por
     * especie: escanear tres Pokémon distintos seguidos tampoco debe encadenar
     * tres narraciones solapadas.
     */
    private static final Cooldown FRENO = new Cooldown(3_000);

    private ScanListener() {}

    public static void register() {
        try {
            CobblemonEvents.POKEMON_SCANNED.subscribe(event -> {
                try {
                    // El nombre sale de la ficha del Pokémon escaneado, no de la
                    // entidad: un Ditto disfrazado se escanea como lo que ES, y
                    // esa es justamente la gracia del escáner.
                    String especie = event.getScannedPokemonEntityData()
                            .getPokemon().getSpecies().getName();
                    hablar(event.getPlayer(), especie);
                } catch (Throwable t) {
                    // Un escaneo que no suena es un fastidio; uno que revienta
                    // le rompe la Pokédex al jugador. Se traga.
                    LunaEternal.LOG.error("Error al dar voz a un escaneo", t);
                }
            });
            LunaEternal.LOG.info("Pokédex: {} voces listas", VozService.cuantas());
        } catch (Throwable t) {
            // Si Cobblemon cambia el evento, el resto del mod sigue vivo.
            LunaEternal.LOG.warn("No se pudo escuchar los escaneos: {}", t.toString());
        }
    }

    /** Manda la voz, si esa especie tiene y si toca. */
    static void hablar(ServerPlayerEntity jugador, String especie) {
        if (jugador == null) {
            return;
        }
        String id = VozService.normalizar(especie);
        if (!VozService.tieneVoz(id)) {
            return;       // sin voz grabada: el escaneo funciona igual, mudo
        }
        String quien = jugador.getUuidAsString();
        // La ráfaga se mide por jugador Y especie; el freno solo por jugador.
        // Así dos especies distintas seguidas las para el freno --que es lo que
        // se quiere-- pero la ráfaga de la misma no se confunde con un
        // reescaneo legítimo de otra.
        if (!RAFAGA.toca(quien + "|" + id) || !FRENO.toca(quien)) {
            return;
        }
        ServerPlayNetworking.send(jugador, new Red.VozPokedex(id));
    }

    /** Al salir un jugador se olvidan sus tiempos. */
    public static void olvidar(ServerPlayerEntity jugador) {
        if (jugador != null) {
            FRENO.olvidar(jugador.getUuidAsString());
        }
    }
}
