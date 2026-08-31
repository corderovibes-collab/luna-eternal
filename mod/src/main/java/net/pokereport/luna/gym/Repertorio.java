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
     * La bolsa de cada líder: qué objetos puede usar en combate.
     *
     * <p>⚠ Los de Brock son los de su datapack (2 Full Restore) y no una
     * invención. La bolsa es la mitad de lo que hace difícil a un líder: sin
     * ella, ganarle es aguantar cuatro turnos.
     */
    private static final Map<String, List<String>> BOLSAS = new LinkedHashMap<>();

    static {
        BOLSAS.put("brock", List.of("cobblemon:full_restore", "cobblemon:full_restore"));
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
