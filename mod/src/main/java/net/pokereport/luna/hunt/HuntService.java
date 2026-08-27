package net.pokereport.luna.hunt;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cazas y Crianza (HUNT-001).
 *
 * <p><b>Las mismas para todo el servidor</b>, rotando cada 12 horas. Es
 * decisión del usuario y es la correcta: que todos persigan lo mismo a la vez
 * crea conversación, y convierte la rotación en una fila de base de datos en
 * vez de N.
 *
 * <p><b>Solo cuenta capturar.</b> También se planteó contar combates, y se
 * descartó por una razón concreta: un combate contra otro jugador se amaña en
 * dos minutos, así que la caza dejaría de significar nada.
 */
public final class HuntService {

    /**
     * Cada cuánto rota. <b>24 h</b>, por decisión del usuario.
     *
     * <p>⚠ Con 12 h la caza cambiaba <b>dos veces al día</b>, y quien juega por
     * la tarde nunca veía la del ciclo de noche. Con 24 h todo el mundo ve la
     * misma, que es justo lo que hace que se hable de ella.
     */
    private static final long HORAS = 24;

    /**
     * Tres de cada tipo. Tres caben grandes en el Pad; con cinco cada tarjeta
     * se quedaba estrecha y el Pokémon salía pequeño.
     */
    private static final int CAZAS = 3;
    private static final int CRIANZAS = 3;

    public enum Tipo { CAPTURA, CRIANZA }

    /**
     * @param rareza 1 basico · 2 intermedio · 3 raro
     *
     * <p>La rareza sale de la POSICION dentro de su tipo, no del azar: asi
     * cada ciclo tiene siempre uno facil, uno medio y uno dificil. Si fuera
     * aleatoria, habria ciclos de tres dificiles que nadie completaria.
     */
    public record Objetivo(long id, Tipo tipo, String especie, int necesarios,
                           long premioDolar, long premioMarca,
                           String premioObjeto, int premioCantidad,
                           String premioObjeto2, int premioCantidad2,
                           int hechos, boolean cobrado, int rareza) {
        public boolean completo() { return hechos >= necesarios; }
    }

    /**
     * LOS PREMIOS, en un solo sitio.
     *
     * <h2>⚠⚠ PROVISIONALES A PROPÓSITO, como los de la tienda</h2>
     *
     * El usuario dejó dicho que los precios se fijan después de un análisis
     * general de la economía. Están aquí, en una tabla de seis filas, para que
     * ese análisis sea <b>cambiar seis números</b> y no buscarlos por el código.
     *
     * <h2>⚠ Y son una FUENTE de dinero, que es lo que más cuidado pide (P3)</h2>
     *
     * Alguien que complete las seis se lleva unos 10.000 de Plata al día. Es
     * mucho, y se sostiene sólo porque completar las seis <b>es difícil</b>:
     * hay que capturar tres de una especie concreta y criar otras tres. Si
     * algún día se hacen más fáciles, esto hay que bajarlo <b>a la vez</b>.
     *
     * <h2>⚠⚠ Los identificadores se comprobaron contra el jar de Cobblemon</h2>
     *
     * {@code python tools/gen_tienda.py --buscar candy}. Un identificador mal
     * escrito no da error: da un premio que no se entrega, y el jugador se
     * queda sin él <b>después de haber hecho el trabajo</b>.
     */
    private record Premio(long dolar, long marca, String objeto, int cantidad,
                          String objeto2, int cantidad2) {}

    /**
     * ⚠⚠ NADA QUE DÉ VENTAJA DE COMBATE, dicho por el usuario: <i>«que no estén
     * rotas o les dé ventaja»</i>.
     *
     * <p>Por eso son <b>balls, pociones y caramelos de experiencia</b>, y no
     * vitaminas, ni objetos equipables, ni Caramelos Raros. La diferencia no es
     * de cantidad sino de clase: <b>esto acelera lo que ya estabas haciendo; no
     * cambia lo que puedes hacer.</b> Una vitamina sube una estadística para
     * siempre; veinte Poké Balls solo te ahorran ir a comprarlas.
     *
     * <p>Y cada objetivo da <b>una ball y algo más</b> — así el premio de cazar
     * es lo que necesitas para seguir cazando, en vez de un número.
     */
    private static final Premio[] CAPTURA_PREMIOS = {
        //          Plata  Marcas  objeto                     x   + objeto     x
        new Premio(   500,      8, "cobblemon:poke_ball",     8, "cobblemon:potion",       3),
        new Premio( 1_200,     16, "cobblemon:great_ball",    6, "cobblemon:super_potion", 3),
        new Premio( 2_500,     24, "cobblemon:ultra_ball",    4, "cobblemon:max_revive",   1),
    };

    /**
     * Criar cuesta más que capturar —hace falta la especie, un Ditto y tiempo—
     * así que paga más y en experiencia, que es lo que le falta a un recién
     * eclosionado.
     */
    private static final Premio[] CRIANZA_PREMIOS = {
        new Premio( 1_000,     20, "cobblemon:exp_candy_s",   4, "cobblemon:poke_ball",    8),
        new Premio( 2_000,     40, "cobblemon:exp_candy_m",   3, "cobblemon:great_ball",   6),
        new Premio( 3_500,     60, "cobblemon:exp_candy_l",   2, "cobblemon:ultra_ball",   4),
    };

    public record Ciclo(long id, long terminaEn, List<Objetivo> objetivos) {}

    private final Database db;

    public HuntService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ rotación

    /**
     * Devuelve el ciclo vigente, creándolo si hace falta.
     *
     * <p>Se genera <b>bajo demanda</b>, no con un temporizador. Un temporizador
     * exige que el servidor esté encendido en el instante exacto del cambio; si
     * estuvo caído, la rotación se salta y nadie se entera. Así, el primero que
     * mira después de las 12 h la provoca.
     */
    public Ciclo cicloActual(long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Long id = vigente(c);
                if (id == null) id = crearCiclo(c);
                Ciclo ciclo = leer(c, id, playerId);
                c.commit();
                return ciclo;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private Long vigente(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM hunt_cycle WHERE ends_at > CURRENT_TIMESTAMP(3) "
              + "ORDER BY id DESC LIMIT 1 FOR UPDATE")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private long crearCiclo(Connection c) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO hunt_cycle (ends_at) VALUES "
              + "(DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, HORAS);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getLong(1);
            }
        }

        // ⚠⚠ UNA DE CADA RAREZA, y por tipo. Antes se sorteaban seis al azar y
        //    la estrella era LA POSICION: un Caterpie podia salir de ★★★ y
        //    pagar 2.500. Hoy la estrella la decide el Pokemon.
        var elegidas = new ArrayList<Especies.Especie>(CAZAS + CRIANZAS);
        elegidas.addAll(Especies.unaDeCada());
        elegidas.addAll(Especies.unaDeCada());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO hunt_target (cycle_id, kind, species, needed, "
              + "reward_dollar, reward_mark, reward_item, reward_qty, "
              + "reward_item2, reward_qty2, rarity) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 0; i < elegidas.size(); i++) {
                boolean caza = i < CAZAS;
                var esp = elegidas.get(i);
                // ⚠ La rareza sale de la ESPECIE, no de la posición. Y como
                //   `unaDeCada` devuelve una de cada tramo en orden, la
                //   posición coincide -- pero el que manda es el Pokémon.
                int rareza = esp.rareza();
                // ⚠⚠ UNO DE CADA, SIEMPRE. Decision del usuario: «la idea es que
                //    sea solo 1 pokemon de cada estrella que se capture y ya
                //    cuente como completado».
                //
                //    Antes eran 3/2/1 por rareza, buscando que las tres
                //    costaran parecido. La decision del usuario es mejor y por
                //    un motivo que no se me habia ocurrido: con «uno de cada»,
                //    LA ESTRELLA ES LO UNICO QUE VARIA. La dificultad la pone
                //    entero el Pokemon --lo raro que sea de encontrar-- y la
                //    estrella dice a la vez lo dificil que es y lo que paga.
                //    Con cantidades distintas, un ★ de tres capturas podia
                //    acabar costando mas que un ★★★ de una, y entonces la
                //    estrella dejaba de predecir nada.
                //
                //    ⚠ Y hace la pantalla mas legible: una barra de 0/1 se lee
                //      de un vistazo como «hecho o no hecho».
                int necesarios = 1;
                // ⚠⚠ EL PREMIO SE GUARDA AQUÍ, no se calcula al cobrar. La
                //    pantalla lo enseña, y entre enseñarlo y cobrarlo pasan
                //    hasta 24 h: si saliera de la tabla de arriba, tocarla
                //    pagaría algo distinto de lo prometido.
                var premio = (caza ? CAPTURA_PREMIOS : CRIANZA_PREMIOS)[rareza - 1];
                ps.setLong(1, id);
                ps.setString(2, caza ? "CAPTURA" : "CRIANZA");
                ps.setString(3, esp.nombre());
                ps.setInt(4, necesarios);
                ps.setLong(5, premio.dolar());
                ps.setLong(6, premio.marca());
                ps.setString(7, premio.objeto());
                ps.setInt(8, premio.cantidad());
                ps.setString(9, premio.objeto2());
                ps.setInt(10, premio.cantidad2());
                ps.setInt(11, rareza);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        LunaEternal.LOG.info("Cazas: ciclo {} creado, rota en {} h", id, HORAS);
        return id;
    }

    private Ciclo leer(Connection c, long cicloId, long playerId)
            throws SQLException {
        long termina = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT UNIX_TIMESTAMP(ends_at) FROM hunt_cycle WHERE id=?")) {
            ps.setLong(1, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) termina = rs.getLong(1);
            }
        }

        List<Objetivo> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT t.id, t.kind, t.species, t.needed, t.reward_dollar,
                       t.reward_mark, t.reward_item, t.reward_qty,
                       t.reward_item2, t.reward_qty2, t.rarity,
                       COALESCE(p.done, 0), p.claimed_at IS NOT NULL
                FROM hunt_target t
                LEFT JOIN hunt_progress p
                       ON p.target_id = t.id AND p.player_id = ?
                WHERE t.cycle_id = ?
                ORDER BY t.kind DESC, t.id
                """)) {
            ps.setLong(1, playerId);
            ps.setLong(2, cicloId);
            try (ResultSet rs = ps.executeQuery()) {
                int nCaptura = 0, nCrianza = 0;
                while (rs.next()) {
                    var tipo = Tipo.valueOf(rs.getString(2));
                    // ⚠⚠ LA RAREZA SALE DE LA FILA, no se recalcula. El premio
                    //    también está guardado ahí, y las dos tienen que venir
                    //    del mismo sitio: si una se recalculara y la otra no,
                    //    un objetivo podría enseñar ★★ pagando lo de ★.
                    // ⚠ Un 0 es un ciclo anterior a V019: para esos se deriva
                    //   de la posición, que es lo que se les prometió.
                    int guardada = rs.getInt(11);
                    int porPosicion = tipo == Tipo.CAPTURA ? ++nCaptura : ++nCrianza;
                    int rareza = guardada > 0 ? guardada : porPosicion;
                    String objeto = rs.getString(7);
                    String objeto2 = rs.getString(9);
                    out.add(new Objetivo(rs.getLong(1), tipo, rs.getString(3),
                        rs.getInt(4), rs.getLong(5), rs.getLong(6),
                        objeto == null ? "" : objeto, rs.getInt(8),
                        objeto2 == null ? "" : objeto2, rs.getInt(10),
                        rs.getInt(12), rs.getBoolean(13), rareza));
                }
            }
        }
        return new Ciclo(cicloId, termina, out);
    }

    /**
     * Cierra el ciclo vigente para que el siguiente vistazo sortee otro.
     *
     * <p>Existe porque un ciclo malo dura 12 horas, y esperarlas para
     * comprobar un arreglo no es razonable. No borra: <b>caduca</b>. Borrar
     * se llevaria por delante el progreso de quien ya hubiera capturado algo,
     * y ese progreso es suyo aunque la caza fuera mala.
     */
    public int rotarYa() throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE hunt_cycle SET ends_at = CURRENT_TIMESTAMP(3) "
               + "WHERE ends_at > CURRENT_TIMESTAMP(3)")) {
            return ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ progreso

    /**
     * Suma uno a los objetivos vivos de esa especie.
     *
     * <p>No falla si no hay ninguno: capturar algo que no está de caza es lo
     * normal, no un error.
     */
    public void avanzar(long playerId, String especie, Tipo tipo) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO hunt_progress (player_id, target_id, done)
                SELECT ?, t.id, 1
                  FROM hunt_target t
                  JOIN hunt_cycle y ON y.id = t.cycle_id
                 WHERE t.species = ? AND t.kind = ?
                   AND y.ends_at > CURRENT_TIMESTAMP(3)
                ON DUPLICATE KEY UPDATE done = done + 1
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, especie);
            ps.setString(3, tipo.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo avanzar la caza de {}", especie, e);
        }
    }

    // ------------------------------------------------------------ cobro

    public enum Resultado { PAGADO, NO_COMPLETO, YA_COBRADO, CADUCADO }

    /**
     * Entrega el premio de un objetivo, una sola vez.
     *
     * <p>Todo ocurre en una transacción: se marca cobrado y se paga en el mismo
     * bloque. Si el pago fallase, la marca se deshace. Y el {@code claimed_at
     * IS NULL} del UPDATE es lo que hace imposible cobrar dos veces aunque
     * lleguen dos clics a la vez: el segundo actualiza cero filas.
     */
    /**
     * Lo que hay que entregar además del dinero. {@code null} si no hay nada.
     *
     * <p>⚠ Se rellena <b>solo cuando se paga de verdad</b>: quien llama lo
     * usa para meter el objeto en el inventario, y meterlo sin haber cobrado
     * sería regalarlo.
     */
    public record Entrega(String objeto, int cantidad) {}

    private final ThreadLocal<List<Entrega>> ultimaEntrega = new ThreadLocal<>();

    /**
     * Lo que hubiera que entregar tras un {@link #cobrar} que devolvió PAGADO.
     *
     * <p>⚠ Devuelve una LISTA y se vacía al leerla. Con un solo objeto esto
     * era un valor suelto; en cuanto entraron dos, un valor suelto habría
     * entregado <b>uno y perdido el otro sin decir nada</b>.
     */
    public List<Entrega> entregaPendiente() {
        var e = ultimaEntrega.get();
        ultimaEntrega.remove();
        return e == null ? List.of() : e;
    }

    public Resultado cobrar(long playerId, long objetivoId, UUID clave) {
        ultimaEntrega.remove();
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                long dolar, marca;
                String objeto, objeto2;
                int cantidad, cantidad2;
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT t.reward_dollar, t.reward_mark, t.needed,
                               COALESCE(p.done,0), p.claimed_at,
                               y.ends_at > CURRENT_TIMESTAMP(3),
                               t.reward_item, t.reward_qty,
                               t.reward_item2, t.reward_qty2
                          FROM hunt_target t
                          JOIN hunt_cycle y ON y.id = t.cycle_id
                          LEFT JOIN hunt_progress p
                                 ON p.target_id = t.id AND p.player_id = ?
                         WHERE t.id = ?
                         FOR UPDATE
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, objetivoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return Resultado.CADUCADO;
                        dolar = rs.getLong(1);
                        marca = rs.getLong(2);
                        int necesarios = rs.getInt(3), hechos = rs.getInt(4);
                        boolean cobrado = rs.getTimestamp(5) != null;
                        boolean vivo = rs.getBoolean(6);
                        objeto = rs.getString(7);
                        cantidad = rs.getInt(8);
                        objeto2 = rs.getString(9);
                        cantidad2 = rs.getInt(10);
                        if (!vivo) return Resultado.CADUCADO;
                        if (cobrado) return Resultado.YA_COBRADO;
                        if (hechos < necesarios) return Resultado.NO_COMPLETO;
                    }
                }

                int filas;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE hunt_progress SET claimed_at = CURRENT_TIMESTAMP(3) "
                      + "WHERE player_id=? AND target_id=? AND claimed_at IS NULL")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, objetivoId);
                    filas = ps.executeUpdate();
                }
                if (filas == 0) {
                    c.rollback();
                    return Resultado.YA_COBRADO;
                }

                var eco = LunaEternal.economy();
                eco.applyInTransaction(c, playerId, Currency.POKEDOLLAR, dolar,
                    "hunt_reward", "hunt", objetivoId, clave + ":d");
                eco.applyInTransaction(c, playerId, Currency.MARK, marca,
                    "hunt_reward", "hunt", objetivoId, clave + ":m");

                c.commit();
                // ⚠ DESPUÉS del commit. Si se anunciara antes y la transacción
                //   se cayera, se entregaría un objeto que nadie ha cobrado.
                var entregas = new ArrayList<Entrega>(2);
                if (objeto != null && !objeto.isBlank() && cantidad > 0) {
                    entregas.add(new Entrega(objeto, cantidad));
                }
                if (objeto2 != null && !objeto2.isBlank() && cantidad2 > 0) {
                    entregas.add(new Entrega(objeto2, cantidad2));
                }
                if (!entregas.isEmpty()) {
                    ultimaEntrega.set(List.copyOf(entregas));
                }
                return Resultado.PAGADO;
            } catch (Exception e) {
                c.rollback();
                LunaEternal.LOG.error("Error cobrando la caza {}", objetivoId, e);
                return Resultado.NO_COMPLETO;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("Sin conexión al cobrar la caza", e);
            return Resultado.NO_COMPLETO;
        }
    }
}
