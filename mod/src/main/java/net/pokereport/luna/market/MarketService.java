package net.pokereport.luna.market;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

/**
 * EL MERCADO: libro de órdenes de compra y venta para objetos.
 *
 * <p>Diseño completo en {@code docs/trading/mercado.md}. Lo que hay que tener en
 * la cabeza antes de tocar una línea de aquí:
 *
 * <h2>⚠⚠⚠ La custodia es DOBLE</h2>
 *
 * <ul>
 *   <li><b>VENTA</b> retiene los <b>objetos</b>. Se sacan del inventario antes
 *       de crear la orden, y quien los saca es la capa de red.</li>
 *   <li><b>COMPRA</b> retiene el <b>dinero</b>. Se cobra <i>aquí</i>, en la
 *       misma transacción que crea la fila.</li>
 * </ul>
 *
 * La segunda es la que se olvida, y es la que crea dinero de la nada: pones una
 * compra de un millón, te lo gastas, y cuando alguien vende el servidor tiene
 * que pagar lo que tú no tenías — porque el vendedor ya entregó.
 *
 * <h2>⚠⚠ Se ejecuta al precio de la orden que YA ESTABA</h2>
 *
 * Quien llega acepta el precio del libro. Si pujas 700 y hay oferta a 595,
 * pagas 595 y se te devuelve la diferencia. Al revés —cobrar lo que ofreciste—
 * haría que poner una orden generosa fuera un castigo, y entonces nadie pondría
 * órdenes por encima del mínimo. Eso es lo que mata la liquidez de un mercado.
 *
 * <h2>⚠⚠ Las filas se bloquean en orden ASCENDENTE de identificador</h2>
 *
 * Un cruce toca dos órdenes y dos monederos. Dos cruces simultáneos que cojan
 * las mismas filas en orden distinto se bloquean mutuamente y MariaDB mata uno.
 * La regla ya estaba escrita en CLAUDE.md para {@code player_id}; aquí vale
 * igual para {@code order_id}.
 *
 * <h2>⚠⚠ {@link Resultado#afectados()} desde el primer día</h2>
 *
 * Cuando tu orden se llena contra la mía, <b>yo tengo que enterarme</b>: mi
 * orden ha cambiado y mi dinero también. Es el bug de los clanes, y aquí es
 * peor — allí se quedaba una etiqueta puesta; aquí el jugador ve órdenes que ya
 * no existen e intenta cancelarlas.
 */
public final class MarketService {

    /** Cuánto dura una orden sin llenarse. */
    public static final int HORAS = 72;

    /**
     * Órdenes abiertas por jugador.
     *
     * <p>⚠ Sin tope, mil órdenes de una unidad convierten cualquier consulta del
     * libro en un escaneo — y poner órdenes es barato por diseño.
     */
    public static final int MAX_ABIERTAS = 20;

    /** Cantidad máxima por orden. Ver {@link #acotar}. */
    public static final int MAX_CANTIDAD = 10_000;

    /** Precio máximo por unidad. Ver {@link #acotar}. */
    public static final long MAX_PRECIO = 100_000_000L;

    /** Cuántas filas del libro se enseñan por lado. */
    public static final int PROFUNDIDAD = 12;

    private final Database db;

    public MarketService(Database db) {
        this.db = db;
    }

    // ---- lo que se devuelve ------------------------------------------------

    public enum Lado { COMPRA, VENTA;
        Lado contrario() {
            return this == COMPRA ? VENTA : COMPRA;
        }
    }

    /** Una orden, tal y como se enseña. */
    public record Orden(long id, long playerId, String quien, Lado lado,
                        String itemId, long precio, int total, int lleno,
                        String estado, long caduca) {
        public int quedan() {
            return total - lleno;
        }
    }

    /** Una fila del libro: precio y cuántas unidades hay a ese precio. */
    public record Nivel(long precio, int unidades, int ordenes) {}

    /** Una operación ya ejecutada, para el histórico. */
    public record Operacion(String itemId, long precio, int qty, long cuando) {}

    /** Lo que le debemos a alguien en objetos. */
    public record Deuda(long id, String itemId, int qty, String motivo) {}

    /**
     * Resultado de una acción.
     *
     * <p>⚠ {@code afectados} son los {@code player_id} cuya pantalla ha dejado
     * de ser cierta: quien actuó <b>y todos los dueños de las órdenes que se
     * cruzaron</b>. Ver el comentario de la clase.
     */
    public record Resultado(boolean ok, String mensaje, Set<Long> afectados,
                            int ejecutado, long gastado) {

        static Resultado no(String m) {
            return new Resultado(false, m, Set.of(), 0, 0);
        }

        static Resultado si(String m, Set<Long> afectados, int ejecutado, long gastado) {
            return new Resultado(true, m, afectados, ejecutado, gastado);
        }
    }

    // ---- plomería ----------------------------------------------------------

    private interface Trabajo {
        Resultado hacer(Connection c) throws SQLException;
    }

    /**
     * Abre una transacción y deshace si algo falla.
     *
     * <p>⚠ El {@code finally} devuelve el {@code autoCommit} <b>siempre</b>: la
     * conexión vuelve al pool, y una que vuelva con la transacción abierta
     * envenena a quien la coja después.
     */
    private Resultado enTransaccion(Trabajo trabajo) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Resultado r = trabajo.hacer(c);
                if (r.ok()) {
                    c.commit();
                } else {
                    c.rollback();
                }
                return r;
            } catch (Exception e) {
                c.rollback();
                throw e instanceof SQLException s ? s : new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Acota lo que llega del cliente <b>antes</b> de multiplicar.
     *
     * <p>⚠⚠ {@code precio * cantidad} es un {@code long}, y con los dos venidos
     * del cliente el producto <b>puede dar la vuelta y salir negativo</b> —
     * cobrar en negativo es ingresar. Acotar después de multiplicar no sirve de
     * nada. Ya pasó en la tienda; aquí los dos factores los elige el jugador, así
     * que el riesgo es el doble.
     *
     * <p>Con los topes de aquí, el peor producto posible es
     * 100.000.000 × 10.000 = 10¹² — que cabe de sobra en un {@code long}.
     */
    private static long acotarPrecio(long precio) {
        return Math.max(1, Math.min(MAX_PRECIO, precio));
    }

    private static int acotarCantidad(int qty) {
        return Math.max(1, Math.min(MAX_CANTIDAD, qty));
    }

    /**
     * Abona dinero dentro de la transacción en curso.
     *
     * <p>⚠ Existe para no repetir el {@code try/catch} tres veces, pero sobre
     * todo para <b>dejar dicho por qué un abono no puede fallar por saldo</b>:
     * sumar nunca deja a nadie en descubierto. Si {@code applyInTransaction}
     * lanza aquí es porque algo está roto de verdad —la clave repetida, la fila
     * ausente— y entonces lo correcto es <b>tumbar la transacción entera</b> y
     * que no se ejecute media operación.
     *
     * <p>Tragarse la excepción sería lo peor posible: el vendedor entregaría los
     * objetos y no cobraría.
     */
    private static void abonar(Connection c, long playerId, long cantidad,
                               String motivo, Long ref, String clave)
            throws SQLException {
        try {
            LunaEternal.economy().applyInTransaction(c, playerId,
                    Currency.POKEDOLLAR, cantidad, motivo, "mercado", ref, clave);
        } catch (EconomyException e) {
            throw new SQLException("Abono imposible en el mercado ("
                    + motivo + ", " + cantidad + "): " + e.getMessage(), e);
        }
    }

    // ---- consultas ---------------------------------------------------------

    /**
     * El libro de un objeto: los dos lados, agregados por precio.
     *
     * <p>⚠ Se agrega <b>por precio</b> y no se devuelven las órdenes sueltas.
     * Dos motivos: es lo que se dibuja (una fila por nivel de precio), y no
     * enseña de quién es cada orden — que en un mercado real es información que
     * no se da, porque saber que la única venta barata es de fulano invita a
     * negociar por fuera y a acosarle.
     */
    public List<Nivel> libro(String itemId, Lado lado) throws SQLException {
        var salida = new ArrayList<Nivel>();
        // Compras: la más CARA primero. Ventas: la más BARATA primero.
        // Es lo que hace que la primera fila de cada lado sea la mejor oferta.
        String orden = lado == Lado.COMPRA ? "DESC" : "ASC";
        String sql = "SELECT unit_price, SUM(qty_total - qty_filled), COUNT(*) "
                + "FROM market_order "
                + "WHERE item_id = ? AND side = ? AND state = 'ABIERTA' "
                + "AND expires_at > CURRENT_TIMESTAMP(3) "
                + "GROUP BY unit_price ORDER BY unit_price " + orden + " LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, lado.name());
            ps.setInt(3, PROFUNDIDAD);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Nivel(rs.getLong(1), rs.getInt(2), rs.getInt(3)));
                }
            }
        }
        return salida;
    }

    /** Las órdenes vivas de un jugador. */
    public List<Orden> mias(long playerId) throws SQLException {
        var salida = new ArrayList<Orden>();
        String sql = "SELECT o.order_id, o.player_id, p.username, o.side, o.item_id, "
                + "o.unit_price, o.qty_total, o.qty_filled, o.state, o.expires_at "
                + "FROM market_order o JOIN player p ON p.player_id = o.player_id "
                + "WHERE o.player_id = ? AND o.state = 'ABIERTA' "
                + "ORDER BY o.created_at DESC LIMIT 50";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(leerOrden(rs));
                }
            }
        }
        return salida;
    }

    /** Las últimas operaciones de un objeto. La base del histórico. */
    public List<Operacion> historial(String itemId, int limite) throws SQLException {
        var salida = new ArrayList<Operacion>();
        String sql = "SELECT item_id, unit_price, qty, created_at FROM market_trade "
                + "WHERE item_id = ? ORDER BY trade_id DESC LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setInt(2, Math.max(1, Math.min(limite, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Operacion(rs.getString(1), rs.getLong(2),
                            rs.getInt(3), rs.getTimestamp(4).getTime()));
                }
            }
        }
        return salida;
    }

    /**
     * El último precio al que se negoció algo. {@code 0} si nunca.
     *
     * <p>Es la referencia que se le enseña a quien va a poner una orden y no
     * tiene ni idea de cuánto vale. Sin ella, el primer precio de cada objeto lo
     * pone alguien a ojo y el resto lo copia.
     */
    public long ultimoPrecio(String itemId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT unit_price FROM market_trade WHERE item_id = ? "
                             + "ORDER BY trade_id DESC LIMIT 1")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Los objetos con más movimiento, para la lista de la izquierda. */
    public List<String> masNegociados(int limite) throws SQLException {
        var salida = new ArrayList<String>();
        // Une lo que se está ofreciendo AHORA con lo que se negoció, porque un
        // objeto recién puesto no tiene operaciones y aun así hay que verlo.
        String sql = "SELECT item_id FROM ("
                + "  SELECT item_id, SUM(qty_total - qty_filled) AS n FROM market_order "
                + "  WHERE state = 'ABIERTA' AND expires_at > CURRENT_TIMESTAMP(3) "
                + "  GROUP BY item_id) t ORDER BY n DESC LIMIT ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limite, 100)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(rs.getString(1));
                }
            }
        }
        return salida;
    }

    /** Lo que se le debe a alguien en objetos y aún no se le ha dado. */
    public List<Deuda> deudas(long playerId) throws SQLException {
        var salida = new ArrayList<Deuda>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT claim_id, item_id, qty, reason FROM market_claim "
                             + "WHERE player_id = ? AND delivered_at IS NULL "
                             + "ORDER BY claim_id LIMIT 200")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    salida.add(new Deuda(rs.getLong(1), rs.getString(2),
                            rs.getInt(3), rs.getString(4)));
                }
            }
        }
        return salida;
    }

    /**
     * Marca una deuda como entregada.
     *
     * <p>⚠ Se llama <b>después</b> de meter los objetos en el inventario, no
     * antes: si se marcara primero y la entrega fallara, el jugador perdería los
     * objetos y la fila diría que los tiene. Al revés, lo peor que pasa es
     * entregar dos veces —y eso lo impide el {@code WHERE delivered_at IS NULL},
     * que solo afecta a una fila.
     *
     * @return {@code true} si esta llamada fue la que la marcó
     */
    public boolean marcarEntregada(long claimId) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE market_claim SET delivered_at = CURRENT_TIMESTAMP(3) "
                             + "WHERE claim_id = ? AND delivered_at IS NULL")) {
            ps.setLong(1, claimId);
            return ps.executeUpdate() == 1;
        }
    }

    private static Orden leerOrden(ResultSet rs) throws SQLException {
        return new Orden(rs.getLong(1), rs.getLong(2), rs.getString(3),
                Lado.valueOf(rs.getString(4)), rs.getString(5), rs.getLong(6),
                rs.getInt(7), rs.getInt(8), rs.getString(9),
                rs.getTimestamp(10).getTime());
    }

    // ---- poner una orden ---------------------------------------------------

    /**
     * Pone una orden y la cruza con lo que haya.
     *
     * <p>⚠ Para una <b>venta</b>, quien llama tiene que haber sacado ya los
     * objetos del inventario: este método da por hecho que están en custodia. Es
     * la misma regla del GTS —<i>lo listado no puede estar en poder del
     * vendedor</i>— y es el vector de duplicación número uno.
     *
     * @param clave de idempotencia, para el cobro de una compra
     */
    public Resultado poner(long playerId, Lado lado, String itemId,
                           long precioPedido, int cantidadPedida, String clave)
            throws SQLException {

        if (itemId == null || itemId.isBlank()) {
            return Resultado.no("Ese objeto no existe.");
        }
        final long precio = acotarPrecio(precioPedido);
        final int cantidad = acotarCantidad(cantidadPedida);

        return enTransaccion(c -> {
            if (abiertas(c, playerId) >= MAX_ABIERTAS) {
                return Resultado.no("Ya tienes " + MAX_ABIERTAS
                        + " órdenes abiertas. Cancela alguna.");
            }

            // ⚠ EL DINERO DE UNA COMPRA SE RETIENE AQUI, ANTES DE CRUZAR. Si se
            //   cobrara al ejecutar, una compra podria quedarse en el libro sin
            //   respaldo -- y cuando alguien vendiera, habria que pagarle con
            //   dinero que no existe.
            if (lado == Lado.COMPRA) {
                try {
                    LunaEternal.economy().applyInTransaction(c, playerId,
                            Currency.POKEDOLLAR, -(precio * cantidad),
                            "mercado_reservar", "mercado", null, clave);
                } catch (EconomyException e) {
                    return Resultado.no(
                            e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                                    ? "No tienes " + String.format("%,d",
                                            precio * cantidad) + " de Plata."
                                    : "No se pudo reservar el dinero.");
                }
            }

            long orderId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO market_order (player_id, side, item_id, unit_price, "
                            + "qty_total, expires_at) VALUES (?,?,?,?,?, "
                            + "DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? HOUR))",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, playerId);
                ps.setString(2, lado.name());
                ps.setString(3, itemId);
                ps.setLong(4, precio);
                ps.setInt(5, cantidad);
                ps.setInt(6, HORAS);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("La orden no devolvió identificador");
                    }
                    orderId = rs.getLong(1);
                }
            }

            var afectados = new LinkedHashSet<Long>();
            afectados.add(playerId);
            Cruce cruce = cruzar(c, orderId, playerId, lado, itemId, precio,
                    cantidad, afectados);

            // ⚠ LO QUE SOBRA SE DEVUELVE AQUI MISMO, no al cancelar. Si pujaste
            //   700 y ejecutaste a 595, la diferencia es TUYA y ya: dejarla
            //   retenida hasta que canceles seria cobrarte por acertar.
            long sobra = 0;
            if (lado == Lado.COMPRA && cruce.ejecutado > 0) {
                sobra = (long) cruce.ejecutado * precio - cruce.gastado;
                if (sobra > 0) {
                    abonar(c, playerId, sobra, "mercado_vuelta", orderId,
                            clave + ":vuelta");
                }
            }

            String mensaje = cruce.ejecutado == 0
                    ? "Orden puesta: " + cantidad + " x " + String.format("%,d", precio)
                    : cruce.ejecutado == cantidad
                        ? "Ejecutada entera: " + cantidad + " unidades"
                        : "Ejecutadas " + cruce.ejecutado + " de " + cantidad
                          + "; el resto queda en el libro";
            return Resultado.si(mensaje, afectados, cruce.ejecutado, cruce.gastado);
        });
    }

    private int abiertas(Connection c, long playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM market_order WHERE player_id = ? "
                        + "AND state = 'ABIERTA'")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Lo que salió de un cruce. */
    private record Cruce(int ejecutado, long gastado) {}

    /**
     * El motor: empareja la orden nueva contra el otro lado del libro.
     *
     * <h2>Prioridad precio-tiempo</h2>
     *
     * Primero el mejor precio y, a igual precio, el que llegó antes. La segunda
     * mitad no es realismo por gusto: sin ella, dos órdenes idénticas se
     * resuelven en el orden que la base devuelva —o sea, al azar— y quien lleva
     * tres días esperando ve cómo le adelanta uno que acaba de llegar.
     *
     * <p>⚠ {@code FOR UPDATE} y {@code ORDER BY ... order_id}: las filas se
     * cogen siempre en el mismo orden, que es lo que evita los interbloqueos.
     */
    private Cruce cruzar(Connection c, long orderId, long playerId, Lado lado,
                         String itemId, long precio, int cantidad,
                         Set<Long> afectados) throws SQLException {

        int quedan = cantidad;
        long gastado = 0;

        // Una compra cruza contra ventas de precio MENOR O IGUAL, de la más
        // barata a la más cara. Una venta, contra compras de precio MAYOR O
        // IGUAL, de la más cara a la más barata.
        String comparador = lado == Lado.COMPRA ? "<=" : ">=";
        String orden = lado == Lado.COMPRA ? "ASC" : "DESC";
        String sql = "SELECT order_id, player_id, unit_price, qty_total, qty_filled "
                + "FROM market_order "
                + "WHERE item_id = ? AND side = ? AND state = 'ABIERTA' "
                + "AND expires_at > CURRENT_TIMESTAMP(3) "
                + "AND unit_price " + comparador + " ? "
                // ⚠⚠ NADIE SE CRUZA CONSIGO MISMO. Va en el WHERE y no en un
                //    `if` dentro del bucle: así la propia orden tampoco puede
                //    aparecer, y cruzarte contigo permitiría fijar el precio que
                //    quisieras y mover el índice de todo el servidor.
                + "AND player_id <> ? "
                + "ORDER BY unit_price " + orden + ", order_id ASC "
                + "LIMIT 50 FOR UPDATE";

        var contrapartes = new ArrayList<long[]>();  // {id, player, precio, quedan}
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, lado.contrario().name());
            ps.setLong(3, precio);
            ps.setLong(4, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contrapartes.add(new long[] { rs.getLong(1), rs.getLong(2),
                            rs.getLong(3), rs.getInt(4) - rs.getInt(5) });
                }
            }
        }

        for (long[] otra : contrapartes) {
            if (quedan <= 0) {
                break;
            }
            int disponible = (int) otra[3];
            if (disponible <= 0) {
                continue;
            }
            int trozo = Math.min(quedan, disponible);
            long precioEjecucion = otra[2];   // ⚠ el del LIBRO, no el mío

            long compradorId = lado == Lado.COMPRA ? playerId : otra[1];
            long vendedorId = lado == Lado.COMPRA ? otra[1] : playerId;
            long compraOrden = lado == Lado.COMPRA ? orderId : otra[0];
            long ventaOrden = lado == Lado.COMPRA ? otra[0] : orderId;

            ejecutar(c, itemId, precioEjecucion, trozo, compradorId, vendedorId,
                    compraOrden, ventaOrden);

            avanzar(c, otra[0], trozo);
            avanzar(c, orderId, trozo);

            afectados.add(otra[1]);
            quedan -= trozo;
            gastado += precioEjecucion * trozo;
        }
        return new Cruce(cantidad - quedan, gastado);
    }

    /**
     * Una operación: el dinero, el impuesto, los objetos y el asiento.
     *
     * <h2>⚠ Quién paga qué</h2>
     *
     * El comprador <b>ya tiene el dinero retenido</b> desde que puso su orden,
     * así que aquí no se le cobra otra vez: se le abona al vendedor. Es la parte
     * que más fácil se hace mal — cobrar aquí además de al reservar duplicaría
     * el cargo, y no cobrar en ninguno de los dos sitios regalaría la compra.
     */
    private void ejecutar(Connection c, String itemId, long precio, int qty,
                          long compradorId, long vendedorId,
                          long compraOrden, long ventaOrden) throws SQLException {

        long bruto = precio * qty;
        // El mismo impuesto progresivo del GTS: el único sink que escala con la
        // riqueza. Los consumibles cuestan lo mismo al novato que al millonario.
        long impuesto = net.pokereport.luna.gts.GtsService.taxFor(bruto);
        long neto = bruto - impuesto;

        // ⚠ La clave se deriva de la OPERACION, no es un UUID nuevo. Un UUID
        //   recién generado no puede coincidir con nada, así que como clave de
        //   idempotencia no protege de nada (R4). Aquí identifica «este cruce
        //   concreto entre estas dos órdenes».
        String clave = "mkt:" + compraOrden + ":" + ventaOrden + ":";

        abonar(c, vendedorId, neto, "mercado_venta", ventaOrden,
                clave + "v" + qty + ":" + precio);
        // El impuesto NO va a un fondo: se destruye. Un sink que reaparece en
        // otro sitio no es un sink.

        // Los objetos van a la lista de deudas del comprador. NO se le meten en
        // el inventario aquí: puede estar desconectado, y un inventario solo
        // existe mientras su dueño está dentro.
        deber(c, compradorId, itemId, qty, "compra");

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO market_trade (item_id, unit_price, qty, buyer_id, "
                        + "seller_id, buy_order, sell_order, tax) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, itemId);
            ps.setLong(2, precio);
            ps.setInt(3, qty);
            ps.setLong(4, compradorId);
            ps.setLong(5, vendedorId);
            ps.setLong(6, compraOrden);
            ps.setLong(7, ventaOrden);
            ps.setLong(8, impuesto);
            ps.executeUpdate();
        }
    }

    /**
     * Suma al llenado y cierra la orden si ya está servida.
     *
     * <p>⚠ {@code qty_filled = qty_filled + ?} y no un total calculado: con el
     * total calculado, dos escrituras simultáneas se pisan y la segunda escribe
     * un número que ya no era cierto. El {@code FOR UPDATE} del cruce ya lo
     * impide, y aun así se escribe relativo — es gratis y no depende de que
     * nadie se acuerde del bloqueo.
     */
    private void avanzar(Connection c, long orderId, int qty) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE market_order SET qty_filled = qty_filled + ? "
                        + "WHERE order_id = ?")) {
            ps.setInt(1, qty);
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE market_order SET state = 'COMPLETA', "
                        + "closed_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE order_id = ? AND qty_filled >= qty_total")) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }

    private void deber(Connection c, long playerId, String itemId, int qty,
                       String motivo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO market_claim (player_id, item_id, qty, reason) "
                        + "VALUES (?,?,?,?)")) {
            ps.setLong(1, playerId);
            ps.setString(2, itemId);
            ps.setInt(3, qty);
            ps.setString(4, motivo);
            ps.executeUpdate();
        }
    }

    // ---- cancelar y caducar ------------------------------------------------

    /**
     * Cancela una orden y devuelve lo que quedaba retenido.
     *
     * <p>⚠ Se devuelve <b>lo no ejecutado</b>, no lo pedido. Devolver lo pedido
     * regalaría lo que ya se cobró; devolver de menos se lo queda el servidor.
     */
    public Resultado cancelar(long playerId, long orderId) throws SQLException {
        return enTransaccion(c -> {
            long dueno;
            Lado lado;
            String itemId;
            long precio;
            int quedan;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT player_id, side, item_id, unit_price, "
                            + "qty_total - qty_filled FROM market_order "
                            + "WHERE order_id = ? AND state = 'ABIERTA' FOR UPDATE")) {
                ps.setLong(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Resultado.no("Esa orden ya no está abierta.");
                    }
                    dueno = rs.getLong(1);
                    lado = Lado.valueOf(rs.getString(2));
                    itemId = rs.getString(3);
                    precio = rs.getLong(4);
                    quedan = rs.getInt(5);
                }
            }
            if (dueno != playerId) {
                return Resultado.no("Esa orden no es tuya.");
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE market_order SET state = 'CANCELADA', "
                            + "closed_at = CURRENT_TIMESTAMP(3), "
                            + "refunded_at = CURRENT_TIMESTAMP(3) "
                            + "WHERE order_id = ? AND state = 'ABIERTA'")) {
                ps.setLong(1, orderId);
                if (ps.executeUpdate() != 1) {
                    // Otro se nos adelantó entre el SELECT y el UPDATE. No es un
                    // error: es la carrera funcionando.
                    return Resultado.no("Esa orden ya no está abierta.");
                }
            }
            devolver(c, playerId, lado, itemId, precio, quedan, orderId, "cancelar");
            return Resultado.si("Orden cancelada.", Set.of(playerId), 0, 0);
        });
    }

    /**
     * Devuelve lo retenido de una orden que se cierra sin ejecutarse del todo.
     *
     * <p>El dinero vuelve al saldo directamente —un saldo vive en la base y no
     * necesita que nadie esté conectado—; los objetos van a la lista de deudas.
     */
    private void devolver(Connection c, long playerId, Lado lado, String itemId,
                          long precio, int quedan, long orderId, String motivo)
            throws SQLException {
        if (quedan <= 0) {
            return;
        }
        if (lado == Lado.COMPRA) {
            abonar(c, playerId, precio * quedan, "mercado_" + motivo, orderId,
                    "mkt_dev:" + orderId);
        } else {
            deber(c, playerId, itemId, quedan, motivo);
        }
    }

    /**
     * Cierra las órdenes caducadas y devuelve lo suyo.
     *
     * <p>⚠ Se llama al arrancar y cada cierto tiempo. Y las consultas del libro
     * filtran <b>también</b> por {@code expires_at}, para que una orden vencida
     * no se pueda cruzar aunque el barrido no haya pasado todavía: una tarea
     * periódica es otra cosa que puede no estar corriendo.
     *
     * @return cuántas se cerraron
     */
    public int caducar() throws SQLException {
        var vencidas = new ArrayList<long[]>();
        var items = new ArrayList<String>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT order_id, player_id, side, item_id, unit_price, "
                             + "qty_total - qty_filled FROM market_order "
                             + "WHERE state = 'ABIERTA' "
                             + "AND expires_at <= CURRENT_TIMESTAMP(3) LIMIT 500")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vencidas.add(new long[] { rs.getLong(1), rs.getLong(2),
                            "COMPRA".equals(rs.getString(3)) ? 0 : 1,
                            rs.getLong(5), rs.getInt(6) });
                    items.add(rs.getString(4));
                }
            }
        }
        int n = 0;
        for (int i = 0; i < vencidas.size(); i++) {
            long[] v = vencidas.get(i);
            final String item = items.get(i);
            // ⚠ Una por transacción: si una falla —un jugador borrado, una
            //   restricción— las demás se cierran igual. Un barrido que se cae
            //   entero por una fila mala deja el mercado atascado para siempre.
            try {
                Resultado r = enTransaccion(c -> {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE market_order SET state = 'CADUCADA', "
                                    + "closed_at = CURRENT_TIMESTAMP(3), "
                                    + "refunded_at = CURRENT_TIMESTAMP(3) "
                                    + "WHERE order_id = ? AND state = 'ABIERTA'")) {
                        ps.setLong(1, v[0]);
                        if (ps.executeUpdate() != 1) {
                            return Resultado.no("ya cerrada");
                        }
                    }
                    devolver(c, v[1], v[2] == 0 ? Lado.COMPRA : Lado.VENTA,
                            item, v[3], (int) v[4], v[0], "caducar");
                    return Resultado.si("", Set.of(v[1]), 0, 0);
                });
                if (r.ok()) {
                    n++;
                }
            } catch (SQLException e) {
                LunaEternal.LOG.warn("No se pudo caducar la orden {}: {}",
                        v[0], e.toString());
            }
        }
        return n;
    }
}
