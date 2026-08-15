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
 * la zona ni sonido en el mundo.
 *
 * <p>No toca la base de datos, así que puede resolverse entero en el hilo del
 * servidor: es una consulta a un conjunto en memoria y un paquete.
 */
public final class ScanListener {

    private ScanListener() {}

    public static void register() {
        try {
            CobblemonEvents.POKEMON_SCANNED.subscribe(event -> {
                try {
                    // El nombre de la especie sale de la ficha del Pokémon
                    // escaneado, no de la entidad: un Ditto disfrazado se
                    // escanea como lo que ES, y esa es la gracia del escáner.
                    String especie = event.getScannedPokemonEntityData()
                            .getPokemon().getSpecies().getName();
                    hablar(event.getPlayer(), especie);
                } catch (Throwable t) {
                    // Un escaneo que no suena es un fastidio; un escaneo que
                    // revienta le rompe la Pokédex al jugador. Se traga.
                    LunaEternal.LOG.error("Error al dar voz a un escaneo", t);
                }
            });
            LunaEternal.LOG.info("Pokédex: {} voces listas", VozService.cuantas());
        } catch (Throwable t) {
            // Si Cobblemon cambia el evento, el resto del mod sigue vivo.
            LunaEternal.LOG.warn("No se pudo escuchar los escaneos: {}", t.toString());
        }
    }

    /** Manda la voz, si esa especie tiene. */
    static void hablar(ServerPlayerEntity jugador, String especie) {
        if (jugador == null || !VozService.tieneVoz(especie)) {
            return;       // sin voz grabada: el escaneo funciona igual, mudo
        }
        ServerPlayNetworking.send(jugador,
                new Red.VozPokedex(VozService.normalizar(especie)));
    }
}
