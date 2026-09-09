package net.pokereport.luna.santuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.ui.Tablist.Rank;

/**
 * Los nichos de Monumentos: alquilar, comprar, caducar y honrar.
 *
 * <p>Reglas que fijo el usuario (2026-09-04):</p>
 * <ul>
 *   <li><b>alquiler</b>: 24 h por {@link #PRECIO_ALQUILER} de Plata;</li>
 *   <li><b>compra</b>: permanente por {@link #PRECIO_PERMANENTE} LunaCoins;</li>
 *   <li><b>tope</b>: un nicho por jugador; desde CAMPEON, mas;</li>
 *   <li><b>honores</b>: 10 por jugador, por nicho, cada 24 h, y un total que
 *       se acumula mientras el memorial vive.</li>
 * </ul>
 *
 * <h2>⚠⚠ EL ORDEN DE LAS OPERACIONES ES LA REGLA, Y ES EL DE LA TIENDA</h2>
 *
 * El dinero vive en la base y el nicho en el mundo, asi que no puede haber una
 * transaccion de verdad entre los dos. Lo que si puede haber es un orden en el
 * que ningun fallo deje al jugador peor que al empezar: se mira el estado y se
 * cobra <b>en una sola transaccion</b>, y solo despues --ya en el hilo del
 * servidor-- se toca lo del mundo (el holograma). Si tocar el mundo falla, se
 * deshace el cobro. Misma leccion que {@code CartasService.abrir}.
 *
 * <h2>⚠⚠ MIRAR Y MARCAR VAN JUNTOS, COMO EN LOS RELOJES DE CARTAS</h2>
 *
 * El cobro del alquiler y la escritura del nuevo dueño van en la misma
 * transaccion con {@code FOR UPDATE}. Separados, dos clics rapidos leen los dos
 * «libre» antes de que ninguno escriba, y un nicho acaba con dos dueños -- o un
 * jugador pagando dos veces por el mismo. No daria ningun error.
 */
public final class SantuarioService {

    private final Database db;

    public SantuarioService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ los numeros

    /**
     * Lo que cuesta alquilar 24 h.
     *
     * <p>⚠ PROVISIONAL, orden del usuario: 5.000 de Plata. Como en la tienda y
     * en los sobres, los precios se fijan cuando haya analisis de economia; por
     * eso estan aqui, juntos y en un solo sitio.
     */
    public static final long PRECIO_ALQUILER = 5_000L;

    /**
     * Lo que cuesta la compra permanente.
     *
     * <p>⚠ PROVISIONAL: 300 LunaCoins. El usuario fijo la MONEDA --LunaCoins,
     * no Plata-- y dejo el importe sin fijar, como el resto de precios del
     * proyecto. Es identidad pura (T1, como los peluches y los cosmeticos de
     * D-039), asi que la moneda premium es la correcta; el numero se ajusta con
     * el analisis general de economia.
     */
    public static final long PRECIO_PERMANENTE = 300L;

    /** Cuanto dura un alquiler: 24 h. */
    public static final long ALQUILER_MS = 24L * 3_600_000L;

    /**
     * El presupuesto diario de honores por jugador y por nicho.
     *
     * <p>⚠⚠ <b>ERA 10 Y HOY ES 1</b> (decision del usuario, 2026-09-08). Y no es
     * tacañeria: con diez, el numero del memorial dejaba de decir «a cuanta
     * gente le importa» y pasaba a decir «cuantas veces ha vuelto a pulsar el
     * mismo». Con uno, <b>honores == personas</b>, que es lo unico que ese
     * contador puede significar para que valga algo.
     *
     * <p>⚠ Lo que NO cambia es que honrar sigue sin dar nada (§3.3): sin
     * recompensa no hay incentivo de multicuenta (B-004), y bajar el tope a 1
     * lo refuerza en vez de sustituirlo.
     */
    public static final int HONORES_DIA = 1;

    /** La ventana de honores: 24 h desde el primer honor del ciclo. */
    public static final long VENTANA_HONOR_MS = 24L * 3_600_000L;

    /** Lo mas largo que puede ser un titulo de memorial. */
    public static final int TITULO_MAX = 32;

    /** Lo mas largo que puede ser una descripcion de memorial. */
    public static final int DESCRIPCION_MAX = 320;

    /**
     * BORRA LOS HONORES DE UN NICHO. Se llama al cambiar la foto.
     *
     * <h2>⚠⚠ CAMBIAR LA FOTO REINICIA LOS HONORES (decision del usuario)</h2>
     *
     * Y tiene sentido de sobra: <b>la gente honra lo que ve</b>. Un memorial con
     * ochocientos honores que cambia de foto se quedaria con ochocientos honores
     * de una foto que ya no esta -- el numero seguiria ahi diciendo algo que
     * dejo de ser verdad. Ademas cierra el hueco obvio: reunir honores con una
     * foto y cambiarla despues por otra cosa.
     *
     * <p>⚠ Se borran <b>las dos</b> cosas: el total de la columna y las filas de
     * {@code santuario_honor_click}. El autotest comprueba que el total iguala a
     * la suma de clics mientras el nicho esta reclamado, asi que borrar solo una
     * lo pondria rojo -- y con razon, porque seria el contador mintiendo.
     *
     * <p>⚠⚠ Lo que NO se toca es {@code santuario_honor}, que es el presupuesto
     * DIARIO de quien honra. Borrarlo tambien dejaria que la misma gente
     * volviera a honrar hoy: cambiar la foto seria una forma de pedir honores
     * otra vez a los mismos, el mismo dia.
     */
    private static void reiniciarHonores(Connection c, String nichoId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM santuario_honor_click WHERE nicho_id = ?")) {
            ps.setString(1, nichoId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE santuario SET honores = 0 WHERE nicho_id = ?")) {
            ps.setString(1, nichoId);
            ps.executeUpdate();
        }
    }

    /**
     * Cuantos nichos puede reclamar un jugador segun su escalon.
     *
     * <p>⚠⚠ SE COMPARA CONTRA {@link Rank#escalon}, NO CONTRA UN NUMERO ESCRITO
     * AQUI. Escrito a mano seria una segunda lista de rangos: el dia que un
     * rango nuevo entre en medio, esta quedaria mintiendo SIN NINGUN ERROR. Es
     * la leccion del switch por indice de {@code KitsScreen}.
     */
    public static int tope(int escalon) {
        return escalon >= Rank.CAMPEON.escalon ? TOPE_NICHOS : 1;
    }

    /**
     * Cuantos nichos como maximo, por alto que sea el rango.
     *
     * <p>⚠⚠⚠ <b>ERA 99, QUE ES NO TENER TOPE</b> (decision del usuario,
     * 2026-09-08: «que maximo puedan comprar 3 nichos»). Noventa y nueve no
     * limitaba nada: <b>un solo CAMPEON con dinero podia quedarse con el
     * Santuario entero</b>, y eso no da ningun error -- da un memorial donde
     * todos los nichos llevan el mismo nombre, que es exactamente lo contrario
     * de para lo que existe.
     *
     * <p>⚠ El limite es del jugador, no del rango: por debajo de CAMPEON sigue
     * siendo <b>uno</b>. Lo que cambia es el techo de arriba.
     */
    public static final int TOPE_NICHOS = 3;

    // -------------------------------------------------------------- el estado

    /** Una fila de {@code santuario}, tal cual la lee la base. */
    public record Nicho(String id, Long ownerId, String ownerUuid,
                        boolean permanente, long expiraMs, Long fotoId,
                        String fotoSha1, String titulo, String descripcion,
                        long honores) {

        /**
         * ¿Esta libre AHORA?
         *
         * <p>⚠⚠ ESTO ES LO QUE MIRA TAMBIEN EL DIBUJADO Y LA COMPRA. Libre no es
         * «sin dueno en la fila»: un alquiler vencido que el barrido aun no ha
         * recogido <b>tambien</b> es libre. Si la regla viviera en dos sitios
         * --la fila y el vencimiento-- bastaria con que uno se olvidara para
         * que un nicho pareciera ocupado para siempre o libre con el holograma
         * del anterior puesto.
         */
        public boolean libre(long ahora) {
            return ownerId == null || (!permanente && expiraMs <= ahora);
        }
    }

    /** Todas las filas, en orden de identificador. Corre en el hilo de E/S. */
    public List<Nicho> nichos() throws SQLException {        var salida = new ArrayList<Nicho>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT s.nicho_id, s.owner_id, p.mc_uuid, s.permanente,
                            s.expira_ms, s.foto_id, f.sha1, s.titulo,
                            s.descripcion, s.honores
                     FROM santuario s
                     LEFT JOIN player p ON p.player_id = s.owner_id
                     LEFT JOIN santuario_foto f ON f.foto_id = s.foto_id
                     ORDER BY s.nicho_id
                     """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(fila(rs));
                }
            }
        }
        return salida;
    }

    private static Nicho fila(ResultSet rs) throws SQLException {
        Long owner = rs.getLong("owner_id");
        if (rs.wasNull()) {
            owner = null;
        }
        String ownerUuid = rs.getString("mc_uuid");
        Long foto = rs.getLong("foto_id");
        if (rs.wasNull()) {
            foto = null;
        }
        String fotoSha1 = rs.getString("sha1");
        return new Nicho(rs.getString("nicho_id"), owner, ownerUuid,
                rs.getBoolean("permanente"), rs.getLong("expira_ms"),
                foto, fotoSha1, rs.getString("titulo"), rs.getString("descripcion"),
                rs.getLong("honores"));
    }

    // ------------------------------------------------------------- reclamar

    /**
     * Cuantos honores le quedan hoy a este jugador en cada nicho.
     *
     * <p>⚠ Lo pide el paquete de estado, no la pantalla de honrar: el boton de
     * honrar tiene que nacer apagado si el jugador ya gasto su presupuesto, y
     * para saberlo hace falta ESTE numero. Que lo calcule el cliente seria
     * confiar en el cliente (P6); que no viaje seria un boton encendido que el
     * servidor rechaza -- que es peor que uno apagado.
     */
    public java.util.Map<String, Integer> restantes(long playerId)
            throws SQLException {
        var salida = new java.util.HashMap<String, Integer>();
        long ahora = System.currentTimeMillis();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT nicho_id, ventana_ms, usados FROM santuario_honor "
                             + "WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean viva = ahora - rs.getLong("ventana_ms") < VENTANA_HONOR_MS;
                    salida.put(rs.getString("nicho_id"),
                            viva ? Math.max(0, HONORES_DIA - rs.getInt("usados"))
                                 : HONORES_DIA);
                }
            }
        }
        return salida;
    }

    /** La respuesta a cualquier operacion. {@code motivo} es la clave de idioma. */
    public record Resultado(boolean ok, String motivo, long honores, int restantes) {
        static Resultado si(long honores, int restantes) {
            return new Resultado(true, null, honores, restantes);
        }

        static Resultado no(String motivo) {
            return new Resultado(false, motivo, 0, 0);
        }
    }

    /**
     * Asegura que cada nicho de la config tenga su fila. Se llama al arrancar.
     *
     * <p>⚠ Es {@code INSERT IGNORE} a proposito: el estado de un nicho que ya
     * existe no se toca jamas desde aqui -- esto solo crea lo que falta.
     */
    public void garantizarNichos(Collection<String> ids) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO santuario (nicho_id) VALUES (?)")) {
            for (String id : ids) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Alquila un nicho por 24 h.
     *
     * <p>⚠⚠ SI YA ES MIO Y SIGUE VIVO, RENUEVA: la expira pasa a ser «max(ahora,
     * expira) + 24 h» y se cobra otra vez. Es la unica lectura que no castiga a
     * quien renueva pronto ni regala horas a quien renueva tarde.
     *
     * @param escalon el escalon del jugador, leido en el hilo del servidor antes
     *                de encolar (la cache de rangos es de ahi)
     * @param idem    clave de idempotencia del cliente, maximo 64 caracteres
     */
    public Resultado alquilar(String nichoId, long playerId, int escalon, String idem) {
        if (!nichoValido(nichoId)) {
            return Resultado.no("nicho_invalido");
        }
        if (!idemValido(idem)) {
            return Resultado.no("idem_invalido");
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();

            boolean soyDueno = false;
            boolean estaLibre = false;
            boolean esPermanente = false;
            long expira = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms FROM santuario "
                            + "WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return Resultado.no("no_existe");
                    }
                    long owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        estaLibre = true;
                    } else {
                        soyDueno = owner == playerId;
                        esPermanente = rs.getBoolean("permanente");
                        expira = rs.getLong("expira_ms");
                        estaLibre = !esPermanente && expira <= ahora;
                    }
                }
            }

            if (!estaLibre && !soyDueno) {
                c.rollback();
                return Resultado.no("ocupado");
            }
            if (soyDueno && esPermanente) {
                c.rollback();
                return Resultado.no("ya_permanente");
            }

            // ⚠ La renovacion de lo mio NO cuenta contra el tope: el tope es de
            //   NICHOS, no de clics. Solo una reclamacion nueva lo consume.
            if (!soyDueno || estaLibre) {
                int mios = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM santuario WHERE owner_id = ? "
                                + "AND (permanente = 1 OR expira_ms > ?)")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, ahora);
                    try (ResultSet rs = ps.executeQuery()) {
                        mios = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (mios >= tope(escalon)) {
                    c.rollback();
                    return Resultado.no("tope");
                }
            }

            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.POKEDOLLAR, -PRECIO_ALQUILER,
                        "santuario_alquiler", "santuario", null, idem);
            } catch (EconomyException e) {
                c.rollback();
                return Resultado.no(e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                        ? "sin_plata"
                        : e.kind == EconomyException.Kind.ALREADY_APPLIED
                                ? "ya_aplicado"
                                : "error");
            }

            long nuevaExpira;
            if (soyDueno && !esPermanente && !estaLibre) {
                // ⚠ Renovar antes de hora no come horas: se suma desde el fin,
                //   no desde el clic. Dos renovaciones a los 5 minutos seguidas
                //   dejan el nicho 48 h, no 24 h y un minuto.
                nuevaExpira = Math.max(expira, ahora) + ALQUILER_MS;
            } else {
                nuevaExpira = ahora + ALQUILER_MS;
            }

            // ⚠ Una reclamacion nueva nace sin memorial: si la fila guardara lo
            //   del inquilino anterior, el nuevo dueño heredaria su foto y sus
            //   honores SIN NINGUN ERROR. En la renovacion no se toca nada.
            boolean renovacion = soyDueno && !esPermanente && !estaLibre;
            try (PreparedStatement ps = c.prepareStatement(renovacion
                    ? "UPDATE santuario SET expira_ms = ?, tocado_ms = ? "
                            + "WHERE nicho_id = ?"
                    : "UPDATE santuario SET owner_id = ?, permanente = 0, "
                            + "expira_ms = ?, foto_id = NULL, titulo = '', "
                            + "descripcion = '', honores = 0, tocado_ms = ? "
                            + "WHERE nicho_id = ?")) {
                if (renovacion) {
                    ps.setLong(1, nuevaExpira);
                    ps.setLong(2, ahora);
                    ps.setString(3, nichoId);
                } else {
                    ps.setLong(1, playerId);
                    ps.setLong(2, nuevaExpira);
                    ps.setLong(3, ahora);
                    ps.setString(4, nichoId);
                }
                ps.executeUpdate();
            }
            if (!renovacion) {
                // ⚠ Las filas de honores del inquilino anterior mueren con su
                //   memorial. Sin esto, el nuevo dueño heredaria un presupuesto
                //   de honores a medias gastar, que es peor que heredar el
                //   contador: es heredar UNA PROHIBICION.
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor_click WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
            }

            c.commit();
            return Resultado.si(0, 0);
        } catch (SQLException e) {
            rollbackQuieto(c);
            return Resultado.no("error");
        } finally {
            cerrar(c, auto);
        }
    }

    /**
     * Compra un nicho para siempre.
     *
     * <p>⚠⚠ SI ESTABA ALQUILADO POR MI, EL ALQUILER NO SE DEVUELVE. Son dos
     * compras distintas --una de tiempo, otra de propiedad-- y devolver la
     * primera crearia una conversion Plata -> LunaCoins por la puerta de atras,
     * que es justo lo que D-014 prohibe. El memorial que ya tuviera se conserva.
     */
    public Resultado comprar(String nichoId, long playerId, int escalon, String idem) {
        if (!nichoValido(nichoId)) {
            return Resultado.no("nicho_invalido");
        }
        if (!idemValido(idem)) {
            return Resultado.no("idem_invalido");
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();

            boolean soyDueno = false;
            boolean estaLibre = false;
            boolean esPermanente = false;
            long expira = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms FROM santuario "
                            + "WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return Resultado.no("no_existe");
                    }
                    long owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        estaLibre = true;
                    } else {
                        soyDueno = owner == playerId;
                        esPermanente = rs.getBoolean("permanente");
                        expira = rs.getLong("expira_ms");
                        estaLibre = !esPermanente && expira <= ahora;
                    }
                }
            }

            if (!estaLibre && !soyDueno) {
                c.rollback();
                return Resultado.no("ocupado");
            }
            if (soyDueno && esPermanente) {
                c.rollback();
                return Resultado.no("ya_permanente");
            }
            if (!soyDueno) {
                int mios = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM santuario WHERE owner_id = ? "
                                + "AND (permanente = 1 OR expira_ms > ?)")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, ahora);
                    try (ResultSet rs = ps.executeQuery()) {
                        mios = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (mios >= tope(escalon)) {
                    c.rollback();
                    return Resultado.no("tope");
                }
            }

            try {
                LunaEternal.economy().applyInTransaction(c, playerId,
                        Currency.REPORTCOIN, -PRECIO_PERMANENTE,
                        "santuario_permanente", "santuario", null, idem);
            } catch (EconomyException e) {
                c.rollback();
                return Resultado.no(e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                        ? "sin_lunacoins"
                        : e.kind == EconomyException.Kind.ALREADY_APPLIED
                                ? "ya_aplicado"
                                : "error");
            }

            // ⚠ Igual que en el alquiler: una compra de un nicho ajeno (libre o
            //   caducado) nace sin memorial; la mejora de lo mio lo conserva.
            if (soyDueno && !esPermanente) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE santuario SET permanente = 1, expira_ms = NULL, "
                                + "tocado_ms = ? WHERE nicho_id = ?")) {
                    ps.setLong(1, ahora);
                    ps.setString(2, nichoId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE santuario SET owner_id = ?, permanente = 1, "
                                + "expira_ms = NULL, foto_id = NULL, titulo = '', "
                                + "descripcion = '', honores = 0, tocado_ms = ? "
                                + "WHERE nicho_id = ?")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, ahora);
                    ps.setString(3, nichoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor_click WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
            }

            c.commit();
            return Resultado.si(0, 0);
        } catch (SQLException e) {
            rollbackQuieto(c);
            return Resultado.no("error");
        } finally {
            cerrar(c, auto);
        }
    }

    // -------------------------------------------------------------- caducar

    /**
     * Libera los alquileres vencidos: dueño fuera, memorial a cero.
     *
     * <p>⚠⚠ LA LIBERACION ES UN UPDATE CON LA CONDICION REPETIDA, no un DELETE
     * de la fila. La fila es del nicho, no del inquilino -- si se borrara, un
     * reinicio sin config seria un monumento sin nichos. Y la condicion va
     * escrita otra vez en el UPDATE para que, pase lo que pase entre el SELECT
     * y el UPDATE, jamas se libere un nicho que ya no esta vencido.
     *
     * @return cuantos nichos se liberaron
     */
    public int caducar() throws SQLException {
        long ahora = System.currentTimeMillis();
        int n = 0;
        Connection c = db.connection();
        boolean auto = c.getAutoCommit();
        try {
            c.setAutoCommit(false);
            List<String> vencidos = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nicho_id FROM santuario WHERE owner_id IS NOT NULL "
                            + "AND permanente = 0 AND expira_ms <= ?")) {
                ps.setLong(1, ahora);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vencidos.add(rs.getString(1));
                    }
                }
            }
            for (String id : vencidos) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor_click WHERE nicho_id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM santuario_honor WHERE nicho_id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE santuario SET owner_id = NULL, permanente = 0, "
                                + "expira_ms = NULL, foto_id = NULL, titulo = '', "
                                + "descripcion = '', honores = 0, tocado_ms = ? "
                                + "WHERE nicho_id = ? AND owner_id IS NOT NULL "
                                + "AND permanente = 0 AND expira_ms <= ?")) {
                    ps.setLong(1, ahora);
                    ps.setString(2, id);
                    ps.setLong(3, ahora);
                    if (ps.executeUpdate() == 1) {
                        n++;
                    }
                }
            }
            c.commit();
            return n;
        } catch (SQLException e) {
            rollbackQuieto(c);
            throw e;
        } finally {
            cerrar(c, auto);
        }
    }

    /**
     * Libera un nicho por completo: borra dueno, memorial, honores y clics.
     *
     * @return {@code null} si salio bien, o un motivo de error
     */
    public String eliminar(String nichoId) {
        try (var c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // ⚠ Comprobar que el nicho existe en la base.
                try (var ps = c.prepareStatement(
                        "SELECT nicho_id FROM santuario WHERE nicho_id = ? FOR UPDATE")) {
                    ps.setString(1, nichoId);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return "no_existe";
                        }
                    }
                }
                // ⚠ El orden importa: FK de clics -> honores -> nicho.
                try (var ps = c.prepareStatement(
                        "DELETE FROM santuario_honor_click WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
                try (var ps = c.prepareStatement(
                        "DELETE FROM santuario_honor WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
                try (var ps = c.prepareStatement(
                        "UPDATE santuario SET owner_id = NULL, permanente = 0, "
                        + "expira_ms = 0, foto_id = NULL, titulo = '', "
                        + "descripcion = '', honores = 0 "
                        + "WHERE nicho_id = ?")) {
                    ps.setString(1, nichoId);
                    ps.executeUpdate();
                }
                c.commit();
                LunaEternal.LOG.info("Santuario: nicho '{}' eliminado por admin", nichoId);
                return null;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("Santuario: error al eliminar nicho '{}'", nichoId, e);
            return e.getMessage();
        }
    }

    /**
     * Informacion legible de un nicho, para el comando /luna santuario info.
     *
     * @return el texto con la info, o {@code null} si no existe
     */
    public String info(String nichoId) {
        try (var c = db.connection();
             var ps = c.prepareStatement(
                     "SELECT s.*, p.username FROM santuario s "
                     + "LEFT JOIN player p ON p.player_id = s.owner_id "
                     + "WHERE s.nicho_id = ?")) {
            ps.setString(1, nichoId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                var sb = new StringBuilder();
                sb.append("§7Nicho: §f").append(nichoId).append("\n");
                String dueno = rs.getString("username");
                if (dueno != null) {
                    sb.append("§7Dueño: §f").append(dueno).append("\n");
                    sb.append("§7Tipo: §f").append(rs.getBoolean("permanente")
                            ? "permanente" : "alquiler").append("\n");
                    long expira = rs.getLong("expira_ms");
                    if (!rs.getBoolean("permanente") && expira > 0) {
                        long resta = (expira - System.currentTimeMillis()) / 1000;
                        sb.append("§7Expira en: §f").append(resta > 0
                                ? (resta / 3600) + "h " + ((resta % 3600) / 60) + "min"
                                : "EXPIRADO").append("\n");
                    }
                    sb.append("§7Honores: §f").append(rs.getLong("honores")).append("\n");
                    String titulo = rs.getString("titulo");
                    if (titulo != null && !titulo.isEmpty()) {
                        sb.append("§7Título: §f").append(titulo).append("\n");
                    }
                    Long fotoId = rs.getObject("foto_id") != null
                            ? rs.getLong("foto_id") : null;
                    if (fotoId != null) {
                        sb.append("§7Foto: §f#").append(fotoId).append("\n");
                    }
                } else {
                    sb.append("§7Estado: §flibre\n");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("Santuario: error al consultar nicho '{}'", nichoId, e);
            return null;
        }
    }

    // --------------------------------------------------------------- honrar

    /**
     * Suma un honor al memorial de un nicho.
     *
     * <h2>⚠⚠⚠ MIRAR EL PRESUPUESTO Y GASTARLO VAN EN LA MISMA TRANSACCION</h2>
     *
     * Con {@code FOR UPDATE} sobre la fila de honores. Separados, dos clics
     * rapidos leen los dos «quedan diez» antes de que ninguno escriba, y el
     * jugador honra dos veces con un solo presupuesto -- o el tope de diez se
     * convierte en once. No daria ningun error. Es la misma regla que los
     * relojes de los sobres.
     *
     * <h2>⚠⚠ EL DUPLICADO SE CORTA CON LA CLAVE DEL CLIC, NO CON UN `if`</h2>
     *
     * {@code santuario_honor_click} tiene {@code idem} como clave primaria: un
     * paquete reenviado choca contra la base y se contesta con el estado actual
     * como si hubiera ido bien -- que es exactamente lo que paso. Sin esto, un
     * reintento infla un contador publico (P6, y la leccion de {@code crate_open}).
     *
     * <p>⚠⚠ UNO NO SE HONRA A SI MISMO. El honor es el gesto DE LOS DEMAS: si el
     * dueño pudiera, su presupuesto diario seria un +10 garantizado a su propio
     * contador y el numero dejaria de significar nada. Sin recompensa economica
     * no es un agujero de dinero, pero si de sentido.
     */
    public Resultado honrar(String nichoId, long playerId, String idem) {
        if (!nichoValido(nichoId)) {
            return Resultado.no("nicho_invalido");
        }
        if (!idemValido(idem)) {
            return Resultado.no("idem_invalido");
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return Resultado.no("error");
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();

            long owner;
            boolean permanente;
            long expira;
            long total;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms, honores "
                            + "FROM santuario WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return Resultado.no("no_existe");
                    }
                    owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        c.rollback();
                        return Resultado.no("libre");
                    }
                    permanente = rs.getBoolean("permanente");
                    expira = rs.getLong("expira_ms");
                    total = rs.getLong("honores");
                }
            }
            if (!permanente && expira <= ahora) {
                c.rollback();
                return Resultado.no("libre");
            }
            if (owner == playerId) {
                c.rollback();
                return Resultado.no("eres_tu");
            }

            // ⚠ La marca de idempotencia va ANTES de gastar el presupuesto. Si
            //   va despues, un reintento del paquete llegaria al tope y se
            //   contestaria «tope_diario» a alguien que ya honro -- un mensaje
            //   que miente sobre lo que paso.
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO santuario_honor_click (idem, nicho_id, player_id, hecho_ms) "
                            + "VALUES (?,?,?,?)")) {
                ps.setString(1, idem);
                ps.setString(2, nichoId);
                ps.setLong(3, playerId);
                ps.setLong(4, ahora);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                c.rollback();
                return estadoHonor(nichoId, playerId);
            }

            // ⚠⚠ TRES CASOS Y NO UN UPSERT CON TRUCO: la ventana se resetea
            //    SOLO si la fila no existia o su ventana ya caduco. Si existia
            //    y sigue viva, se conserva la ventana original -- escribirla
            //    siempre moveria el final del dia con cada honor, y el dia
            //    seria eterno.
            boolean habia = false;
            boolean nuevaVentana = true;
            int usados = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ventana_ms, usados FROM santuario_honor "
                            + "WHERE nicho_id = ? AND player_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        habia = true;
                        long ventana = rs.getLong("ventana_ms");
                        if (ahora - ventana < VENTANA_HONOR_MS) {
                            usados = rs.getInt("usados");
                            nuevaVentana = false;
                        }
                    }
                }
            }
            if (usados >= HONORES_DIA) {
                c.rollback();
                return Resultado.no("tope_diario");
            }
            usados++;

            if (!habia) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO santuario_honor (nicho_id, player_id, ventana_ms, usados) "
                                + "VALUES (?,?,?,?)")) {
                    ps.setString(1, nichoId);
                    ps.setLong(2, playerId);
                    ps.setLong(3, ahora);
                    ps.setInt(4, usados);
                    ps.executeUpdate();
                }
            } else if (nuevaVentana) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE santuario_honor SET ventana_ms = ?, usados = ? "
                                + "WHERE nicho_id = ? AND player_id = ?")) {
                    ps.setLong(1, ahora);
                    ps.setInt(2, usados);
                    ps.setString(3, nichoId);
                    ps.setLong(4, playerId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE santuario_honor SET usados = ? "
                                + "WHERE nicho_id = ? AND player_id = ?")) {
                    ps.setInt(1, usados);
                    ps.setString(2, nichoId);
                    ps.setLong(3, playerId);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE santuario SET honores = honores + 1, tocado_ms = ? "
                            + "WHERE nicho_id = ?")) {
                ps.setLong(1, ahora);
                ps.setString(2, nichoId);
                ps.executeUpdate();
            }

            c.commit();
            return Resultado.si(total + 1, HONORES_DIA - usados);
        } catch (SQLException e) {
            rollbackQuieto(c);
            return Resultado.no("error");
        } finally {
            cerrar(c, auto);
        }
    }

    /**
     * El estado de honores tal cual esta ahora: total del memorial y lo que
     * este jugador lleva gastado de su ventana. Sin tocar nada.
     */
    public Resultado estadoHonor(String nichoId, long playerId) {
        try (Connection c = db.connection()) {
            long total = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT honores FROM santuario WHERE nicho_id = ?")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    total = rs.next() ? rs.getLong(1) : 0;
                }
            }
            int usados = 0;
            long ahora = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ventana_ms, usados FROM santuario_honor "
                            + "WHERE nicho_id = ? AND player_id = ?")) {
                ps.setString(1, nichoId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && ahora - rs.getLong("ventana_ms") < VENTANA_HONOR_MS) {
                        usados = rs.getInt("usados");
                    }
                }
            }
            return Resultado.si(total, Math.max(0, HONORES_DIA - usados));
        } catch (SQLException e) {
            return Resultado.no("error");
        }
    }

    // ---------------------------------------------------------------- fotos

    /** Lo mas que puede pesar una foto tal y como la manda el cliente. */
    public static final int FOTO_MAX_BYTES = 2_500_000;

    /** El lado maximo de la foto ya guardada. Lo que pase de ahi se reescala. */
    public static final int FOTO_LADO_MAX = 512;

    /** Cuantas fotos PENDIENTES puede tener un jugador a la vez. */
    public static final int PENDIENTES_MAX = 3;

    /** Donde viven los PNG en el servidor. El nombre es el sha1 del contenido. */
    // =====================================================================
    // EL PASEO POR EL SANTUARIO
    //
    // ⚠⚠⚠ LA XP POR VISITAR ES LA UNICA FUENTE DEL PASE QUE CRECE CON LO QUE
    //    CONSTRUIMOS NOSOTROS, NO CON LO QUE JUEGA EL JUGADOR. Hay 341 nichos:
    //    sin techo, «una vez cada 24 h por nicho» son 341 cobros al dia por
    //    hacer clic derecho, y sube cada vez que el equipo construye mas.
    //    El techo esta en CUANTAS VISITAS CUENTAN (PaseXp.VISITAS_DIA), no en
    //    cuanto paga cada una: asi, mil nichos mas no mueven ni una XP.
    //
    // ⚠⚠ Y LO DECIDE EL SERVIDOR. El cliente solo dice «he abierto este»; si
    //    un cliente modificado los mandara los 341 de golpe, se le concederian
    //    diez y el resto se rechazan aqui (P6).
    // =====================================================================

    /** La ventana del paseo: 24 h, la misma que la de los honores. */
    public static final long VENTANA_PASEO_MS = 24L * 3_600_000L;

    /** Cuantos nichos hay que honrar para poder cobrar la Ultra Ball. */
    public static final int HONORES_PREMIO = 10;

    /** Lo que se lleva quien honra {@link #HONORES_PREMIO} nichos. */
    public static final String PREMIO_ITEM = "cobblemon:ultra_ball";

    /**
     * Lo que hay que enseñar del paseo de un jugador.
     *
     * @param visitasHoy   memoriales que ya le han dado XP en la ventana
     * @param honradosHoy  nichos distintos que ha honrado en la ventana
     * @param premioListo  si puede cobrar la Ultra Ball ya
     */
    public record Paseo(int visitasHoy, int honradosHoy, boolean premioListo) {}

    /**
     * ABRIR EL MEMORIAL DE UN NICHO: da XP del pase, una vez cada 24 h por
     * nicho y como mucho {@link net.pokereport.luna.pase.PaseXp#VISITAS_DIA}
     * veces al dia.
     *
     * <p>⚠ Devuelve la XP que hay que conceder, o <b>0</b> si ya estaba visto
     * hoy o si el jugador ya gasto sus visitas. Quien llama <b>no decide</b>:
     * solo entrega lo que esto diga.
     *
     * @return la XP a conceder (0 si no toca)
     */
    public long verNicho(long playerId, String nichoId) throws SQLException {
        if (!nichoValido(nichoId)) {
            return 0;
        }
        long ahora = System.currentTimeMillis();
        long desde = ahora - VENTANA_PASEO_MS;
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // ⚠⚠ La fila del nicho se bloquea ANTES de contar: dos clics
                //    rapidos sobre dos memoriales distintos podrian leer los dos
                //    «llevo 9» y cobrar los dos el decimo. Con FOR UPDATE sobre
                //    la propia fila de visita, el segundo espera al primero.
                long visto = 0;
                boolean habia = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT visto_ms FROM santuario_visita "
                                + "WHERE player_id = ? AND nicho_id = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, nichoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            habia = true;
                            visto = rs.getLong(1);
                        }
                    }
                }
                if (habia && visto > desde) {
                    c.rollback();
                    return 0;   // ya cobrado hoy
                }
                int usadas;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM santuario_visita "
                                + "WHERE player_id = ? AND visto_ms > ?")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, desde);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        usadas = rs.getInt(1);
                    }
                }
                if (usadas >= net.pokereport.luna.pase.PaseXp.VISITAS_DIA) {
                    c.rollback();
                    return 0;   // techo del dia
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO santuario_visita (player_id, nicho_id, visto_ms) "
                                + "VALUES (?,?,?) ON DUPLICATE KEY UPDATE visto_ms = ?")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, nichoId);
                    ps.setLong(3, ahora);
                    ps.setLong(4, ahora);
                    ps.executeUpdate();
                }
                c.commit();
                return net.pokereport.luna.pase.PaseXp.VISITA_NICHO;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * El estado del paseo de un jugador.
     *
     * <p>⚠⚠ «Cuantos he honrado hoy» <b>se cuenta</b> sobre
     * {@code santuario_honor} en vez de guardarse en un contador: esa tabla ya
     * lleva una fila por (nicho, jugador) con su ventana. Un contador aparte
     * seria un segundo sitio donde vive la misma verdad, y el dia que se
     * desincronizara pagaria una Ultra Ball de mas o de menos <b>sin dar ningun
     * error</b>.
     */
    public Paseo paseo(long playerId) throws SQLException {
        long ahora = System.currentTimeMillis();
        long desde = ahora - VENTANA_PASEO_MS;
        try (Connection c = db.connection()) {
            return paseo(c, playerId, ahora, desde);
        }
    }

    private Paseo paseo(Connection c, long playerId, long ahora, long desde)
            throws SQLException {
        int visitas = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM santuario_visita "
                        + "WHERE player_id = ? AND visto_ms > ?")) {
            ps.setLong(1, playerId);
            ps.setLong(2, desde);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                visitas = rs.getInt(1);
            }
        }
        int honrados = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM santuario_honor "
                        + "WHERE player_id = ? AND ventana_ms > ?")) {
            ps.setLong(1, playerId);
            ps.setLong(2, desde);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                honrados = rs.getInt(1);
            }
        }
        long cobrado = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ultimo_ms FROM santuario_premio WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cobrado = rs.getLong(1);
                }
            }
        }
        boolean listo = honrados >= HONORES_PREMIO && cobrado <= desde;
        return new Paseo(visitas, honrados, listo);
    }

    /**
     * COBRAR LA ULTRA BALL POR HABER HONRADO {@link #HONORES_PREMIO} NICHOS.
     *
     * <h2>⚠⚠⚠ ESTO ROMPE UNA REGLA ESCRITA, Y HAY QUE DECIRLO</h2>
     *
     * {@code santuario.md} §3.3 dice <i>«honrar NO da nada, a proposito: sin
     * recompensa no hay incentivo de multicuenta (B-004)»</i>. <b>El usuario lo
     * ha cambiado</b> (2026-09-09), asi que la regla cae -- pero el motivo por
     * el que existia sigue vivo y hay que taparlo por otro lado:
     *
     * <ul>
     *   <li>el premio va a <b>quien honra</b>, no al nicho, asi que
     *       <b>el contador del memorial sigue significando lo mismo</b>: nadie
     *       gana nada por que le honren;</li>
     *   <li>y hay que honrar <b>diez nichos DISTINTOS</b> --el tope de honores
     *       es 1 por nicho y dia-- asi que no se puede farmear sobre uno
     *       propio.</li>
     * </ul>
     *
     * <p>&#9888; Lo que si queda abierto: con 341 nichos y una cuenta nueva
     * gratis (B-004), diez honores al dia son faciles. El precio de eso es
     * <b>una Ultra Ball</b>, que se compra en la tienda: el techo del abuso es
     * el valor de una Ultra Ball al dia por cuenta, y eso se acepto a sabiendas.
     *
     * <p>&#9888;&#9888; <b>LA CLAVE PRIMARIA ES QUIEN CORTA EL DOBLE COBRO</b>,
     * no un {@code if}: la fila de {@code santuario_premio} es por jugador, y se
     * escribe con la condicion de que lo cobrado sea viejo. Dos clics rapidos,
     * un cliente modificado o un reintento fallan en la base.
     *
     * @return {@code null} si se cobro, o el motivo por el que no
     */
    public String cobrarPremio(long playerId) {
        long ahora = System.currentTimeMillis();
        long desde = ahora - VENTANA_PASEO_MS;
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                var p = paseo(c, playerId, ahora, desde);
                if (p.honradosHoy() < HONORES_PREMIO) {
                    c.rollback();
                    return "faltan_honores";
                }
                // ⚠ El UPDATE lleva la condicion dentro: si otra peticion cobro
                //   entre el `paseo` y esto, afecta a CERO filas y se rechaza.
                int filas;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO santuario_premio (player_id, ultimo_ms, cobrados) "
                                + "VALUES (?,?,1) ON DUPLICATE KEY UPDATE "
                                + "ultimo_ms = IF(ultimo_ms <= ?, ?, ultimo_ms), "
                                + "cobrados = cobrados + IF(ultimo_ms <= ?, 1, 0)")) {
                    ps.setLong(1, playerId);
                    ps.setLong(2, ahora);
                    ps.setLong(3, desde);
                    ps.setLong(4, ahora);
                    ps.setLong(5, desde);
                    filas = ps.executeUpdate();
                }
                // ⚠⚠ MariaDB devuelve 1 al insertar y 2 al actualizar de verdad;
                //    0 significa «no cambio nada», o sea que ya estaba cobrado.
                if (filas == 0) {
                    c.rollback();
                    return "ya_cobrado";
                }
                // Y si el IF no movio la fecha, tampoco toca.
                long ahoraGuardado = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ultimo_ms FROM santuario_premio WHERE player_id = ?")) {
                    ps.setLong(1, playerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        ahoraGuardado = rs.getLong(1);
                    }
                }
                if (ahoraGuardado != ahora) {
                    c.rollback();
                    return "ya_cobrado";
                }
                c.commit();
                return null;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo cobrar el premio del santuario", e);
            return "error";
        }
    }

    public static java.nio.file.Path carpetaFotos() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("lunaeternal/fotos");
    }

    /** Una foto del jugador, para enseñarla en la pantalla. */
    public record Foto(long id, String estado, String sha1) {}

    /**
     * Las fotos PENDIENTES de moderar, con el uuid del dueno.
     *
     * <p>⚠ Solo debe llamarse para alguien con permiso de moderar (nivel 3+):
     * aqui viaja el sha1, que es justo lo que permite pedir la imagen, y una
     * foto PENDIENTE no es publica todavia -- es contenido sin moderar. La
     * comprobacion de permiso la hace quien llama (el manejador del paquete);
     * aqui solo se lee.
     */
    public java.util.List<java.util.AbstractMap.SimpleEntry<Foto, String>> pendientes()
            throws SQLException {
        var salida = new java.util.ArrayList<java.util.AbstractMap.SimpleEntry<Foto, String>>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT f.foto_id, f.sha1, p.mc_uuid FROM santuario_foto f "
                             + "JOIN player p ON p.player_id = f.owner_id "
                             + "WHERE f.estado = 'PENDIENTE' ORDER BY f.foto_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new java.util.AbstractMap.SimpleEntry<>(
                            new Foto(rs.getLong("foto_id"), "PENDIENTE",
                                    rs.getString("sha1")),
                            rs.getString("mc_uuid")));
                }
            }
        }
        return salida;
    }

    /** Las fotos del jugador, la mas nueva primero. Corre en el hilo de E/S. */
    public List<Foto> misFotos(long playerId) throws SQLException {
        var salida = new ArrayList<Foto>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT foto_id, estado, sha1 FROM santuario_foto "
                             + "WHERE owner_id = ? ORDER BY foto_id DESC")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Foto(rs.getLong("foto_id"),
                            rs.getString("estado"), rs.getString("sha1")));
                }
            }
        }
        return salida;
    }

    /** La respuesta a una subida. */
    public record ResultadoFoto(boolean ok, String motivo, long fotoId, String sha1) {
        static ResultadoFoto si(long fotoId, String sha1) {
            return new ResultadoFoto(true, null, fotoId, sha1);
        }

        static ResultadoFoto no(String motivo) {
            return new ResultadoFoto(false, motivo, 0, "");
        }
    }

    /**
     * Guarda la foto que manda el cliente y la deja PENDIENTE de aprobacion.
     *
     * <h2>⚠⚠ SE REENCODA SIEMPRE, y eso no es por estetica</h2>
     *
     * La foto llega del cliente, o sea que es contenido no confiable (P6):
     * <ul>
     *   <li>se <b>decodifica</b> con ImageIO — si no es una imagen de verdad, se
     *       rechaza en vez de guardar basura que luego el cliente no puede
     *       pintar;</li>
     *   <li>se <b>reescala</b> a {@link #FOTO_LADO_MAX} — una foto de 50
     *       megapixeles tumbaria al servidor decodificandola y a los clientes
     *       recibiendola;</li>
     *   <li>se <b>recodifica</b> a PNG — se va cualquier EXIF (localizacion GPS
     *       incluida) y cualquier payload escondido detras de la imagen. Lo que
     *       queda es exactamente lo que se ve.</li>
     * </ul>
     *
     * <p>⚠ El fichero se llama como el sha1 del contenido y no se reescribe si
     * ya existe: dos jugadores subiendo la misma foto comparten fichero, y un
     * nombre que es un hash no puede atravesar directorios.
     *
     * <p>⚠ PENDIENTE, y no se puede colocar hasta que un staff la apruebe: una
     * foto es contenido publico en medio del monumento, y el moderador es el
     * staff, no la base de datos.
     */
    public ResultadoFoto subirFoto(long playerId, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > FOTO_MAX_BYTES) {
            return ResultadoFoto.no("foto_grande");
        }
        final java.awt.image.BufferedImage imagen;
        try {
            imagen = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (java.io.IOException e) {
            return ResultadoFoto.no("foto_ilegible");
        }
        if (imagen == null) {
            return ResultadoFoto.no("foto_ilegible");
        }
        java.awt.image.BufferedImage lista = imagen;
        if (imagen.getWidth() > FOTO_LADO_MAX || imagen.getHeight() > FOTO_LADO_MAX) {
            double k = Math.min((double) FOTO_LADO_MAX / imagen.getWidth(),
                    (double) FOTO_LADO_MAX / imagen.getHeight());
            int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * k));
            int alto = Math.max(1, (int) Math.round(imagen.getHeight() * k));
            lista = new java.awt.image.BufferedImage(ancho, alto,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = lista.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(imagen, 0, 0, ancho, alto, null);
            } finally {
                g.dispose();
            }
        }
        final byte[] png;
        try (var salida = new java.io.ByteArrayOutputStream()) {
            if (!javax.imageio.ImageIO.write(lista, "png", salida)) {
                return ResultadoFoto.no("foto_ilegible");
            }
            png = salida.toByteArray();
        } catch (java.io.IOException e) {
            return ResultadoFoto.no("foto_ilegible");
        }
        String sha1;
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(png)) {
                hex.append(String.format("%02x", b));
            }
            sha1 = hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return ResultadoFoto.no("error");
        }
        try {
            var carpeta = carpetaFotos();
            java.nio.file.Files.createDirectories(carpeta);
            var destino = carpeta.resolve(sha1 + ".png");
            if (!java.nio.file.Files.exists(destino)) {
                java.nio.file.Files.write(destino, png);
            }
        } catch (java.io.IOException e) {
            LunaEternal.LOG.error("No se pudo guardar la foto {}", sha1, e);
            return ResultadoFoto.no("error");
        }
        try (Connection c = db.connection()) {
            int pendientes = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM santuario_foto "
                            + "WHERE owner_id = ? AND estado = 'PENDIENTE'")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    pendientes = rs.next() ? rs.getInt(1) : 0;
                }
            }
            if (pendientes >= PENDIENTES_MAX) {
                return ResultadoFoto.no("pendientes_llenas");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO santuario_foto (owner_id, sha1, estado, subida_ms) "
                            + "VALUES (?,?,'PENDIENTE',?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, playerId);
                ps.setString(2, sha1);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
                try (ResultSet claves = ps.getGeneratedKeys()) {
                    if (claves.next()) {
                        return ResultadoFoto.si(claves.getLong(1), sha1);
                    }
                }
            }
            return ResultadoFoto.no("error");
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo registrar la foto de {}", playerId, e);
            return ResultadoFoto.no("error");
        }
    }

    /** Aprueba una foto pendiente. {@code null} si fue bien, o la clave del motivo. */
    public String aprobar(long fotoId) {
        return cambiarEstadoFoto(fotoId, "APROBADA");
    }

    /** Rechaza una foto pendiente. {@code null} si fue bien, o la clave del motivo. */
    public String rechazar(long fotoId) {
        return cambiarEstadoFoto(fotoId, "RECHAZADA");
    }

    private String cambiarEstadoFoto(long fotoId, String estado) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE santuario_foto SET estado = ? "
                             + "WHERE foto_id = ? AND estado = 'PENDIENTE'")) {
            ps.setString(1, estado);
            ps.setLong(2, fotoId);
            return ps.executeUpdate() == 1 ? null : "no_pendiente";
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo cambiar el estado de la foto {}", fotoId, e);
            return "error";
        }
    }

    /**
     * Coloca una foto en el nicho. Solo el dueno, y solo una foto SUYA y
     * APROBADA.
     *
     * <p>⚠⚠ SE COMPRUEBA EN LA BASE, que es donde vive la verdad: el cliente
     * manda un {@code fotoId} y nada mas (P6). Un id que no exista, que no sea
     * suyo o que siga pendiente se rechaza aqui, no en la pantalla.
     */
    public String ponerFoto(String nichoId, long playerId, long fotoId) {
        if (!nichoValido(nichoId)) {
            return "nicho_invalido";
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return "error";
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return "error";
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();
            boolean mio;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms FROM santuario "
                            + "WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return "no_existe";
                    }
                    long owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        c.rollback();
                        return "no_es_tuyo";
                    }
                    boolean permanente = rs.getBoolean("permanente");
                    long expira = rs.getLong("expira_ms");
                    mio = owner == playerId && (permanente || expira > ahora);
                }
            }
            if (!mio) {
                c.rollback();
                return "no_es_tuyo";
            }
            String estado = null;
            long dueno = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, estado FROM santuario_foto WHERE foto_id = ?")) {
                ps.setLong(1, fotoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dueno = rs.getLong("owner_id");
                        estado = rs.getString("estado");
                    }
                }
            }
            if (estado == null) {
                c.rollback();
                return "foto_no_existe";
            }
            if (dueno != playerId) {
                c.rollback();
                return "foto_no_tuya";
            }
            if (!"APROBADA".equals(estado)) {
                c.rollback();
                return "foto_no_aprobada";
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE santuario SET foto_id = ?, tocado_ms = ? "
                            + "WHERE nicho_id = ?")) {
                ps.setLong(1, fotoId);
                ps.setLong(2, ahora);
                ps.setString(3, nichoId);
                ps.executeUpdate();
            }
            // ⚠⚠ La foto cambia, los honores vuelven a cero: la gente honra LO
            //    QUE VE. Va DENTRO de la misma transaccion que el cambio, o
            //    habria un instante con la foto nueva y los honores viejos.
            reiniciarHonores(c, nichoId);
            c.commit();
            return null;
        } catch (SQLException e) {
            rollbackQuieto(c);
            return "error";
        } finally {
            cerrar(c, auto);
        }
    }

    /** Quita la foto del nicho. El memorial sigue con su titulo y sus honores. */
    public String quitarFoto(String nichoId, long playerId) {
        if (!nichoValido(nichoId)) {
            return "nicho_invalido";
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return "error";
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return "error";
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();
            boolean mio;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms FROM santuario "
                            + "WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return "no_existe";
                    }
                    long owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        c.rollback();
                        return "no_es_tuyo";
                    }
                    boolean permanente = rs.getBoolean("permanente");
                    long expira = rs.getLong("expira_ms");
                    mio = owner == playerId && (permanente || expira > ahora);
                }
            }
            if (!mio) {
                c.rollback();
                return "no_es_tuyo";
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE santuario SET foto_id = NULL, tocado_ms = ? "
                            + "WHERE nicho_id = ?")) {
                ps.setLong(1, ahora);
                ps.setString(2, nichoId);
                ps.executeUpdate();
            }
            // ⚠ Quitar tambien es cambiar: si no, el camino «quitar y poner
            //   otra» conservaria los honores y seria el hueco que esto cierra.
            reiniciarHonores(c, nichoId);
            c.commit();
            return null;
        } catch (SQLException e) {
            rollbackQuieto(c);
            return "error";
        } finally {
            cerrar(c, auto);
        }
    }

    // --------------------------------------------------------------- textos
    /**
     * Cambia el titulo y la descripcion del memorial. Solo el dueno.
     *
     * <p>⚠⚠ SE ACOTA AQUI, QUE ES DONDE LLEGA DEL CLIENTE. Y se prohibe el «§»
     * por lo mismo que en los clanes y las protecciones: un codigo de color
     * dentro pintaria el resto de la linea de quien lo lea.
     *
     * @return la clave del motivo si no se pudo, o {@code null}
     */
    public String textos(String nichoId, long playerId, String titulo,
                         String descripcion) {
        if (!nichoValido(nichoId)) {
            return "nicho_invalido";
        }
        String t = titulo == null ? "" : titulo.trim();
        String d = descripcion == null ? "" : descripcion.trim();
        if (t.isEmpty() || t.length() > TITULO_MAX || t.indexOf('§') >= 0
                || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            return "titulo_invalido";
        }
        if (d.length() > DESCRIPCION_MAX || d.indexOf('§') >= 0
                || d.indexOf('\r') >= 0) {
            return "descripcion_invalida";
        }
        Connection c;
        try {
            c = db.connection();
        } catch (SQLException e) {
            return "error";
        }
        boolean auto;
        try {
            auto = c.getAutoCommit();
        } catch (SQLException e) {
            return "error";
        }
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();
            boolean mio;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT owner_id, permanente, expira_ms FROM santuario "
                            + "WHERE nicho_id = ? FOR UPDATE")) {
                ps.setString(1, nichoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return "no_existe";
                    }
                    long owner = rs.getLong("owner_id");
                    if (rs.wasNull()) {
                        c.rollback();
                        return "no_es_tuyo";
                    }
                    boolean permanente = rs.getBoolean("permanente");
                    long expira = rs.getLong("expira_ms");
                    mio = owner == playerId
                            && (permanente || expira > ahora);
                }
            }
            if (!mio) {
                c.rollback();
                return "no_es_tuyo";
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE santuario SET titulo = ?, descripcion = ?, tocado_ms = ? "
                            + "WHERE nicho_id = ?")) {
                ps.setString(1, t);
                ps.setString(2, d);
                ps.setLong(3, ahora);
                ps.setString(4, nichoId);
                ps.executeUpdate();
            }
            c.commit();
            return null;
        } catch (SQLException e) {
            rollbackQuieto(c);
            return "error";
        } finally {
            cerrar(c, auto);
        }
    }

    // ------------------------------------------------------------ auxiliares

    /** Los ids de nicho son cortos y simples: viven en la config y en paquetes. */
    private static boolean nichoValido(String id) {
        return id != null && id.length() >= 1 && id.length() <= 32
                && id.matches("[a-z0-9_-]+");
    }

    private static boolean idemValido(String idem) {
        return idem != null && !idem.isBlank() && idem.length() <= 64;
    }

    private static void rollbackQuieto(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignorado) {
            // Ya estamos en el camino de error; lo que importa es el de arriba.
        }
    }

    private static void cerrar(Connection c, boolean auto) {
        try {
            c.setAutoCommit(auto);
        } catch (SQLException ignorado) {
            // La conexion se cierra igual; autoCommit no cambia el resultado.
        } finally {
            try {
                c.close();
            } catch (SQLException ignorado) {
                // Nada que hacer con una conexion que no se deja cerrar.
            }
        }
    }
}
