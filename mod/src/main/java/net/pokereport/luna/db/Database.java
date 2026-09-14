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
    static final String[] MIGRATIONS = {
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
        "V015__mercado.sql",
        "V016__gts_pokemon.sql",
        "V017__cazas_premios.sql",
        "V018__cazas_dos_premios.sql",
        "V019__cazas_rareza.sql",
        "V020__rangos.sql",
        "V021__mochila.sql",
        "V022__regreso.sql",
        "V023__trajes.sql",
        "V024__medallas.sql",
        "V025__rango_entrenador.sql",
        "V026__tesoros.sql",
        "V027__claves_de_especie.sql",
        // ⚠⚠ LA 28 Y LA 29 SON DE LAS CARTAS Y VENIAN DE OTRA RAMA. Estuvieron
        //    en la base sin estar en esta lista, y por eso los trajes --que
        //    tambien habian elegido la 28-- se saltaron sin una linea en el log.
        //    Ver el aviso de `migrate()`: hoy eso ya no puede pasar callado.
        "V028__cartas.sql",
        "V029__habilidad_cartas.sql",
        "V030__trajes_comprados.sql",
        "V031__santuario.sql",
        "V032__pase.sql",
        "V033__pase_una_via.sql",
        "V034__pase_lunacoins.sql",
        "V035__santuario_paseo.sql",
        "V036__puerta.sql",
        "V037__kit_rotacion.sql",
        "V038__crianza.sql",
        "V039__crianza_entrega_pendiente.sql"
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
                String sql = read("/db/migration/" + file);
                if (applied.contains(version)) {
                    // ⚠⚠⚠ DOS RAMAS PUEDEN ELEGIR EL MISMO NUMERO, Y ANTES ESO SE
                    //    SALTABA EN SILENCIO. Paso el 2026-09-03: los numeros 28
                    //    y 29 ya estaban cogidos por las migraciones de las
                    //    CARTAS, que venian de otra rama y ya estaban en la base.
                    //    Esta linea decia solo `if (applied.contains(version))
                    //    continue;`, asi que la migracion de los trajes --que
                    //    tambien se llamaba 28-- NUNCA SE APLICO y no hubo ni una
                    //    linea en el log.
                    //
                    //    ⚠⚠ Y EL FALLO APARECIO DESPUES Y CON OTRA CARA: «Table
                    //       player_suit_owned doesn't exist», una vez por cada
                    //       jugador que entraba. Nada apuntaba a la migracion.
                    //
                    //    La descripcion es lo que distingue una migracion de otra
                    //    con el mismo numero, y ya estaba guardada: no hace falta
                    //    tocar el esquema para comprobarlo.
                    String mia = descriptionOf(sql);
                    String suya = descriptionApplied(c, version);
                    if (mia != null && suya != null && !mia.equals(suya)) {
                        throw new SQLException(
                            "CHOQUE DE MIGRACIONES: la version " + version
                          + " ya esta aplicada con la descripcion \"" + suya
                          + "\", y " + file + " dice \"" + mia + "\". Son dos "
                          + "migraciones distintas con el mismo numero, "
                          + "probablemente de dos ramas. Renumera " + file
                          + " al siguiente numero libre (y su INSERT final).");
                    }
                    String sha = computeSha256(sql);
                    String shaApplied = checksumApplied(c, version);
                    if (shaApplied != null && sha != null && !shaApplied.equalsIgnoreCase(sha)) {
                        LunaEternal.LOG.warn("Migración {}: el contenido local difiere del aplicado (checksum actual={}, registrado={})",
                                file, sha, shaApplied);
                    }
                    continue;
                }

                LunaEternal.LOG.info("Aplicando migracion {}", file);

                long start = System.currentTimeMillis();
                String sha = computeSha256(sql);
                String desc = descriptionOf(sql);
                if (desc == null) {
                    desc = file;
                }

                // ⚠ MariaDB produce commits implícitos al ejecutar sentencias DDL (CREATE, ALTER, DROP).
                // Por ello, una transacción JDBC estándar no puede revertir cambios de esquema intermedios.
                // Se ejecutan las sentencias controlando idempotencia y capturando errores de objetos ya creados
                // para garantizar re-entrancia segura ante caídas previas.
                try (Statement st = c.createStatement()) {
                    for (String stmt : splitStatements(sql)) {
                        if (stmt.isBlank()) continue;
                        try {
                            st.execute(stmt);
                        } catch (SQLException e) {
                            // Si falla porque el objeto ya existe de una ejecución parcial interrumpida, advertir y continuar
                            if (e.getErrorCode() == 1050 /* ER_TABLE_EXISTS_ERROR */
                                    || e.getErrorCode() == 1060 /* ER_DUP_FIELDNAME */
                                    || e.getErrorCode() == 1061 /* ER_DUP_KEYNAME */) {
                                LunaEternal.LOG.warn("Sentencia DDL omitida en {} (el objeto ya existía): {}", file, e.getMessage());
                                continue;
                            }
                            throw e;
                        }
                    }
                } catch (SQLException e) {
                    throw new SQLException("Fallo en la migracion " + file + ": " + e.getMessage(), e);
                }

                long dur = System.currentTimeMillis() - start;
                recordMigration(c, version, desc, sha, dur);
                LunaEternal.LOG.info("Migracion {} aplicada con exito en {} ms (checksum={})", file, dur, sha);
            }
        }
    }

    /** Calcula el hash SHA-256 del contenido del archivo de migración. */
    public static String computeSha256(String content) {
        if (content == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String checksumApplied(Connection c, int version) throws SQLException {
        try (var ps = c.prepareStatement("SELECT checksum FROM schema_version WHERE version = ?")) {
            ps.setInt(1, version);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void recordMigration(Connection c, int version, String description, String checksum, long execTimeMs) throws SQLException {
        String sql = """
            INSERT INTO schema_version (version, description, checksum, execution_time_ms, status, applied_at)
            VALUES (?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                description = VALUES(description),
                checksum = COALESCE(VALUES(checksum), checksum),
                execution_time_ms = VALUES(execution_time_ms),
                status = 'SUCCESS'
            """;
        try (var ps = c.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, description != null ? description : ("v" + version));
            ps.setString(3, checksum);
            ps.setLong(4, execTimeMs);
            ps.executeUpdate();
        }
    }

    /** La descripcion que la propia migracion se pone en su INSERT final. */
    private static String descriptionOf(String sql) {
        var m = java.util.regex.Pattern.compile(
                """
                INSERT\\s+INTO\\s+schema_version\\s*\\([^)]*\\)\\s*VALUES\\s*\
                \\(\\s*\\d+\\s*,\\s*'((?:[^']|'')*)'""",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sql);
        return m.find() ? m.group(1).replace("''", "'") : null;
    }

    /** La descripcion con la que esa version quedo registrada en la base. */
    private static String descriptionApplied(Connection c, int version)
            throws SQLException {
        try (var ps = c.prepareStatement(
                "SELECT description FROM schema_version WHERE version = ?")) {
            ps.setInt(1, version);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version           INT          NOT NULL PRIMARY KEY,
                    description       VARCHAR(191) NOT NULL,
                    checksum          VARCHAR(64)  NULL,
                    execution_time_ms BIGINT       NULL,
                    status            VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
                    applied_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            try {
                st.execute("ALTER TABLE schema_version ADD COLUMN checksum VARCHAR(64) NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE schema_version ADD COLUMN execution_time_ms BIGINT NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE schema_version ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'");
            } catch (SQLException ignored) {}
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
