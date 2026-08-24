package net.pokereport.luna.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.pokereport.luna.LunaConfig;
import net.pokereport.luna.LunaEternal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool de conexiones y ejecutor de migraciones.
 *
 * <p>Las migraciones se numeran ({@code V001__…sql}), viven en el jar y se
 * aplican en orden una sola vez. La tabla {@code schema_version} registra
 * cuáles ya corrieron (data-model.md §6).
 */
public final class Database implements AutoCloseable {

    /** Migraciones conocidas, en orden. Añadir aquí cada fichero nuevo. */
    private static final String[] MIGRATIONS = {
        // ⚠️ AÑADIR AQUI CADA MIGRACION NUEVA.
        //
        // La lista es manual a proposito: el orden de aplicacion tiene que
        // ser explicito y no depender de como el sistema de ficheros ordene
        // un directorio dentro de un jar.
        //
        // El precio es este: crear V010__cazas.sql y olvidarse de listarla
        // hizo que el servidor arrancara sin la tabla, y solo lo pillo el
        // autotest. Si anades un fichero, anade la linea.
        "V001__initial.sql",
        "V002__widen_idempotency_key.sql",
        "V003__add_reportcoin.sql",
        "V004__player_paths.sql",
        "V005__gts.sql",
        "V006__gts_delivery.sql",
        "V007__pokedex.sql",
        "V008__kits.sql",
        "V009__quests.sql",
        "V010__cazas.sql",
        "V011__cosmeticos.sql",
        "V012__oficios.sql",
        "V013__clanes.sql",
        "V014__clan_auditoria.sql",
        "V015__mercado.sql"
    };

    private final HikariDataSource ds;

    public Database(LunaConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.jdbcUrl);
        hc.setUsername(cfg.user);
        hc.setPassword(cfg.password);
        hc.setMaximumPoolSize(cfg.poolSize);
        hc.setMinimumIdle(1);
        hc.setPoolName("luna-db");
        // Timeouts cortos: si la base no responde, queremos saberlo ya,
        // no bloquear el hilo del servidor. Ver data-model.md §7.
        hc.setConnectionTimeout(5_000);
        hc.setValidationTimeout(3_000);
        hc.setInitializationFailTimeout(cfg.failFast ? 10_000 : -1);
        hc.setAutoCommit(true);
        this.ds = new HikariDataSource(hc);
    }

    public Connection connection() throws SQLException {
        return ds.getConnection();
    }

    // ---------------------------------------------------------------- migraciones

    public void migrate() throws SQLException, IOException {
        try (Connection c = connection()) {
            ensureVersionTable(c);
            List<Integer> applied = appliedVersions(c);

            for (String file : MIGRATIONS) {
                int version = versionOf(file);
                if (applied.contains(version)) continue;

                LunaEternal.LOG.info("Aplicando migracion {}", file);
                String sql = read("/db/migration/" + file);

                // Cada migración es atómica: o entera o ninguna.
                boolean prev = c.getAutoCommit();
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    for (String stmt : splitStatements(sql)) {
                        st.execute(stmt);
                    }
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw new SQLException("Fallo en la migracion " + file, e);
                } finally {
                    c.setAutoCommit(prev);
                }
                // Cada migracion se registra a si misma con un INSERT final.
                // Se COMPRUEBA que lo haya hecho: olvidarlo hace que la
                // migracion se reaplique en cada arranque, y si contiene un
                // DROP TABLE eso borra datos de produccion en silencio. Paso
                // con V010 y solo se noto por casualidad.
                if (!appliedVersions(c).contains(version)) {
                    throw new SQLException(
                        "La migracion " + file + " no se registro en "
                      + "schema_version. Le falta el INSERT final: "
                      + "INSERT INTO schema_version (version, description) "
                      + "VALUES (" + version + ", '...') "
                      + "ON DUPLICATE KEY UPDATE version = version;");
                }
                LunaEternal.LOG.info("Migracion {} aplicada", file);
            }
        }
    }

    private static void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version     INT          NOT NULL PRIMARY KEY,
                    description VARCHAR(191) NOT NULL,
                    applied_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
    }

    private static List<Integer> appliedVersions(Connection c) throws SQLException {
        List<Integer> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) out.add(rs.getInt(1));
        }
        return out;
    }

    private static int versionOf(String filename) {
        // "V001__initial.sql" -> 1
        int end = filename.indexOf("__");
        return Integer.parseInt(filename.substring(1, end));
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("No se encuentra " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parte el fichero en sentencias por ';', ignorando los comentarios de
     * línea. Suficiente para nuestras migraciones, que no usan procedimientos
     * almacenados ni delimitadores propios.
     */
    private static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            cur.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String stmt = cur.toString().strip();
                out.add(stmt.substring(0, stmt.length() - 1));
                cur.setLength(0);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().strip());
        return out;
    }

    @Override
    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}
