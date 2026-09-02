package net.pokereport.luna.crate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;

/**
 * EL TIEMPO DE JUEGO ACTIVO, que es lo que da la llave del Gacha Diario.
 *
 * <h2>⚠⚠⚠ ACTIVO, NO CONECTADO, Y ESA ES TODA LA DECISIÓN</h2>
 *
 * Por tiempo <i>conectado</i>, la recompensa se la lleva quien deja el juego
 * abierto de fondo mientras hace otra cosa — y el que juega de verdad cobra lo
 * mismo. Por tiempo <b>activo</b> la cobra quien juega.
 *
 * <p>Y no es solo justicia: una llave diaria por estar conectado <b>llena el
 * servidor de gente quieta</b>, que consume chunks, entidades y memoria sin
 * jugar. Eso lo paga el rendimiento de todos.
 *
 * <h2>⚠⚠ QUÉ CUENTA COMO ACTIVO, Y POR QUÉ ASÍ</h2>
 *
 * <b>Moverse de sitio.</b> Ni mirar, ni tener el juego en primer plano.
 *
 * <ul>
 *   <li>⚠ <b>Mirar no cuenta</b>: si contara la rotación, un ratón apoyado en
 *       una superficie que vibra daría tiempo activo toda la noche. Ya se tomó
 *       esta misma decisión en {@code world/Espera}.</li>
 *   <li>⚠ <b>Y hay margen</b>: medio bloque. Sin él, el balanceo de un barco o
 *       la deriva de un jugador en el agua contarían como jugar.</li>
 * </ul>
 *
 * <h2>⚠⚠ SE ACUMULA EN MEMORIA Y SE GUARDA CADA POCO</h2>
 *
 * Escribir en la base cada segundo serían <b>una consulta por jugador por
 * segundo</b>, para siempre. Se lleva la cuenta en memoria y se vuelca cada
 * {@link #GUARDAR_CADA} segundos, y también al salir.
 *
 * <p>⚠ El peor caso de perder el volcado es <b>un minuto de progreso</b>, y eso
 * es aceptable. Perderlo todo no lo sería — por eso el guardado al salir.
 */
public final class Actividad {

    private Actividad() {}

    /** Cuánto hay que jugar para la llave diaria. */
    public static final int SEGUNDOS_LLAVE = 3600;   // una hora

    /** Cada cuánto se vuelca a la base lo acumulado en memoria. */
    private static final int GUARDAR_CADA = 60;

    /** Cuánto se tiene que mover para contar. Medio bloque. */
    private static final double MARGEN2 = 0.25;      // al cuadrado

    /**
     * CUÁNTO SE SIGUE CONTANDO DESPUÉS DEL ÚLTIMO MOVIMIENTO.
     *
     * <h2>⚠⚠⚠ «MOVERSE ESTE SEGUNDO» ERA DEMASIADO ESTRECHO</h2>
     *
     * Con esa regla <b>no contaban como jugar</b>: pescar, un combate Pokémon
     * entero, leer el PokePad, comerciar en el GTS ni construir en un sitio. Y
     * lo peor: <b>con una pantalla abierta no te puedes mover</b>, así que la
     * cuenta atrás del Gacha Diario se quedaba clavada justo mientras la
     * mirabas — correcto según la regla, y se lee como «esto está roto».
     *
     * <p>Tres minutos de tolerancia lo arreglan sin abrir la puerta al AFK: un
     * combate, un menú o una pausa corta siguen contando; dejar el juego puesto
     * mientras haces otra cosa deja de contar a los tres minutos.
     *
     * <p>⚠ Y sigue sin contar la rotación: girar el ratón NO reinicia la
     * tolerancia. Un ratón apoyado en algo que vibra daría la llave sola.
     */
    private static final long TOLERANCIA_MS = 180_000;   // 3 minutos

    /** El cofre cuya llave se regala. */
    public static final String COFRE_DIARIO = "gacha_diario";

    /**
     * Lo que se lleva de cada jugador conectado.
     *
     * @param dia         qué día es el total. Ver el javadoc de {@link #tick}
     * @param total       segundos activos de HOY, contando lo no volcado
     * @param sinGuardar  cuántos de esos aún no están en la base
     */
    private record Estado(Vec3d donde, java.time.LocalDate dia, int total,
                          int sinGuardar, long ultimoMovimiento) {}

    private static final Map<UUID, Estado> VIVOS = new ConcurrentHashMap<>();

    /** Lo que ya está guardado hoy, por jugador. Se lee al entrar. */
    private static final Map<UUID, int[]> HOY = new ConcurrentHashMap<>();

    private static Database db;

    public static void arrancar(Database base) {
        db = base;
    }

    // ------------------------------------------------------------ el tick

    /**
     * Una vez por segundo. Suma a quien se haya movido.
     *
     * <p>⚠ Va en el hilo del servidor y <b>no toca la base</b> salvo en el
     * volcado, que se manda al executor de E/S. Consultar aquí sería una
     * consulta por jugador por segundo.
     */
    public static void tick(MinecraftServer servidor) {
        if (db == null) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        for (ServerPlayerEntity p : servidor.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            Vec3d ahora = p.getPos();
            Estado e = VIVOS.get(id);
            if (e == null) {
                VIVOS.put(id, new Estado(ahora, hoy, 0, 0, System.currentTimeMillis()));
                continue;
            }
            // ⚠⚠ CAMBIO DE DÍA CON EL JUGADOR DENTRO. A medianoche la fila de
            //    la base pasa a ser otra, y si el total de memoria siguiera
            //    creciendo el jugador tendría la llave del día nuevo NADA MÁS
            //    empezarlo -- con el tiempo del día anterior. Se vuelca lo que
            //    quede del día viejo y se empieza de cero.
            if (!hoy.equals(e.dia())) {
                if (e.sinGuardar() > 0) {
                    volcar(id, e.dia(), e.sinGuardar());
                }
                VIVOS.put(id, new Estado(ahora, hoy, 0, 0, System.currentTimeMillis()));
                HOY.remove(id);
                continue;
            }
            long ahoraMs = System.currentTimeMillis();
            boolean semovio = e.donde().squaredDistanceTo(ahora) > MARGEN2;
            long ultimo = semovio ? ahoraMs : e.ultimoMovimiento();
            // ⚠⚠ CUENTA SI SE HA MOVIDO EN LOS ULTIMOS TRES MINUTOS, no si se
            //    mueve AHORA MISMO. Ver TOLERANCIA_MS: con la regla estrecha,
            //    un combate entero o mirar el PokePad no contaban como jugar.
            boolean activo = (ahoraMs - ultimo) < TOLERANCIA_MS;
            int total = e.total() + (activo ? 1 : 0);
            int sin = e.sinGuardar() + (activo ? 1 : 0);
            if (sin >= GUARDAR_CADA) {
                volcar(id, hoy, sin);
                sin = 0;
            }
            VIVOS.put(id, new Estado(ahora, hoy, total, sin, ultimo));
        }
    }

    /** Al salir: se vuelca lo que quede, o se pierde. */
    public static void alSalir(ServerPlayerEntity jugador) {
        Estado e = VIVOS.remove(jugador.getUuid());
        if (e != null && e.sinGuardar() > 0) {
            volcar(jugador.getUuid(), e.dia(), e.sinGuardar());
        }
        HOY.remove(jugador.getUuid());
    }

    /** Al entrar: se lee lo que ya llevaba hoy. */
    public static void alEntrar(ServerPlayerEntity jugador, long playerId) {
        UUID uuid = jugador.getUuid();
        VIVOS.put(uuid, new Estado(jugador.getPos(), LocalDate.now(), 0, 0,
                System.currentTimeMillis()));
        LunaEternal.submit(() -> {
            try {
                int[] v = leer(playerId);
                HOY.put(uuid, v);
                // ⚠⚠ EL TOTAL ARRANCA DE LO QUE YA LLEVABA HOY. Sin esto, quien
                //    reconecta empieza otra vez de cero y la pantalla le dice
                //    que le falta una hora entera -- cuando la base sabe que ya
                //    llevaba cincuenta minutos.
                //    ⚠ Se SUMA lo que haya contado entre medias en vez de
                //      asignar: entre que se pidio la lectura y llego, el tick
                //      ya puede haber contado unos segundos, y asignar los
                //      tiraria.
                VIVOS.computeIfPresent(uuid, (k, e) ->
                        new Estado(e.donde(), e.dia(), e.total() + v[0],
                                   e.sinGuardar(), e.ultimoMovimiento()));
            } catch (SQLException e) {
                LunaEternal.LOG.warn("No se pudo leer la actividad de {}",
                        jugador.getGameProfile().getName(), e);
            }
        });
    }

    /**
     * Vuelca a la base los segundos que aún no estaban.
     *
     * <h2>⚠⚠⚠ NO TOCA EL TOTAL DE MEMORIA, Y AHÍ ESTABA EL CONTADOR QUE SALTABA
     * HACIA ATRÁS</h2>
     *
     * Antes el total se calculaba como «lo guardado en la base + lo no
     * volcado». El volcado es asíncrono, así que había una ventana —entre que
     * {@code tick} pone {@code sinGuardar} a cero y la base contesta— en la que
     * <b>se habían restado sesenta segundos y aún no se habían sumado</b>: la
     * cuenta atrás daba un salto de un minuto hacia atrás, cada minuto.
     *
     * <p>Y no daba ningún error. Se veía como «esto va mal contado».
     *
     * <p>Hoy el total vive <b>solo en memoria</b> y la base es únicamente
     * persistencia. Un sitio, un número.
     *
     * @param dia el día al que pertenecen esos segundos. <b>No {@code now()}</b>:
     *            un volcado que cruza la medianoche los sumaría al día nuevo
     */
    private static void volcar(UUID uuid, LocalDate dia, int segundos) {
        Long id = LunaEternal.players().cachedId(uuid);
        if (id == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_activity (player_id, day, seconds)
                    VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE seconds = seconds + VALUES(seconds)
                    """)) {
                ps.setLong(1, id);
                ps.setObject(2, dia);
                ps.setInt(3, segundos);
                ps.executeUpdate();
            } catch (SQLException e) {
                LunaEternal.LOG.warn("No se pudo guardar la actividad", e);
            }
        });
    }

    /** {@code [segundos, reclamada]} de hoy. */
    private static int[] leer(long playerId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT seconds, claimed FROM player_activity "
                 + "WHERE player_id = ? AND day = ?")) {
            ps.setLong(1, playerId);
            ps.setObject(2, LocalDate.now());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[] {rs.getInt(1), rs.getBoolean(2) ? 1 : 0};
                }
            }
        }
        return new int[] {0, 0};
    }

    /**
     * Cuántos segundos activos lleva hoy, contando lo que aún no se ha volcado.
     *
     * <p>⚠ Se suma lo de memoria a lo guardado. Enseñar solo lo guardado haría
     * que la cuenta atrás <b>se quedara parada hasta un minuto</b>, y desde
     * fuera eso se lee como «esto no funciona».
     */
    /** ¿Se le está contando el tiempo ahora mismo? La pantalla lo dice. */
    public static boolean contandoAhora(UUID uuid) {
        Estado e = VIVOS.get(uuid);
        return e != null
                && (System.currentTimeMillis() - e.ultimoMovimiento()) < TOLERANCIA_MS;
    }

    public static int segundosHoy(UUID uuid) {
        Estado e = VIVOS.get(uuid);
        return e == null ? HOY.getOrDefault(uuid, new int[] {0, 0})[0] : e.total();
    }

    /** ¿Ya se llevó la llave de hoy? */
    public static boolean reclamadaHoy(UUID uuid) {
        return HOY.getOrDefault(uuid, new int[] {0, 0})[1] == 1;
    }

    /**
     * DA LA LLAVE DIARIA, si toca. Devuelve {@code true} si la dio.
     *
     * <p>⚠⚠⚠ LA MARCA `claimed` SE PONE EN LA MISMA TRANSACCIÓN QUE LA LLAVE, y
     * la condición {@code claimed = 0} va <b>dentro del UPDATE</b>. Si se
     * comprobara antes en Java, dos peticiones a la vez leerían las dos «aún no
     * la ha cogido» y darían <b>dos llaves</b>. Es la misma trampa que las
     * llaves al abrir, y la misma solución: que lo decida la base.
     */
    public static boolean reclamar(long playerId, UUID uuid) {
        if (db == null) {
            return false;
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // ⚠⚠⚠ SE VUELCA LO PENDIENTE ANTES DE COMPROBAR. La condicion
                //    `seconds >= 3600` mira LA BASE, y en la base puede faltar
                //    hasta un minuto de lo acumulado en memoria: sin esto, el
                //    boton se enciende --porque la pantalla mira memoria-- y al
                //    pulsarlo NO PASA NADA, porque la base aun no llega.
                //    Se veria como «el boton no funciona», que es de los peores
                //    fallos: el jugador no sabe si esperar o insistir.
                Estado vivo = VIVOS.get(uuid);
                if (vivo != null && vivo.sinGuardar() > 0) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO player_activity (player_id, day, seconds)
                            VALUES (?,?,?)
                            ON DUPLICATE KEY UPDATE seconds = seconds + VALUES(seconds)
                            """)) {
                        ps.setLong(1, playerId);
                        ps.setObject(2, vivo.dia());
                        ps.setInt(3, vivo.sinGuardar());
                        ps.executeUpdate();
                    }
                    VIVOS.computeIfPresent(uuid, (k, e) ->
                            new Estado(e.donde(), e.dia(), e.total(), 0,
                                       e.ultimoMovimiento()));
                }
                int cambiadas;
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE player_activity SET claimed = 1
                        WHERE player_id = ? AND day = ?
                          AND claimed = 0 AND seconds >= ?
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setObject(2, LocalDate.now());
                    ps.setInt(3, SEGUNDOS_LLAVE);
                    cambiadas = ps.executeUpdate();
                }
                if (cambiadas != 1) {
                    c.rollback();
                    return false;
                }
                LunaEternal.crates().darLlaves(c, playerId, COFRE_DIARIO, 1);
                c.commit();
                int[] v = leer(playerId);
                HOY.put(uuid, v);
                return true;
            } catch (Exception e) {
                c.rollback();
                LunaEternal.LOG.error("No se pudo dar la llave diaria", e);
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo dar la llave diaria", e);
            return false;
        }
    }

    /** Para el autotest: olvida lo que hay en memoria. */
    public static void olvidarTodo() {
        VIVOS.clear();
        HOY.clear();
    }
}
