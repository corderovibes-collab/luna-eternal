package net.pokereport.luna.backpack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.gts.ItemCodec;

/**
 * Guarda y recupera la mochila.
 *
 * <h2>⚠⚠ CUÁNDO SE GUARDA, Y POR QUÉ NO EN CADA CLIC</h2>
 *
 * Se guarda <b>al cerrar la pantalla</b> y <b>al desconectar</b>. Guardar en
 * cada movimiento serían decenas de escrituras por minuto y por jugador para un
 * dato que no cambia nada fuera de esa pantalla.
 *
 * <p>⚠ El precio está medido y es aceptable: si el servidor se cae con alguien
 * dentro de la mochila, pierde <b>lo que haya movido en esa sesión de
 * pantalla</b> — los objetos no se duplican ni desaparecen, vuelven a estar
 * donde estaban al abrir. Duplicar sí sería inaceptable; volver atrás no.
 */
public final class BackpackService {

    private final Database db;

    public BackpackService(Database db) {
        this.db = db;
    }

    /**
     * Lee la mochila entera. <b>Va por el executor de E/S.</b>
     *
     * <p>⚠ Un hueco ilegible se deja VACÍO y se avisa, en vez de abortar la
     * carga. Abortar dejaría al jugador sin mochila por un solo objeto roto —
     * y sin saber por qué.
     */
    public SimpleInventory cargar(long playerId, RegistryWrapper.WrapperLookup registros)
            throws SQLException {
        var inv = new SimpleInventory(Mochila.HUECOS);
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT slot, payload FROM backpack_slot WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt(1);
                    if (slot < 0 || slot >= Mochila.HUECOS) {
                        // Pasa si algún día se reducen las filas. El objeto no
                        // se borra de la base: se ignora, y vuelve solo si se
                        // amplían otra vez.
                        continue;
                    }
                    try {
                        var pila = ItemCodec.decode(rs.getBytes(2), registros);
                        inv.setStack(slot, pila);
                    } catch (Exception e) {
                        LunaEternal.LOG.error(
                            "Hueco {} de la mochila de {} ilegible: {}",
                            slot, playerId, e.toString());
                    }
                }
            }
        }
        return inv;
    }

    /**
     * Escribe la mochila entera.
     *
     * <h2>⚠⚠⚠ BORRAR Y ESCRIBIR EN UNA TRANSACCIÓN, NO EN DOS PASOS</h2>
     *
     * Si el borrado se confirmara y la escritura fallara, la mochila quedaría
     * <b>vacía</b> — y el jugador habría perdido todo sin que nada diera error.
     * Con la transacción, o queda la nueva o queda la de antes.
     *
     * <p>⚠ Los huecos vacíos <b>no se guardan</b>: la fila se borra. Guardar
     * aire ocuparía sitio y, peor, haría que «no hay fila» y «hay una fila con
     * aire» fueran dos formas de decir lo mismo.
     */
    public void guardar(long playerId, SimpleInventory inv,
                        RegistryWrapper.WrapperLookup registros) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement borrar = c.prepareStatement(
                        "DELETE FROM backpack_slot WHERE player_id = ?")) {
                    borrar.setLong(1, playerId);
                    borrar.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO backpack_slot (player_id, slot, payload) "
                      + "VALUES (?,?,?)")) {
                    int escritos = 0;
                    for (int i = 0; i < inv.size(); i++) {
                        ItemStack pila = inv.getStack(i);
                        if (pila.isEmpty()) {
                            continue;
                        }
                        ps.setLong(1, playerId);
                        ps.setInt(2, i);
                        ps.setBytes(3, ItemCodec.encode(pila, registros));
                        ps.addBatch();
                        escritos++;
                    }
                    if (escritos > 0) {
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Cuántos objetos tiene guardados. Para el aviso de bajar de rango.
     *
     * <p>⚠ Cuenta <b>solo por encima de la fila indicada</b>, que es la
     * pregunta que de verdad importa: «si este jugador baja a este rango, ¿se
     * le queda algo encerrado?».
     */
    public int atrapadosPorEncima(long playerId, int filas) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM backpack_slot "
               + "WHERE player_id = ? AND slot >= ?")) {
            ps.setLong(1, playerId);
            ps.setInt(2, filas * Mochila.COLUMNAS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
