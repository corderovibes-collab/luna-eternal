package net.pokereport.luna.market;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.pokereport.luna.db.Database;

/**
 * EL TASADOR: cuánto vale un Pokémon, y por qué.
 *
 * <p>Diseño completo en {@code docs/trading/mercado.md} §5-bis.
 *
 * <h2>Dos mitades</h2>
 *
 * <ol>
 *   <li>Una <b>fórmula</b> que mira lo que hace caro a un ejemplar: la especie,
 *       los IVs, los EVs, el nivel y si es shiny.</li>
 *   <li>Una <b>corrección de mercado</b> que ajusta esa fórmula con lo que la
 *       gente <i>ha pagado de verdad</i>.</li>
 * </ol>
 *
 * <h2>⚠⚠⚠ La corrección no sustituye a la fórmula: la calibra</h2>
 *
 * Lo obvio sería usar «la mediana de lo que se ha pagado por esa especie», y
 * estaría <b>mal</b>: mezcla un shiny 6×31 con un ejemplar de nivel 5, y la
 * mediana de esa mezcla no describe a ninguno de los dos.
 *
 * <p>Lo que se corrige es <b>el sesgo de la fórmula</b>:
 *
 * <pre>
 *   ratio  = precio_real / estimado_al_publicar
 *   correc = mediana de los ratios de esa especie
 *   final  = formula × (1 + peso × (correc − 1))     peso = n / (n + K)
 * </pre>
 *
 * Con cero ventas el tasador es pura fórmula; con muchas, casi puro mercado. Y
 * por el camino se mezclan solos, sin que nadie decida cuándo cambiar de método.
 *
 * <h2>⚠⚠ Solo cuentan las ventas CERRADAS</h2>
 *
 * Un precio que nadie ha pagado no es información. Si el tasador mirase lo
 * <i>publicado</i>, mover la referencia sería gratis: publicas un Magikarp a diez
 * millones y ya está.
 *
 * <h2>⚠ El tasador no pone el precio: lo sugiere</h2>
 *
 * El jugador escribe el que quiera. Si el servidor fijara precios dejaría de
 * haber mercado — y media gracia de esto es encontrar a alguien que no sabe lo
 * que tiene.
 */
public final class Tasador {

    /**
     * Cuántas ventas hacen falta para que el mercado pese la mitad.
     *
     * <p>⚠ Es la cifra que decide <b>cuánto tarda en sincronizarse</b>. Con
     * {@code K = 8}, a las ocho ventas la fórmula y el mercado pesan igual.
     * Provisional como todo lo económico, y se toca en un solo sitio.
     */
    public static final int K = 8;

    /** Cuántas ventas mira para calibrar. Más allá, la mediana ya no se mueve. */
    private static final int MUESTRA = 40;

    /**
     * El suelo de cualquier tasación.
     *
     * <p>⚠ Sin suelo, un Magikarp de nivel 1 sin EVs sale a un precio tan bajo
     * que el impuesto progresivo se lo come entero, y publicar sale a perder. Un
     * mercado en el que la mitad de las cosas no compensa listar es medio
     * mercado.
     */
    public static final long MINIMO = 200;

    private final Database db;

    public Tasador(Database db) {
        this.db = db;
    }

    /**
     * Lo que se sabe de un ejemplar para tasarlo.
     *
     * <p>⚠ Es un record propio y no el {@code Pokemon} de Cobblemon a propósito:
     * así el tasador se puede probar sin arrancar el juego. Un motor de precios
     * que solo se puede ejercitar con un servidor en marcha es un motor de
     * precios que nadie prueba.
     */
    public record Ficha(String especie, int nivel, boolean shiny,
                        int bst, Rareza rareza,
                        int ivTotal, int ivsPerfectos, int evTotal,
                        boolean habilidadOculta) {}

    /**
     * La rareza, tal y como la etiqueta Cobblemon en sus propios datos.
     *
     * <p>⚠ Se lee de sus etiquetas ({@code legendary}, {@code mythical}…) y no
     * de una lista nuestra. Mantener a mano 1.025 especies sería garantizar que
     * se queda vieja — la misma lección que el catálogo de la tienda y los 62
     * cosméticos que no existían.
     */
    public enum Rareza {
        COMUN(1.0),
        STARTER(1.4),
        FOSIL(1.8),
        PARADOJA(4.0),
        ULTRAENTE(5.0),
        LEGENDARIO(8.0),
        MITICO(12.0);

        public final double factor;

        Rareza(double factor) {
            this.factor = factor;
        }

        /** La rareza a partir de las etiquetas de la especie. */
        public static Rareza de(java.util.Collection<String> etiquetas) {
            if (etiquetas == null) {
                return COMUN;
            }
            // ⚠ De más rara a menos: una especie puede llevar VARIAS etiquetas
            //   —un mítico también es legendario en algunos casos— y gana la más
            //   alta. Al revés, un mítico se tasaría como legendario.
            if (contiene(etiquetas, "mythical")) {
                return MITICO;
            }
            if (contiene(etiquetas, "legendary") || contiene(etiquetas, "restricted")) {
                return LEGENDARIO;
            }
            if (contiene(etiquetas, "ultra_beast")) {
                return ULTRAENTE;
            }
            if (contiene(etiquetas, "paradox")) {
                return PARADOJA;
            }
            if (contiene(etiquetas, "fossil")) {
                return FOSIL;
            }
            if (contiene(etiquetas, "starter")) {
                return STARTER;
            }
            return COMUN;
        }

        private static boolean contiene(java.util.Collection<String> etiquetas,
                                        String cual) {
            for (String e : etiquetas) {
                if (e != null && e.toLowerCase(Locale.ROOT).equals(cual)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Lo que devuelve una tasación, con el desglose para poder enseñarlo. */
    public record Tasacion(long estimado, long formula, double correccion,
                           int ventasVistas, String explicacion) {}

    // ---- la fórmula --------------------------------------------------------

    /**
     * La parte que no depende del mercado. <b>Pura, y por eso comprobable.</b>
     *
     * <p>⚠ Es {@code static} y no toca la base: el autotest puede fijarla entera
     * sin un servidor detrás. Todo lo que dependa de la base está en
     * {@link #tasar}.
     */
    public static long formula(Ficha f) {
        // BASE POR ESPECIE. El total de estadísticas base va de ~175 (Sunkern) a
        // ~720 (Arceus). Se normaliza contra 500, que es un evolucionado
        // decente, y se eleva al cuadrado: la diferencia entre 600 y 500 importa
        // mucho más que entre 300 y 200, porque solo lo alto sirve para competir.
        double bst = Math.max(150, Math.min(800, f.bst())) / 500.0;
        double base = 800 * bst * bst * f.rareza().factor;

        // IVs. Se miden DOS VECES y no es redundante: el total dice cuánto tiene
        // en general, y los perfectos dicen lo que el mercado competitivo paga
        // de verdad. Un 6×31 vale mucho más que la suma de sus partes.
        double porTotal = 0.5 + 0.5 * (f.ivTotal() / 186.0);
        double porPerfectos = 1.0 + 0.35 * f.ivsPerfectos();
        double ivs = porTotal * porPerfectos;

        // EVs. Valen porque son TRABAJO, no potencial: 508 puntos son horas de
        // alguien. Sube poco pero sube.
        double evs = 1.0 + 0.6 * (Math.min(510, f.evTotal()) / 510.0);

        // Nivel. Igual: es tiempo invertido, no rareza.
        double nivel = 0.6 + 0.4 * (Math.max(1, Math.min(100, f.nivel())) / 100.0);

        // ⚠ SHINY VA APARTE DE TODO LO DEMAS, y multiplica al final. Un shiny
        //   malo sigue siendo un shiny: su valor no depende de sus números.
        double shiny = f.shiny() ? 12.0 : 1.0;

        double oculta = f.habilidadOculta() ? 1.5 : 1.0;

        double total = base * ivs * evs * nivel * shiny * oculta;
        return Math.max(MINIMO, Math.round(total));
    }

    // ---- la corrección de mercado ------------------------------------------

    /**
     * Tasa un ejemplar: fórmula más lo que el mercado haya enseñado.
     *
     * <p>⚠ Va por el hilo de E/S: consulta las ventas cerradas de la especie.
     */
    public Tasacion tasar(Ficha f) throws SQLException {
        long base = formula(f);
        List<Double> ratios = ratios(f.especie());

        if (ratios.isEmpty()) {
            return new Tasacion(base, base, 1.0, 0,
                    "Sin ventas de esta especie todavía: es una estimación.");
        }
        double correccion = mediana(ratios);
        int n = ratios.size();

        // ⚠ EL PESO CRECE CON LAS VENTAS. Con una sola venta la corrección casi
        //   no se nota --podría ser un regalo entre amigos--; con veinte, manda
        //   casi del todo. Nadie tiene que decidir cuándo cambiar de método.
        double peso = n / (double) (n + K);
        double ajustado = base * (1 + peso * (correccion - 1));

        String explicacion = n == 1
                ? "Ajustado con 1 venta cerrada de esta especie."
                : "Ajustado con " + n + " ventas cerradas de esta especie.";
        return new Tasacion(Math.max(MINIMO, Math.round(ajustado)), base,
                correccion, n, explicacion);
    }

    /**
     * Los ratios {@code precio pagado / estimado al publicar} de una especie.
     *
     * <p>⚠⚠ Solo listados <b>vendidos</b>, y solo los que llevaban estimado. Los
     * publicados no cuentan: un precio que nadie ha pagado no es información, y
     * si contara, mover la referencia sería gratis.
     *
     * <p>⚠ Y solo los <b>recientes</b>: un precio de hace seis meses describe
     * otra economía. El {@code ORDER BY sold_at DESC} con límite es lo que hace
     * que el tasador siga a la realidad en vez de arrastrar el pasado.
     */
    private List<Double> ratios(String especie) throws SQLException {
        var salida = new ArrayList<Double>();
        String sql = "SELECT price, estimated FROM gts_listing "
                + "WHERE species = ? AND state = 'SOLD' AND estimated IS NOT NULL "
                + "AND estimated > 0 ORDER BY sold_at DESC LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, especie);
            ps.setInt(2, MUESTRA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double estimado = rs.getLong(2);
                    if (estimado <= 0) {
                        continue;
                    }
                    double ratio = rs.getLong(1) / estimado;
                    // ⚠ Se acota entre 1/10 y 10. Una venta simbólica de 1 Plata
                    //   entre amigos, o un regalo al revés, no puede arrastrar la
                    //   referencia de todo el servidor. La mediana ya protege
                    //   bastante; esto protege del caso de pocas muestras.
                    salida.add(Math.max(0.1, Math.min(10.0, ratio)));
                }
            }
        }
        return salida;
    }

    /**
     * La mediana. <b>No la media, y es deliberado.</b>
     *
     * <p>⚠ Con media, dos cuentas haciéndose una venta absurda mueven la
     * referencia. Con mediana hacen falta más operaciones falsas que reales, que
     * es un listón mucho más alto para el mismo esfuerzo.
     */
    public static double mediana(List<Double> xs) {
        if (xs.isEmpty()) {
            return 1.0;
        }
        var copia = new ArrayList<>(xs);
        java.util.Collections.sort(copia);
        int n = copia.size();
        return n % 2 == 1
                ? copia.get(n / 2)
                : (copia.get(n / 2 - 1) + copia.get(n / 2)) / 2.0;
    }
}
