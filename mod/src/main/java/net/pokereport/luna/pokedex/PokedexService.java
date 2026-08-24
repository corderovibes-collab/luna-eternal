package net.pokereport.luna.pokedex;

import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pokédex: qué ha visto y capturado cada jugador.
 *
 * <p>Guarda <b>observaciones</b>, no Pokémon. El almacén de criaturas es de
 * Cobblemon y sigue siéndolo: duplicarlo aquí daría dos fuentes de verdad que
 * se desincronizarían a la primera.
 */
public final class PokedexService {

    /** Una entrada del registro. */
    public record Entry(String species, int dexNumber, boolean seen, boolean caught,
                        boolean shinyCaught, int caughtCount, Integer bestLevel,
                        Integer firstMoonPhase) {}

    /** Resumen para la barra lateral y las recompensas. */
    public record Summary(int caught, int seen, int shiny) {}

    private final Database db;

    public PokedexService(Database db) {
        this.db = db;
    }

    /**
     * Anota una captura.
     *
     * <p>Un solo {@code INSERT … ON DUPLICATE KEY UPDATE} en vez de leer,
     * decidir y escribir: sin él, dos capturas simultáneas del mismo jugador
     * —posible con dos balls en vuelo— se pisarían y una de las dos no
     * contaría.
     *
     * @return {@code true} si es la primera captura de esa especie
     */
    /**
     * Anota una captura. Devuelve {@code true} <b>si es la primera</b>.
     *
     * <h2>⚠⚠ Lo decide una ESCRITURA, no una lectura previa</h2>
     *
     * La versión anterior hacía {@code SELECT caught} y después el
     * {@code INSERT}, y entre las dos cabe otra captura: con el executor de E/S
     * a <b>dos hilos</b>, dos capturas de la misma especie leían las dos «no la
     * tienes» y las dos contestaban «nueva». Eso paga las Marcas dos veces, da
     * la XP de especie nueva dos veces y avanza dos veces la misión de registrar
     * especies.
     *
     * <p>Hoy la respuesta sale del <b>número de filas afectadas</b>, que sí es
     * atómico:
     *
     * <ul>
     *   <li>El {@code INSERT IGNORE} entra una sola vez: de dos simultáneos,
     *       uno afecta a 1 fila y el otro a 0.</li>
     *   <li>Si la fila ya estaba, el {@code UPDATE ... WHERE caught = 0}
     *       distingue «vista pero no capturada» de «ya capturada» — y bloquea la
     *       fila, así que el segundo la reevalúa ya con {@code caught = 1} y
     *       afecta a 0.</li>
     * </ul>
     *
     * <p>⚠ Que las Marcas lleven además clave de idempotencia derivada no
     * sobra: son <b>dos redes independientes</b>, y la de la economía es la que
     * aguanta si algún día alguien vuelve a tocar esto.
     */
    public boolean recordCapture(long playerId, String species, int dexNumber,
                                 boolean shiny, int level, int moonPhase)
            throws SQLException {

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                boolean first;

                int insertadas;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT IGNORE INTO pokedex_entry
                          (player_id, species, dex_number, seen, caught, shiny_caught,
                           caught_count, best_level, first_caught_at, first_moon_phase)
                        VALUES (?,?,?,1,1,?,1,?,CURRENT_TIMESTAMP(3),?)
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setString(2, species);
                    ps.setInt(3, dexNumber);
                    ps.setBoolean(4, shiny);
                    ps.setInt(5, level);
                    ps.setInt(6, moonPhase);
                    insertadas = ps.executeUpdate();
                }

                if (insertadas == 1) {
                    // Nadie la tenía. Es nueva, y la fila ya queda completa.
                    first = true;
                } else {
                    // La fila existía: solo es «primera captura» si estaba VISTA
                    // pero no capturada, y eso lo decide el WHERE, no un if.
                    try (PreparedStatement ps = c.prepareStatement("""
                            UPDATE pokedex_entry SET
                              seen = 1, caught = 1,
                              shiny_caught = GREATEST(shiny_caught, ?),
                              caught_count = caught_count + 1,
                              best_level = GREATEST(COALESCE(best_level, 0), ?),
                              first_caught_at = COALESCE(first_caught_at,
                                                         CURRENT_TIMESTAMP(3)),
                              first_moon_phase = COALESCE(first_moon_phase, ?)
                            WHERE player_id = ? AND species = ? AND caught = 0
                            """)) {
                        ps.setBoolean(1, shiny);
                        ps.setInt(2, level);
                        ps.setInt(3, moonPhase);
                        ps.setLong(4, playerId);
                        ps.setString(5, species);
                        first = ps.executeUpdate() == 1;
                    }
                    if (!first) {
                        // Recaptura normal: suben el contador, el shiny y el
                        // nivel máximo, pero la primera vez no se toca -- es la
                        // que cuenta la historia de cuándo y con qué luna.
                        try (PreparedStatement ps = c.prepareStatement("""
                                UPDATE pokedex_entry SET
                                  seen = 1, caught = 1,
                                  shiny_caught = GREATEST(shiny_caught, ?),
                                  caught_count = caught_count + 1,
                                  best_level = GREATEST(COALESCE(best_level, 0), ?)
                                WHERE player_id = ? AND species = ?
                                """)) {
                            ps.setBoolean(1, shiny);
                            ps.setInt(2, level);
                            ps.setLong(3, playerId);
                            ps.setString(4, species);
                            ps.executeUpdate();
                        }
                    }
                }

                c.commit();
                return first;
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Anota que se ha visto una especie, sin capturarla. */
    public void recordSeen(long playerId, String species, int dexNumber)
            throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO pokedex_entry (player_id, species, dex_number, seen)
                VALUES (?,?,?,1)
                ON DUPLICATE KEY UPDATE seen = 1
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, species);
            ps.setInt(3, dexNumber);
            ps.executeUpdate();
        }
    }

    /**
     * Número de Pokédex más bajo que el jugador aún no ha capturado.
     *
     * <p>Se resuelve <b>en la base</b>, no mirando la página que se está
     * enseñando: con la página en memoria solo se ve una ventana de 28
     * entradas, y el "ir a lo que falta" acabaría mandando siempre al
     * principio.
     *
     * @return el número, o {@code maxDex + 1} si ya están todas
     */
    public int firstUncaughtDex(long playerId, int maxDex) throws SQLException {
        var capturadas = new java.util.HashSet<Integer>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT dex_number FROM pokedex_entry
                WHERE player_id = ? AND caught = 1 AND dex_number <= ?
                """)) {
            ps.setLong(1, playerId);
            ps.setInt(2, maxDex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) capturadas.add(rs.getInt(1));
            }
        }
        for (int dex = 1; dex <= maxDex; dex++) {
            if (!capturadas.contains(dex)) return dex;
        }
        return maxDex + 1;
    }

    public Summary summary(long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT COALESCE(SUM(caught),0), COALESCE(SUM(seen),0),
                       COALESCE(SUM(shiny_caught),0)
                FROM pokedex_entry WHERE player_id = ?
                """)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                    ? new Summary(rs.getInt(1), rs.getInt(2), rs.getInt(3))
                    : new Summary(0, 0, 0);
            }
        }
    }

    /**
     * Entradas por rango de número de Pokédex, para paginar.
     *
     * <p>Siempre acotado: la Pokédex completa son 1 025 entradas y cargarlas
     * todas para enseñar 28 es la clase de consulta que se nota cuando hay
     * gente conectada.
     */
    public List<Entry> range(long playerId, int fromDex, int toDex) throws SQLException {
        List<Entry> out = new ArrayList<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT species, dex_number, seen, caught, shiny_caught,
                       caught_count, best_level, first_moon_phase
                FROM pokedex_entry
                WHERE player_id = ? AND dex_number BETWEEN ? AND ?
                ORDER BY dex_number
                """)) {
            ps.setLong(1, playerId);
            ps.setInt(2, fromDex);
            ps.setInt(3, toDex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer best = rs.getObject("best_level") == null
                        ? null : rs.getInt("best_level");
                    Integer moon = rs.getObject("first_moon_phase") == null
                        ? null : rs.getInt("first_moon_phase");
                    out.add(new Entry(
                        rs.getString("species"), rs.getInt("dex_number"),
                        rs.getBoolean("seen"), rs.getBoolean("caught"),
                        rs.getBoolean("shiny_caught"), rs.getInt("caught_count"),
                        best, moon));
                }
            }
        }
        return out;
    }
}
