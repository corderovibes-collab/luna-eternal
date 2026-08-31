package net.pokereport.luna.gym;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Species;

/**
 * LA TABLA DE TIPOS.
 *
 * <h2>⚠⚠⚠ NO ESTÁ ESCRITA DE MEMORIA: SE SACÓ DE LOS DATOS DEL PROPIO JUEGO</h2>
 *
 * Cobblemon empaqueta Pokémon Showdown entero en
 * {@code data/cobblemon/showdown.zip}, y dentro está {@code data/typechart.js}
 * con el {@code damageTaken} de cada tipo. La constante de abajo se generó
 * leyendo ese fichero, y el generador comprobó <b>once pares conocidos</b>
 * (agua→roca 2, eléctrico→tierra 0, normal→fantasma 0, fuego→roca 0,5…) antes
 * de escribir nada.
 *
 * <p>Escribir 324 multiplicadores a mano es la clase de trabajo en la que un
 * error <b>no da ningún error</b>: el gimnasio elegiría mal a un Pokémon y
 * nadie sabría por qué el rival es raro. Y ni siquiera se vería revisando, que
 * es lo que ya nos pasó con la separación de las ranuras.
 *
 * <h2>⚠⚠ Y POR QUÉ NO SE USA LA DE rctapi</h2>
 *
 * {@code TypeChart.getEffectiveness} existe y es pública, pero pide
 * {@code BattlePokemon} — o sea que <b>solo sirve dentro de un combate</b>, y
 * aquí hay que elegir el equipo <b>antes</b> de que el combate exista.
 */
public final class Tipos {

    private Tipos() {}

    /**
     * Los dieciocho, en el orden en que están las columnas de {@link #TABLA}.
     *
     * <p>⚠ El orden <b>es</b> el índice, así que reordenar esta lista sin
     * reordenar la tabla cambia todos los multiplicadores a la vez. Van juntas
     * y se generan juntas.
     */
    public static final String[] NOMBRES = {
        "normal", "fighting", "flying", "poison", "ground", "rock",
        "bug", "ghost", "steel", "fire", "water", "grass",
        "electric", "psychic", "ice", "dragon", "dark", "fairy"
    };

    /**
     * Una fila por DEFENSOR, una columna por ATACANTE.
     *
     * <p>Código: {@code 0} normal · {@code 1} resiste (×0,5) · {@code 2} débil
     * (×2) · {@code 3} inmune (×0).
     *
     * <p>Va como cadenas y no como {@code double[][]} porque así <b>se lee de un
     * vistazo si una fila tiene dieciocho</b>. Con literales sueltos, una fila
     * corta compila igual y desplaza toda la tabla.
     */
    private static final String[] TABLA = {
        "020000030000000000", // normal
        "002001100000020012", // fighting
        "010032100001202000", // flying
        "010120100001020001", // poison
        "000101000022302000", // ground
        "121120002122000000", // rock
        "012012000201000000", // bug
        "330100120000000020", // ghost
        "121321101201011101", // steel
        "000022101121001001", // fire
        "000000001112201000", // water
        "002210200211102000", // grass
        "001020001000100000", // electric
        "010000220000010020", // psychic
        "020002002200001000", // ice
        "000000000111102202", // dragon
        "020000210000030012", // dark
        "010200102000000310", // fairy
    };

    private static final double[] MULT = {1.0, 0.5, 2.0, 0.0};

    /** El índice de un tipo, o −1 si no lo conocemos (un tipo de otro mod). */
    public static int indice(String tipo) {
        if (tipo == null) {
            return -1;
        }
        String t = tipo.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < NOMBRES.length; i++) {
            if (NOMBRES[i].equals(t)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Cuánto multiplica un ataque de {@code atacante} contra un defensor de uno
     * o dos tipos.
     *
     * <p>⚠ Un tipo desconocido cuenta como ×1 y <b>no revienta</b>: si mañana un
     * mod añade un tipo, el gimnasio elige un poco peor pero sigue funcionando.
     * Lanzar aquí dejaría el reto sin combate y sin explicación.
     */
    public static double contra(String atacante, String tipo1, String tipo2) {
        int a = indice(atacante);
        if (a < 0) {
            return 1.0;
        }
        double m = 1.0;
        for (String d : new String[] {tipo1, tipo2}) {
            int i = indice(d);
            if (i >= 0) {
                m *= MULT[TABLA[i].charAt(a) - '0'];
            }
        }
        return m;
    }

    /** Los dos tipos de una especie, el segundo puede ser {@code null}. */
    public static String[] deEspecie(Species s) {
        String p = s.getPrimaryType() != null ? s.getPrimaryType().getName() : null;
        String q = s.getSecondaryType() != null ? s.getSecondaryType().getName() : null;
        return new String[] {p, q};
    }

    /**
     * ¿Esta especie pega más de físico que de especial?
     *
     * <p>Es una aproximación a propósito: mira <b>el ataque base contra el
     * ataque especial</b> de la especie, no los movimientos que lleve puestos.
     *
     * <p>⚠ Los movimientos de verdad no se pueden mirar: leerlos obligaría a
     * consultar el equipo real, y el equipo real puede cambiar entre que el
     * jugador acepta el reto y entra a la arena. La especie no cambia.
     */
    public static boolean pegaFisico(Species s) {
        return base(s, Stats.ATTACK) >= base(s, Stats.SPECIAL_ATTACK);
    }

    /**
     * ⚠ Con valor por defecto: {@code getBaseStats()} es un mapa y una especie
     * rara podría no traer una entrada. Sin el defecto sería un
     * {@code NullPointerException} <b>al desempaquetar el Integer</b>, que es de
     * los que no nombran la causa por ninguna parte.
     */
    private static int base(Species s, Stats cual) {
        Integer v = s.getBaseStats().get(cual);
        return v == null ? 0 : v;
    }
}
