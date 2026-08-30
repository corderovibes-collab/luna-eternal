package net.pokereport.luna.gym;

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

/**
 * LAS MEDALLAS DE GIMNASIO.
 *
 * <h2>No son un objeto, y es orden del usuario</h2>
 *
 * <i>«Si derrota a Brock obtiene la medalla, pero no física: la obtienen ya en
 * el PokePad»</i>. El PokePad <b>ya las dibujaba</b> —dieciséis casillas abajo a
 * la izquierda, apagadas las que no se tienen— y lo único que le faltaba era que
 * alguien rellenara el número. Esto es ese alguien.
 *
 * <p>⚠ Es la misma decisión que los trajes (V023) y por el mismo motivo: un
 * objeto se tira, se pierde al morir y <b>se puede regalar</b> — y una medalla
 * regalada deja de decir «yo gané a Brock», que es lo único que una medalla
 * significa.
 *
 * <h2>⚠⚠ LA CACHÉ NO ES UNA OPTIMIZACIÓN, ES UN REQUISITO</h2>
 *
 * La máscara la pregunta el diálogo del gimnasio <b>en el momento del clic
 * derecho</b>, que corre en el hilo del servidor — donde consultar la base está
 * prohibido. Se lee una vez al entrar y a partir de ahí manda la caché. Es
 * exactamente la misma decisión, y por el mismo motivo, que
 * {@link net.pokereport.luna.rank.RankService}.
 *
 * <p>⚠ Y por eso conceder una medalla escribe <b>en los dos sitios</b>, con la
 * base primero: al revés, un fallo al escribir dejaría al jugador viendo una
 * medalla que no tiene, y creyéndosela hasta salir.
 *
 * <h2>⚠⚠⚠ LA MÁSCARA SE COMPONE, NO SE GUARDA</h2>
 *
 * En la base hay una fila por medalla ganada; el número de dieciséis bits que
 * viaja al PokePad se monta al leer. Guardar la máscara sería más compacto y
 * <b>no se podría consultar</b>: «cuánta gente tiene la de Brock» pasaría a ser
 * un barrido con aritmética de bits, y «cuándo la ganó» no cabría en ninguna
 * parte.
 *
 * <p>⚠ El bit de una medalla es {@code Gimnasio_.sala()}, que es el orden de
 * gimnasio — el mismo con el que el PokePad dibuja las dieciséis. Si algún día
 * dejaran de coincidir, la medalla de Brock se encendería en la casilla de
 * Misty <b>sin dar ningún error</b>, así que hay una comprobación en el autotest
 * que ata las dos listas.
 */
public final class MedallaService {

    private final Database db;

    /**
     * uuid -&gt; máscara de bits.
     *
     * <p>⚠ Por UUID y no por {@code player_id}: quien pregunta tiene delante al
     * jugador de Minecraft, no su identificador de nuestra base. Resolver el id
     * aquí obligaría a consultar, que es justo lo que la caché evita.
     */
    private static final Map<UUID, Integer> CACHE = new ConcurrentHashMap<>();

    public MedallaService(Database db) {
        this.db = db;
    }

    /**
     * Las medallas que tiene ahora mismo, sin tocar la base.
     *
     * <p>⚠ Sin entrada en la caché devuelve <b>cero</b>. Pasa entre que alguien
     * entra y que la base contesta, y es la respuesta correcta: dar de más por
     * no haber leído todavía sería regalar una medalla.
     */
    public static int enCache(UUID uuid) {
        return CACHE.getOrDefault(uuid, 0);
    }

    /** ¿Tiene ya la de este gimnasio? */
    public static boolean tiene(UUID uuid, Gimnasio.Gimnasio_ g) {
        return (enCache(uuid) & (1 << g.sala())) != 0;
    }

    /** Cuántas lleva. Es lo que abre el gimnasio siguiente. */
    public static int cuantas(UUID uuid) {
        return Integer.bitCount(enCache(uuid));
    }

    /** Se olvida al salir: la caché es de los que están dentro. */
    public static void olvidar(UUID uuid) {
        CACHE.remove(uuid);
    }

    /** Para las pruebas: deja la caché como recién arrancado. */
    public static void olvidarTodo() {
        CACHE.clear();
    }

    /** Solo para el autotest: mete una máscara sin pasar por la base. */
    public static void ponerEnCache(UUID uuid, int mascara) {
        CACHE.put(uuid, mascara);
    }

    /**
     * Carga las medallas al entrar. <b>Va por el executor de E/S.</b>
     *
     * @param despues qué hacer cuando ya están
     */
    public void cargar(UUID uuid, String nombre, Runnable despues) {
        LunaEternal.submit(() -> {
            int mascara = 0;
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                mascara = leer(id);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron leer las medallas de {}: {}",
                        nombre, e.toString());
            }
            CACHE.put(uuid, mascara);
            if (despues != null) {
                despues.run();
            }
        });
    }

    /** La máscara de un jugador leída de la base. <b>Hilo de E/S.</b> */
    public int leer(long playerId) throws SQLException {
        int mascara = 0;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT gym FROM gym_badge WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var g = Gimnasio.de(rs.getString(1));
                    // ⚠ Una fila de un gimnasio que ya no existe se SALTA en vez
                    //   de reventar la lectura: si algún día se renombra uno, lo
                    //   que se pierde es una medalla en la pantalla, no la
                    //   sesión entera del jugador.
                    if (g != null) {
                        mascara |= 1 << g.sala();
                    }
                }
            }
        }
        return mascara;
    }

    /**
     * Concede la medalla. <b>Va por el executor de E/S.</b>
     *
     * <p>⚠⚠ {@code INSERT IGNORE} y no «mira si está y luego inserta»: entre
     * mirar e insertar cabe otro combate. Con la clave primaria (player_id, gym)
     * la base es la que decide, y el segundo intento no hace nada en vez de
     * fallar.
     *
     * @param despues se le pasa {@code true} si la medalla es NUEVA, y corre en
     *                el hilo del servidor — que es donde se puede avisar al
     *                jugador y donde se puede tocar el mundo
     */
    public void conceder(ServerPlayerEntity jugador, Gimnasio.Gimnasio_ g,
                         java.util.function.Consumer<Boolean> despues) {
        UUID uuid = jugador.getUuid();
        String nombre = jugador.getName().getString();
        var servidor = jugador.getServer();
        LunaEternal.submit(() -> {
            boolean nueva = false;
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                try (Connection c = db.connection();
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT IGNORE INTO gym_badge (player_id, gym) "
                         + "VALUES (?, ?)")) {
                    ps.setLong(1, id);
                    ps.setString(2, g.id());
                    nueva = ps.executeUpdate() > 0;
                }
                // ⚠ La base PRIMERO y la caché después. Al revés, un fallo al
                //   escribir dejaría al jugador viendo una medalla que no tiene.
                CACHE.merge(uuid, 1 << g.sala(), (a, b) -> a | b);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo conceder la medalla {} a {}",
                        g.id(), nombre, e);
            }
            final boolean fue = nueva;
            if (despues != null && servidor != null) {
                servidor.execute(() -> despues.accept(fue));
            }
        });
    }

    /**
     * QUITA UNA MEDALLA. <b>Va por el executor de E/S.</b>
     *
     * <h2>⚠ Existe para PROBAR, y por eso es de nivel 4</h2>
     *
     * Sin esto, un gimnasio solo se puede pelear <b>una vez por cuenta</b> —
     * que es justo lo que se quiere en el juego y justo lo que estorba al
     * probarlo. Mismo motivo que {@code /luna reiniciarinicial}.
     *
     * <p>⚠⚠ Y borra en <b>los dos sitios</b>, la base primero. Si solo se
     * borrara la fila, la caché seguiría diciendo que la tiene y el diálogo
     * seguiría contestando «ya la tienes» hasta que el jugador volviera a
     * entrar — el fallo mudo de siempre, con la mitad del estado vieja.
     *
     * @param despues se le pasa {@code true} si de verdad había una que quitar
     */
    public void quitar(ServerPlayerEntity jugador, Gimnasio.Gimnasio_ g,
                       java.util.function.Consumer<Boolean> despues) {
        UUID uuid = jugador.getUuid();
        String nombre = jugador.getName().getString();
        var servidor = jugador.getServer();
        LunaEternal.submit(() -> {
            boolean habia = false;
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                try (Connection c = db.connection();
                     PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM gym_badge WHERE player_id = ? AND gym = ?")) {
                    ps.setLong(1, id);
                    ps.setString(2, g.id());
                    habia = ps.executeUpdate() > 0;
                }
                CACHE.computeIfPresent(uuid, (k, v) -> v & ~(1 << g.sala()));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo quitar la medalla {} a {}",
                        g.id(), nombre, e);
            }
            final boolean fue = habia;
            if (despues != null && servidor != null) {
                servidor.execute(() -> despues.accept(fue));
            }
        });
    }

    /** Cuánta gente tiene cada medalla. Para el informe de administración. */
    public Map<String, Integer> reparto() throws SQLException {
        var salida = new java.util.LinkedHashMap<String, Integer>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT gym, COUNT(*) FROM gym_badge GROUP BY gym");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                salida.put(rs.getString(1), rs.getInt(2));
            }
        }
        return salida;
    }
}
