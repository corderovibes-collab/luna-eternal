package net.pokereport.luna.cosmetics;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Comprar y equipar cosméticos.
 *
 * <p>Con {@code D-039} —solo se consiguen comprándolos o en eventos— este
 * servicio es el <b>único</b> sitio por donde un cosmético puede entrar en la
 * colección de alguien. No hay botín, ni receta, ni comando de mundo que lo
 * conceda, así que todo el anti-abuso cabe aquí.
 */
public class CosmeticsService {

    private final Database db;

    public CosmeticsService(Database db) {
        this.db = db;
    }

    /** Lo que posee un jugador. */
    public Set<String> poseidos(long playerId) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT cosmetic_id FROM player_cosmetics WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** Lo que lleva puesto, por categoría. */
    public Map<String, String> equipados(long playerId) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT categoria, cosmetic_id FROM player_cosmetic_equipped "
                             + "WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return out;
    }

    public record Resultado(boolean ok, String mensaje) {
        static Resultado si() {
            return new Resultado(true, "");
        }

        static Resultado no(String m) {
            return new Resultado(false, m);
        }
    }

    /**
     * Compra un cosmético: cobra y anota la posesión <b>en la misma
     * transacción</b>.
     *
     * <p>⚠ Las dos cosas van juntas o no va ninguna. Cobrar fuera de la
     * transacción abre la única ventana que de verdad duele: descontar
     * LunaCoins —que se compran con dinero real (D-013)— y que la anotación
     * falle. El jugador se queda sin saldo y sin cosmético, y eso no se arregla
     * solo.
     *
     * <p>⚠⚠ La defensa contra comprar dos veces <b>no es</b> la comprobación de
     * más abajo: es la clave primaria {@code (player_id, cosmetic_id)}. La
     * comprobación evita cobrar en el caso normal, pero dos clics rápidos
     * pueden pasarla los dos. Lo que impide el doble cobro es que la segunda
     * inserción choca contra la clave y deshace su transacción entera.
     *
     * @param clave clave de idempotencia (R4). <b>Un UUID nuevo por peticion</b>,
     *              NO una clave derivada del jugador y el cosmetico: esa se
     *              probo y abria un agujero -- si alguna vez se le retira el
     *              cosmetico a alguien y lo vuelve a comprar, la clave ya esta
     *              usada, la economia contesta {@code ALREADY_APPLIED}, el cobro
     *              se salta y el {@code INSERT} si entra. Cosmetico gratis, sin
     *              error. El doble clic ya lo cubren la transaccion y la clave
     *              primaria
     */
    public Resultado comprar(long playerId, String cosmeticId, String clave)
            throws SQLException {

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null) {
            // Un identificador que no está en el catálogo. El cliente no debería
            // poder mandarlo, y justamente por eso se comprueba: lo que "no
            // debería poder pasar" es lo que hay que rechazar explícitamente.
            return Resultado.no("Ese cosmético no existe.");
        }
        if (pieza.precio() <= 0) {
            // Precio 0 es "no está a la venta", no "gratis" (D-039). Sin esta
            // línea, todo cosmético de evento sería gratis para cualquiera.
            return Resultado.no("Ese cosmético no está a la venta: solo sale en eventos.");
        }

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                if (tiene(c, playerId, cosmeticId)) {
                    c.rollback();
                    return Resultado.no("Ya tienes ese cosmético.");
                }

                LunaEternal.economy().applyInTransaction(
                        c, playerId, Currency.REPORTCOIN, -pieza.precio(),
                        "cosmetico_compra", "cosmetic", null, clave);

                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO player_cosmetics
                            (player_id, cosmetic_id, origen, precio_pagado)
                        VALUES (?, ?, 'compra', ?)
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cosmeticId);
                    ps.setInt(3, pieza.precio());
                    ps.executeUpdate();
                }

                c.commit();
                return Resultado.si();
            } catch (EconomyException e) {
                c.rollback();
                // Se distingue por tipo. Antes todo fallo economico decia "no
                // tienes suficientes", asi que un ALREADY_APPLIED --que es un
                // fallo NUESTRO de claves-- se le echaba al saldo del jugador y
                // nadie lo habria investigado nunca.
                return switch (e.kind) {
                    case INSUFFICIENT_FUNDS -> Resultado.no("No tienes LunaCoins suficientes.");
                    default -> {
                        LunaEternal.LOG.warn("Compra de {} por el jugador {} rechazada: {} ({})",
                                cosmeticId, playerId, e.kind, e.getMessage());
                        yield Resultado.no("No se pudo completar la compra.");
                    }
                };
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Concede un cosmético sin cobrar: eventos y regalos.
     *
     * <p>Es la <b>otra</b> vía de D-039, y la que evita que la tienda se apague
     * sola: si todo fuera de pago, los únicos que llevarían cosméticos serían
     * los que pagan, y un cosmético que nadie ve no vale nada.
     *
     * <p>No pasa por la economía —no hay nada que cobrar— pero sí por la misma
     * clave primaria, así que conceder dos veces es inofensivo.
     */
    public Resultado conceder(long playerId, String cosmeticId, String origen)
            throws SQLException {
        if (Catalogo.de(cosmeticId) == null) {
            return Resultado.no("Ese cosmético no existe.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT IGNORE INTO player_cosmetics
                         (player_id, cosmetic_id, origen, precio_pagado)
                     VALUES (?, ?, ?, 0)
                     """)) {
            ps.setLong(1, playerId);
            ps.setString(2, cosmeticId);
            ps.setString(3, "evento".equals(origen) ? "evento" : "regalo");
            ps.executeUpdate();
        }
        return Resultado.si();
    }

    /**
     * Se pone un cosmético que ya posee, o se lo quita.
     *
     * <p>⚠ <b>Comprobar la posesión aquí no es redundante.</b> Sin esta línea,
     * un cliente modificado podría equiparse cualquier cosa del catálogo sin
     * haberla comprado — y como el equipado es lo que ven los demás, sería
     * indistinguible de haberla comprado. La tienda se quedaría sin sentido sin
     * que nadie tocara la tabla de posesión.
     *
     * @param cosmeticId {@code null} o vacío para quitarse el de esa categoría
     */
    public Resultado equipar(long playerId, String categoria, String cosmeticId)
            throws SQLException {

        if (cosmeticId == null || cosmeticId.isEmpty()) {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM player_cosmetic_equipped "
                                 + "WHERE player_id = ? AND categoria = ?")) {
                ps.setLong(1, playerId);
                ps.setString(2, categoria);
                ps.executeUpdate();
            }
            return Resultado.si();
        }

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null || !pieza.categoria().equals(categoria)) {
            return Resultado.no("Ese cosmético no existe en esa categoría.");
        }

        try (Connection c = db.connection()) {
            if (!tiene(c, playerId, cosmeticId)) {
                return Resultado.no("No tienes ese cosmético.");
            }
            // REPLACE y no INSERT: la clave (jugador, categoria) garantiza una
            // sola pieza puesta, y reemplazar la cambia en UN paso. Con borrar
            // e insertar habría un instante llevando dos, o ninguna.
            try (PreparedStatement ps = c.prepareStatement("""
                    REPLACE INTO player_cosmetic_equipped
                        (player_id, categoria, cosmetic_id)
                    VALUES (?, ?, ?)
                    """)) {
                ps.setLong(1, playerId);
                ps.setString(2, categoria);
                ps.setString(3, cosmeticId);
                ps.executeUpdate();
            }
        }
        return Resultado.si();
    }

    private boolean tiene(Connection c, long playerId, String cosmeticId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM player_cosmetics WHERE player_id = ? AND cosmetic_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, cosmeticId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
