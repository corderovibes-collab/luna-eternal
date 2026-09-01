package net.pokereport.luna.crate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.crate.Cofre.Cofre_;
import net.pokereport.luna.crate.Cofre.Premio;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

/**
 * ABRIR UN COFRE.
 *
 * <p>Diseño en {@code docs/economy/treasures.md}; la decisión, D-020.
 *
 * <h2>⚠⚠⚠ ABRIR SON TRES COSAS Y TIENEN QUE SER UNA</h2>
 *
 * Gastar la llave, sortear el premio y anotarlo. Si esas tres no van en la
 * misma transacción, cualquier corte por medio deja un estado imposible:
 *
 * <ul>
 *   <li>llave gastada y premio no anotado → <b>el jugador pagó por nada</b>;</li>
 *   <li>premio anotado y llave no gastada → <b>abrir es gratis</b>.</li>
 * </ul>
 *
 * <p>Y ninguno de los dos da error. El primero llega como una queja tres días
 * después; el segundo no llega nunca, porque a nadie le extraña tener suerte.
 *
 * <h2>⚠⚠⚠ LA ENTREGA VA FUERA, Y ESO NO ES UNA INCOHERENCIA</h2>
 *
 * El Pokémon o el objeto se entregan <b>después del commit</b>, porque un
 * inventario y un equipo Pokémon <b>no son tablas</b>: no se pueden meter en
 * una transacción de MariaDB. Es exactamente el mismo reparto que ya hace el
 * escaparate al publicar objetos.
 *
 * <p>Lo que hace que eso sea seguro es que <b>lo anotado manda</b>: la fila de
 * {@code crate_open} dice qué le tocó, así que si la entrega falla se puede
 * repetir mirando la tabla, sin volver a sortear y sin volver a cobrar.
 *
 * <h2>⚠⚠ LA IDEMPOTENCIA NO ES DEFENSIVA: ES LA REGLA</h2>
 *
 * La clave única de {@code crate_open.idem} corta el doble clic <b>en la
 * base</b>, venga de donde venga la petición — un cliente modificado, un
 * reintento de red, dos pulsaciones rápidas. Es la misma decisión que la clave
 * primaria de {@code gym_badge}.
 */
public final class CrateService {

    private final Database db;
    private final Random rnd = new Random();

    public CrateService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------- llaves

    /** Cuántas llaves de cada cofre tiene. Cero si no tiene fila. */
    public int llaves(long playerId, String cofre) throws SQLException {
        try (Connection c = db.connection()) {
            return llaves(c, playerId, cofre);
        }
    }

    private int llaves(Connection c, long playerId, String cofre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT amount FROM crate_key WHERE player_id = ? AND crate = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, cofre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Todas sus llaves, en el orden de {@link Cofre#TODOS}. */
    public int[] todasLasLlaves(long playerId) throws SQLException {
        int[] salida = new int[Cofre.TODOS.size()];
        try (Connection c = db.connection()) {
            for (int i = 0; i < salida.length; i++) {
                salida[i] = llaves(c, playerId, Cofre.TODOS.get(i).id());
            }
        }
        return salida;
    }

    /**
     * Suma llaves. Devuelve cuántas quedan.
     *
     * <p>⚠ {@code ON DUPLICATE KEY} y no «lee, suma y escribe»: dos hilos
     * dándole llaves a la vez —el premio de una misión y la llave diaria— se
     * pisarían y una de las dos se perdería sin dar ningún error.
     */
    public int darLlaves(Connection c, long playerId, String cofre, int cuantas)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO crate_key (player_id, crate, amount) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, cofre);
            ps.setInt(3, cuantas);
            ps.executeUpdate();
        }
        return llaves(c, playerId, cofre);
    }

    /** La versión suelta, con su propia conexión. */
    public int darLlaves(long playerId, String cofre, int cuantas) throws SQLException {
        try (Connection c = db.connection()) {
            return darLlaves(c, playerId, cofre, cuantas);
        }
    }

    /**
     * COMPRA UNA LLAVE CON LUNACOINS.
     *
     * <p>⚠⚠⚠ EL PRECIO NO VIAJA EN EL PAQUETE. Llega el identificador del cofre
     * y el servidor mira el precio en <b>su</b> tabla. Si viniera del cliente,
     * un cliente modificado compraría la llave del cofre shiny por 1 (P6). Es
     * la misma regla que la tienda.
     */
    public int comprarLlave(long playerId, String cofreId, int cuantas)
            throws SQLException, EconomyException {
        Cofre_ cofre = Cofre.de(cofreId);
        if (cofre == null || cofre.llave() != Cofre.Llave.PREMIUM
                || cofre.precio() <= 0) {
            throw new IllegalArgumentException("Ese cofre no vende llaves");
        }
        // ⚠⚠ SE ACOTA ANTES DE MULTIPLICAR. `precio * cuantas` con un número
        //    enorme del cliente DESBORDA el long y sale negativo, y cobrar en
        //    negativo es INGRESAR dinero. Acotar después no sirve de nada: es
        //    la lección que ya está escrita en la tienda.
        int n = Math.max(1, Math.min(64, cuantas));
        long coste = (long) cofre.precio() * n;

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                String idem = "crate_key:" + playerId + ":" + cofreId + ":"
                        + java.util.UUID.randomUUID();
                LunaEternal.economy().applyInTransaction(
                        c, playerId, Currency.REPORTCOIN, -coste,
                        "compra de llave " + cofreId, "crate_key", null, idem);
                int quedan = darLlaves(c, playerId, cofreId, n);
                c.commit();
                return quedan;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ------------------------------------------------------------ abrir

    /** Lo que salió de una apertura. */
    public record Resultado(String cofre, Premio premio, boolean porPiedad,
                            int llavesRestantes, int piedadActual) {}

    /**
     * ABRE UN COFRE. Gasta una llave, sortea, anota y devuelve qué tocó.
     *
     * <p><b>No entrega nada</b>: entregar toca el mundo y esto toca la base.
     * Quien llama se encarga, mirando {@link Resultado}.
     *
     * @param idem clave de idempotencia. La pone el servidor, no el cliente
     */
    public Resultado abrir(long playerId, String cofreId, String idem)
            throws SQLException {
        Cofre_ cofre = Cofre.de(cofreId);
        if (cofre == null) {
            throw new IllegalArgumentException("No existe el cofre " + cofreId);
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // ⚠⚠ FOR UPDATE: bloquea la fila de la llave hasta el commit.
                //    Sin esto, dos aperturas a la vez leen «tengo 1» las dos y
                //    se abren las dos con una sola llave. Es exactamente cómo
                //    se duplica dinero, aplicado a llaves.
                int tiene;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT amount FROM crate_key "
                        + "WHERE player_id = ? AND crate = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cofreId);
                    try (ResultSet rs = ps.executeQuery()) {
                        tiene = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (tiene <= 0) {
                    c.rollback();
                    return null;
                }

                int desde = piedad(c, playerId, cofreId);
                boolean forzar = cofre.tieneMayor() && cofre.piedad() > 0
                        && desde + 1 >= cofre.piedad();
                Premio premio = Cofre.sortear(cofre, rnd, forzar);
                if (premio == null) {
                    c.rollback();
                    return null;
                }
                // ⚠ `porPiedad` es TRUE solo si la piedad forzó Y el premio es
                //   mayor. Si el cofre no tuviera mayores, `sortear` cae al
                //   sorteo normal y decir «te tocó por piedad» sería mentira.
                boolean porPiedad = forzar && premio.mayor();

                // 1) la llave
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE crate_key SET amount = amount - 1 "
                        + "WHERE player_id = ? AND crate = ? AND amount > 0")) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cofreId);
                    if (ps.executeUpdate() != 1) {
                        c.rollback();
                        return null;
                    }
                }

                // 2) el registro. Su clave UNICA es lo que corta el doble clic.
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO crate_open
                          (player_id, crate, prize, quantity, major, by_pity, idem)
                        VALUES (?,?,?,?,?,?,?)
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cofreId);
                    ps.setString(3, premio.id());
                    ps.setInt(4, premio.cantidad());
                    ps.setBoolean(5, premio.mayor());
                    ps.setBoolean(6, porPiedad);
                    ps.setString(7, idem);
                    ps.executeUpdate();
                } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                    // Ya se abrió con esta clave: un doble clic o un reintento.
                    c.rollback();
                    return null;
                }

                // 3) la piedad: a cero si tocó mayor, +1 si no.
                int nuevaPiedad = premio.mayor() ? 0 : desde + 1;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO crate_pity (player_id, crate, since) VALUES (?,?,?)
                        ON DUPLICATE KEY UPDATE since = VALUES(since)
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cofreId);
                    ps.setInt(3, nuevaPiedad);
                    ps.executeUpdate();
                }

                // 4) y si el premio es dinero, va al libro de asientos como
                //    todo lo demás: sin esto, `/luna economia` no vería una de
                //    las fuentes y el informe de inflación mentiría.
                if (premio.tipo() == Cofre.Tipo.PLATA) {
                    LunaEternal.economy().applyInTransaction(
                            c, playerId, Currency.POKEDOLLAR, premio.cantidad(),
                            "premio de cofre " + cofreId, "crate_open", null,
                            "crate_prize:" + idem);
                }

                int quedan = llaves(c, playerId, cofreId);
                c.commit();
                return new Resultado(cofreId, premio, porPiedad, quedan, nuevaPiedad);
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) {
                    throw se;
                }
                throw new RuntimeException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private int piedad(Connection c, long playerId, String cofre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT since FROM crate_pity WHERE player_id = ? AND crate = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, cofre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Cuántas aperturas lleva sin premio mayor, por cofre. Para la pantalla. */
    public int[] todaLaPiedad(long playerId) throws SQLException {
        int[] salida = new int[Cofre.TODOS.size()];
        try (Connection c = db.connection()) {
            for (int i = 0; i < salida.length; i++) {
                salida[i] = piedad(c, playerId, Cofre.TODOS.get(i).id());
            }
        }
        return salida;
    }

    /** Las últimas aperturas de un jugador. Para el comando de auditoría. */
    public List<String> historial(long playerId, int cuantas) throws SQLException {
        var salida = new ArrayList<String>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT crate, prize, quantity, by_pity, opened_at
                FROM crate_open WHERE player_id = ?
                ORDER BY opened_at DESC LIMIT ?
                """)) {
            ps.setLong(1, playerId);
            ps.setInt(2, Math.max(1, Math.min(100, cuantas)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(String.format("%s  %s x%d%s  %s",
                            rs.getString(1), rs.getString(2), rs.getInt(3),
                            rs.getBoolean(4) ? " (piedad)" : "",
                            rs.getTimestamp(5)));
                }
            }
        }
        return salida;
    }
}
