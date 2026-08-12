package net.pokereport.luna.pokedex;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.progression.Path;

import java.util.UUID;

/**
 * Escucha las capturas de Cobblemon y las anota.
 *
 * <p>Es el primer punto donde el mod propio y Cobblemon se tocan de verdad, y
 * la razón por la que D-005 declaraba obligatorio un mod de servidor: un
 * datapack no puede enterarse de que alguien ha capturado algo.
 *
 * <p>El manejador se ejecuta en el hilo del servidor, así que <b>todo lo que
 * toque la base de datos se delega</b>. Escribir en MariaDB dentro del evento
 * congelaría el servidor en cada Poké Ball lanzada.
 */
public final class CaptureListener {

    /** Recompensa por capturar una especie nueva. Sin calibrar. */
    private static final long MARCAS_ESPECIE_NUEVA = 5;
    private static final long XP_CAPTURA = 10;
    private static final long XP_ESPECIE_NUEVA = 40;

    private CaptureListener() {}

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(event -> {
            try {
                handle(event.getPlayer(), event.getPokemon());
            } catch (Throwable t) {
                // Nunca dejar que un fallo nuestro rompa una captura: el
                // jugador ya tiene su Pokémon, y perderlo por un error de
                // contabilidad seria mucho peor que no anotarlo.
                LunaEternal.LOG.error("Error anotando una captura", t);
            }
        });
        // Crianza: cuenta al ECLOSIONAR, no al recoger el huevo. Recoger es
        // gratis y se podria repetir; nacer ocurre una vez.
        try {
            CobblemonEvents.HATCH_EGG_POST.subscribe(event -> {
                try {
                    eclosion(event.getPlayer(), event.getPokemon());
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando una eclosión", t);
                }
            });
            LunaEternal.LOG.info("Crianza: escuchando eclosiones");
        } catch (Throwable t) {
            LunaEternal.LOG.warn("No se pudo escuchar eclosiones: {}", t.toString());
        }

        LunaEternal.LOG.info("Pokédex: escuchando capturas de Cobblemon");
    }

    /**
     * Un huevo ha eclosionado: cuenta para la vía de Crianza (HUNT-001).
     *
     * <p>Se cuenta al NACER y no al recoger el huevo. Recoger es gratis y
     * repetible; nacer ocurre una sola vez por huevo, así que no se puede
     * inflar el contador.
     */
    private static void eclosion(ServerPlayerEntity player,
                                 com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        if (player == null || pokemon == null) return;
        String especie = pokemon.getSpecies().getName().toLowerCase();
        UUID uuid = player.getGameProfile().getId();
        String username = player.getGameProfile().getName();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, username);
                if (LunaEternal.hunts() != null) {
                    LunaEternal.hunts().avanzar(id, especie,
                        net.pokereport.luna.hunt.HuntService.Tipo.CRIANZA);
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Error anotando la eclosión de {}", especie, e);
            }
        });
    }

    /** Nivel más alto entre las cinco vías. */
    private static long maxNivelDeVia(long playerId) {
        try {
            var dom = LunaEternal.progression().dominant(playerId);
            return dom == null ? 0 : dom.level();
        } catch (Exception e) {
            return 0;
        }
    }

    private static void handle(ServerPlayerEntity player,
                               com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        if (player == null || pokemon == null) return;

        var species = pokemon.getSpecies();
        String name = species.getName();
        int dex = species.getNationalPokedexNumber();
        boolean shiny = pokemon.getShiny();
        int level = pokemon.getLevel();
        int moon = player.getWorld().getMoonPhase();

        UUID uuid = player.getGameProfile().getId();
        String username = player.getGameProfile().getName();
        var server = player.getServer();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, username);

                boolean nueva = LunaEternal.pokedex()
                    .recordCapture(id, name.toLowerCase(), dex, shiny, level, moon);

                // Cazas: capturar es lo UNICO que las avanza (HUNT-001).
                if (LunaEternal.hunts() != null) {
                    LunaEternal.hunts().avanzar(id, name.toLowerCase(),
                        net.pokereport.luna.hunt.HuntService.Tipo.CAPTURA);
                }

                // Progresión: capturar sube Coleccionista.
                LunaEternal.progression().grant(id, Path.COLECCIONISTA,
                    nueva ? XP_ESPECIE_NUEVA : XP_CAPTURA);

                if (nueva) {
                    // Las Marcas no se comercian, así que premiar aquí no
                    // infla nada (ECO-001 §2).
                    LunaEternal.economy().credit(id, Currency.MARK,
                        MARCAS_ESPECIE_NUEVA, "pokedex_nueva",
                        UUID.randomUUID().toString());
                }

                // Misiones, TODO en una sola llamada. Antes eran cuatro, y
                // cada una recorria el catalogo consultando la base por
                // mision: decenas de consultas por Poke Ball lanzada.
                var T = net.pokereport.luna.quest.Quest.Objective.Type.class;
                var sumas = new java.util.EnumMap<
                    net.pokereport.luna.quest.Quest.Objective.Type, Long>(T);
                sumas.put(net.pokereport.luna.quest.Quest.Objective.Type.CATCH, 1L);
                if (nueva) {
                    sumas.put(net.pokereport.luna.quest.Quest.Objective.Type.POKEDEX_NEW, 1L);
                }
                var absolutos = new java.util.EnumMap<
                    net.pokereport.luna.quest.Quest.Objective.Type, Long>(T);
                // La Pokedex es una FOTO del total, no un contador.
                absolutos.put(net.pokereport.luna.quest.Quest.Objective.Type.POKEDEX,
                    (long) LunaEternal.pokedex().summary(id).caught());
                absolutos.put(net.pokereport.luna.quest.Quest.Objective.Type.PATH_LEVEL,
                    maxNivelDeVia(id));

                LunaEternal.quests().record(id, sumas, absolutos);

                if (server != null) {
                    server.execute(() -> {
                        if (player.isRemoved()) return;
                        if (nueva) {
                            player.sendMessage(Text.literal(
                                "§8[§bPokédex§8] §f#" + dex + " " + name
                                + " §aregistrado por primera vez §8(+"
                                + MARCAS_ESPECIE_NUEVA + " Marcas)"), false);
                        }
                        if (shiny) {
                            player.sendMessage(Text.literal(
                                "§8[§bPokédex§8] §e✦ Variocolor registrado ✦"), false);
                        }
                        // El saldo y las vías han cambiado: refrescar la caché
                        // para que la barra lateral no muestre datos viejos.
                        net.pokereport.luna.ui.MenuService.refresh(player);
                    });
                }

            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo anotar la captura de {} por {}",
                    name, username, e);
            }
        });
    }
}
