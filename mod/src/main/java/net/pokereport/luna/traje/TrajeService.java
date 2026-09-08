package net.pokereport.luna.traje;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;

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

    /**
     * uuid -> los trajes que ha adquirido. Sin entrada = todavia no ha entrado.
     *
     * <p>⚠⚠ NO INCLUYE EL ENTRENADOR: ese es gratis y lo dice
     * {@link Traje#gratis()}. Meterlo aqui obligaria a que alguien lo insertara,
     * y esa insercion es justo la que un dia falta.
     */
    private static final Map<UUID, Set<String>> TENGO = new ConcurrentHashMap<>();

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
        TENGO.remove(uuid);
    }

    /**
     * ¿Tiene este jugador este traje?
     *
     * <h2>⚠⚠ SE LEE DE MEMORIA, Y NO ES UNA OPTIMIZACION</h2>
     *
     * Lo pregunta la pantalla de KITS y lo pregunta cada vez que alguien se
     * pone un traje, en el hilo del servidor. Consultar la base ahi esta
     * prohibido (R1). Se lee una vez al entrar.
     *
     * <p>⚠ Y comprueba tambien {@code listo()}: un traje sin arte no se puede
     * poner ni habiendolo comprado. Sin eso se equipa, se sincroniza, no da
     * ningun error y el jugador NO VE NADA.
     */
    public static boolean tiene(UUID uuid, Traje t) {
        if (t == null || !t.listo()) {
            return false;
        }
        if (t.gratis()) {
            return true;
        }
        Set<String> suyos = TENGO.get(uuid);
        return suyos != null && suyos.contains(t.id());
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
        cargarPropiedad(playerId, uuid);
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
     * Lee de la base los trajes que ha adquirido.
     *
     * <p>⚠ Si falla, se deja el conjunto VACIO en vez de no poner nada: sin
     * entrada, {@link #tiene} no sabria distinguir «no tiene ninguno» de «aun no
     * se ha leido», y lo segundo se leeria como lo primero de todas formas. Con
     * el conjunto vacio el jugador ve sus trajes bloqueados —que es visible y se
     * reporta— en vez de podérselos poner por un fallo de lectura.
     */
    private void cargarPropiedad(long playerId, UUID uuid) {
        Set<String> suyos = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT suit FROM player_suit_owned WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    if (Traje.de(id) != null) {
                        suyos.add(id);
                    } else if (id != null) {
                        // ⚠ Una fila que nombra un traje que ya no existe no es
                        //   inofensiva: es una compra que el jugador hizo y que
                        //   hoy no puede usar. Se dice, no se ignora en silencio.
                        LunaEternal.LOG.warn(
                                "{} tiene comprado el traje {}, que ya no existe",
                                uuid, id);
                    }
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudieron leer los trajes de {}", uuid, e);
        }
        TENGO.put(uuid, suyos);
    }

    /**
     * Le concede un traje. Es lo que llama Tebex por consola.
     *
     * <p>⚠⚠ ESCRIBE EN LOS DOS SITIOS, LA BASE PRIMERO. Al reves, un fallo al
     * guardar le dejaria el traje disponible hasta reconectar y entonces se le
     * caeria solo — que parece que se lo hemos quitado.
     *
     * <p>⚠ `INSERT IGNORE` y no un `SELECT` antes: concederlo dos veces tiene
     * que ser inofensivo. Un webhook de Tebex reintenta, y dos comandos a la vez
     * no se ven el uno al otro. La clave primaria (player_id, suit) es la que
     * decide, no una comprobacion en Java.
     *
     * @return {@code true} si no lo tenia y ahora si
     */
    public boolean conceder(long playerId, UUID uuid, Traje t) {
        if (t == null || t.gratis()) {
            return false;
        }
        int filas;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO player_suit_owned (player_id, suit) "
                     + "VALUES (?, ?)")) {
            ps.setLong(1, playerId);
            ps.setString(2, t.id());
            filas = ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo conceder el traje {} a {}", t.id(), uuid, e);
            return false;
        }
        // ⚠ `uuid` es null si esta desconectado, y entonces no hay cache que
        //   tocar: se rellena al entrar. La fila ya esta escrita, que es lo que
        //   importa.
        if (uuid != null) {
            TENGO.computeIfAbsent(uuid,
                    k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(t.id());
        }
        return filas > 0;
    }

    /**
     * Le retira un traje (devolucion, contracargo).
     *
     * <p>⚠⚠ NO LE QUITA EL PUESTO. De eso se encarga {@link #revisar}, que corre
     * al entrar y es el unico sitio donde se mira si lo que llevas sigue siendo
     * tuyo. Hacerlo aqui tambien obligaria a tener el ServerPlayerEntity, que no
     * existe si el jugador esta desconectado — y una devolucion llega cuando
     * llega.
     *
     * @return {@code true} si lo tenia
     */
    public boolean retirar(long playerId, UUID uuid, Traje t) {
        if (t == null || t.gratis()) {
            return false;
        }
        int filas;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM player_suit_owned WHERE player_id = ? AND suit = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, t.id());
            filas = ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo retirar el traje {} a {}", t.id(), uuid, e);
            return false;
        }
        Set<String> suyos = uuid == null ? null : TENGO.get(uuid);
        if (suyos != null) {
            suyos.remove(t.id());
        }
        return filas > 0;
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
            // ⚠⚠ EL PERMISO SE COMPRUEBA AQUI Y NO EN LA PANTALLA. El cliente
            //    manda un identificador y nada mas (P6): si se fiara de el, un
            //    cliente modificado se pondria el de LEYENDA sin comprarlo.
            if (!tiene(jugador.getUuid(), t)) {
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
     * Le quita el traje puesto a quien ya no lo tiene.
     *
     * <h2>⚠⚠ HACE FALTA PORQUE UN TRAJE SE PUEDE RETIRAR</h2>
     *
     * Una devolución o un contracargo borran la fila, y eso puede pasar con el
     * jugador desconectado. El permiso solo se mira al <b>ponérselo</b>, y ya se
     * lo puso: sin esta revisión seguiría llevándolo para siempre. Se comprueba
     * al entrar, que es cuando el estado se vuelve a leer.
     *
     * <p>⚠⚠ Y ya NO se dispara por bajar de rango, que es lo que hacía antes.
     * Con la propiedad separada del rango, bajar de rango <b>no</b> quita nada:
     * lo comprado es comprado. Lo que sí lo dispara es que le retiren el traje,
     * o que se le quite el arte (deja de estar {@code listo}).
     *
     * @return {@code true} si hubo que quitárselo
     */
    public boolean revisar(ServerPlayerEntity jugador, long playerId) {
        String id = PUESTO.get(jugador.getUuid());
        if (id == null) {
            return false;
        }
        if (tiene(jugador.getUuid(), Traje.de(id))) {
            return false;
        }
        LunaEternal.LOG.info("{} llevaba el traje {} y ya no lo tiene: se retira",
                jugador.getGameProfile().getName(), id);
        return ponerse(jugador, playerId, null);
    }
}
