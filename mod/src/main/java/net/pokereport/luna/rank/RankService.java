package net.pokereport.luna.rank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.ui.Tablist.Rank;

/**
 * Los rangos de jugador.
 *
 * <h2>Qué es un rango y qué no</h2>
 *
 * ENTRENADOR · ÉLITE · CAMPEÓN · MAESTRO · LEYENDA. Se conceden desde fuera del
 * juego —los da la administración— y <b>desbloquean comodidad</b>, empezando
 * por las filas de la mochila.
 *
 * <p>⚠ Un rango NO da ventaja de combate ni economía. Es la misma línea que
 * D-040 puso a los clanes: si un rango diera dinero o estadísticas, «tener
 * rango» pasaría a ser una estadística más y castigaría al que no lo tiene.
 *
 * <h2>⚠⚠ LA CACHÉ NO ES UNA OPTIMIZACIÓN, ES UN REQUISITO</h2>
 *
 * El rango lo pregunta el prefijo del tablist, el del chat y cada apertura de
 * la mochila — todo eso corre <b>en el hilo del servidor</b>, donde consultar la
 * base está prohibido (regla 1 del proyecto). Así que la base se lee <b>una vez
 * al entrar</b> y a partir de ahí manda la caché.
 *
 * <p>⚠ Y por eso cambiar un rango tiene que escribir <b>en los dos sitios</b>:
 * si solo se escribiera en la base, el jugador no vería el cambio hasta volver
 * a entrar; si solo en la caché, lo perdería al salir.
 */
public final class RankService {

    private final Database db;

    /**
     * ⚠ Por UUID y no por `player_id`: quien pregunta es el dibujado, que tiene
     * el jugador de Minecraft delante y no su identificador de nuestra base.
     * Resolver el id ahí obligaría a consultar, que es lo que evitamos.
     */
    private static final Map<UUID, Rank> CACHE = new ConcurrentHashMap<>();

    public RankService(Database db) {
        this.db = db;
    }

    /**
     * El rango que tiene ahora mismo, sin tocar la base.
     *
     * <p>⚠ Si no está en la caché devuelve el más bajo. Pasa entre que un
     * jugador entra y que la base contesta, y es la respuesta correcta: dar de
     * más por no haber leído todavía sería regalar lo que no le toca.
     */
    public static Rank enCache(UUID uuid) {
        return CACHE.getOrDefault(uuid, Rank.porDefecto());
    }

    /** Se olvida al salir: la caché es de los que están dentro. */
    public static void olvidar(UUID uuid) {
        CACHE.remove(uuid);
    }

    /**
     * Carga el rango al entrar. <b>Va por el executor de E/S.</b>
     *
     * @param despues qué hacer cuando ya está cargado, en el hilo del servidor
     */
    public void cargar(UUID uuid, String nombre, Runnable despues) {
        LunaEternal.submit(() -> {
            Rank r = Rank.porDefecto();
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                r = leer(id);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo leer el rango de {}: {}",
                        nombre, e.toString());
            }
            CACHE.put(uuid, r);
            if (despues != null) {
                despues.run();
            }
        });
    }

    private Rank leer(long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT rank_id FROM player WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Rank.de(rs.getString(1)) : Rank.porDefecto();
            }
        }
    }

    /**
     * Cambia el rango de un jugador. <b>Va por el executor de E/S.</b>
     *
     * <p>⚠⚠ NO SE PUEDE PONER UN RANGO DE EQUIPO. ADMIN, DEV y MODERADOR son
     * operativos: se dan con {@code /op} y con la configuración del servidor, no
     * con un comando de juego. Si se pudieran conceder aquí, cualquiera con
     * acceso al comando podría fabricarse un administrador.
     *
     * @return el rango que quedó, o {@code null} si no se pudo
     */
    public Rank cambiar(long playerId, UUID uuid, Rank nuevo) {
        if (nuevo == null || nuevo.equipo) {
            return null;
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE player SET rank_id = ? WHERE player_id = ?")) {
            ps.setString(1, nuevo.name());
            ps.setLong(2, playerId);
            if (ps.executeUpdate() != 1) {
                return null;
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo cambiar el rango de {}", playerId, e);
            return null;
        }
        // ⚠ La base PRIMERO y la caché después. Al revés, un fallo al escribir
        //   dejaría al jugador viendo un rango que no tiene — y creyéndoselo
        //   hasta que saliera.
        if (uuid != null) {
            CACHE.put(uuid, nuevo);
        }
        return nuevo;
    }

    /** Cuántos hay de cada rango. Para el informe de administración. */
    public Map<Rank, Integer> reparto() throws SQLException {
        var salida = new java.util.EnumMap<Rank, Integer>(Rank.class);
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT rank_id, COUNT(*) FROM player GROUP BY rank_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                salida.merge(Rank.de(rs.getString(1)), rs.getInt(2), Integer::sum);
            }
        }
        return salida;
    }
}
