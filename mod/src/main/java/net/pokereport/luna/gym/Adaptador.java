package net.pokereport.luna.gym;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.gitlab.srcmc.rctapi.api.battle.BattleRules;
import com.gitlab.srcmc.rctapi.api.models.BagItemModel;
import com.gitlab.srcmc.rctapi.api.models.PokemonModel;
import com.gitlab.srcmc.rctapi.api.models.TrainerModel;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;
import com.gitlab.srcmc.rctapi.api.util.JTO;
import com.gitlab.srcmc.rctmod.ModCommon;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.gym.Gimnasio.Gimnasio_;

/**
 * EL COMBATE DE GIMNASIO: MISMO NIVEL, MISMA CANTIDAD, Y EL LÍDER SE PREPARA.
 *
 * <p>Diseño completo y auditoría en {@code docs/pokemon/combate-gimnasios.md}.
 *
 * <h2>Qué hace, en orden</h2>
 *
 * <ol>
 *   <li>mira el equipo del jugador: cuántos van sanos y de qué tipos son;</li>
 *   <li>puntúa el repertorio del líder contra eso y se queda con los mejores,
 *       <b>tantos como traiga el jugador</b>;</li>
 *   <li>a cada uno le elige los cuatro ataques que mejor cubren lo que viene,
 *       y le pone los EVs donde hagan falta;</li>
 *   <li>lo registra como un entrenador <b>propio de este combate</b> y empieza
 *       la pelea, con los dos lados al nivel del gimnasio.</li>
 * </ol>
 *
 * <h2>⚠⚠⚠ NO SE TOCA EL POKÉMON DEL JUGADOR. Comprobado antes de escribirlo</h2>
 *
 * La igualdad de nivel la hace {@code BattleFormat.setAdjustLevel}, y rctapi lo
 * aplica llamando a {@code setLevel} sobre {@code getEffectedPokemon()}. Leído
 * del bytecode: cuando {@code adjustLevel > 0}, {@code toBattlePokemons} hace
 * {@code original.clone(true, null)}, le marca {@code BattleCloneProperty} y
 * {@code UncatchableProperty}, y construye
 * {@code new BattlePokemon(original, clon, …)}. <b>El que cambia de nivel es el
 * clon</b>, y el clon se tira al acabar.
 *
 * <p>Si esto fuera falso, el gimnasio le bajaría el nivel a la gente <b>de
 * verdad</b> y no habría vuelta atrás. Por eso se comprobó y no se supuso.
 *
 * <h2>⚠⚠⚠ UN ENTRENADOR POR COMBATE, Y HAY QUE BORRARLO</h2>
 *
 * El {@code TrainerNPC} del registro es <b>uno por identificador</b>. Si dos
 * jugadores retaran a Brock a la vez y compartieran el suyo, el segundo le
 * cambiaría el equipo al primero <b>en mitad del combate</b> — y eso no daría
 * ningún error: daría un combate en el que salen Pokémon que no estaban.
 *
 * <p>Por eso el identificador lleva el UUID del jugador. Y por eso
 * {@link #soltar} tiene que llamarse <b>en los tres caminos</b>: ganar, perder y
 * desconectarse. Sin el tercero el registro crece para siempre, que es la misma
 * lista de tres que ya tienen las ranuras.
 */
public final class Adaptador {

    private Adaptador() {}

    /** El prefijo de los entrenadores que fabricamos. Nunca choca con rctmod. */
    private static final String PREFIJO = "luna_gym_";

    /**
     * Qué identificador de registro tiene abierto cada jugador.
     *
     * <p>⚠ En memoria y no en la base, igual que las ranuras: al reiniciar no
     * hay ningún combate vivo, así que guardarlo solo serviría para resucitar
     * entrenadores fantasma.
     */
    private static final Map<UUID, String> ABIERTOS = new ConcurrentHashMap<>();

    /** Cuántos Pokémon puede llevar cada lado como mucho. */
    private static final int TOPE_EQUIPO = 6;

    /** Cuántos ataques lleva cada uno. */
    private static final int ATAQUES = 4;

    // ---- la puerta -------------------------------------------------------

    /**
     * Empieza el combate adaptado. Devuelve {@code false} si no se pudo, y
     * entonces quien llama debe caer al camino de siempre.
     *
     * <p>⚠ Va <b>en el hilo del servidor</b> y no consulta la base: el equipo
     * del jugador vive en memoria ({@code PlayerPartyStore}) y el repertorio es
     * una constante.
     */
    public static boolean pelear(ServerPlayerEntity jugador, TrainerMob mob,
                                 Gimnasio_ g) {
        List<Repertorio.Ficha> pool = Repertorio.de(g.id());
        if (pool.isEmpty()) {
            // El líder todavía no tiene repertorio: que lo lleve el datapack.
            return false;
        }
        try {
            var rival = leerEquipo(jugador);
            int cuantos = Math.max(1, Math.min(TOPE_EQUIPO, rival.size()));
            var equipo = componer(pool, rival, cuantos, g.nivel());

            var registro = ModCommon.RCT.getTrainerRegistry();
            String id = PREFIJO + g.id() + "_" + jugador.getUuid();

            // ⚠ Se borra antes de crear: si un combate anterior no llegó a
            //   soltarse --una desconexión mal cerrada--, registrar encima
            //   dejaría el viejo colgado sin que nada lo dijera.
            registro.unregisterById(id);

            var modelo = new TrainerModel(
                    com.gitlab.srcmc.rctapi.api.util.Text.literal(g.lider()),
                    JTO.of(() -> new StrongBattleAI(g.pericia())),
                    bolsaDe(g), equipo);

            TrainerNPC npc = registro.registerNPC(id, modelo);
            npc.setEntity(mob);

            var jugadorTrainer = registro.getById(
                    RCTMod.getInstance().getTrainerManager().getTrainerId(jugador),
                    TrainerPlayer.class);
            if (jugadorTrainer == null) {
                LunaEternal.LOG.warn("El jugador {} no está registrado en rctmod",
                        jugador.getGameProfile().getName());
                registro.unregisterById(id);
                return false;
            }

            UUID combate = ModCommon.RCT.getBattleManager().startBattle(
                    List.of(jugadorTrainer), List.of(npc),
                    formatoDe(g), reglasDe(g));

            if (combate == null) {
                registro.unregisterById(id);
                return false;
            }
            ABIERTOS.put(jugador.getUuid(), id);
            LunaEternal.LOG.info(
                    "Gimnasio {}: {} pelea a nivel {} con {} contra {} (pericia {})",
                    g.id(), jugador.getGameProfile().getName(), g.nivel(),
                    rival.size(), resumen(equipo), g.pericia());
            return true;
        } catch (Exception e) {
            // ⚠⚠ SE CAE AL CAMINO DE SIEMPRE EN VEZ DE DEJAR AL JUGADOR SIN
            //    COMBATE. Ya está dentro de la arena y de esa dimensión no se
            //    sale andando: un reto que no empieza lo deja encerrado.
            LunaEternal.LOG.error("No se pudo componer el equipo de {}", g.id(), e);
            soltar(jugador.getUuid());
            return false;
        }
    }

    /**
     * Borra el entrenador de este jugador del registro.
     *
     * <p>⚠⚠ HAY QUE LLAMARLO EN LOS TRES CAMINOS: ganar, perder y desconectarse.
     * El tercero es el que se olvida, y es el que convierte esto en una fuga que
     * crece con cada reto.
     */
    public static void soltar(UUID jugador) {
        String id = ABIERTOS.remove(jugador);
        if (id != null) {
            try {
                ModCommon.RCT.getTrainerRegistry().unregisterById(id);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo soltar el entrenador {}", id, e);
            }
        }
    }

    /** Cuántos entrenadores fabricados hay vivos. Para el comando y el autotest. */
    public static int abiertos() {
        return ABIERTOS.size();
    }

    // ---- el formato y las reglas ----------------------------------------

    /**
     * EL FORMATO, con la igualdad de nivel dentro.
     *
     * <p>⚠⚠ Se construye uno NUEVO en cada combate y no se reutiliza el del
     * datapack. {@code setAdjustLevel} <b>muta</b> el objeto, así que tocar el
     * que rctmod tiene cacheado le cambiaría el nivel a todos los combates del
     * servidor — incluidos los de entrenadores que no son de gimnasio.
     */
    private static com.gitlab.srcmc.rctapi.api.battle.BattleFormatProvider
            formatoDe(Gimnasio_ g) {
        // ⚠⚠⚠ SE COPIA, NO SE MUTA. `BattleFormat.GEN_9_SINGLES` de rctapi es
        //    un ENUM, así que su formato de Cobblemon es UNA instancia
        //    compartida por todo el servidor. Llamar a `setAdjustLevel` sobre
        //    ella le pondría el nivel del gimnasio a TODOS los combates --los
        //    salvajes, los duelos, los entrenadores del mundo-- y no daría
        //    ningún error: daría un servidor entero peleando a nivel 15.
        var base = com.gitlab.srcmc.rctapi.api.battle.BattleFormat
                .GEN_9_SINGLES.getCobblemonBattleFormat();
        // ⚠ El quinto parámetro ES `adjustLevel`, así que no hace falta el
        //   setter: la copia nace ya con el nivel puesto y nadie puede
        //   cambiárselo a medias.
        var formato = new BattleFormat(base.getMod(), base.getBattleType(),
                new java.util.HashSet<>(base.getRuleSet()), base.getGen(),
                g.nivel());
        // `BattleFormatProvider` tiene un solo método: vale una lambda.
        return () -> formato;
    }

    /**
     * ⚠ Los dos ajustes de nivel encendidos: {@code adjustLevel} solo se aplica
     * al lado cuyo interruptor esté puesto. Con uno solo, el gimnasio bajaría a
     * un lado y dejaría al otro como estaba — que es peor que no igualar nada.
     *
     * <p>⚠ Y {@code healPlayers}: se entra a la arena con el equipo curado. Si
     * no, el reto lo decidiría lo que quedara del camino y no el combate.
     */
    private static BattleRules reglasDe(Gimnasio_ g) {
        return new BattleRules.Builder()
                .withAdjustPlayerLevels(true)
                .withAdjustNPCLevels(true)
                .withHealPlayers(true)
                .withMaxItemUses(Repertorio.bolsa(g.id()).size())
                .build();
    }

    private static List<BagItemModel> bolsaDe(Gimnasio_ g) {
        var salida = new ArrayList<BagItemModel>();
        for (String item : Repertorio.bolsa(g.id())) {
            salida.add(new BagItemModel(item, 1));
        }
        return salida;
    }

    // ---- leer al rival ---------------------------------------------------

    /** Un Pokémon del jugador reducido a lo que importa para elegir. */
    private record Enemigo(String tipo1, String tipo2, boolean fisico) {}

    /**
     * El equipo del jugador, <b>solo los que pueden pelear</b>.
     *
     * <p>⚠⚠ Los debilitados no cuentan, y eso decide la cantidad. Si contaran,
     * alguien entraría con cinco KO y un titular y el líder sacaría seis: la
     * paridad diría seis contra seis y el combate sería seis contra uno.
     */
    private static List<Enemigo> leerEquipo(ServerPlayerEntity jugador) {
        var salida = new ArrayList<Enemigo>();
        var party = com.cobblemon.mod.common.Cobblemon.INSTANCE
                .getStorage().getParty(jugador);
        for (Pokemon p : party) {
            if (p == null || p.isFainted()) {
                continue;
            }
            Species s = p.getSpecies();
            var t = Tipos.deEspecie(s);
            salida.add(new Enemigo(t[0], t[1], Tipos.pegaFisico(s)));
        }
        return salida;
    }

    // ---- componer el equipo del líder ------------------------------------

    /**
     * ELIGE Y PREPARA. Es el corazón del sistema.
     *
     * <p>Puntúa cada candidato del repertorio contra el equipo del jugador —
     * cuánto le pega y cuánto le pegan— y se queda con los mejores.
     *
     * <p>⚠⚠ El Pokémon de firma lleva una ventaja fija ({@link #VENTAJA_FIRMA}),
     * y no es un capricho: sin ella, un jugador con el equipo adecuado se
     * encontraría un gimnasio de Brock <b>sin Onix</b>. Un líder tiene que
     * traer lo suyo aunque no sea lo óptimo — es lo que le hace ser él.
     */
    private static List<PokemonModel> componer(List<Repertorio.Ficha> pool,
                                               List<Enemigo> rival,
                                               int cuantos, int nivel) {
        var ordenado = new ArrayList<>(pool);
        ordenado.sort(Comparator.comparingDouble(
                (Repertorio.Ficha c) -> -puntuar(c, rival)));

        boolean rivalFisico = mayoriaFisica(rival);
        var salida = new ArrayList<PokemonModel>();
        for (int i = 0; i < cuantos && i < ordenado.size(); i++) {
            salida.add(construir(ordenado.get(i), rival, nivel, rivalFisico));
        }
        return salida;
    }

    /** Cuánto suma ser el Pokémon del líder. */
    private static final double VENTAJA_FIRMA = 1.5;

    /**
     * La nota de un candidato: lo que reparte menos lo que recibe.
     *
     * <p>⚠ Se usan <b>los tipos</b> del rival como aproximación de sus ataques,
     * que es lo que se puede saber sin abrir su equipo. Un Pokémon con ataques
     * fuera de su tipo escapa a esto, y está bien: la idea es que el líder venga
     * preparado, no que sepa tus movimientos.
     */
    private static double puntuar(Repertorio.Ficha c, List<Enemigo> rival) {
        Species s = especie(c.especie());
        if (s == null) {
            // ⚠ Una especie que no existe se hunde en el orden en vez de
            //   reventar aquí: el autotest ya lo dice en el arranque, y en
            //   combate lo que hace falta es que salga alguien.
            return Double.NEGATIVE_INFINITY;
        }
        var mios = Tipos.deEspecie(s);
        double nota = c.firma() ? VENTAJA_FIRMA : 0.0;
        if (rival.isEmpty()) {
            return nota;
        }
        double ataque = 0, defensa = 0;
        for (Enemigo e : rival) {
            // lo que mis tipos le hacen: me quedo con mi mejor golpe
            double mejor = 0;
            for (String mio : mios) {
                if (mio != null) {
                    mejor = Math.max(mejor, Tipos.contra(mio, e.tipo1(), e.tipo2()));
                }
            }
            ataque += mejor;
            // lo que sus tipos me hacen: me quedo con su peor golpe para mí
            double peor = 0;
            for (String suyo : new String[] {e.tipo1(), e.tipo2()}) {
                if (suyo != null) {
                    peor = Math.max(peor, Tipos.contra(suyo, mios[0], mios[1]));
                }
            }
            defensa += peor;
        }
        int n = rival.size();
        return nota + (ataque / n) - (defensa / n);
    }

    /** ¿El equipo del jugador pega sobre todo de físico? */
    private static boolean mayoriaFisica(List<Enemigo> rival) {
        int f = 0;
        for (Enemigo e : rival) {
            if (e.fisico()) {
                f++;
            }
        }
        return f * 2 >= rival.size();
    }

    /**
     * Un Pokémon del líder, ya preparado.
     *
     * <p>⚠⚠ LOS EVs SON LA ADAPTACIÓN MÁS SILENCIOSA Y LA MÁS EFICAZ: si el
     * jugador viene con ataques físicos, los 252 puntos van a Defensa; si viene
     * con especiales, a Defensa Especial. No cambia qué Pokémon es, cambia
     * cuántos turnos aguanta.
     *
     * <p>⚠ 252 + 252 + 4 y no más: es el reparto máximo legal, y ponerlo más
     * alto no lo hace más fuerte — <b>Showdown lo recorta</b> y el resultado
     * sería un Pokémon peor de lo que dice la ficha.
     */
    private static PokemonModel construir(Repertorio.Ficha c, List<Enemigo> rival,
                                          int nivel, boolean rivalFisico) {
        var ivs = new PokemonModel.StatsModel(31, 31, 31, 31, 31, 31);
        var evs = rivalFisico
                ? new PokemonModel.StatsModel(252, 0, 252, 0, 4, 0)
                : new PokemonModel.StatsModel(252, 0, 4, 0, 252, 0);
        return new PokemonModel(
                c.especie(), null, nivel, c.naturaleza(), c.habilidad(),
                new java.util.LinkedHashSet<>(elegirAtaques(c, rival)),
                ivs, evs, false, c.objeto(), java.util.Set.of());
    }

    /**
     * LOS CUATRO ATAQUES, elegidos contra lo que trae el jugador.
     *
     * <p>Cada ficha declara de cuatro a seis. Se ordenan por lo que le hacen al
     * equipo que hay enfrente y se cogen los cuatro primeros.
     *
     * <p>⚠⚠ EL PRIMERO DE LA FICHA SE QUEDA SIEMPRE, sea cual sea su nota: es el
     * ataque de tipo del Pokémon, y un líder de roca sin un ataque de roca no es
     * un líder de roca. Sin esta línea, contra un equipo de tierra Brock sacaría
     * un Onix con cuatro movimientos de estado.
     *
     * <p>⚠ Un movimiento cuyo tipo no conocemos —los de estado, y los que
     * añadiría otro mod— puntúa {@link #NOTA_UTILIDAD}: ni el mejor ni el
     * último. Con nota cero, {@code protect} y {@code block} no entrarían jamás
     * y el líder perdería justo lo que le hace pesado.
     */
    private static List<String> elegirAtaques(Repertorio.Ficha c, List<Enemigo> rival) {
        var restantes = new ArrayList<>(c.ataques());
        var salida = new ArrayList<String>(ATAQUES);
        salida.add(restantes.remove(0));

        restantes.sort(Comparator.comparingDouble(m -> -notaAtaque(m, rival)));
        for (String m : restantes) {
            if (salida.size() >= ATAQUES) {
                break;
            }
            salida.add(m);
        }
        return salida;
    }

    /** Lo que vale un movimiento que no sabemos de qué tipo es. */
    private static final double NOTA_UTILIDAD = 1.0;

    private static double notaAtaque(String movimiento, List<Enemigo> rival) {
        String tipo = TIPO_DE_ATAQUE.get(movimiento);
        if (tipo == null || rival.isEmpty()) {
            return NOTA_UTILIDAD;
        }
        double suma = 0;
        for (Enemigo e : rival) {
            suma += Tipos.contra(tipo, e.tipo1(), e.tipo2());
        }
        return suma / rival.size();
    }

    /**
     * De qué tipo es cada ataque del repertorio.
     *
     * <h2>⚠⚠ POR QUÉ ES UNA TABLA Y NO SE LE PREGUNTA A COBBLEMON</h2>
     *
     * {@code Moves.getByName} existe, y <b>devuelve null antes de que Showdown
     * haya cargado</b> — que es justo cuando corre el autotest. Una tabla se
     * puede comprobar en el arranque; una consulta que a veces contesta null
     * daría un gimnasio que elige bien o mal según cuándo se abra.
     *
     * <p>⚠ Solo hacen falta los del repertorio. Un movimiento que falte aquí no
     * rompe nada: cuenta como utilidad, que es la respuesta prudente.
     */
    private static final Map<String, String> TIPO_DE_ATAQUE = Map.ofEntries(
        Map.entry("rockthrow", "rock"), Map.entry("rocktomb", "rock"),
        Map.entry("rockslide", "rock"), Map.entry("rockblast", "rock"),
        Map.entry("ancientpower", "rock"), Map.entry("rollout", "rock"),
        Map.entry("smackdown", "rock"),
        Map.entry("bulldoze", "ground"), Map.entry("mudshot", "ground"),
        Map.entry("mudslap", "ground"),
        Map.entry("bind", "normal"), Map.entry("headbutt", "normal"),
        Map.entry("takedown", "normal"), Map.entry("hornattack", "normal"),
        Map.entry("flail", "normal"), Map.entry("chipaway", "normal"),
        Map.entry("dragonbreath", "dragon"),
        Map.entry("rocksmash", "fighting"), Map.entry("hammerarm", "fighting"),
        Map.entry("lowkick", "fighting"),
        Map.entry("watergun", "water"), Map.entry("aquajet", "water"),
        Map.entry("absorb", "grass"),
        Map.entry("bite", "dark"), Map.entry("pursuit", "dark"),
        Map.entry("payback", "dark"), Map.entry("assurance", "dark"),
        Map.entry("icefang", "ice"), Map.entry("thunderfang", "electric"),
        Map.entry("metalclaw", "steel"),
        Map.entry("strugglebug", "bug"));

    /** La especie, o {@code null} si el identificador no existe. */
    public static Species especie(String id) {
        return PokemonSpecies.INSTANCE.getByName(id);
    }

    /**
     * QUÉ SACARÍA EL LÍDER contra el equipo de ese jugador, sin pelear.
     *
     * <h2>⚠⚠ ESTO NO ES UN LUJO: ES LA ÚNICA FORMA DE VER LA ADAPTACIÓN</h2>
     *
     * Elegir mal <b>no da ningún error</b>. El combate empieza, salen cuatro
     * Pokémon de roca, y que sean los cuatro equivocados solo se nota jugando
     * mucho y con suerte. Con esto se ve la decisión y el porqué: la nota de
     * cada candidato y qué se lleva puesto el que sale.
     *
     * <p>Es el mismo papel que {@code /luna gimnasio brock posiciones}, que se
     * escribió para otra cosa y acabó cazando el fallo de la separación de
     * ranuras.
     */
    public static List<String> ensayo(ServerPlayerEntity jugador, Gimnasio_ g) {
        var salida = new ArrayList<String>();
        var pool = Repertorio.de(g.id());
        if (pool.isEmpty()) {
            salida.add("§7" + g.lider()
                    + " todavía no tiene repertorio: pelea con el del datapack");
            return salida;
        }
        var rival = leerEquipo(jugador);
        int cuantos = Math.max(1, Math.min(TOPE_EQUIPO, rival.size()));
        salida.add("§6" + g.lider() + " §7· nivel §f"
                + g.nivel() + " §7· pericia §f" + g.pericia()
                + "§7 · tu equipo: §f" + rival.size()
                + (rival.isEmpty() ? " §c(sin Pokémon sanos)" : ""));

        var ordenado = new ArrayList<>(pool);
        ordenado.sort(Comparator.comparingDouble(
                (Repertorio.Ficha c) -> -puntuar(c, rival)));
        boolean fisico = mayoriaFisica(rival);
        salida.add("§7tus ataques son sobre todo §f"
                + (fisico ? "físicos" : "especiales")
                + "§7, así que sus EVs van a §f"
                + (fisico ? "Defensa" : "Def. Especial"));

        for (int i = 0; i < ordenado.size(); i++) {
            var c = ordenado.get(i);
            boolean sale = i < cuantos;
            salida.add((sale ? "§a  SALE  " : "§8  banca ")
                    + "§f" + c.especie()
                    + "§7 nota §f"
                    + String.format(java.util.Locale.ROOT, "%.2f", puntuar(c, rival))
                    + (sale ? "§7 · " + String.join(", ",
                              elegirAtaques(c, rival)) : ""));
        }
        return salida;
    }

    private static String resumen(List<PokemonModel> equipo) {
        var sb = new StringBuilder();
        for (PokemonModel p : equipo) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(p.getSpecies());
        }
        return sb.toString();
    }
}
