package net.pokereport.luna.starter;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.progression.Path;

import java.util.List;

/**
 * El Pokémon inicial: catálogo y entrega.
 *
 * <p>Es <b>lo que desbloquea el juego</b>. Sin un Pokémon, nada de lo
 * construido —economía, mercado, Pokédex, progresión— se puede usar, y capturar
 * el primero sin nada con lo que debilitar es cuestión de suerte.
 *
 * <p>Solo Kanto y Johto (D-017).
 *
 * <p><b>Por qué esto es un servicio y no una pantalla:</b> vivía dentro de un
 * menú de cofre, mezclado con iconos y huecos de rejilla. Al retirar los menús
 * (D-026) la lógica se habría ido con ellos. Aquí queda el <i>qué</i> —qué
 * iniciales hay, qué pasa al elegir uno— y el <i>cómo se ve</i> lo decidirá la
 * interfaz de cliente cuando exista. El catálogo lleva tipo y consejo a
 * propósito: son datos que la pantalla va a necesitar y que no deben acabar
 * escritos otra vez dentro de ella.
 */
public final class StarterService {

    /** Clave de la reclamación única. Es lo que impide elegir dos veces. */
    public static final String CLAVE = "__inicial";

    private static final int NIVEL = 5;

    /** Un inicial: qué es y por qué elegirlo. */
    public record Inicial(String especie, String nombre, String region,
                          String tipo, String consejo) {}

    public static final List<Inicial> KANTO = List.of(
        new Inicial("bulbasaur", "Bulbasaur", "Kanto", "Planta / Veneno",
            "El más cómodo para empezar: aguanta bien los primeros combates."),
        new Inicial("charmander", "Charmander", "Kanto", "Fuego",
            "El más exigente al principio y el más fuerte después."),
        new Inicial("squirtle", "Squirtle", "Kanto", "Agua",
            "El más equilibrado: encaja golpes y responde."));

    public static final List<Inicial> JOHTO = List.of(
        new Inicial("chikorita", "Chikorita", "Johto", "Planta",
            "Defensivo. Premia la paciencia."),
        new Inicial("cyndaquil", "Cyndaquil", "Johto", "Fuego",
            "Rápido y agresivo."),
        new Inicial("totodile", "Totodile", "Johto", "Agua",
            "El que más daño hace de los tres."));

    private StarterService() {}

    /** Busca un inicial por su especie. {@code null} si no está en el catálogo. */
    public static Inicial porEspecie(String especie) {
        for (Inicial i : KANTO) if (i.especie().equals(especie)) return i;
        for (Inicial i : JOHTO) if (i.especie().equals(especie)) return i;
        return null;
    }

    /** ¿Ya eligió? Consulta a la base: llámalo desde el hilo de E/S. */
    public static boolean yaEligio(long playerId) throws Exception {
        return LunaEternal.kitService().hasClaimed(playerId, CLAVE);
    }

    /**
     * Entrega el inicial.
     *
     * <p><b>Se marca primero y se entrega después.</b> Lo malo aquí es entregar
     * dos (un exploit permanente), no dejar de entregar uno (recuperable). Si la
     * entrega falla, se deshace la marca.
     *
     * <p>Se puede llamar desde el hilo del servidor: el trabajo de base de datos
     * se manda al de E/S y la parte que toca el mundo vuelve al del servidor.
     */
    public static void conceder(ServerPlayerEntity player, long playerId, String especie) {
        conceder(player, playerId, especie, null);
    }

    /**
     * Igual, pero avisando al terminar.
     *
     * <p>⚠⚠ EL AVISO NO ES UN EXTRA: ES LO QUE FALTABA Y DEJABA COLGADA LA
     * PANTALLA. {@code conceder} <b>encola trabajo y vuelve al instante</b> —la
     * marca y la entrega ocurren después, en otros hilos—, así que quien la
     * llamaba y preguntaba a continuación «¿ya eligió?» leía el estado ANTERIOR.
     * La pantalla del inicial se quedaba en «ENTREGANDO…» para siempre, y como no
     * se puede cerrar sin elegir, <b>el jugador se quedaba atrapado</b>.
     *
     * <p>Que un método asíncrono parezca síncrono es la trampa: no da error, no
     * tarda, y devuelve. Por eso ahora avisa, y avisa <b>en todos los caminos</b>
     * —entregado, ya elegido antes, o fallo— porque quien espera necesita saberlo
     * en los tres.
     *
     * @param alTerminar se ejecuta en el hilo de E/S cuando ya se sabe el
     *                   resultado. Puede ser {@code null}
     */
    public static void conceder(ServerPlayerEntity player, long playerId, String especie,
                                Runnable alTerminar) {
        Inicial op = porEspecie(especie);
        if (op == null) {
            LunaEternal.LOG.warn("{} pidió un inicial que no existe: {}",
                player.getGameProfile().getName(), especie);
            avisar(alTerminar);
            return;
        }
        var server = player.getServer();
        if (server == null) {
            avisar(alTerminar);
            return;
        }

        LunaEternal.submit(() -> {
            try {
                if (!LunaEternal.kitService().claimOnce(playerId, CLAVE)) {
                    server.execute(() -> player.sendMessage(Text.literal(
                        "§7Ya elegiste tu primer compañero."), false));
                    avisar(alTerminar);
                    return;
                }

                server.execute(() -> {
                    boolean entregado = false;
                    try {
                        var props = PokemonProperties.Companion.parse(
                            op.especie() + " level=" + NIVEL);
                        entregado = Cobblemon.INSTANCE.getStorage()
                            .getParty(player).add(props.create());
                    } catch (Throwable t) {
                        LunaEternal.LOG.error("No se pudo crear el inicial {}",
                            op.especie(), t);
                    }

                    if (!entregado) {
                        deshacer(playerId);
                        player.sendMessage(Text.literal(
                            "§cNo se pudo entregar. Inténtalo otra vez."), false);
                        // ⚠ TAMBIEN se avisa al fallar, y va DESPUES de `deshacer`
                        //   para que quien pregunte lea la marca ya retirada. Si
                        //   no, la pantalla se cerraria creyendo que hubo suerte.
                        avisar(alTerminar);
                        return;
                    }

                    player.sendMessage(Text.literal(
                        "§8[§6Luna Eternal§8] §f" + op.nombre()
                        + " §7se une a tu equipo."), false);

                    // Elegir compañero es el primer paso de la vía Entrenador.
                    LunaEternal.submit(() -> {
                        try {
                            LunaEternal.progression().grant(playerId, Path.ENTRENADOR, 25);
                            LunaEternal.quests().advance(playerId,
                                net.pokereport.luna.quest.Quest.Objective.Type.STARTER, 1);
                        } catch (Exception e) {
                            LunaEternal.LOG.error("No se pudo dar XP del inicial", e);
                        }
                        // El aviso va aqui, al final del todo: para entonces la
                        // marca esta puesta y el Pokemon entregado, asi que quien
                        // pregunte lee la verdad.
                        avisar(alTerminar);
                    });
                });

            } catch (Exception e) {
                LunaEternal.LOG.error("Error entregando el inicial", e);
                deshacer(playerId);
                avisar(alTerminar);
            }
        });
    }

    /** Nunca deja que un fallo del que avisa se lleve por delante la entrega. */
    private static void avisar(Runnable alTerminar) {
        if (alTerminar == null) {
            return;
        }
        try {
            alTerminar.run();
        } catch (Throwable t) {
            LunaEternal.LOG.warn("Fallo avisando del inicial: {}", t.toString());
        }
    }

    private static void deshacer(long playerId) {
        LunaEternal.submit(() -> {
            try {
                LunaEternal.kitService().undoOnce(playerId, CLAVE);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo deshacer la marca del inicial", e);
            }
        });
    }
}
