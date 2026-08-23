package net.pokereport.luna.progression;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * De qué se saca XP en cada oficio.
 *
 * <h2>⚠ Todo sale de eventos del SERVIDOR</h2>
 *
 * Los oficios <b>crean Plata de la nada</b> —son una fuente, no una
 * transferencia— así que ningún camino hasta aquí puede depender de algo que
 * diga el cliente. Los cinco enganches son eventos de Minecraft o de Cobblemon,
 * disparados por el servidor cuando el hecho ya ha ocurrido.
 *
 * <h2>⚠ Nada de esto puede tirar la acción que lo dispara</h2>
 *
 * Todo va en {@code try/catch}, y es la misma regla que ya sigue
 * {@code CaptureListener}: el jugador ya ha picado su mena o ha pescado su
 * Pokémon, y perderlo por un fallo de contabilidad sería mucho peor que no
 * apuntarlo.
 *
 * <h2>Cocina</h2>
 *
 * <b>No está, y no es un olvido.</b> Cobblemon 1.7 trae olla de cocina
 * ({@code CookingPotMenu}, {@code CookingPotRecipe}) pero <b>no publica ningún
 * evento</b> para ella —se revisaron sus 98—, así que engancharla pide un mixin
 * dentro de su código y este mod no tiene mixins. Declarar el oficio sin su
 * enganche dejaría uno que nunca da XP: exactamente el fallo silencioso que este
 * proyecto lleva toda la semana pagando.
 */
public final class OficiosListener {

    private OficiosListener() {
    }

    // ---- cuánto vale cada cosa ---------------------------------------------
    //
    // ⚠ CIFRAS SIN CALIBRAR, como toda la economía. Lo único que se ha cuidado es
    //   la PROPORCIÓN entre ellas: lo que cuesta más esfuerzo da más. Un diamante
    //   no vale lo mismo que un carbón porque encontrarlo no cuesta lo mismo.
    //
    //   Los umbrales de nivel son 100 · 400 · 1.200 · 3.000 · 7.500, así que con
    //   1 XP por piedra hacen falta ~12.200 piedras para Minero V. Suena mucho y
    //   lo es a propósito: el nivel V paga 25.000 de Plata.
    private static final long XP_PIEDRA = 1;
    private static final long XP_MENA = 8;
    private static final long XP_MENA_RARA = 25;
    private static final long XP_PESCA = 12;
    private static final long XP_BAYA = 10;
    private static final long XP_BELLOTA = 10;
    private static final long XP_CULTIVO = 3;
    private static final long XP_ECLOSION = 40;

    public static void register() {
        minero();
        pescador();
        agricultor();
        criador();
        combatiente();
    }

    /**
     * Avanza el oficio Y la mision correspondiente, en el mismo sitio.
     *
     * <p>⚠ Van juntos a proposito. Si las misiones se avanzaran desde otro
     * listener, el dia que alguien cambie de que evento cuelga la pesca lo
     * cambiaria en uno solo, y el otro se quedaria mirando un evento que ya no
     * ocurre — sin dar ningun error, como siempre.
     *
     * <p>{@code oficio} puede ser {@code null}: hay cosas que cuentan para una
     * mision y no para ningun oficio, como ganar un combate.
     */
    private static void anotar(ServerPlayerEntity jugador, Path oficio,
                               long xp, net.pokereport.luna.quest.Quest.Objective.Type mision,
                               long cuantas) {
        if (jugador == null) {
            return;
        }
        if (oficio != null && xp > 0) {
            OficiosService.ganarAsync(jugador, oficio, xp);
        }
        if (mision != null && cuantas > 0) {
            var uuid = jugador.getUuid();
            var nombre = jugador.getName().getString();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players().resolve(uuid, nombre);
                    LunaEternal.quests().advance(id, mision, cuantas);
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudo avanzar la mision {}: {}",
                            mision, e.toString());
                }
            });
        }
    }

    /** Ganar combates. No da oficio --no es un trabajo-- pero si mision. */
    private static void combatiente() {
        try {
            CobblemonEvents.BATTLE_VICTORY.subscribe(evento -> {
                try {
                    // ⚠ UN `BattleActor` NO ES UN JUGADOR. Puede ser una IA, un
                    //   entrenador NPC o un jugador, y lo unico que da son los
                    //   UUID de los jugadores que hay detras --puede haber varios
                    //   en un combate doble--. Se resuelve cada uno contra el
                    //   servidor; los que no esten conectados se ignoran solos.
                    var servidor = evento.getBattle().getPlayers().isEmpty()
                            ? null : evento.getBattle().getPlayers().get(0).getServer();
                    if (servidor == null) {
                        return;
                    }
                    for (var ganador : evento.getWinners()) {
                        for (var uuid : ganador.getPlayerUUIDs()) {
                            var sp = servidor.getPlayerManager().getPlayer(uuid);
                            if (sp != null) {
                                anotar(sp, null, 0,
                                       net.pokereport.luna.quest.Quest.Objective.Type.BATTLE_WIN,
                                       1);
                            }
                        }
                    }
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando victoria", t);
                }
            });
        } catch (Throwable t) {
            LunaEternal.LOG.error("COMBATES: sin enganche de victoria", t);
        }
    }

    /**
     * MINERO: picar piedra y menas.
     *
     * <p>⚠ <b>Se usa el evento AFTER, no el BEFORE.</b> El «antes» se dispara
     * aunque el bloque no llegue a romperse —lo puede cancelar una protección,
     * o el jugador puede no tener herramienta— y pagaría por trabajo no hecho.
     *
     * <p>⚠ <b>Y se excluye el modo creativo.</b> Un constructor con Axiom rompe
     * miles de bloques en segundos: sin este filtro, la ciudadela sería la mina
     * más rentable del servidor. Es el caso raro que aquí NO es raro, porque
     * ahora mismo el trabajo del proyecto es construirla.
     */
    private static void minero() {
        PlayerBlockBreakEvents.AFTER.register((mundo, jugador, pos, estado, entidad) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)
                    || jugador.isCreative() || jugador.isSpectator()) {
                return;
            }
            try {
                // ⚠ UN BLOQUE PUEDE VALER PARA UN OFICIO O PARA OTRO, NUNCA PARA
                //   LOS DOS. Se comprueba el cultivo primero porque es el caso
                //   estrecho; la piedra es el ancho. Si algun dia se solaparan,
                //   picar daria XP de dos oficios a la vez y eso es una fuente de
                //   Plata al doble sin que nada lo delate.
                long cultivo = valorCultivo(estado);
                if (cultivo > 0) {
                    anotar(sp, Path.AGRICULTOR, cultivo,
                           net.pokereport.luna.quest.Quest.Objective.Type.HARVEST, 1);
                    return;
                }
                long xp = valorDe(estado.getBlock());
                if (xp > 0) {
                    anotar(sp, Path.MINERO, xp,
                           net.pokereport.luna.quest.Quest.Objective.Type.MINE, 1);
                }
            } catch (Throwable t) {
                LunaEternal.LOG.error("Error anotando mineria o cultivo", t);
            }
        });
    }

    /**
     * Cuánto vale romper ese bloque.
     *
     * <p>⚠ Se decide por <b>etiquetas</b> y no por una lista de bloques. Las
     * etiquetas las mantiene Minecraft: cuando salga un mineral nuevo, o cuando
     * un mod añada el suyo con la etiqueta correcta, entra solo. Una lista
     * escrita a mano se queda corta el día de la actualización y nadie lo nota,
     * porque el síntoma es «esto no da XP» y no un error.
     */
    private static long valorDe(Block bloque) {
        var estado = bloque.getDefaultState();
        if (estado.isIn(BlockTags.DIAMOND_ORES) || estado.isIn(BlockTags.EMERALD_ORES)) {
            return XP_MENA_RARA;
        }
        if (estado.isIn(BlockTags.GOLD_ORES) || estado.isIn(BlockTags.IRON_ORES)
                || estado.isIn(BlockTags.REDSTONE_ORES) || estado.isIn(BlockTags.LAPIS_ORES)
                || estado.isIn(BlockTags.COPPER_ORES) || estado.isIn(BlockTags.COAL_ORES)) {
            return XP_MENA;
        }
        // La piedra da poquísimo, pero da: cavar un túnel también es minar, y sin
        // esto el oficio solo avanzaría con suerte.
        if (estado.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || estado.isIn(BlockTags.BASE_STONE_NETHER)
                || estado.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return XP_PIEDRA;
        }
        return 0;
    }

    /**
     * Cuánto vale cosechar ese bloque. <b>0 si no es un cultivo, o si no está
     * maduro.</b>
     *
     * <p>⚠⚠ SOLO CUENTA SI ESTÁ MADURO, y sin eso el oficio sería un exploit
     * obvio: plantar y arrancar al segundo siguiente daría XP infinita sin
     * esperar a que creciera nada. Cosechar es esperar; eso es el oficio.
     *
     * <p>⚠ {@code CROPS} cubre trigo, zanahoria, patata, remolacha y las
     * verrugas del Nether —y lo que añada un mod que use la etiqueta—, pero
     * <b>no</b> melón, calabaza, caña ni cacao, que no son {@code CropBlock} y no
     * tienen edad. Esos se nombran aparte, y es la excepción a «usar etiquetas y
     * no listas»: aquí no hay etiqueta que los agrupe.
     *
     * <p>⚠ Y la caña <b>no cuenta</b>: crece sola, se corta sin replantar, y una
     * granja automática de caña convertiría el oficio en un temporizador. Melón y
     * calabaza sí, porque su tallo hay que plantarlo.
     */
    private static long valorCultivo(net.minecraft.block.BlockState estado) {
        var bloque = estado.getBlock();
        if (bloque instanceof net.minecraft.block.CropBlock cultivo) {
            return cultivo.isMature(estado) ? XP_CULTIVO : 0;
        }
        if (bloque == net.minecraft.block.Blocks.MELON
                || bloque == net.minecraft.block.Blocks.PUMPKIN) {
            return XP_CULTIVO;
        }
        if (bloque instanceof net.minecraft.block.CocoaBlock) {
            return estado.get(net.minecraft.block.CocoaBlock.AGE)
                    >= net.minecraft.block.CocoaBlock.MAX_AGE ? XP_CULTIVO : 0;
        }
        return 0;
    }

    /**
     * PESCADOR: recoger la caña Poké.
     *
     * <p>⚠ Se cuenta el <b>recogido</b> y no el lanzamiento. Lanzar es gratis y se
     * puede repetir sin parar; recoger exige que haya picado algo. Es la misma
     * decisión que ya se tomó con la cría —cuenta al eclosionar, no al recoger el
     * huevo— y por el mismo motivo.
     */
    private static void pescador() {
        try {
            CobblemonEvents.POKEROD_REEL.subscribe(evento -> {
                try {
                    if (evento.getPlayer() instanceof ServerPlayerEntity sp) {
                        anotar(sp, Path.PESCADOR, XP_PESCA,
                               net.pokereport.luna.quest.Quest.Objective.Type.FISH, 1);
                    }
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando pesca", t);
                }
            });
        } catch (Throwable t) {
            // Si Cobblemon cambia el evento, el oficio deja de avanzar pero el
            // servidor arranca. Se anota FUERTE porque es justo el fallo que no
            // se ve: nadie reporta "no subo de Pescador" hasta semanas después.
            LunaEternal.LOG.error("PESCADOR SIN ENGANCHE: el evento POKEROD_REEL "
                    + "no se pudo suscribir. El oficio no avanzara.", t);
        }
    }

    /** AGRICULTOR: bayas y bellotas, que son los dos cultivos de Cobblemon. */
    private static void agricultor() {
        try {
            CobblemonEvents.BERRY_HARVEST.subscribe(evento -> {
                try {
                    anotar(evento.getPlayer(), Path.AGRICULTOR, XP_BAYA,
                           net.pokereport.luna.quest.Quest.Objective.Type.HARVEST, 1);
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando cosecha de bayas", t);
                }
            });
        } catch (Throwable t) {
            LunaEternal.LOG.error("AGRICULTOR: sin enganche de bayas", t);
        }
        try {
            CobblemonEvents.APRICORN_HARVESTED.subscribe(evento -> {
                try {
                    anotar(evento.getPlayer(), Path.AGRICULTOR, XP_BELLOTA,
                           net.pokereport.luna.quest.Quest.Objective.Type.HARVEST, 1);
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando cosecha de bellotas", t);
                }
            });
        } catch (Throwable t) {
            LunaEternal.LOG.error("AGRICULTOR: sin enganche de bellotas", t);
        }
    }

    /**
     * CRIADOR: al ECLOSIONAR, no al recoger el huevo.
     *
     * <p>Ya estaba decidido así para la Vía de Criador y se mantiene: recoger un
     * huevo es gratis y repetible; que nazca ocurre una vez.
     */
    private static void criador() {
        try {
            CobblemonEvents.HATCH_EGG_POST.subscribe(evento -> {
                try {
                    anotar(evento.getPlayer(), Path.CRIADOR, XP_ECLOSION,
                           net.pokereport.luna.quest.Quest.Objective.Type.HATCH, 1);
                } catch (Throwable t) {
                    LunaEternal.LOG.error("Error anotando eclosion", t);
                }
            });
        } catch (Throwable t) {
            LunaEternal.LOG.error("CRIADOR: sin enganche de eclosion", t);
        }
    }
}
