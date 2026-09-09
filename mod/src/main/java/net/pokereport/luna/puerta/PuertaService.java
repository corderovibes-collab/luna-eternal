package net.pokereport.luna.puerta;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUIEN HA CRUZADO LA PUERTA.
 *
 * <p>Una sola pregunta: <i>¿este jugador ha entrado alguna vez al mundo por el
 * lobby?</i> Quien no, empieza en el lobby y solo sale por el NPC.
 *
 * <h2>&#9888;&#9888; LA CACHE NO ES UNA OPTIMIZACION, ES UN REQUISITO</h2>
 *
 * La respuesta hace falta <b>en el momento del clic al NPC</b> y <b>al decidir
 * si mandar a alguien al lobby</b>, y las dos cosas corren en el hilo del
 * servidor -- donde consultar la base esta prohibido (data-model.md §4). Se lee
 * una vez al entrar y se responde de memoria, igual que el rango y las medallas.
 *
 * <p>&#9888; Y al cruzar se escribe <b>en los dos sitios, la base PRIMERO</b>.
 * Al reves, una caida entre las dos escrituras dejaria al jugador «graduado»
 * en memoria y sin fila: volveria al lobby en el siguiente arranque, despues de
 * haber entrado al mundo.
 */
public final class PuertaService {

    private final Database db;

    /**
     * Lo que sabemos de cada jugador conectado.
     *
     * <p>&#9888; {@code Boolean} y no {@code boolean}: <b>«todavia no ha
     * contestado la base» es un estado distinto de «no ha cruzado»</b>, y
     * confundirlos manda al lobby a alguien que si habia cruzado, solo porque su
     * consulta aun no habia vuelto. Quien pregunta tiene que distinguirlos.
     */
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public PuertaService(Database db) {
        this.db = db;
    }

    /**
     * Lo que sabemos ya, sin ir a la base.
     *
     * @return {@code null} si todavia no ha contestado
     */
    public Boolean cruzadaEnCache(UUID uuid) {
        return cache.get(uuid);
    }

    /** Lee de la base y avisa. El callback corre en el hilo de E/S. */
    public void cargar(UUID uuid, long playerId, Runnable cuandoEste) {
        LunaEternal.submit(() -> {
            try {
                cache.put(uuid, leer(playerId));
            } catch (SQLException e) {
                // ⚠⚠ ANTE UN FALLO SE ASUME QUE **SI** CRUZO, y es deliberado.
                //    Las dos equivocaciones no cuestan lo mismo: dar por nuevo a
                //    un veterano lo arranca de su casa y lo suelta en el lobby;
                //    dar por veterano a un nuevo solo hace que se salte una
                //    presentacion. Si la base falla, que falle hacia el lado que
                //    no mueve a nadie de sitio.
                LunaEternal.LOG.error("No se pudo leer la puerta de {}: se asume "
                        + "que ya cruzo para no moverlo de sitio", uuid, e);
                cache.put(uuid, Boolean.TRUE);
            }
            if (cuandoEste != null) {
                cuandoEste.run();
            }
        });
    }

    private boolean leer(long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM player_puerta WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Apunta que ha cruzado. Corre en el hilo de E/S.
     *
     * <p>&#9888; {@code INSERT ... ON DUPLICATE KEY} y no un {@code if}: cruzar
     * dos veces --dos clics rapidos al NPC-- no puede crear dos filas ni fallar.
     * Lo resuelve la clave primaria, venga de donde venga la peticion.
     */
    public void cruzar(UUID uuid, long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_puerta (player_id, cruzada_ms) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE player_id = player_id")) {
            ps.setLong(1, playerId);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
        cache.put(uuid, Boolean.TRUE);
    }

    /**
     * Deshace el cruce: vuelve a ser un jugador nuevo.
     *
     * <h2>&#9888;&#9888;&#9888; BORRA LA FILA **Y** LA CACHE, Y ESO ES TODO EL
     * COMANDO</h2>
     *
     * Borrar solo la fila no hace nada visible: la cache se rellena en el
     * evento de conexion y manda mientras el jugador siga dentro, asi que
     * seguiria contando como cruzado hasta que se desconectara. Es exactamente
     * por lo que {@code /luna reiniciarinicial} «no servia» en su dia --
     * «borraba la fila y el cliente seguia con su copia»--, y por lo que
     * reiniciar el inicial tiene que tocar DOS tablas.
     *
     * <p>&#9888; Se pone {@code FALSE} en vez de quitar la entrada: quitarla
     * dejaria «no lo se», y ante «no lo se» la puerta <b>deja pasar</b> a
     * proposito. El jugador no volveria al lobby hasta reconectar, que es justo
     * lo que este comando existe para evitar.
     */
    public void reiniciar(UUID uuid, long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM player_puerta WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            ps.executeUpdate();
        }
        cache.put(uuid, Boolean.FALSE);
    }

    public void olvidar(UUID uuid) {
        cache.remove(uuid);
    }

    /** Cuantos hay en memoria. Lo usa el autotest. */
    public int enCache() {
        return cache.size();
    }
}
