package net.pokereport.luna.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * <p>Las tres podrían comprobarse antes en Java, y ninguna se sostendría sola:
 * entre la comprobación y la escritura cabe otra petición.
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
     * <p><b>Sin calibrar</b>, como toda la economía. Referencias para juzgarlo:
     * la tienda va de 8 a 3.000, y exprimir los tres oficios da 12.300.
     */
    public static final long COSTE_FUNDAR = 5_000;

    /** Días que dura una invitación sin contestar. Ver la migración. */
    public static final int DIAS_INVITACION = 7;

    /** Tope de miembros. Un clan que no cabe en la pantalla no se puede dirigir. */
    public static final int MAX_MIEMBROS = 30;

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
                       String descripcion, long liderId, long tesoro, int miembros) {}

    public record Miembro(long playerId, String nombre, Rol rol, long desde) {}

    public record Invitacion(long clanId, String clanNombre, String clanEtiqueta,
                             char color, String invitadoPor) {}

    /** Lo que sale mal se dice, no se traga. El mensaje va al jugador tal cual. */
    public record Resultado(boolean ok, String mensaje) {
        static Resultado si() {
            return new Resultado(true, "");
        }

        static Resultado si(String m) {
            return new Resultado(true, m);
        }

        static Resultado no(String m) {
            return new Resultado(false, m);
        }
    }

    // ---- consultas ---------------------------------------------------------

    /** El clan de un jugador, o {@code null}. */
    public Clan clanDe(long playerId) throws SQLException {
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, c.description, "
                + "c.leader_id, c.treasury, "
                + "(SELECT COUNT(*) FROM clan_member m2 WHERE m2.clan_id = c.clan_id) "
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
     * condición en el {@code WHERE} no se olvida nunca. Las filas viejas se
     * limpian al aceptar o rechazar, y mientras tanto no molestan.
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

    /** Los clanes que hay, para poder pedir entrar. Paginado: nunca `SELECT *`. */
    public List<Clan> listar(int limite) throws SQLException {
        var salida = new ArrayList<Clan>();
        String sql = "SELECT c.clan_id, c.name, c.tag, c.color, c.description, "
                + "c.leader_id, c.treasury, "
                + "(SELECT COUNT(*) FROM clan_member m2 WHERE m2.clan_id = c.clan_id) AS n "
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

    private static Clan leerClan(ResultSet rs) throws SQLException {
        return new Clan(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4).charAt(0), rs.getString(5), rs.getLong(6),
                rs.getLong(7), rs.getInt(8));
    }

    // ---- acciones ----------------------------------------------------------

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
        if (clanDe(playerId) != null) {
            return Resultado.no("Ya estás en un clan. Sal antes de fundar otro.");
        }

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // El cobro comparte Connection con la creación: es lo que hace
                // que sean atómicas. `applyInTransaction` es la vía del proyecto.
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, -COSTE_FUNDAR, "clan_fundar", null, null, clave);

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

                c.commit();
                return Resultado.si("Has fundado " + nombre + ".");

            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                c.rollback();
                // ⚠ Se distingue del error genérico: el choque contra el índice
                //   único es el caso NORMAL --dos personas eligiendo el mismo
                //   nombre-- y merece un mensaje que se entienda, no un «error».
                return Resultado.no("Ese nombre o esa etiqueta ya están cogidos.");
            } catch (net.pokereport.luna.economy.EconomyException e) {
                c.rollback();
                return Resultado.no(e.kind
                        == net.pokereport.luna.economy.EconomyException.Kind.INSUFFICIENT_FUNDS
                        ? "Fundar un clan cuesta " + String.format("%,d", COSTE_FUNDAR)
                          + " de Plata."
                        : "No se pudo fundar el clan.");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
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

    /** Invita a alguien. Solo líder y oficiales. */
    public Resultado invitar(long quienInvita, long invitado) throws SQLException {
        Clan clan = clanDe(quienInvita);
        if (clan == null) {
            return Resultado.no("No estás en ningún clan.");
        }
        Rol rol = rolDe(quienInvita);
        if (rol == null || !rol.manda()) {
            return Resultado.no("Solo el líder y los oficiales pueden invitar.");
        }
        if (quienInvita == invitado) {
            return Resultado.no("Ya estás dentro.");
        }
        if (clanDe(invitado) != null) {
            return Resultado.no("Esa persona ya está en un clan.");
        }
        if (clan.miembros() >= MAX_MIEMBROS) {
            return Resultado.no("El clan está lleno (" + MAX_MIEMBROS + ").");
        }
        // ⚠ REPLACE y no INSERT: invitar dos veces renueva la caducidad en vez
        //   de fallar. Volver a invitar a alguien que no contestó es lo normal,
        //   no un error.
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO clan_invite (clan_id, player_id, invited_by, expires_at) "
                             + "VALUES (?,?,?, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? DAY))")) {
            ps.setLong(1, clan.id());
            ps.setLong(2, invitado);
            ps.setLong(3, quienInvita);
            ps.setInt(4, DIAS_INVITACION);
            ps.executeUpdate();
        }
        return Resultado.si("Invitación enviada.");
    }

    /**
     * Acepta una invitación.
     *
     * <p>⚠ El {@code INSERT} en {@code clan_member} es lo que decide, no la
     * comprobación previa: si dos invitaciones se aceptan a la vez, la segunda
     * choca contra la clave primaria y no entra. La comprobación solo sirve para
     * dar un mensaje mejor en el caso normal.
     */
    public Resultado aceptar(long playerId, long clanId) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
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
                    c.rollback();
                    return Resultado.no("Esa invitación ya no vale.");
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
                c.commit();
                return Resultado.si("Has entrado en el clan.");

            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                c.rollback();
                return Resultado.no("Ya estás en un clan.");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public Resultado rechazar(long playerId, long clanId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM clan_invite WHERE clan_id = ? AND player_id = ?")) {
            ps.setLong(1, clanId);
            ps.setLong(2, playerId);
            ps.executeUpdate();
        }
        return Resultado.si("Invitación rechazada.");
    }

    /**
     * Salir del clan.
     *
     * <p>⚠⚠ <b>EL LÍDER NO PUEDE SALIR SIN MÁS.</b> Un clan sin líder no lo puede
     * dirigir nadie: no se puede invitar, ni echar, ni sacar del tesoro, y no hay
     * forma de arreglarlo desde dentro. Antes tiene que pasar el mando o
     * disolverlo — las dos son decisiones suyas, y ninguna se puede tomar por él.
     */
    public Resultado salir(long playerId) throws SQLException {
        Clan clan = clanDe(playerId);
        if (clan == null) {
            return Resultado.no("No estás en ningún clan.");
        }
        if (clan.liderId() == playerId) {
            return clan.miembros() > 1
                    ? Resultado.no("Eres el líder. Pasa el mando antes de salir.")
                    : Resultado.no("Eres el único miembro. Disuelve el clan.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM clan_member WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }
        return Resultado.si("Has salido de " + clan.nombre() + ".");
    }

    /** Echar a alguien. Un oficial no puede echar a otro oficial ni al líder. */
    public Resultado echar(long quien, long echado) throws SQLException {
        Clan clan = clanDe(quien);
        Rol mio = rolDe(quien);
        if (clan == null || mio == null || !mio.manda()) {
            return Resultado.no("No puedes echar a nadie.");
        }
        Clan suyo = clanDe(echado);
        if (suyo == null || suyo.id() != clan.id()) {
            return Resultado.no("Esa persona no está en tu clan.");
        }
        if (echado == clan.liderId()) {
            return Resultado.no("No se puede echar al líder.");
        }
        Rol suyoRol = rolDe(echado);
        if (mio == Rol.OFICIAL && suyoRol == Rol.OFICIAL) {
            return Resultado.no("Un oficial no puede echar a otro oficial.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM clan_member WHERE player_id = ?")) {
            ps.setLong(1, echado);
            ps.executeUpdate();
        }
        return Resultado.si("Fuera del clan.");
    }

    /** Cambia el rol de alguien. Solo el líder, y no puede tocarse a sí mismo. */
    public Resultado cambiarRol(long lider, long objetivo, Rol nuevo) throws SQLException {
        Clan clan = clanDe(lider);
        if (clan == null || clan.liderId() != lider) {
            return Resultado.no("Solo el líder cambia los rangos.");
        }
        if (objetivo == lider) {
            return Resultado.no("Para dejar de ser líder, pasa el mando.");
        }
        Clan suyo = clanDe(objetivo);
        if (suyo == null || suyo.id() != clan.id()) {
            return Resultado.no("Esa persona no está en tu clan.");
        }
        // No delega en `traspasar`, lo RECHAZA. Y la diferencia importa.
        //
        // Delegar era seguro --`traspasar` es transaccional y baja al anterior--
        // pero convertia «cambiar el rango de alguien» en «regalarle el clan»
        // sin que nadie lo pidiera. El dia que una pantalla nueva, un comando o
        // una migracion pasen LIDER creyendo que asciende a un segundo mando, el
        // lider se queda sin clan y no hay forma de deshacerlo desde dentro.
        //
        // Pasar el mando es una decision aparte y tiene su propio metodo.
        if (nuevo == Rol.LIDER) {
            return Resultado.no("Para nombrar a otro líder, pasa el mando.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE clan_member SET role = ? WHERE player_id = ?")) {
            ps.setString(1, nuevo.name());
            ps.setLong(2, objetivo);
            ps.executeUpdate();
        }
        return Resultado.si("Rango cambiado.");
    }

    /**
     * Pasar el mando.
     *
     * <p>⚠ Las dos escrituras van en una transacción: si solo entrara una, el
     * clan quedaría con dos líderes o con ninguno. Y «ninguno» no se puede
     * arreglar desde dentro.
     */
    public Resultado traspasar(long lider, long nuevo) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan SET leader_id = ? WHERE leader_id = ? "
                                + "AND clan_id = (SELECT clan_id FROM clan_member "
                                + "WHERE player_id = ?)")) {
                    ps.setLong(1, nuevo);
                    ps.setLong(2, lider);
                    ps.setLong(3, lider);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return Resultado.no("No eres el líder de ese clan.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan_member SET role = 'LIDER' WHERE player_id = ?")) {
                    ps.setLong(1, nuevo);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan_member SET role = 'OFICIAL' WHERE player_id = ?")) {
                    ps.setLong(1, lider);
                    ps.executeUpdate();
                }
                c.commit();
                return Resultado.si("Mando traspasado.");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
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
        Clan clan = clanDe(lider);
        if (clan == null || clan.liderId() != lider) {
            return Resultado.no("Solo el líder disuelve el clan.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM clan WHERE clan_id = ?")) {
            // Los miembros y las invitaciones caen solos: ON DELETE CASCADE.
            ps.setLong(1, clan.id());
            ps.executeUpdate();
        }
        return Resultado.si(clan.nombre() + " se ha disuelto.");
    }

    // ---- el tesoro ---------------------------------------------------------

    /**
     * Aportar al tesoro.
     *
     * <p>⚠ Cobrar al jugador y sumar al clan van en la MISMA transacción, igual
     * que una transferencia entre jugadores. Si solo pasara una, el dinero se
     * habría creado o destruido — y {@code /luna economia} lo cazaría, pero
     * después de que alguien lo perdiera.
     */
    public Resultado aportar(long playerId, long cantidad, String clave) throws SQLException {
        if (cantidad <= 0) {
            return Resultado.no("La cantidad tiene que ser positiva.");
        }
        Clan clan = clanDe(playerId);
        if (clan == null) {
            return Resultado.no("No estás en ningún clan.");
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, -cantidad, "clan_aportar", null, null, clave);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan SET treasury = treasury + ? WHERE clan_id = ?")) {
                    ps.setLong(1, cantidad);
                    ps.setLong(2, clan.id());
                    ps.executeUpdate();
                }
                c.commit();
                return Resultado.si("Has aportado " + String.format("%,d", cantidad) + ".");
            } catch (net.pokereport.luna.economy.EconomyException e) {
                c.rollback();
                return Resultado.no("No tienes suficiente Plata.");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Sacar del tesoro. Solo líder y oficiales.
     *
     * <p>⚠ El {@code UPDATE} lleva la condición {@code treasury >= ?}: es lo que
     * impide sacar más de lo que hay <b>aunque dos oficiales lo intenten a la
     * vez</b>. Comprobar antes y restar después deja un hueco entre las dos.
     */
    public Resultado sacar(long playerId, long cantidad, String clave) throws SQLException {
        if (cantidad <= 0) {
            return Resultado.no("La cantidad tiene que ser positiva.");
        }
        Clan clan = clanDe(playerId);
        Rol rol = rolDe(playerId);
        if (clan == null || rol == null || !rol.manda()) {
            return Resultado.no("Solo el líder y los oficiales sacan del tesoro.");
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                int filas;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clan SET treasury = treasury - ? "
                                + "WHERE clan_id = ? AND treasury >= ?")) {
                    ps.setLong(1, cantidad);
                    ps.setLong(2, clan.id());
                    ps.setLong(3, cantidad);
                    filas = ps.executeUpdate();
                }
                if (filas == 0) {
                    c.rollback();
                    return Resultado.no("El tesoro no tiene tanto.");
                }
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, cantidad, "clan_sacar", null, null, clave);
                c.commit();
                return Resultado.si("Has sacado " + String.format("%,d", cantidad) + ".");
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
