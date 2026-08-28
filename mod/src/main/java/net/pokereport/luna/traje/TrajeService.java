package net.pokereport.luna.traje;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.ui.Tablist;

/**
 * QUIÉN LLEVA QUÉ TRAJE.
 *
 * <h2>⚠⚠⚠ LA CACHÉ NO ES UNA OPTIMIZACIÓN, ES UN REQUISITO</h2>
 *
 * El traje lo pregunta <b>el dibujado de cada jugador, en cada fotograma</b>, y
 * eso corre en el hilo del servidor cuando alguien entra. Consultar la base ahí
 * está prohibido (R1). Se lee una vez al entrar y vive en memoria.
 *
 * <p>⚠ Y cambiarlo escribe <b>en los dos sitios</b>, la base primero: si se
 * escribiera la caché primero y la base fallara, el jugador vería su traje
 * puesto hasta reconectar y entonces se le caería solo.
 *
 * <h2>⚠⚠ EL ESTADO NO ES DE QUIEN LO MIRA</h2>
 *
 * Es la lección de los clanes, y aquí es más literal que en ningún sitio: un
 * traje lo ve <b>todo el mundo menos tú</b>. Si al cambiarlo solo se avisara al
 * que lo cambia, los demás seguirían viéndote con el anterior hasta que
 * reconectaran. Por eso {@link #ponerse} devuelve para que quien llama lo
 * reparta a todos.
 */
public final class TrajeService {

    /** uuid -> identificador del traje puesto. Sin entrada = ninguno. */
    private static final Map<UUID, String> PUESTO = new ConcurrentHashMap<>();

    private final Database db;

    public TrajeService(Database db) {
        this.db = db;
    }

    /** Lo que lleva puesto, o {@code null}. Se lee de memoria (R1). */
    public static String enCache(UUID uuid) {
        return PUESTO.get(uuid);
    }

    /** Todo lo que hay puesto ahora mismo, para mandárselo a quien entra. */
    public static Map<UUID, String> todos() {
        return Map.copyOf(PUESTO);
    }

    public static void olvidar(UUID uuid) {
        PUESTO.remove(uuid);
    }

    /**
     * Carga lo que lleva puesto al entrar.
     *
     * <p>⚠ Si la fila dice un traje que ya no existe —porque se le quitó el arte
     * o se renombró— se trata como «ninguno» en vez de propagarlo. Un
     * identificador muerto viajaría hasta el cliente y allí no dibujaría nada,
     * que es un fallo mudo; aquí muere en el sitio donde se puede ver.
     */
    public void cargar(long playerId, UUID uuid) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT suit FROM player_suit WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString(1);
                    if (Traje.de(id) != null) {
                        PUESTO.put(uuid, id);
                        return;
                    }
                    if (id != null) {
                        LunaEternal.LOG.warn(
                                "El traje guardado de {} ({}) ya no existe: se ignora",
                                uuid, id);
                    }
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo leer el traje de {}", uuid, e);
        }
        PUESTO.remove(uuid);
    }

    /**
     * Se pone (o se quita, con {@code null}) un traje.
     *
     * <p>⚠⚠ EL PERMISO SE COMPRUEBA AQUÍ Y NO EN LA PANTALLA. El cliente manda
     * un identificador y nada más; el rango lo mira el servidor (P6). Si se
     * fiara del cliente, un cliente modificado se pondría el de LEYENDA.
     *
     * @return {@code true} si cambió algo
     */
    public boolean ponerse(ServerPlayerEntity jugador, long playerId, String id) {
        Traje t = null;
        if (id != null && !id.isEmpty()) {
            t = Traje.de(id);
            if (t == null || !t.puede(Tablist.escalonDe(jugador))) {
                return false;
            }
        }
        String nuevo = t == null ? null : t.id();
        if (java.util.Objects.equals(nuevo, PUESTO.get(jugador.getUuid()))) {
            return false;
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_suit (player_id, suit) VALUES (?, ?) "
                     + "ON DUPLICATE KEY UPDATE suit = VALUES(suit)")) {
            ps.setLong(1, playerId);
            if (nuevo == null) {
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setString(2, nuevo);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo guardar el traje de {}",
                    jugador.getGameProfile().getName(), e);
            return false;
        }
        // La base primero, la cache despues: al reves, un fallo al escribir
        // dejaria al jugador con el traje puesto hasta reconectar.
        if (nuevo == null) {
            PUESTO.remove(jugador.getUuid());
        } else {
            PUESTO.put(jugador.getUuid(), nuevo);
        }
        return true;
    }

    /**
     * Le quita el traje a quien ya no llega a su rango.
     *
     * <p>⚠⚠ HACE FALTA PORQUE EL RANGO SE PUEDE BAJAR. Sin esto, alguien que
     * baje de MAESTRO a ELITE <b>seguiría llevando el traje de MAESTRO</b> para
     * siempre: el permiso solo se mira al ponérselo, y ya se lo puso. Se
     * comprueba al entrar, que es cuando el estado se vuelve a leer.
     *
     * @return {@code true} si hubo que quitárselo
     */
    public boolean revisar(ServerPlayerEntity jugador, long playerId) {
        String id = PUESTO.get(jugador.getUuid());
        if (id == null) {
            return false;
        }
        Traje t = Traje.de(id);
        if (t != null && t.puede(Tablist.escalonDe(jugador))) {
            return false;
        }
        LunaEternal.LOG.info("{} llevaba el traje {} y ya no le corresponde: se retira",
                jugador.getGameProfile().getName(), id);
        return ponerse(jugador, playerId, null);
    }
}
