package net.pokereport.luna.gym;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QUÉ PUEDE SACAR CADA LÍDER.
 *
 * <h2>⚠⚠⚠ EL LÍDER NO DEJA DE SER DE SU TIPO. ESTE ES EL LÍMITE</h2>
 *
 * El usuario pidió que el rival <i>«se adapte a los Pokémon del jugador para que
 * sea más difícil»</i>, y eso tiene un final malo si se lleva hasta el extremo:
 * un rival generado <b>solo</b> para contrarrestar deja de ser un gimnasio y
 * pasa a ser un espejo. Si traes Agua y Brock responde con Planta, Brock ha
 * dejado de ser Brock.
 *
 * <p><b>Lo único que hace memorable a un gimnasio es que sabes a qué vas.</b>
 *
 * <p>Así que se adapta <b>el nivel, la cantidad, cuál de los suyos sale, qué
 * movimientos lleva, qué habilidad y dónde van sus EVs</b>. La lista de especies
 * es de roca y se queda de roca. El autotest lo comprueba: si algún día alguien
 * mete un Pikachu en el repertorio de Brock, se pone rojo.
 *
 * <h2>⚠⚠ NADA DE ESTO SE ESCRIBIÓ DE MEMORIA</h2>
 *
 * Las once especies, sus habilidades y sus sesenta y seis movimientos se
 * validaron uno a uno contra {@code data/cobblemon/species/} del jar de
 * Cobblemon 1.7.3 antes de escribirlos aquí, y el autotest los vuelve a validar
 * contra el registro <b>en cada arranque</b>.
 *
 * <p>⚠ Importa porque el fallo es mudo: un movimiento que esa especie no puede
 * aprender <b>no da ningún error</b>. Sale un Pokémon con tres ataques en vez de
 * cuatro, y el jugador solo nota que el líder juega raro.
 *
 * <h2>⚠ Hay especies que el jugador NO puede conseguir, y es a propósito</h2>
 *
 * Bonsly y Cranidos son de cuarta generación, y este servidor solo tiene Kanto y
 * Johto. <b>Ya venían así en el datapack oficial</b>, y encaja: un líder de
 * gimnasio tiene Pokémon que tú no. Lo que está apagado son los <i>spawns</i> y
 * la Pokédex, no el registro de especies.
 */
public final class Repertorio {

    private Repertorio() {}

    /**
     * Un candidato del repertorio de un líder.
     *
     * @param especie    el identificador de Cobblemon, en minúsculas
     * @param habilidad  cuál de las suyas. <b>Tiene que ser de esa especie</b>
     * @param naturaleza sube una estadística y baja otra
     * @param objeto     lo que lleva en la mano, o {@code null}
     * @param ataques    de cuatro a seis. Se eligen cuatro según el rival
     * @param firma      ¿es SU Pokémon? El que sale casi siempre
     */
    public record Ficha(String especie, String habilidad, String naturaleza,
                        String objeto, List<String> ataques, boolean firma) {}

    private static Ficha f(String especie, String habilidad, String naturaleza,
                           String objeto, boolean firma, String... ataques) {
        return new Ficha(especie, habilidad, naturaleza, objeto,
                         Arrays.asList(ataques), firma);
    }

    /**
     * EL REPERTORIO DE BROCK: once Pokémon de roca.
     *
     * <p>Los cuatro primeros son <b>su equipo canónico</b>, leído del datapack
     * que está instalado en el servidor ({@code data/rctmod/trainers/}): Geodude
     * 16, Bonsly 16, Cranidos 18, Onix 20. Los siete siguientes son alternativas
     * del mismo tipo que le dan respuestas distintas.
     *
     * <p>⚠⚠ Once y no cuatro <b>porque hacen falta seis</b>: un jugador puede
     * traer seis Pokémon, y el líder saca los mismos. Con cuatro en la lista, un
     * equipo de seis dejaría a Brock con cuatro — la paridad se rompería sin dar
     * ningún error, y encima justo en el combate más difícil.
     *
     * <p>⚠ Cada uno está por un motivo, y el motivo es a qué responde:
     * <pre>
     *   onix       roca/tierra  SU POKÉMON. Muro físico, Rocky Helmet
     *   geodude    roca/tierra  el otro canónico. Sturdy y pega de físico
     *   bonsly     roca         estorba: Block + Flail con Focus Sash
     *   cranidos   roca         puro ataque. Mold Breaker se salta Sturdy ajeno
     *   rhyhorn    tierra/roca  LIGHTNING ROD: absorbe el eléctrico
     *   omanyte    roca/agua    responde al fuego, la tierra y la roca
     *   kabuto     roca/agua    lo mismo pero rápido y de físico
     *   sudowoodo  roca         Hammer Arm contra acero, hielo y normal
     *   shuckle    bicho/roca   muro absoluto. El que aparece si le pegas fuerte
     *   larvitar   roca/tierra  Payback y Bite contra psíquico y fantasma
     *   aron       acero/roca   resiste volador, hada, hielo y normal
     * </pre>
     */
    private static final List<Ficha> BROCK = List.of(
        f("onix",      "sturdy",      "impish",  "rocky_helmet", true,
          "rockthrow", "bulldoze", "bind", "dragonbreath", "rocktomb", "screech"),
        f("geodude",   "sturdy",      "adamant", null, true,
          "rockthrow", "bulldoze", "rocktomb", "protect", "rollout", "defensecurl"),
        f("bonsly",    "rockhead",    "careful", "focus_sash", false,
          "rockthrow", "block", "flail", "copycat", "faketears", "mudslap"),
        f("cranidos",  "moldbreaker", "adamant", null, false,
          "takedown", "rocktomb", "headbutt", "rocksmash", "pursuit", "assurance"),
        f("rhyhorn",   "lightningrod", "impish", null, false,
          "smackdown", "bulldoze", "hornattack", "rocktomb", "icefang", "thunderfang"),
        f("omanyte",   "shellarmor",  "modest",  "eviolite", false,
          "rockthrow", "watergun", "bite", "mudshot", "rockblast", "withdraw"),
        f("kabuto",    "battlearmor", "adamant", null, false,
          "aquajet", "rocktomb", "absorb", "sandattack", "mudshot", "ancientpower"),
        f("sudowoodo", "rockhead",    "adamant", "sitrus_berry", false,
          "rockthrow", "hammerarm", "flail", "block", "copycat", "lowkick"),
        f("shuckle",   "sturdy",      "bold",    "leftovers", false,
          "rockthrow", "bind", "encore", "rollout", "strugglebug", "mudslap"),
        f("larvitar",  "guts",        "adamant", null, false,
          "rockslide", "bite", "payback", "scaryface", "chipaway", "ancientpower"),
        f("aron",      "sturdy",      "impish",  null, false,
          "rocktomb", "metalclaw", "headbutt", "roar", "irondefense", "mudslap"));

    /**
     * CISSY — AGUA. La Estrella del Oeste, Isla Mikan.
     *
     * <p>⚠⚠ EN EL ANIME NO ES UN GIMNASIO DE TIPO: es una prueba de habilidad
     * —una carrera sobre el agua y un duelo de puntería— y eso <b>no se puede
     * jugar aquí</b>: lo que tenemos es un combate Pokémon. Así que a cada líder
     * naranja se le da el tipo que mejor describe su equipo del anime, y el de
     * Cissy es claro: Seadra y Blastoise.
     *
     * <p>⚠ Los dos de firma son justo esos, para que salga al menos uno de los
     * que el jugador reconoce de la serie.
     */
    private static final List<Ficha> CISSY = List.of(
        f("blastoise",  "torrent",     "modest",  "leftovers", true,
          "hydropump", "icebeam", "flashcannon", "rapidspin", "waterpulse", "bite"),
        f("seadra",     "poisonpoint", "timid",   "eviolite", true,
          "hydropump", "icebeam", "dragonpulse", "agility", "waterpulse", "twister"),
        f("starmie",    "illuminate",  "timid",   "life_orb", false,
          "surf", "icebeam", "psychic", "recover", "thunderbolt", "rapidspin"),
        f("lapras",     "waterabsorb", "modest",  "leftovers", false,
          "surf", "icebeam", "bodyslam", "sing", "thunderbolt", "raindance"),
        f("vaporeon",   "waterabsorb", "bold",    "leftovers", false,
          "surf", "icebeam", "aurorabeam", "quickattack", "shadowball", "helpinghand"),
        f("gyarados",   "intimidate",  "adamant", "life_orb", false,
          "waterfall", "crunch", "icefang", "dragondance", "bite", "bounce"),
        f("cloyster",   "shellarmor",  "adamant", "focus_sash", false,
          "iciclespear", "surf", "spikes", "irondefense", "razorshell", "toxicspikes"),
        f("poliwrath",  "waterabsorb", "adamant", "sitrus_berry", false,
          "waterfall", "brickbreak", "icepunch", "bulkup", "bodyslam", "mudshot"),
        f("kingdra",    "swiftswim",   "modest",  "leftovers", false,
          "hydropump", "dragonpulse", "icebeam", "agility", "waterpulse", "twister"));

    /**
     * DANNY — HIELO. La Estrella del Este, Isla Ombligo.
     *
     * <p>⚠⚠ Su prueba del anime es <b>congelar un géiser</b>, y su equipo de
     * allí (Nidoqueen, Machoke, Scyther) no dice ningún tipo. Se le da HIELO
     * porque es lo que hace, no lo que lleva: es la única lectura que produce un
     * gimnasio reconocible.
     *
     * <p>⚠ El hielo es un tipo <b>pobre</b> en Kanto y Johto — de ahí que entren
     * Mamoswine (4.ª gen) para llegar a ocho candidatos. Ya pasa con Bonsly y
     * Cranidos en el equipo canónico de Brock: el líder tiene Pokémon que tú no.
     */
    private static final List<Ficha> DANNY = List.of(
        f("cloyster",   "shellarmor",  "adamant", "focus_sash", true,
          "iciclespear", "surf", "spikes", "irondefense", "razorshell", "toxicspikes"),
        f("dewgong",    "thickfat",    "modest",  "leftovers", true,
          "icebeam", "surf", "aurorabeam", "rest", "bodyslam", "signalbeam"),
        f("lapras",     "waterabsorb", "modest",  "leftovers", false,
          "icebeam", "surf", "bodyslam", "sing", "thunderbolt", "raindance"),
        f("jynx",       "oblivious",   "timid",   "life_orb", false,
          "icebeam", "psychic", "lovelykiss", "shadowball", "icywind", "nastyplot"),
        f("piloswine",  "oblivious",   "adamant", "eviolite", false,
          "icefang", "earthquake", "blizzard", "takedown", "icywind", "mudshot"),
        f("sneasel",    "innerfocus",  "jolly",   "focus_sash", false,
          "icepunch", "throatchop", "feintattack", "agility", "icywind", "screech"),
        f("delibird",   "vitalspirit", "jolly",   "focus_sash", false,
          "icepunch", "present", "drillpeck", "icywind", "aerialace", "rapidspin"),
        f("mamoswine",  "oblivious",   "adamant", "life_orb", false,
          "icefang", "earthquake", "blizzard", "takedown", "icywind", "mudshot"));

    /**
     * RUDY — ELÉCTRICO. La Estrella del Sur, Isla Trovita.
     *
     * <p>⚠ Su Pokémon del anime es <b>Electabuzz</b>, y es el que manda: en la
     * serie exige combatir con el mismo tipo que él saque, así que un gimnasio
     * eléctrico es lo más cercano que se puede jugar.
     *
     * <p>⚠⚠ Y su repertorio es de <b>siete</b>, no de ocho: el eléctrico también
     * es pobre en Kanto y Johto, y siete ya cubre el máximo de seis que puede
     * traer un jugador. El autotest pide seis, no ocho.
     */
    private static final List<Ficha> RUDY = List.of(
        f("electabuzz", "static",      "timid",   "life_orb", true,
          "thunderbolt", "thunderpunch", "icepunch", "lightscreen", "swift", "psychic"),
        f("raichu",     "static",      "jolly",   "life_orb", true,
          "thunderbolt", "irontail", "quickattack", "thunderwave", "surf", "focusblast"),
        f("jolteon",    "voltabsorb",  "timid",   "leftovers", false,
          "thunderbolt", "shadowball", "quickattack", "agility", "thunderwave", "swift"),
        f("magneton",   "magnetpull",  "modest",  "eviolite", false,
          "thunderbolt", "flashcannon", "thunderwave", "lightscreen", "magnetrise", "swift"),
        f("electrode",  "soundproof",  "timid",   "focus_sash", false,
          "thunderbolt", "explosion", "lightscreen", "swift", "thunderwave", "screech"),
        f("ampharos",   "static",      "modest",  "leftovers", false,
          "thunderbolt", "powergem", "thunderwave", "lightscreen", "firepunch", "cottonspore"),
        f("lanturn",    "voltabsorb",  "modest",  "leftovers", false,
          "thunderbolt", "surf", "icebeam", "confuseray", "thunderwave", "signalbeam"));

    /**
     * LUANA — FUEGO. La Estrella del Norte, Isla Kumquat.
     *
     * <p>⚠⚠ ES LA QUE PEOR ENCAJA, y hay que decirlo. Su equipo del anime
     * (Marowak y Alakazam) no da ningún tipo, y su prueba es un <b>combate
     * doble</b>, que es una mecánica y no un tipo. El fuego se le asigna por
     * descarte: es el hueco que quedaba entre agua, hielo y eléctrico, y cierra
     * la Liga Naranja con los cuatro elementos.
     *
     * <p>⚠ Si algún día se implementan los combates dobles, ella es la
     * candidata natural — y entonces el tipo importará menos que el formato.
     */
    private static final List<Ficha> LUANA = List.of(
        f("arcanine",   "intimidate",  "adamant", "life_orb", true,
          "flamethrower", "extremespeed", "crunch", "willowisp", "firefang", "bulldoze"),
        f("ninetales",  "flashfire",   "timid",   "leftovers", true,
          "flamethrower", "confuseray", "willowisp", "energyball", "firespin", "payback"),
        f("rapidash",   "runaway",     "jolly",   "life_orb", false,
          "flareblitz", "megahorn", "bounce", "willowisp", "firespin", "stomp"),
        f("magmar",     "flamebody",   "modest",  "eviolite", false,
          "flamethrower", "thunderpunch", "confuseray", "firepunch", "psychic", "crosschop"),
        f("flareon",    "flashfire",   "adamant", "sitrus_berry", false,
          "flamethrower", "quickattack", "bite", "firefang", "shadowball", "helpinghand"),
        f("typhlosion", "blaze",       "timid",   "life_orb", false,
          "eruption", "flamethrower", "swift", "thunderpunch", "firepunch", "extrasensory"),
        f("houndoom",   "earlybird",   "timid",   "leftovers", false,
          "flamethrower", "crunch", "willowisp", "suckerpunch", "firefang", "beatup"),
        f("magcargo",   "magmaarmor",  "bold",    "leftovers", false,
          "lavaplume", "powergem", "rockslide", "amnesia", "flamethrower", "yawn"));

    /**
     * DRAKE — MIXTO. El campeón de la Liga Naranja, Isla Pomelo.
     *
     * <h2>⚠⚠⚠ ESTE NO TIENE TIPO, Y ES LA DECISIÓN, NO UN OLVIDO</h2>
     *
     * Se le iba a poner DRAGÓN —es lo que propuse— y al ir a escribirlo salió el
     * dato que lo tumba: <b>en Kanto y Johto solo hay cuatro Pokémon dragón</b>
     * (Dratini, Dragonair, Dragonite y Kingdra), y hacen falta seis. Habría que
     * importar dragones de generaciones que este servidor no tiene, solo para
     * cumplir una regla que él nunca cumplió.
     *
     * <p>Y resulta que lo correcto era lo contrario: <b>su equipo del anime es
     * deliberadamente mixto</b> — Ditto, Onix, Gengar, Venusaur, Electabuzz y
     * Dragonite. Un campeón no es un especialista; eso es justo lo que lo separa
     * de un líder de gimnasio. Ponerle un tipo lo habría hecho <i>menos</i> fiel.
     *
     * <p>⚠ Falta Ditto, que es su primer Pokémon en la serie, y el motivo es
     * técnico: solo aprende <b>Transformación</b>, y aquí cada candidato declara
     * de cuatro a seis ataques entre los que elegir. Con uno no se puede.
     *
     * <p>⚠⚠ Sin tipo declarado, el autotest <b>se salta</b> la comprobación de
     * «todo el repertorio es de su tipo» — y lo dice por el log en vez de
     * callárselo, que es lo que convertiría una excepción a propósito en un
     * agujero por el que se cuela la siguiente.
     */
    private static final List<Ficha> DRAKE = List.of(
        f("dragonite",  "innerfocus",  "adamant", "life_orb", true,
          "outrage", "dragondance", "firepunch", "extremespeed", "dragonclaw", "thunderpunch"),
        f("onix",       "rockhead",    "impish",  "eviolite", true,
          "rockslide", "earthquake", "irontail", "dragonbreath", "rocktomb", "screech"),
        f("gengar",     "cursedbody",  "timid",   "focus_sash", false,
          "shadowball", "sludgebomb", "thunderbolt", "hypnosis", "dreameater", "darkpulse"),
        f("venusaur",   "overgrow",    "bold",    "leftovers", false,
          "gigadrain", "sludgebomb", "sleeppowder", "synthesis", "leechseed", "earthquake"),
        f("electabuzz", "static",      "timid",   "life_orb", false,
          "thunderbolt", "thunderpunch", "icepunch", "lightscreen", "swift", "psychic"),
        f("kingdra",    "swiftswim",   "modest",  "leftovers", false,
          "hydropump", "dragonpulse", "icebeam", "agility", "waterpulse", "twister"),
        f("machamp",    "guts",        "adamant", "sitrus_berry", false,
          "crosschop", "earthquake", "rockslide", "bulkup", "knockoff", "bulletpunch"),
        f("exeggutor",  "chlorophyll", "modest",  "leftovers", false,
          "gigadrain", "psychic", "sleeppowder", "leechseed", "eggbomb", "lightscreen"));

    // ======================================================================
    //  LOS SIETE QUE FALTABAN DE KANTO, Y EL CAMPEÓN
    //
    //  ⚠⚠ ESTOS NO SE ESCRIBIERON A MANO: SE GENERARON DE LOS DATOS DEL JUEGO
    //     (tools/... en el historial de la sesión del 2026-08-31). La habilidad
    //     de cada uno sale de su propio fichero de especie y la naturaleza de
    //     sus estadísticas base, así que NO PUEDEN estar mal.
    //
    //     Escribir 68 fichas a mano es exactamente donde se cuela la habilidad
    //     de otra especie, y eso NO DA NINGÚN ERROR: Cobblemon se queda con la
    //     primera que tenga y el líder juega con otra cosa de la que dice.
    //
    //  ⚠ Los 414 movimientos se validaron uno a uno ANTES de generar nada.
    //    Salieron dos fallos, y los dos eran de grafía: `vicegrip` se escribe
    //    `visegrip` en Cobblemon, y Girafarig no aprende `babydolleyes`.
    //
    //  ⚠ El objeto sale del ROL, calculado: el de firma lleva Life Orb porque
    //    es el que tiene que doler, un muro lleva Restos porque su trabajo es
    //    aguantar, y uno frágil lleva Focus Sash porque si no muere antes de
    //    hacer nada.
    // ======================================================================

    /** El repertorio de MISTY. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> MISTY = List.of(
        f("starmie",      "illuminate",    "timid",    "life_orb", true,
          "surf", "icebeam", "psychic", "recover", "rapidspin", "thunderbolt"),
        f("staryu",       "illuminate",    "modest",   "life_orb", true,
          "watergun", "swift", "rapidspin", "recover", "bubblebeam", "harden"),
        f("psyduck",      "damp",          "modest",   "focus_sash", false,
          "watergun", "confusion", "disable", "waterpulse", "screech", "aquatail"),
        f("poliwhirl",    "waterabsorb",   "jolly",    null, false,
          "bubblebeam", "bodyslam", "hypnosis", "doubleslap", "mudshot", "icepunch"),
        f("goldeen",      "swiftswim",     "adamant",  "focus_sash", false,
          "hornattack", "waterpulse", "flail", "agility", "icebeam", "peck"),
        f("horsea",       "swiftswim",     "modest",   "focus_sash", false,
          "watergun", "smokescreen", "bubblebeam", "twister", "agility", "icebeam"),
        f("shellder",     "shellarmor",    "adamant",  "focus_sash", false,
          "iciclespear", "withdraw", "supersonic", "watergun", "leer", "protect"),
        f("tentacool",    "clearbody",     "modest",   "focus_sash", false,
          "acid", "wrap", "watergun", "supersonic", "poisonsting", "bubblebeam"),
        f("krabby",       "hypercutter",   "adamant",  "focus_sash", false,
          "bubblebeam", "visegrip", "harden", "mudshot", "protect", "leer"));

    /** El repertorio de SURGE. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> SURGE = List.of(
        f("raichu",       "static",        "jolly",    "life_orb", true,
          "thunderbolt", "quickattack", "thunderwave", "irontail", "swift", "surf"),
        f("pikachu",      "static",        "jolly",    "life_orb", true,
          "thunderbolt", "quickattack", "thunderwave", "irontail", "doubleteam", "swift"),
        f("magneton",     "magnetpull",    "modest",   "focus_sash", false,
          "thunderbolt", "flashcannon", "thunderwave", "lightscreen", "swift", "supersonic"),
        f("magnemite",    "magnetpull",    "modest",   "focus_sash", false,
          "thundershock", "thunderwave", "magnetbomb", "supersonic", "swift", "lightscreen"),
        f("electrode",    "soundproof",    "timid",    null, false,
          "thunderbolt", "explosion", "screech", "swift", "thunderwave", "lightscreen"),
        f("voltorb",      "soundproof",    "timid",    "focus_sash", false,
          "thundershock", "screech", "sonicboom", "swift", "thunderwave", "rollout"),
        f("chinchou",     "voltabsorb",    "modest",   null, false,
          "thunderbolt", "waterpulse", "confuseray", "thunderwave", "bubblebeam", "supersonic"),
        f("mareep",       "static",        "modest",   null, false,
          "thundershock", "thunderwave", "cottonspore", "bodyslam", "swift", "lightscreen"));

    /** El repertorio de ERIKA. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> ERIKA = List.of(
        f("vileplume",    "chlorophyll",   "modest",   "life_orb", true,
          "petalblizzard", "sludgebomb", "sleeppowder", "moonlight", "gigadrain", "stunspore"),
        f("gloom",        "chlorophyll",   "modest",   "life_orb", true,
          "megadrain", "sludgebomb", "sleeppowder", "acid", "stunspore", "moonlight"),
        f("tangela",      "chlorophyll",   "modest",   null, false,
          "vinewhip", "gigadrain", "sleeppowder", "ancientpower", "bind", "growth"),
        f("exeggcute",    "chlorophyll",   "modest",   null, false,
          "gigadrain", "psychic", "sleeppowder", "leechseed", "reflect", "bulletseed"),
        f("victreebel",   "chlorophyll",   "adamant",  null, false,
          "razorleaf", "sludgebomb", "sleeppowder", "gigadrain", "acid", "stunspore"),
        f("weepinbell",   "chlorophyll",   "adamant",  null, false,
          "razorleaf", "acid", "sleeppowder", "gigadrain", "stunspore", "poisonpowder"),
        f("bellossom",    "chlorophyll",   "modest",   "leftovers", false,
          "petalblizzard", "magicalleaf", "sleeppowder", "moonlight", "gigadrain", "stunspore"),
        f("sunflora",     "chlorophyll",   "modest",   null, false,
          "gigadrain", "solarbeam", "sunnyday", "petalblizzard", "growth", "razorleaf"),
        f("jumpluff",     "chlorophyll",   "jolly",    null, false,
          "gigadrain", "sleeppowder", "leechseed", "aerialace", "stunspore", "cottonspore"));

    /** El repertorio de KOGA. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> KOGA = List.of(
        f("weezing",      "levitate",      "adamant",  "life_orb", true,
          "sludgebomb", "flamethrower", "explosion", "willowisp", "clearsmog", "thunderbolt"),
        f("muk",          "stench",        "adamant",  "life_orb", true,
          "sludgebomb", "gunkshot", "shadowsneak", "poisongas", "acidarmor", "brickbreak"),
        f("koffing",      "levitate",      "adamant",  "focus_sash", false,
          "sludge", "smokescreen", "explosion", "clearsmog", "willowisp", "thunderbolt"),
        f("venomoth",     "shielddust",    "timid",    null, false,
          "sludgebomb", "psychic", "sleeppowder", "bugbuzz", "supersonic", "doubleteam"),
        f("arbok",        "intimidate",    "adamant",  null, false,
          "gunkshot", "crunch", "glare", "earthquake", "acid", "screech"),
        f("golbat",       "innerfocus",    "jolly",    null, false,
          "airslash", "sludgebomb", "confuseray", "leechlife", "bite", "supersonic"),
        f("ariados",      "swarm",         "adamant",  null, false,
          "sludgebomb", "xscissor", "spiderweb", "suckerpunch", "poisonsting", "agility"),
        f("qwilfish",     "poisonpoint",   "adamant",  null, false,
          "sludgebomb", "waterfall", "spikes", "toxicspikes", "poisonsting", "minimize"));

    /** El repertorio de SABRINA. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> SABRINA = List.of(
        f("alakazam",     "synchronize",   "timid",    "life_orb", true,
          "psychic", "shadowball", "recover", "calmmind", "psybeam", "reflect"),
        f("kadabra",      "synchronize",   "timid",    "life_orb", true,
          "psybeam", "shadowball", "recover", "reflect", "confusion", "disable"),
        f("mrmime",       "soundproof",    "timid",    "focus_sash", false,
          "psychic", "lightscreen", "reflect", "dazzlinggleam", "barrier", "encore"),
        f("hypno",        "insomnia",      "adamant",  "leftovers", false,
          "psychic", "hypnosis", "shadowball", "nastyplot", "confusion", "headbutt"),
        f("exeggutor",    "chlorophyll",   "modest",   "leftovers", false,
          "psychic", "gigadrain", "sleeppowder", "leechseed", "reflect", "eggbomb"),
        f("slowbro",      "oblivious",     "modest",   "leftovers", false,
          "psychic", "surf", "slackoff", "calmmind", "waterpulse", "yawn"),
        f("espeon",       "synchronize",   "timid",    null, false,
          "psychic", "shadowball", "morningsun", "calmmind", "swift", "psybeam"),
        f("xatu",         "synchronize",   "timid",    null, false,
          "psychic", "airslash", "reflect", "lightscreen", "confuseray", "nightshade"),
        f("girafarig",    "innerfocus",    "modest",   null, false,
          "psychic", "crunch", "agility", "lightscreen", "confusion", "stomp"));

    /** El repertorio de BLAINE. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> BLAINE = List.of(
        f("arcanine",     "intimidate",    "jolly",    "life_orb", true,
          "flamethrower", "extremespeed", "crunch", "willowisp", "firefang", "roar"),
        f("magmar",       "flamebody",     "timid",    "life_orb", true,
          "flamethrower", "thunderpunch", "confuseray", "firepunch", "crosschop", "smokescreen"),
        f("rapidash",     "runaway",       "jolly",    null, false,
          "flareblitz", "megahorn", "bounce", "willowisp", "stomp", "agility"),
        f("ninetales",    "flashfire",     "timid",    null, false,
          "flamethrower", "confuseray", "willowisp", "energyball", "firespin", "safeguard"),
        f("flareon",      "flashfire",     "adamant",  null, false,
          "flamethrower", "quickattack", "bite", "firefang", "doublekick", "smog"),
        f("growlithe",    "intimidate",    "adamant",  null, false,
          "flamethrower", "bite", "willowisp", "firefang", "roar", "reversal"),
        f("ponyta",       "runaway",       "jolly",    "focus_sash", false,
          "flamewheel", "stomp", "agility", "firespin", "bounce", "tailwhip"),
        f("magcargo",     "magmaarmor",    "modest",   "leftovers", false,
          "lavaplume", "powergem", "rockslide", "amnesia", "flamethrower", "harden"));

    /** El repertorio de GIOVANNI. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> GIOVANNI = List.of(
        f("rhydon",       "lightningrod",  "adamant",  "life_orb", true,
          "earthquake", "rockslide", "megahorn", "hammerarm", "stomp", "drillrun"),
        f("nidoking",     "poisonpoint",   "adamant",  "life_orb", true,
          "earthquake", "sludgebomb", "megahorn", "icebeam", "poisonjab", "thunderbolt"),
        f("nidoqueen",    "poisonpoint",   "adamant",  "leftovers", false,
          "earthquake", "sludgebomb", "bodyslam", "icebeam", "poisonjab", "crunch"),
        f("dugtrio",      "sandveil",      "jolly",    "focus_sash", false,
          "earthquake", "rockslide", "suckerpunch", "sandtomb", "slash", "mudslap"),
        f("sandslash",    "sandveil",      "adamant",  null, false,
          "earthquake", "rockslide", "swordsdance", "crushclaw", "slash", "sandtomb"),
        f("marowak",      "rockhead",      "adamant",  "leftovers", false,
          "bonemerang", "earthquake", "rockslide", "firepunch", "boneclub", "thrash"),
        f("golem",        "rockhead",      "adamant",  "leftovers", false,
          "earthquake", "rockslide", "explosion", "bulldoze", "rockthrow", "rollout"),
        f("donphan",      "sturdy",        "adamant",  "leftovers", false,
          "earthquake", "rockslide", "rapidspin", "knockoff", "bulldoze", "rollout"),
        f("steelix",      "rockhead",      "adamant",  "leftovers", false,
          "earthquake", "ironhead", "rockslide", "irondefense", "dragonbreath", "screech"));

    /** El repertorio de BLUE. Generado y validado: ver el javadoc de
     *  la clase. */
    private static final List<Ficha> BLUE = List.of(
        f("pidgeot",      "keeneye",       "jolly",    "life_orb", true,
          "bravebird", "airslash", "uturn", "featherdance", "quickattack", "roost"),
        f("alakazam",     "synchronize",   "timid",    "life_orb", true,
          "psychic", "shadowball", "recover", "calmmind", "psybeam", "reflect"),
        f("rhydon",       "lightningrod",  "adamant",  "leftovers", false,
          "earthquake", "rockslide", "megahorn", "hammerarm", "stomp", "drillrun"),
        f("arcanine",     "intimidate",    "jolly",    "leftovers", false,
          "flamethrower", "extremespeed", "crunch", "willowisp", "firefang", "roar"),
        f("gyarados",     "intimidate",    "adamant",  "leftovers", false,
          "waterfall", "crunch", "icefang", "dragondance", "bounce", "bite"),
        f("exeggutor",    "chlorophyll",   "modest",   "leftovers", false,
          "psychic", "gigadrain", "sleeppowder", "leechseed", "reflect", "eggbomb"),
        f("blastoise",    "torrent",       "modest",   "leftovers", false,
          "hydropump", "icebeam", "flashcannon", "rapidspin", "waterpulse", "bite"),
        f("machamp",      "guts",          "adamant",  "leftovers", false,
          "crosschop", "earthquake", "rockslide", "bulkup", "knockoff", "bulletpunch"));



    /**
     * La bolsa de cada líder: qué objetos puede usar en combate.
     *
     * <p>⚠ Los de Brock son los de su datapack (2 Full Restore) y no una
     * invención. La bolsa es la mitad de lo que hace difícil a un líder: sin
     * ella, ganarle es aguantar cuatro turnos.
     */
    private static final Map<String, List<String>> BOLSAS = new LinkedHashMap<>();

    static {
        BOLSAS.put("brock", List.of("cobblemon:full_restore", "cobblemon:full_restore"));
        // ⚠ Los de la Liga Naranja llevan MÁS objetos que Brock, y no es un
        //   capricho: pelean entre el nivel 49 y el 62, o sea combates largos.
        //   Con dos Full Restore, un líder de nivel 60 se cae en cuanto pierde
        //   el primero -- la bolsa es la mitad de lo que hace duro a un líder.
        BOLSAS.put("cissy", List.of("cobblemon:full_restore", "cobblemon:full_restore",
                                    "cobblemon:hyper_potion"));
        BOLSAS.put("danny", List.of("cobblemon:full_restore", "cobblemon:full_restore",
                                    "cobblemon:hyper_potion"));
        BOLSAS.put("rudy", List.of("cobblemon:full_restore", "cobblemon:full_restore",
                                   "cobblemon:hyper_potion"));
        BOLSAS.put("luana", List.of("cobblemon:full_restore", "cobblemon:full_restore",
                                    "cobblemon:full_restore"));
        // ⚠⚠ Y el CAMPEÓN lleva cuatro: es el último de la región y un combate a
        //    seis. Si se le acaban a mitad, la segunda mitad es un paseo.
        BOLSAS.put("drake", List.of("cobblemon:full_restore", "cobblemon:full_restore",
                                    "cobblemon:full_restore", "cobblemon:full_restore"));

        // ⚠⚠ LA BOLSA CRECE CON EL NIVEL, y es la palanca de dificultad más
        //    barata que hay: no cambia el equipo, cambia cuántos turnos hay que
        //    aguantar. Misty pelea a 19 con dos pociones; Giovanni a 46 con
        //    tres Full Restore.
        BOLSAS.put("misty", List.of("cobblemon:super_potion",
                                    "cobblemon:super_potion"));
        BOLSAS.put("surge", List.of("cobblemon:super_potion",
                                    "cobblemon:super_potion"));
        BOLSAS.put("erika", List.of("cobblemon:hyper_potion",
                                    "cobblemon:hyper_potion"));
        BOLSAS.put("koga", List.of("cobblemon:hyper_potion",
                                   "cobblemon:hyper_potion",
                                   "cobblemon:full_heal"));
        BOLSAS.put("sabrina", List.of("cobblemon:hyper_potion",
                                      "cobblemon:hyper_potion",
                                      "cobblemon:full_restore"));
        BOLSAS.put("blaine", List.of("cobblemon:full_restore",
                                     "cobblemon:full_restore"));
        BOLSAS.put("giovanni", List.of("cobblemon:full_restore",
                                       "cobblemon:full_restore",
                                       "cobblemon:full_restore"));
        // ⚠ El campeón lleva cuatro, igual que Drake: es el último de la región
        //   y si se le acaban a mitad, la segunda mitad del combate es un paseo.
        BOLSAS.put("campeon_kanto", List.of("cobblemon:full_restore",
                                            "cobblemon:full_restore",
                                            "cobblemon:full_restore",
                                            "cobblemon:full_restore"));
    }

    private static final Map<String, List<Ficha>> POR_GIMNASIO = new LinkedHashMap<>();

    /**
     * DE QUÉ TIPO ES CADA LÍDER.
     *
     * <p>⚠⚠ Aquí y no en {@link Gimnasio}, y no es indiferente: es <b>la regla
     * del repertorio</b>, así que vive al lado de la lista que restringe. El
     * autotest comprueba que todo el repertorio de un líder sea de su tipo, y
     * esa comprobación es lo único que impide que el gimnasio se convierta en un
     * espejo con el paso del tiempo.
     *
     * <p>⚠ No se saca del nombre de la medalla. La de Brock se llama «Roca» por
     * casualidad; la de Misty se llama «Cascada» y su tipo es agua. Deducirlo
     * del nombre funcionaría con el primero y fallaría con el segundo.
     */
    private static final Map<String, String> TIPO_LIDER = new LinkedHashMap<>();

    static {
        POR_GIMNASIO.put("brock", BROCK);
        TIPO_LIDER.put("brock", "rock");

        POR_GIMNASIO.put("cissy", CISSY);
        TIPO_LIDER.put("cissy", "water");
        POR_GIMNASIO.put("danny", DANNY);
        TIPO_LIDER.put("danny", "ice");
        POR_GIMNASIO.put("rudy", RUDY);
        TIPO_LIDER.put("rudy", "electric");
        POR_GIMNASIO.put("luana", LUANA);
        TIPO_LIDER.put("luana", "fire");
        // ⚠⚠⚠ DRAKE NO LLEVA TIPO A PROPÓSITO: es un campeón y su equipo es
        //    mixto. Ver el javadoc de DRAKE -- ahí está por qué, y por qué el
        //    dragón (que es lo que parecía) no daba ni para seis candidatos.
        POR_GIMNASIO.put("drake", DRAKE);

        POR_GIMNASIO.put("misty", MISTY);
        TIPO_LIDER.put("misty", "water");
        POR_GIMNASIO.put("surge", SURGE);
        TIPO_LIDER.put("surge", "electric");
        POR_GIMNASIO.put("erika", ERIKA);
        TIPO_LIDER.put("erika", "grass");
        POR_GIMNASIO.put("koga", KOGA);
        TIPO_LIDER.put("koga", "poison");
        POR_GIMNASIO.put("sabrina", SABRINA);
        TIPO_LIDER.put("sabrina", "psychic");
        POR_GIMNASIO.put("blaine", BLAINE);
        TIPO_LIDER.put("blaine", "fire");
        POR_GIMNASIO.put("giovanni", GIOVANNI);
        TIPO_LIDER.put("giovanni", "ground");
        POR_GIMNASIO.put("campeon_kanto", BLUE);
        // ⚠ BLUE es MIXTO: campeon, sin tipo. Ver DRAKE.
    }

    /**
     * ¿Este líder es de un tipo, o su equipo es mixto?
     *
     * <p>⚠⚠ Existe para que el autotest pueda <b>saltarse</b> la comprobación de
     * tipo <i>diciéndolo</i>. Sin esta pregunta explícita, un tipo que falta por
     * descuido se leería igual que uno que falta a propósito, y la comprobación
     * que protege a los gimnasios dejaría de proteger a nadie sin avisar.
     */
    public static boolean esDeUnTipo(String gimnasio) {
        return TIPO_LIDER.containsKey(gimnasio);
    }

    /** De qué tipo es ese líder, o {@code null} si no se ha declarado. */
    public static String tipoDe(String gimnasio) {
        return TIPO_LIDER.get(gimnasio);
    }

    /**
     * El repertorio de un gimnasio, o vacío si todavía no tiene.
     *
     * <p>⚠⚠ VACÍO Y NO UN RESPALDO. Un repertorio inventado para un líder que no
     * lo tiene sería un gimnasio que <b>parece funcionar</b> y saca Pokémon que
     * no pegan con él. {@link Adaptador} lo comprueba y, si está vacío, deja el
     * combate en manos del datapack — que es lo que había antes y funciona.
     */
    public static List<Ficha> de(String gimnasio) {
        return POR_GIMNASIO.getOrDefault(gimnasio, List.of());
    }

    /** ¿Este gimnasio ya se pelea con equipo adaptado? */
    public static boolean adaptado(String gimnasio) {
        return !de(gimnasio).isEmpty();
    }

    /** Los gimnasios que ya tienen repertorio. Para el autotest y el comando. */
    public static List<String> conRepertorio() {
        return new ArrayList<>(POR_GIMNASIO.keySet());
    }

    /** La bolsa de un líder. Vacía si no se le declaró ninguna. */
    public static List<String> bolsa(String gimnasio) {
        return BOLSAS.getOrDefault(gimnasio, List.of());
    }
}
