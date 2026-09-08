package net.pokereport.luna.pase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

/**
 * EL PASE DE BATALLA: temporada, XP, compra de la via Luna y cobro de premios.
 *
 * <p>Como todo lo que toca la base, <b>nunca</b> se llama desde el hilo del
 * servidor: se entra por {@code LunaEternal.submit()}.
 *
 * <h2>&#9888;&#9888;&#9888; EL TOPE DIARIO VIVE AQUI Y EN NINGUN OTRO SITIO</h2>
 *
 * Todas las fuentes de XP pasan por {@link #ganar}, asi que el tope se aplica
 * una sola vez y da igual cuantas fuentes haya ni cuanto pague cada una. Si
 * cada fuente lo comprobara por su cuenta, bastaria con que una se olvidara
 * para que el pase se completara en dos dias &mdash; y el sintoma seria
 * <i>alguien va muy rapido</i>, que no se investiga hasta que ya han terminado
 * todos.
 *
 * <h2>&#9888;&#9888; LA TEMPORADA NO ROTA SOLA</h2>
 *
 * Las Cazas rotan al mirar y esta bien: lo que se pierde es un ciclo de 24 h.
 * Aqui rotar <b>borra el progreso de sesenta dias y la via Luna que alguien
 * pago con 15.000 LunaCoins</b>, asi que no puede dispararlo un reloj: lo hace
 * un operador con {@code /luna pase nueva_temporada}, igual que en la Torre.
 *
 * <p>&#9888; Y pasada la fecha de fin <b>se sigue ganando XP</b>. Pararla
 * castigaria al jugador por un despiste del operador, que no es suyo; la
 * pantalla dice que la temporada ha terminado y que las recompensas siguen
 * ahi.
 */
public final class PaseService {

    private final Database db;

    /**
     * Lo que se escribe en la columna {@code via}, que desde D-046 es siempre lo
     * mismo: solo hay UNA via y es de pago.
     */
    private static final String VIA = "luna";

    public PaseService(Database db) {
        this.db = db;
    }

    /** La temporada en curso. */
    public record Temporada(int numero, long empiezaMs, long acabaMs) {

        public boolean terminada() {
            return System.currentTimeMillis() >= acabaMs;
        }

        /** Dias que quedan. {@code 0} si ya ha terminado. */
        public int diasRestantes() {
            long ms = acabaMs - System.currentTimeMillis();
            return ms <= 0 ? 0 : (int) Math.ceil(ms / 86_400_000.0);
        }
    }

    /**
     * Todo lo que hay que saber para dibujar el pase.
     *
     * @param reclamadas los NIVELES ya cobrados
     */
    public record Estado(Temporada temporada, long xp, int nivel, boolean premium,
                         int xpHoy, int topeHoy, Set<Integer> reclamadas) {
    }

    /** Lo que ha pasado al conceder XP. */
    public record Ganancia(long pedida, long concedida, int nivelAntes, int nivelDespues) {

        public boolean subio() {
            return nivelDespues > nivelAntes;
        }

        /** Se corto por el tope del dia. La pantalla lo dice; el chat no. */
        public boolean topado() {
            return concedida < pedida;
        }
    }

    // ------------------------------------------------------------ temporada

    public Temporada temporada() throws SQLException {
        try (Connection c = db.connection()) {
            return temporada(c);
        }
    }

    private Temporada temporada(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT numero, empieza_ms, acaba_ms FROM pase_temporada WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Temporada(rs.getInt(1), rs.getLong(2), rs.getLong(3));
                }
            }
        }
        // La migracion inserta la fila; si no esta, algo se ha borrado a mano.
        // Se devuelve una temporada valida en vez de reventar el arranque: sin
        // pase se puede jugar, sin servidor no.
        long ahora = System.currentTimeMillis();
        return new Temporada(1, ahora, ahora + 60L * 86_400_000L);
    }

    /**
     * Avanza a la temporada siguiente.
     *
     * <p>&#9888; NO borra nada. Las filas viejas de {@code pase_jugador} y
     * {@code pase_reclamo} llevan el numero de temporada en la clave, asi que la
     * nueva empieza vacia sin tocar la anterior. Ademas de ser mas seguro que
     * un DELETE, permite mirar hacia atras: cuanta gente termino la temporada 1.
     */
    public Temporada nuevaTemporada(int dias) throws SQLException {
        long ahora = System.currentTimeMillis();
        long acaba = ahora + (long) Math.max(1, dias) * 86_400_000L;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE pase_temporada SET numero = numero + 1, "
               + "empieza_ms = ?, acaba_ms = ? WHERE id = 1")) {
            ps.setLong(1, ahora);
            ps.setLong(2, acaba);
            ps.executeUpdate();
        }
        return temporada();
    }

    // --------------------------------------------------------------- estado

    public Estado estado(long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());

            long xp = 0;
            boolean premium = false;
            int xpHoy = 0;
            int topeHoy = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT xp, premium, dia, xp_hoy, tope_hoy FROM pase_jugador "
                  + "WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, playerId);
                ps.setInt(2, t.numero());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        xp = rs.getLong(1);
                        premium = rs.getBoolean(2);
                        Date dia = rs.getDate(3);
                        // El tope que se enseña es el de HOY. Si la ultima vez
                        // que gano XP fue otro dia, lo de aquella fila ya no
                        // vale: se calcula el de ahora sin escribir nada.
                        boolean esHoy = dia != null && dia.toLocalDate().equals(LocalDate.now());
                        xpHoy = esHoy ? rs.getInt(4) : 0;
                        topeHoy = esHoy ? rs.getInt(5) : topeDe(dia);
                    }
                }
            }
            return new Estado(t, xp, PaseNivel.nivelDe(xp), premium, xpHoy, topeHoy,
                              reclamadas(c, playerId, t.numero()));
        }
    }

    private Set<Integer> reclamadas(Connection c, long playerId, int temporada)
            throws SQLException {
        Set<Integer> out = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT nivel FROM pase_reclamo "
              + "WHERE player_id = ? AND temporada = ?")) {
            ps.setLong(1, playerId);
            ps.setInt(2, temporada);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        }
        return out;
    }

    private void asegurarFila(Connection c, long playerId, int temporada)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO pase_jugador "
              + "(player_id, temporada, xp, premium, dia, xp_hoy, tope_hoy) "
              + "VALUES (?,?,0,0,NULL,0,0)")) {
            ps.setLong(1, playerId);
            ps.setInt(2, temporada);
            ps.executeUpdate();
        }
    }

    /**
     * El tope de hoy segun cuando fue la ultima vez que se gano XP.
     *
     * <p>&#9888;&#9888; ES DONDE VIVE EL DESCANSO ACUMULADO, y la cuenta es
     * exactamente <i>tantos dias como hayan pasado, hasta tres</i>. Quien no
     * juega un dia se lleva ese tope al siguiente, asi que un jugador de fin de
     * semana no queda fuera del pase; y como lo que se acumula son dias que YA
     * han pasado, el minimo de 44 dias naturales sigue en pie (ver
     * {@link PaseNivel}).
     *
     * <p>&#9888; Un {@code dia} en el FUTURO &mdash;reloj del servidor movido
     * hacia atras, o una restauracion&mdash; cuenta como un dia normal. Sin ese
     * caso, {@code dias} saldria negativo y el tope, cero: el pase dejaria de
     * avanzar y nadie sabria por que.
     */
    private static int topeDe(Date ultimo) {
        int dias;
        if (ultimo == null) {
            dias = 1;
        } else {
            long d = ChronoUnit.DAYS.between(ultimo.toLocalDate(), LocalDate.now());
            dias = d < 1 ? 1 : (int) Math.min(d, PaseNivel.DIAS_DESCANSO);
        }
        return Math.min(PaseNivel.TOPE_MAXIMO, PaseNivel.TOPE_DIARIO * dias);
    }

    // ------------------------------------------------------------------ XP

    /**
     * Suma XP del pase, acotada por el tope del dia.
     *
     * <p>&#9888; {@code FOR UPDATE} sobre la fila: el ejecutor de E/S tiene dos
     * hilos, asi que dos concesiones a la vez leerian el mismo {@code xp_hoy} y
     * <b>las dos cabrian en el tope</b>. Es el mismo motivo por el que la
     * economia bloquea la fila del saldo.
     */
    public Ganancia ganar(long playerId, long xp) throws SQLException {
        if (xp <= 0) {
            return new Ganancia(xp, 0, 0, 0);
        }
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());
            c.setAutoCommit(false);
            try {
                long total = 0;
                Date dia = null;
                int xpHoy = 0;
                int topeHoy = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT xp, dia, xp_hoy, tope_hoy FROM pase_jugador "
                      + "WHERE player_id = ? AND temporada = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setInt(2, t.numero());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Fila del pase no encontrada");
                        }
                        total = rs.getLong(1);
                        dia = rs.getDate(2);
                        xpHoy = rs.getInt(3);
                        topeHoy = rs.getInt(4);
                    }
                }

                LocalDate hoy = LocalDate.now();
                if (dia == null || !dia.toLocalDate().equals(hoy)) {
                    topeHoy = topeDe(dia);
                    xpHoy = 0;
                    dia = Date.valueOf(hoy);
                }

                int nivelAntes = PaseNivel.nivelDe(total);
                long hueco = Math.max(0, topeHoy - xpHoy);
                // Al completar el pase se deja de consumir tope: asi el numero
                // que ve el jugador no baja por XP que ya no le sirve.
                long cabe = Math.max(0, PaseNivel.total() - total);
                long real = Math.min(xp, Math.min(hueco, cabe));

                total += real;
                xpHoy += (int) real;

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pase_jugador SET xp = ?, dia = ?, xp_hoy = ?, "
                      + "tope_hoy = ? WHERE player_id = ? AND temporada = ?")) {
                    ps.setLong(1, total);
                    ps.setDate(2, dia);
                    ps.setInt(3, xpHoy);
                    ps.setInt(4, topeHoy);
                    ps.setLong(5, playerId);
                    ps.setInt(6, t.numero());
                    ps.executeUpdate();
                }
                c.commit();
                return new Ganancia(xp, real, nivelAntes, PaseNivel.nivelDe(total));
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * PONE AL JUGADOR EN ESE NIVEL EXACTO. Solo para probar (nivel 4).
     *
     * <h2>&#9888;&#9888; ES LA UNICA FORMA DE PROBAR EL FINAL DEL PASE</h2>
     *
     * {@code /luna pase xp} respeta el tope diario &mdash;tiene que hacerlo, o
     * dejaria de probar el sistema de verdad&mdash; asi que llegar al nivel 100
     * con el serian <b>cuarenta y cinco dias</b>. Sin esto no hay manera de
     * mirar el Charizard variocolor antes de que lo vea un jugador.
     *
     * <p>&#9888; Escribe la XP ACUMULADA de ese nivel, no un numero de nivel: el
     * nivel es una funcion pura de la XP ({@code PaseNivel.nivelDe}) y guardar
     * las dos cosas es justo lo que V032 evita.
     *
     * <p>&#9888; NO toca {@code pase_reclamo}: bajar de nivel no descobra nada,
     * porque lo cobrado ya esta en el inventario.
     */
    public void fijarNivel(long playerId, int nivel) throws SQLException {
        int n = Math.max(0, Math.min(PaseNivel.MAX, nivel));
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE pase_jugador SET xp = ? "
                  + "WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, PaseNivel.acumulada(n));
                ps.setLong(2, playerId);
                ps.setInt(3, t.numero());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Devuelve el pase a cero: XP, premium y reclamos de ESTA temporada.
     *
     * <p>&#9888; Para volver a probar el recorrido entero sin rotar la
     * temporada, que ademas afectaria a todo el mundo.
     */
    public void reiniciar(long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM pase_reclamo WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, playerId);
                ps.setInt(2, t.numero());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM pase_jugador WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, playerId);
                ps.setInt(2, t.numero());
                ps.executeUpdate();
            }
        }
    }

    // --------------------------------------------------------- via de pago

    /** Lo que pudo pasar al comprar la via Luna. */
    public record Compra(boolean ok, String clave) {
    }

    /**
     * Compra la via Luna de la temporada en curso.
     *
     * <p>&#9888;&#9888; LA CLAVE DE IDEMPOTENCIA LLEVA EL JUGADOR Y LA
     * TEMPORADA, y no un UUID nuevo. Un UUID recien generado no puede coincidir
     * con nada, asi que como clave no protege de nada: es idempotencia de
     * adorno. Con esta, dos clics rapidos cobran una vez &mdash; y la temporada
     * siguiente se puede volver a comprar, que es lo que tiene que pasar.
     *
     * <p>&#9888; El cobro y el {@code premium = 1} van en la MISMA transaccion
     * (R3). Separados, un fallo entre los dos deja a alguien pagando 15.000
     * LunaCoins sin via Luna, o con via Luna sin pagar.
     */
    public Compra comprarLuna(long playerId)
            throws SQLException, EconomyException {
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());

            String clave = "pase_luna:" + playerId + ":" + t.numero();
            c.setAutoCommit(false);
            try {
                boolean ya = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT premium FROM pase_jugador "
                      + "WHERE player_id = ? AND temporada = ? FOR UPDATE")) {
                    ps.setLong(1, playerId);
                    ps.setInt(2, t.numero());
                    try (ResultSet rs = ps.executeQuery()) {
                        ya = rs.next() && rs.getBoolean(1);
                    }
                }
                if (ya) {
                    c.rollback();
                    return new Compra(false, clave);
                }

                net.pokereport.luna.LunaEternal.economy().applyInTransaction(
                        c, playerId, Currency.REPORTCOIN, -PaseCatalogo.PRECIO,
                        "pase_luna", "pase", (long) t.numero(), clave);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pase_jugador SET premium = 1 "
                      + "WHERE player_id = ? AND temporada = ?")) {
                    ps.setLong(1, playerId);
                    ps.setInt(2, t.numero());
                    ps.executeUpdate();
                }
                c.commit();
                return new Compra(true, clave);
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Da la via Luna SIN COBRAR: reembolsos y premios de evento.
     *
     * <p>&#9888; No pasa por la economia a proposito. Un regalo no es una
     * compra, y meterlo por {@code comprarLuna} con importe cero ensuciaria el
     * libro de asientos con movimientos de 0 LunaCoins que no significan nada.
     * Quien quiera saber quien pago mira {@code ledger_entry}; quien tenga la
     * via y no salga ahi, la recibio.
     */
    public void darLuna(long playerId) throws SQLException {
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE pase_jugador SET premium = 1 "
                  + "WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, playerId);
                ps.setInt(2, t.numero());
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------- reclamos

    /** Un premio ya apuntado como cobrado y listo para entregar. */
    public record Cobro(int nivel, Recompensa recompensa) {
    }

    /**
     * Apunta un premio como cobrado y devuelve lo que hay que entregar.
     *
     * <p>&#9888;&#9888;&#9888; SE APUNTA ANTES DE ENTREGAR, y esa es toda la
     * defensa. La clave primaria de {@code pase_reclamo} hace que el segundo
     * intento falle EN LA BASE venga de donde venga &mdash; dos clics, un
     * cliente modificado, un reintento de red. Al reves (entregar y luego
     * apuntar) un fallo entre los dos pasos regala el premio otra vez, que es
     * la misma decision que ya tomo {@code StarterService}.
     *
     * <p>&#9888;&#9888; Y DESDE D-046 NADA SE ENTREGA DENTRO DE LA TRANSACCION,
     * porque ya no hay premios de moneda: los cien niveles dan <b>objetos o
     * Pokemon</b>, y ninguno de los dos es una tabla. Se entregan despues del
     * commit &mdash; los objetos con {@code offerOrDrop}, que no puede fallar, y
     * los Pokemon por el almacen de Cobblemon.
     *
     * @return {@code null} si no habia nada que cobrar o ya estaba cobrado
     */
    public Cobro reclamar(long playerId, int nivel) throws SQLException {
        var uno = reclamarVarios(playerId, List.of(nivel));
        return uno.isEmpty() ? null : uno.get(0);
    }

    /**
     * Cobra TODO lo que este disponible y no cobrado.
     *
     * <p>Va en una sola transaccion: cobrar cuarenta premios en cuarenta
     * transacciones deja cuarenta formas de quedarse a medias.
     */
    public List<Cobro> reclamarTodo(long playerId) throws SQLException {
        Estado e = estado(playerId);
        if (!e.premium()) {
            return List.of();
        }
        List<Integer> niveles = new ArrayList<>();
        for (int n = 1; n <= e.nivel(); n++) {
            if (PaseCatalogo.de(n) != null && !e.reclamadas().contains(n)) {
                niveles.add(n);
            }
        }
        return reclamarVarios(playerId, niveles);
    }

    private List<Cobro> reclamarVarios(long playerId, List<Integer> niveles)
            throws SQLException {
        List<Cobro> out = new ArrayList<>();
        if (niveles.isEmpty()) {
            return out;
        }
        try (Connection c = db.connection()) {
            Temporada t = temporada(c);
            asegurarFila(c, playerId, t.numero());

            long xp = 0;
            boolean premium = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT xp, premium FROM pase_jugador "
                  + "WHERE player_id = ? AND temporada = ?")) {
                ps.setLong(1, playerId);
                ps.setInt(2, t.numero());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        xp = rs.getLong(1);
                        premium = rs.getBoolean(2);
                    }
                }
            }
            // ⚠⚠ SIN EL PASE NO SE COBRA NADA (D-046). La XP se sigue ganando
            //    --y el nivel sube-- para que comprarlo a mitad de temporada
            //    abra de golpe todo lo que ya se tenia; lo que el pase compra
            //    es el derecho a cobrar, no el derecho a progresar.
            if (!premium) {
                return out;
            }
            int nivelActual = PaseNivel.nivelDe(xp);

            c.setAutoCommit(false);
            try {
                for (int nivel : niveles) {
                    // P6: el cliente manda el nivel, el servidor decide si le
                    // toca. Sin esto, un cliente modificado pide el nivel 100 el
                    // primer dia y se lleva el Charizard shiny.
                    if (nivel > nivelActual) {
                        continue;
                    }
                    Recompensa r = PaseCatalogo.de(nivel);
                    if (r == null) {
                        continue;
                    }
                    int filas;
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT IGNORE INTO pase_reclamo "
                          + "(player_id, temporada, nivel, via) VALUES (?,?,?,?)")) {
                        ps.setLong(1, playerId);
                        ps.setInt(2, t.numero());
                        ps.setInt(3, nivel);
                        // ⚠ La columna `via` se queda con un valor fijo. Sobra
                        //   desde D-046, y quitarla seria una migracion que
                        //   reescribe una tabla viva para ahorrar ocho bytes por
                        //   fila. La clave primaria sigue siendo la que corta el
                        //   doble cobro; que lleve una columna constante dentro
                        //   no cambia nada.
                        ps.setString(4, VIA);
                        filas = ps.executeUpdate();
                    }
                    // INSERT IGNORE devuelve 0 si ya estaba: el premio ya se
                    // cobro y no se vuelve a entregar.
                    if (filas == 0) {
                        continue;
                    }
                    out.add(new Cobro(nivel, r));
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new SQLException("No se pudo cobrar el pase", e);
            } finally {
                c.setAutoCommit(true);
            }
        }
        return out;
    }
}
