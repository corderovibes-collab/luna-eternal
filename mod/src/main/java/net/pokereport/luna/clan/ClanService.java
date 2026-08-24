package net.pokereport.luna.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;

/**
 * Clanes: crear, invitar, entrar, salir, mandar y aportar.
 *
 * <h2>Qué decide este fichero</h2>
 *
 * <b>Todo.</b> El cliente manda «quiero crear un clan que se llame X» y nada
 * más; quién puede hacer qué, cuánto cuesta y si el nombre está libre se decide
 * aquí. Es P6, y en un sistema social importa el doble: los permisos <i>son</i>
 * la funcionalidad.
 *
 * <h2>⚠ Los tres invariantes que sostiene la BASE, no el código</h2>
 *
 * <ul>
 *   <li><b>Un jugador está en un clan como mucho.</b> Lo garantiza que
 *       {@code clan_member.player_id} sea la clave primaria — no una
 *       comprobación previa, que dos invitaciones aceptadas a la vez se
 *       saltarían.</li>
 *   <li><b>El nombre y la etiqueta son únicos.</b> Índices únicos sobre las
 *       columnas normalizadas a minúsculas, para no depender del
 *       <i>collation</i>.</li>
 *   <li><b>El tesoro nunca baja de cero.</b> {@code CHECK (treasury >= 0)}.</li>
 * </ul>
 *
 * <h2>⚠⚠ TODA ACCIÓN VA EN UNA TRANSACCIÓN Y BLOQUEA LA FILA DEL CLAN</h2>
 *
 * La primera versión comprobaba con una conexión y escribía con otra, y eso
 * abre un hueco entre las dos: entre «¿mandas aquí?» y «pues echa a este» cabe
 * otra petición que te haya degradado. Hoy todo pasa por
 * {@link #enTransaccion} y empieza por un {@code SELECT ... FOR UPDATE} sobre
 * la fila del clan, que serializa las acciones de <b>ese</b> clan sin frenar
 * las de los demás.
 *
 * <h2>⚠⚠ Y CADA ACCIÓN DEVUELVE A QUIÉN AFECTA</h2>
 *
 * {@link Resultado#afectados()} no es un adorno: es la corrección de un fallo
 * real. Al echar a alguien, la lista de miembros de <i>después</i> ya no le
 * incluye, así que quien calculara a quién avisar mirando el clan resultante
 * <b>dejaba al echado con la etiqueta puesta y creyéndose dentro</b>. El
 * servicio acaba de hacer el trabajo y sabe exactamente a quién ha tocado; que
 * lo diga él es lo único que impide volver a olvidarlo.
 */
public final class ClanService {

    /**
     * Lo que cuesta fundar un clan.
     *
     * <p>⚠ <b>Es un sumidero, y eso es lo que lo justifica</b> (P3: sumideros
     * antes que fuentes). Además hace que un clan signifique algo: si fundarlo
     * fuera gratis habría uno por jugador y la palabra dejaría de querer decir
     * nada.
     *
     * <p><b>Sin calibrar</b>, como toda la economía.
     */
    public static final long COSTE_FUNDAR = 5_000;

    /** Días que dura una invitación sin contestar. Ver la migración. */
    public static final int DIAS_INVITACION = 7;

    /** Tope de miembros. Un clan que no cabe en la pantalla no se puede dirigir. */
    public static final int MAX_MIEMBROS = 30;

    /** Cuántos movimientos del tesoro y del registro se enseñan. */
    public static final int HISTORIAL = 40;

    /** Tope máximo que un líder puede ponerle a sus oficiales. */
    public static final long TOPE_MAXIMO = 10_000_000;

    private final Database db;

    public ClanService(Database db) {
        this.db = db;
    }

    // ---- lo que se devuelve ------------------------------------------------

    public enum Rol { LIDER, OFICIAL, MIEMBRO;

        /** Puede invitar, echar y sacar del tesoro. */
        public boolean manda() {
            return this == LIDER || this == OFICIAL;
        }
    }

    public record Clan(long id, String nombre, String etiqueta, char color,
                       String descripcion, long liderId, long tesoro, int miembros,
                       long topeOficial) {}

    public record Miembro(long playerId, String nombre, Rol rol, long desde) {}

    public record Invitacion(long clanId, String clanNombre, String clanEtiqueta,
                             char color, String invitadoPor) {}

    /** Un movimiento del tesoro. {@code delta} con signo: + entra, − sale. */
    public record Movimiento(String quien, long delta, long saldoDespues,
                             String motivo, long cuando) {}

    /** Una línea del registro de acciones. */
    public record Anotacion(String quien, String aQuien, String accion,
                            String detalle, long cuando) {}

    /**
     * Lo que sale mal se dice, no se traga.
     *
     * <p>⚠ {@code afectados} son los {@code player_id} cuya pantalla o etiqueta
     * ha dejado de ser cierta. Incluye <b>a los que ya no están en el clan</b>:
     * al echado, al que se fue, y a todos cuando se disuelve. Ver el comentario
     * de la clase.
     */
    public record Resultado(boolean ok, String mensaje, Set<Long> afectados) {

        static Resultado si(String m, Set<Long> afectados) {
            return new Resultado(true, m, afectados);
        }

        static Resultado no(String m) {
            return new Resultado(false, m, Set.of());
        }
    }

    // ---- plomería ----------------------------------------------------------

    /** Lo que hace una acción una vez tiene la conexión y el clan bloqueado. */
    private interface Trabajo {
        Resultado hacer(Connection c) throws SQLException;
    }

    /**
     * Abre una transacción, corre el trabajo y deshace si algo falla.
     *
     * <p>⚠ El {@code finally} devuelve el {@code autoCommit} <b>siempre</b>: la
     * conexión vuelve al pool, y una que vuelva con la transacción abierta
     * envenena a quien la coja después.
     */
    private Resultado enTransaccion(Trabajo trabajo) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Resultado r = trabajo.hacer(c);
                if (r.ok()) {
                    c.commit();
                } else {
                    c.rollback();
                }
                return r;
            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                c.rollback();
                // ⚠ Se distingue del error genérico: chocar contra un índice
                //   único es el caso NORMAL --dos personas eligiendo el mismo
                //   nombre, dos invitaciones aceptadas a la vez-- y merece un
                //   mensaje que se entienda, no un «error».
                LunaEternal.LOG.debug("Choque de unicidad en un clan: {}", e.toString());
                return Resultado.no("Eso ya está cogido o ya ha pasado.");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Lee la fila del clan <b>bloqueándola</b> hasta el final de la transacción.
     *
     * <p>⚠⚠ Es lo que hace que dos acciones sobre el mismo clan no se pisen. Sin
     * esto, dos oficiales sacando del tesoro a la vez leen el mismo saldo y los
     * dos creen que les llega; y dos líderes... no, líder solo hay uno, pero un
     * traspaso y un ascenso simultáneos sí pueden dejar dos.
     *
     * <p>Bloquea <b>una fila</b>, no la tabla: los demás clanes siguen a lo suyo.
     */
    private Clan bloquear(Connection c, long clanId) throws SQLException {
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, c.description, "
                + "c.leader_id, c.treasury, "
                + "(SELECT COUNT(*) FROM clan_member m2 WHERE m2.clan_id = c.clan_id), "
                + "c.officer_daily_limit "
                + "FROM clan c WHERE c.clan_id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? leerClan(rs) : null;
            }
        }
    }

    /** El clan de alguien, dentro de una transacción ya abierta. */
    private Long clanIdDe(Connection c, long playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT clan_id FROM clan_member WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** El rol de alguien EN UN CLAN CONCRETO. {@code null} si no está en él. */
    private Rol rolEn(Connection c, long clanId, long playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT role FROM clan_member WHERE player_id = ? AND clan_id = ?")) {
            ps.setLong(1, playerId);
            ps.setLong(2, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Rol.valueOf(rs.getString(1)) : null;
            }
        }
    }

    /** Los ids de los miembros. Para saber a quién hay que refrescar. */
    private Set<Long> idsDe(Connection c, long clanId) throws SQLException {
        var salida = new LinkedHashSet<Long>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT player_id FROM clan_member WHERE clan_id = ?")) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(rs.getLong(1));
                }
            }
        }
        return salida;
    }

    /**
     * Escribe una línea en el registro. <b>Nunca falla hacia fuera.</b>
     *
     * <p>⚠ Va en la misma transacción que la acción, así que si la acción se
     * deshace la línea también: un registro que anote cosas que no pasaron es
     * peor que no tenerlo.
     */
    private void anotar(Connection c, long clanId, long actor, Long objetivo,
                        String accion, String detalle) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_log (clan_id, actor_id, target_id, action, detail) "
                        + "VALUES (?,?,?,?,?)")) {
            ps.setLong(1, clanId);
            ps.setLong(2, actor);
            if (objetivo == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, objetivo);
            }
            ps.setString(4, accion);
            ps.setString(5, detalle == null ? "" : detalle);
            ps.executeUpdate();
        }
    }

    // ---- consultas ---------------------------------------------------------

    /** El clan de un jugador, o {@code null}. */
    public Clan clanDe(long playerId) throws SQLException {
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, c.description, "
                + "c.leader_id, c.treasury, "
                + "(SELECT COUNT(*) FROM clan_member m2 WHERE m2.clan_id = c.clan_id), "
                + "c.officer_daily_limit "
                + "FROM clan c JOIN clan_member m ON m.clan_id = c.clan_id "
                + "WHERE m.player_id = ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? leerClan(rs) : null;
            }
        }
    }

    /** El rol de un jugador en su clan, o {@code null} si no tiene. */
    public Rol rolDe(long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT role FROM clan_member WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Rol.valueOf(rs.getString(1)) : null;
            }
        }
    }

    public List<Miembro> miembros(long clanId) throws SQLException {
        var salida = new ArrayList<Miembro>();
        // ⚠ Se ordena por ROL y luego por antigüedad. Alfabético pondría al
        //   líder en medio de la lista, y lo primero que se mira en un clan es
        //   quién manda.
        String sql = "SELECT m.player_id, p.username, m.role, m.joined_at "
                + "FROM clan_member m JOIN player p ON p.player_id = m.player_id "
                + "WHERE m.clan_id = ? ORDER BY m.role, m.joined_at";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Miembro(rs.getLong(1), rs.getString(2),
                            Rol.valueOf(rs.getString(3)), rs.getTimestamp(4).getTime()));
                }
            }
        }
        return salida;
    }

    /**
     * Las invitaciones vivas de un jugador.
     *
     * <p>⚠ Se filtran las caducadas <b>al leer</b> y no con una tarea que las
     * borre. Una tarea periódica es otra cosa que puede no estar corriendo; la
     * condición en el {@code WHERE} no se olvida nunca.
     */
    public List<Invitacion> invitaciones(long playerId) throws SQLException {
        var salida = new ArrayList<Invitacion>();
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, p.username "
                + "FROM clan_invite i JOIN clan c ON c.clan_id = i.clan_id "
                + "JOIN player p ON p.player_id = i.invited_by "
                + "WHERE i.player_id = ? AND i.expires_at > CURRENT_TIMESTAMP(3) "
                + "ORDER BY i.created_at DESC";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Invitacion(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4).charAt(0), rs.getString(5)));
                }
            }
        }
        return salida;
    }

    /** Los clanes que hay. Paginado: nunca {@code SELECT *}. */
    public List<Clan> listar(int limite) throws SQLException {
        var salida = new ArrayList<Clan>();
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, c.description, "
                + "c.leader_id, c.treasury, "
                + "(SELECT COUNT(*) FROM clan_member m2 WHERE m2.clan_id = c.clan_id) AS n, "
                + "c.officer_daily_limit "
                + "FROM clan c ORDER BY n DESC, c.created_at LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limite, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(leerClan(rs));
                }
            }
        }
        return salida;
    }

    /**
     * El historial del tesoro: quién metió y quién sacó.
     *
     * <p>⚠ Lo pidió el usuario, y es lo primero que se mira cuando falta
     * dinero. Se lee del <b>lado del clan</b> ({@code clan_ledger}) y no del
     * libro de asientos de cada jugador, porque ese está ordenado por jugador:
     * listar «los movimientos de este clan» obligaría a recorrerlo entero.
     */
    public List<Movimiento> historial(long clanId, int limite) throws SQLException {
        var salida = new ArrayList<Movimiento>();
        String sql = "SELECT p.username, l.delta, l.balance_after, l.reason, l.created_at "
                + "FROM clan_ledger l JOIN player p ON p.player_id = l.player_id "
                + "WHERE l.clan_id = ? ORDER BY l.entry_id DESC LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.setInt(2, Math.max(1, Math.min(limite, 200)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Movimiento(rs.getString(1), rs.getLong(2),
                            rs.getLong(3), rs.getString(4),
                            rs.getTimestamp(5).getTime()));
                }
            }
        }
        return salida;
    }

    /**
     * El registro de acciones: quién entró, quién echó a quién, quién ascendió.
     *
     * <p>⚠ {@code target_id} no lleva clave ajena (ver la migración), así que
     * el nombre puede no resolverse. Se enseña el número antes que nada.
     */
    public List<Anotacion> registro(long clanId, int limite) throws SQLException {
        var salida = new ArrayList<Anotacion>();
        String sql = "SELECT a.username, t.username, g.action, g.detail, g.created_at, "
                + "g.target_id "
                + "FROM clan_log g JOIN player a ON a.player_id = g.actor_id "
                + "LEFT JOIN player t ON t.player_id = g.target_id "
                + "WHERE g.clan_id = ? ORDER BY g.log_id DESC LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.setInt(2, Math.max(1, Math.min(limite, 200)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String objetivo = rs.getString(2);
                    long objetivoId = rs.getLong(6);
                    if (objetivo == null && !rs.wasNull() && objetivoId > 0) {
                        objetivo = "#" + objetivoId;
                    }
                    salida.add(new Anotacion(rs.getString(1),
                            objetivo == null ? "" : objetivo,
                            rs.getString(3), rs.getString(4),
                            rs.getTimestamp(5).getTime()));
                }
            }
        }
        return salida;
    }

    /** Lo que este oficial lleva sacado hoy. Para poder enseñárselo. */
    public long sacadoHoy(long clanId, long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            return sacadoHoy(c, clanId, playerId);
        }
    }

    private long sacadoHoy(Connection c, long clanId, long playerId) throws SQLException {
        // ⚠ VENTANA DESLIZANTE DE 24 h, no «desde medianoche». Con medianoche,
        //   sacar el tope a las 23:59 y otra vez a las 00:01 da el doble en dos
        //   minutos -- y eso es exactamente lo que hace quien va a vaciar la
        //   caja. Deslizante no tiene ese borde.
        String sql = "SELECT COALESCE(SUM(-delta), 0) FROM clan_ledger "
                + "WHERE clan_id = ? AND player_id = ? AND delta < 0 "
                + "AND created_at > DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.setLong(2, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static Clan leerClan(ResultSet rs) throws SQLException {
        return new Clan(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4).charAt(0), rs.getString(5), rs.getLong(6),
                rs.getLong(7), rs.getInt(8), rs.getLong(9));
    }

    // ---- fundar ------------------------------------------------------------

    /**
     * Funda un clan. <b>Cobra y crea en la misma transacción.</b>
     *
     * <p>⚠ Las dos cosas van juntas o no va ninguna. Cobrar fuera de la
     * transacción abre el hueco de siempre: se cobra, falla la creación, y el
     * jugador se queda sin 5.000 y sin clan.
     */
    public Resultado fundar(long playerId, String nombre, String etiqueta,
                            char color, String descripcion, String clave)
            throws SQLException {

        String problema = validarNombre(nombre, etiqueta);
        if (problema != null) {
            return Resultado.no(problema);
        }
        return enTransaccion(c -> {
            if (clanIdDe(c, playerId) != null) {
                return Resultado.no("Ya estás en un clan. Sal antes de fundar otro.");
            }
            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, -COSTE_FUNDAR, "clan_fundar", null, null, clave);
            } catch (net.pokereport.luna.economy.EconomyException e) {
                return Resultado.no(e.kind
                        == net.pokereport.luna.economy.EconomyException.Kind.INSUFFICIENT_FUNDS
                        ? "Fundar un clan cuesta " + String.format("%,d", COSTE_FUNDAR)
                          + " de Plata."
                        : "No se pudo fundar el clan.");
            }

            long clanId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan (name, name_lower, tag, tag_lower, color, "
                            + "description, leader_id) VALUES (?,?,?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombre);
                ps.setString(2, nombre.toLowerCase(Locale.ROOT));
                ps.setString(3, etiqueta);
                ps.setString(4, etiqueta.toLowerCase(Locale.ROOT));
                ps.setString(5, String.valueOf(color));
                ps.setString(6, descripcion == null ? "" : descripcion);
                ps.setLong(7, playerId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("El clan no devolvió identificador");
                    }
                    clanId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_member (player_id, clan_id, role) "
                            + "VALUES (?,?,'LIDER')")) {
                ps.setLong(1, playerId);
                ps.setLong(2, clanId);
                ps.executeUpdate();
            }
            anotar(c, clanId, playerId, null, "FUNDAR", nombre);
            return Resultado.si("Has fundado " + nombre + ".", Set.of(playerId));
        });
    }

    /**
     * ⚠ Las reglas del nombre están AQUÍ y no en la pantalla.
     *
     * <p>El cliente puede comprobarlas también —es mejor decirle «demasiado
     * largo» mientras escribe que después de pulsar— pero la que manda es esta.
     * Un cliente modificado puede mandar lo que quiera, y un nombre de 400
     * caracteres o con códigos de color rompe el chat de todos los demás.
     */
    private static String validarNombre(String nombre, String etiqueta) {
        if (nombre == null || nombre.isBlank()) {
            return "El clan necesita un nombre.";
        }
        if (etiqueta == null || etiqueta.isBlank()) {
            return "El clan necesita una etiqueta.";
        }
        if (nombre.length() < 3 || nombre.length() > 24) {
            return "El nombre va de 3 a 24 caracteres.";
        }
        if (etiqueta.length() < 2 || etiqueta.length() > 5) {
            return "La etiqueta va de 2 a 5 caracteres.";
        }
        // ⚠ SOLO LETRAS, NUMEROS Y ESPACIOS. El § es lo que importa: con un
        //   código de color dentro, la etiqueta pintaría el resto de la línea
        //   del chat de todo el mundo.
        if (!nombre.matches("[\\p{L}\\p{N} ]+")) {
            return "El nombre solo admite letras, números y espacios.";
        }
        if (!etiqueta.matches("[\\p{L}\\p{N}]+")) {
            return "La etiqueta solo admite letras y números.";
        }
        return null;
    }

    // ---- entrar y salir ----------------------------------------------------

    /** Invita a alguien. Solo líder y oficiales. */
    public Resultado invitar(long quienInvita, long invitado) throws SQLException {
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, quienInvita);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            Rol rol = rolEn(c, clanId, quienInvita);
            if (clan == null || rol == null || !rol.manda()) {
                return Resultado.no("Solo el líder y los oficiales pueden invitar.");
            }
            if (quienInvita == invitado) {
                return Resultado.no("Ya estás dentro.");
            }
            if (clanIdDe(c, invitado) != null) {
                return Resultado.no("Esa persona ya está en un clan.");
            }
            if (clan.miembros() >= MAX_MIEMBROS) {
                return Resultado.no("El clan está lleno (" + MAX_MIEMBROS + ").");
            }
            // ⚠ REPLACE y no INSERT: invitar dos veces renueva la caducidad en
            //   vez de fallar. Volver a invitar a quien no contestó es lo
            //   normal, no un error.
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO clan_invite (clan_id, player_id, invited_by, expires_at) "
                            + "VALUES (?,?,?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? DAY))")) {
                ps.setLong(1, clanId);
                ps.setLong(2, invitado);
                ps.setLong(3, quienInvita);
                ps.setInt(4, DIAS_INVITACION);
                ps.executeUpdate();
            }
            anotar(c, clanId, quienInvita, invitado, "INVITAR", "");
            // ⚠ EL INVITADO ENTRA EN LA LISTA DE AFECTADOS AUNQUE NO SEA
            //   MIEMBRO. Es el que tiene que ver aparecer la invitación, y es
            //   justo el que se quedaría fuera si la lista saliera del clan.
            var afectados = idsDe(c, clanId);
            afectados.add(invitado);
            return Resultado.si("Invitación enviada.", afectados);
        });
    }

    /**
     * Acepta una invitación.
     *
     * <p>⚠ El {@code INSERT} en {@code clan_member} es lo que decide, no la
     * comprobación previa: si dos invitaciones se aceptan a la vez, la segunda
     * choca contra la clave primaria y no entra.
     */
    public Resultado aceptar(long playerId, long clanId) throws SQLException {
        return enTransaccion(c -> {
            Clan clan = bloquear(c, clanId);
            if (clan == null) {
                return Resultado.no("Ese clan ya no existe.");
            }
            boolean valida;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM clan_invite WHERE clan_id = ? AND player_id = ? "
                            + "AND expires_at > CURRENT_TIMESTAMP(3)")) {
                ps.setLong(1, clanId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    valida = rs.next();
                }
            }
            if (!valida) {
                return Resultado.no("Esa invitación ya no vale.");
            }
            // ⚠ SE VUELVE A MIRAR EL AFORO CON EL CLAN BLOQUEADO. Sin esto, 30
            //   invitados aceptando a la vez entran los 30: cada uno leyó 29.
            if (clan.miembros() >= MAX_MIEMBROS) {
                return Resultado.no("El clan está lleno (" + MAX_MIEMBROS + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO clan_member (player_id, clan_id) VALUES (?,?)")) {
                ps.setLong(1, playerId);
                ps.setLong(2, clanId);
                ps.executeUpdate();
            }
            // Al entrar en uno, las demás invitaciones sobran.
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM clan_invite WHERE player_id = ?")) {
                ps.setLong(1, playerId);
                ps.executeUpdate();
            }
            anotar(c, clanId, playerId, null, "ENTRAR", "");
            return Resultado.si("Has entrado en " + clan.nombre() + ".",
                    idsDe(c, clanId));
        });
    }

    public Resultado rechazar(long playerId, long clanId) throws SQLException {
        return enTransaccion(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM clan_invite WHERE clan_id = ? AND player_id = ?")) {
                ps.setLong(1, clanId);
                ps.setLong(2, playerId);
                ps.executeUpdate();
            }
            // Solo se refresca él: nadie más ve sus invitaciones.
            return Resultado.si("Invitación rechazada.", Set.of(playerId));
        });
    }

    /**
     * Salir del clan.
     *
     * <p>⚠⚠ <b>EL LÍDER NO PUEDE SALIR SIN MÁS.</b> Un clan sin líder no lo puede
     * dirigir nadie: no se puede invitar, ni echar, ni sacar del tesoro, y no hay
     * forma de arreglarlo desde dentro.
     */
    public Resultado salir(long playerId) throws SQLException {
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, playerId);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null) {
                return Resultado.no("Ese clan ya no existe.");
            }
            if (clan.liderId() == playerId) {
                return clan.miembros() > 1
                        ? Resultado.no("Eres el líder. Pasa el mando antes de salir.")
                        : Resultado.no("Eres el único miembro. Disuelve el clan.");
            }
            // ⚠ LOS AFECTADOS SE LEEN ANTES DE BORRAR. Después, el que se va ya
            //   no está en la lista -- y es el que más necesita enterarse.
            var afectados = idsDe(c, clanId);
            borrarMiembro(c, clanId, playerId);
            anotar(c, clanId, playerId, null, "SALIR", "");
            return Resultado.si("Has salido de " + clan.nombre() + ".", afectados);
        });
    }

    /** Echar a alguien. Un oficial no puede echar a otro oficial ni al líder. */
    public Resultado echar(long quien, long echado) throws SQLException {
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, quien);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            Rol mio = rolEn(c, clanId, quien);
            if (clan == null || mio == null || !mio.manda()) {
                return Resultado.no("No puedes echar a nadie.");
            }
            // ⚠ EL ROL SE PIDE PARA ESTE CLAN, no «el rol de esa persona». Así,
            //   si el objetivo se cambió de clan entre la comprobación y ahora,
            //   sale null en vez de dejarnos borrar su fila en OTRO clan.
            Rol suyo = rolEn(c, clanId, echado);
            if (suyo == null) {
                return Resultado.no("Esa persona no está en tu clan.");
            }
            if (echado == clan.liderId()) {
                return Resultado.no("No se puede echar al líder.");
            }
            if (mio == Rol.OFICIAL && suyo == Rol.OFICIAL) {
                return Resultado.no("Un oficial no puede echar a otro oficial.");
            }
            var afectados = idsDe(c, clanId);
            borrarMiembro(c, clanId, echado);
            anotar(c, clanId, quien, echado, "ECHAR", "");
            return Resultado.si("Fuera del clan.", afectados);
        });
    }

    /**
     * ⚠⚠ EL {@code AND clan_id = ?} NO SOBRA, aunque {@code player_id} sea la
     * clave primaria. Precisamente porque lo es, la fila puede haber cambiado de
     * clan entre que se comprobó y que se borra: sin la guarda, echar a alguien
     * de tu clan le echaría del clan <b>nuevo</b> al que acaba de entrar.
     */
    private void borrarMiembro(Connection c, long clanId, long playerId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM clan_member WHERE player_id = ? AND clan_id = ?")) {
            ps.setLong(1, playerId);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    // ---- mandar ------------------------------------------------------------

    /** Cambia el rol de alguien. Solo el líder, y no puede tocarse a sí mismo. */
    public Resultado cambiarRol(long lider, long objetivo, Rol nuevo) throws SQLException {
        // No delega en `traspasar`, lo RECHAZA. Y la diferencia importa.
        //
        // Delegar era seguro --`traspasar` es transaccional y baja al anterior--
        // pero convertia «cambiar el rango de alguien» en «regalarle el clan»
        // sin que nadie lo pidiera. Lo cazo el autotest en su primera ejecucion.
        if (nuevo == Rol.LIDER) {
            return Resultado.no("Para nombrar a otro líder, pasa el mando.");
        }
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, lider);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null || clan.liderId() != lider) {
                return Resultado.no("Solo el líder cambia los rangos.");
            }
            if (objetivo == lider) {
                return Resultado.no("Para dejar de ser líder, pasa el mando.");
            }
            Rol antes = rolEn(c, clanId, objetivo);
            if (antes == null) {
                return Resultado.no("Esa persona no está en tu clan.");
            }
            if (antes == nuevo) {
                return Resultado.no("Ya tiene ese rango.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clan_member SET role = ? WHERE player_id = ? AND clan_id = ?")) {
                ps.setString(1, nuevo.name());
                ps.setLong(2, objetivo);
                ps.setLong(3, clanId);
                ps.executeUpdate();
            }
            anotar(c, clanId, lider, objetivo,
                    nuevo == Rol.OFICIAL ? "ASCENDER" : "DEGRADAR", nuevo.name());
            return Resultado.si("Rango cambiado.", idsDe(c, clanId));
        });
    }

    /**
     * Pasar el mando.
     *
     * <p>⚠⚠ <b>SE COMPRUEBA QUE EL NUEVO ESTÉ EN EL CLAN.</b> La primera versión
     * no lo hacía, y era el fallo más grave que ha tenido este sistema: subía
     * {@code role='LIDER'} a un {@code player_id} cualquiera. Con alguien de
     * otro clan, le convertía en líder <b>del suyo</b> y dejaba este con un
     * líder que no es miembro — o sea, sin nadie que pueda dirigirlo y sin forma
     * de arreglarlo desde dentro. La pantalla no expone traspasar todavía, pero
     * el manejador de red sí: un cliente modificado llegaba.
     */
    public Resultado traspasar(long lider, long nuevo) throws SQLException {
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, lider);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null || clan.liderId() != lider) {
                return Resultado.no("No eres el líder de ese clan.");
            }
            if (nuevo == lider) {
                return Resultado.no("Ya eres el líder.");
            }
            if (rolEn(c, clanId, nuevo) == null) {
                return Resultado.no("Esa persona no está en tu clan.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clan SET leader_id = ? WHERE clan_id = ? AND leader_id = ?")) {
                ps.setLong(1, nuevo);
                ps.setLong(2, clanId);
                ps.setLong(3, lider);
                if (ps.executeUpdate() == 0) {
                    return Resultado.no("No eres el líder de ese clan.");
                }
            }
            ponerRol(c, clanId, nuevo, Rol.LIDER);
            // ⚠ El anterior baja a OFICIAL, no se queda LIDER ni se va. Si se
            //   quedara habría dos líderes; si se fuera, pasar el mando sería
            //   también irse del clan, que no es lo que nadie pide.
            ponerRol(c, clanId, lider, Rol.OFICIAL);
            anotar(c, clanId, lider, nuevo, "TRASPASAR", "");
            return Resultado.si("Mando traspasado.", idsDe(c, clanId));
        });
    }

    private void ponerRol(Connection c, long clanId, long playerId, Rol rol)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clan_member SET role = ? WHERE player_id = ? AND clan_id = ?")) {
            ps.setString(1, rol.name());
            ps.setLong(2, playerId);
            ps.setLong(3, clanId);
            ps.executeUpdate();
        }
    }

    /**
     * Disolver.
     *
     * <p>⚠⚠ <b>EL TESORO SE PIERDE, y hay que decirlo antes.</b> Repartirlo
     * automáticamente sería peor: no hay una forma justa de repartir —¿a partes
     * iguales? ¿por antigüedad?— y elegir una mal reparte dinero de verdad. Que
     * el líder lo saque antes es explícito y es suyo.
     */
    public Resultado disolver(long lider) throws SQLException {
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, lider);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null || clan.liderId() != lider) {
                return Resultado.no("Solo el líder disuelve el clan.");
            }
            // ⚠ TODOS los miembros, leídos ANTES de borrar. Después no queda
            //   ninguno --CASCADE-- y nadie se enteraría de que su clan ya no
            //   existe hasta reconectar.
            var afectados = idsDe(c, clanId);
            if (clan.tesoro() > 0) {
                // Queda escrito que ese dinero desapareció y con cuánto. Sin
                // esta línea, el historial acaba en «alguien aportó 40.000» y
                // no dice a dónde fue.
                anotar(c, clanId, lider, null, "DISOLVER",
                        "tesoro perdido: " + clan.tesoro());
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM clan WHERE clan_id = ?")) {
                // Miembros, invitaciones, historial y registro caen con él.
                ps.setLong(1, clanId);
                ps.executeUpdate();
            }
            return Resultado.si(clan.nombre() + " se ha disuelto.", afectados);
        });
    }

    // ---- el tesoro ---------------------------------------------------------

    /**
     * Aportar al tesoro.
     *
     * <p>⚠ Cobrar al jugador y sumar al clan van en la MISMA transacción, igual
     * que una transferencia entre jugadores. Si solo pasara una, el dinero se
     * habría creado o destruido.
     */
    public Resultado aportar(long playerId, long cantidad, String clave)
            throws SQLException {
        if (cantidad <= 0) {
            return Resultado.no("La cantidad tiene que ser positiva.");
        }
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, playerId);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null) {
                return Resultado.no("Ese clan ya no existe.");
            }
            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, -cantidad, "clan_aportar",
                        "clan", clanId, clave);
            } catch (net.pokereport.luna.economy.EconomyException e) {
                return Resultado.no(e.kind
                        == net.pokereport.luna.economy.EconomyException.Kind.INSUFFICIENT_FUNDS
                        ? "No tienes tanta Plata."
                        : "No se pudo aportar.");
            }
            moverTesoro(c, clanId, playerId, cantidad, clan.tesoro() + cantidad,
                    "aportar");
            return Resultado.si("Has aportado " + String.format("%,d", cantidad)
                    + " al tesoro.", idsDe(c, clanId));
        });
    }

    /**
     * Sacar del tesoro. Solo líder y oficiales, y los oficiales <b>con tope</b>.
     *
     * <p>⚠⚠ <b>EL TOPE ES LA PIEZA DE SEGURIDAD.</b> Sin él, ascender a alguien
     * a oficial es darle la caja entera: un registro documenta el robo, pero un
     * tope lo impide. Se comprueba <b>dentro de la transacción y con el clan
     * bloqueado</b>, porque si no, dos retiradas simultáneas leen el mismo
     * «llevas sacado hoy» y las dos pasan.
     */
    public Resultado sacar(long playerId, long cantidad, String clave)
            throws SQLException {
        if (cantidad <= 0) {
            return Resultado.no("La cantidad tiene que ser positiva.");
        }
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, playerId);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            Rol rol = rolEn(c, clanId, playerId);
            if (clan == null || rol == null || !rol.manda()) {
                return Resultado.no("Solo el líder y los oficiales pueden sacar.");
            }
            if (clan.tesoro() < cantidad) {
                return Resultado.no("El tesoro no llega a "
                        + String.format("%,d", cantidad) + ".");
            }
            if (rol == Rol.OFICIAL) {
                long yaHoy = sacadoHoy(c, clanId, playerId);
                long libre = clan.topeOficial() - yaHoy;
                if (cantidad > libre) {
                    return Resultado.no(libre <= 0
                            ? "Has llegado a tu tope diario ("
                              + String.format("%,d", clan.topeOficial()) + ")."
                            : "Tu tope diario te deja sacar "
                              + String.format("%,d", libre) + " más.");
                }
            }
            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, cantidad, "clan_sacar",
                        "clan", clanId, clave);
            } catch (net.pokereport.luna.economy.EconomyException e) {
                return Resultado.no("No se pudo sacar.");
            }
            moverTesoro(c, clanId, playerId, -cantidad, clan.tesoro() - cantidad,
                    "sacar");
            return Resultado.si("Has sacado " + String.format("%,d", cantidad)
                    + " del tesoro.", idsDe(c, clanId));
        });
    }

    /**
     * Mueve el tesoro y deja la línea del historial. <b>Las dos cosas juntas.</b>
     *
     * <p>⚠ El {@code UPDATE} lleva {@code treasury = treasury + ?} y no
     * {@code = <valor calculado>}. Con el valor calculado, dos escrituras
     * simultáneas se pisan: la segunda escribe un total que ya no era cierto. El
     * bloqueo de {@link #bloquear} ya lo impide, y aun así se escribe relativo —
     * es gratis y no depende de que nadie se acuerde del bloqueo.
     *
     * <p>El {@code CHECK (treasury >= 0)} de la tabla es la última red.
     */
    private void moverTesoro(Connection c, long clanId, long playerId,
                             long delta, long saldoDespues, String motivo)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clan SET treasury = treasury + ? WHERE clan_id = ?")) {
            ps.setLong(1, delta);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO clan_ledger (clan_id, player_id, delta, balance_after, "
                        + "reason) VALUES (?,?,?,?,?)")) {
            ps.setLong(1, clanId);
            ps.setLong(2, playerId);
            ps.setLong(3, delta);
            ps.setLong(4, saldoDespues);
            ps.setString(5, motivo);
            ps.executeUpdate();
        }
    }

    /**
     * Cambia el tope diario de los oficiales. Solo el líder.
     *
     * <p>⚠ <b>0 significa «no pueden sacar nada»</b>, no «sin límite». Sin
     * límite se escribe con un número grande. Que el 0 quisiera decir infinito
     * es como se acaba con la caja vacía por teclear mal una cifra.
     */
    public Resultado cambiarTope(long lider, long tope) throws SQLException {
        if (tope < 0 || tope > TOPE_MAXIMO) {
            return Resultado.no("El tope va de 0 a "
                    + String.format("%,d", TOPE_MAXIMO) + ".");
        }
        return enTransaccion(c -> {
            Long clanId = clanIdDe(c, lider);
            if (clanId == null) {
                return Resultado.no("No estás en ningún clan.");
            }
            Clan clan = bloquear(c, clanId);
            if (clan == null || clan.liderId() != lider) {
                return Resultado.no("Solo el líder cambia el tope.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE clan SET officer_daily_limit = ? WHERE clan_id = ?")) {
                ps.setLong(1, tope);
                ps.setLong(2, clanId);
                ps.executeUpdate();
            }
            anotar(c, clanId, lider, null, "TOPE", String.valueOf(tope));
            return Resultado.si("Tope de los oficiales: "
                    + String.format("%,d", tope) + ".", idsDe(c, clanId));
        });
    }
}
