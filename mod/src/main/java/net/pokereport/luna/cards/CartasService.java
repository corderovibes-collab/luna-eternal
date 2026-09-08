package net.pokereport.luna.cards;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

/**
 * Los sobres de cartas: las tres zonas de la pantalla CARTAS.
 *
 * <p>Detalle y motivos en
 * {@code docs/analysis/cobblemon-cards.md} §5. En corto, y es decisión del
 * usuario: <b>gratis una vez al día · por Plata una vez al día · por LunaCoins
 * sin límite</b>.
 *
 * <h2>⚠⚠⚠ NO SE COMPILA CONTRA SU JAR: EL SOBRE SALE DEL REGISTRO</h2>
 *
 * Lo natural habría sido un {@code modCompileOnly} y usar {@code ModItems.
 * BOOSTER_PACK}, como se hizo con rctmod. Aquí no hace falta y sale mejor: el
 * objeto se pide por identificador, así que <b>nuestro jar compila, arranca y
 * funciona con el mod y sin él</b>. Sin el mod, el registro devuelve
 * {@code AIR}, {@link #hayCartas()} da {@code false} y la pantalla lo dice en
 * vez de reventar.
 *
 * <p>Es la misma guarda que {@code hayEntrenadores()} con rctmod, pero sin la
 * dependencia de compilación — que es una razón menos para que un arranque se
 * caiga con un {@code NoClassDefFoundError} que no nombra al mod que falta.
 */
public final class CartasService {

    private CartasService() {}

    /** El sobre normal de Cobblemon Cards. */
    private static final Identifier SOBRE =
            Identifier.of("cobblemon-cards", "booster_pack");

    /** El boleto divino: usarlo garantiza que el siguiente sobre sea un God Pack. */
    private static final Identifier BOLETO =
            Identifier.of("cobblemon-cards", "god_pack_ticket");

    /**
     * Sobres dorados por boleto divino.
     *
     * <h2>⚠⚠⚠ ES UN CONTADOR, NO UNA PROBABILIDAD</h2>
     *
     * Decisión del usuario (2026-09-03), y es la misma idea que «15 capturas,
     * una carta»: <b>al décimo sobre dorado, boleto</b>. Con un 2 % de azar
     * puedes comprar cincuenta y no ver ninguno; con un contador sabes
     * exactamente lo que te cuesta, que es lo que D-020 llama <i>piedad
     * acumulada</i> y pone como obligatorio en cualquier caja de botín.
     *
     * <p>⚠ Y por eso la tirada al azar del mod se apaga
     * ({@code lunaGoldGodPackChance = 0}): dos mecanismos para lo mismo es
     * como acaban regalándose dos boletos en la misma compra.
     */
    private static final int SOBRES_POR_BOLETO = 10;

    /**
     * Las tres zonas.
     *
     * <h2>⚠⚠ LOS IMPORTES SON PROVISIONALES, Y A PROPÓSITO</h2>
     *
     * Orden del usuario, la misma que rige la tienda: <i>no se fijan precios
     * hasta que haya un análisis general de economía</i>. Están aquí, juntos y
     * en un solo sitio, para que aplicarlo sea cambiar dos números.
     *
     * <p>De dónde salen mientras tanto: la Plata se ancla al escalón de la
     * Superpoción (900), que es el de «capricho asumible»; las LunaCoins a que
     * el cosmético más barato son 1.200, así que a 50 el sobre son veinticuatro
     * sobres por un cosmético.
     *
     * @param horas 0 = <b>sin reloj</b>. Es lo que distingue la zona de
     *              LunaCoins de las otras dos, y por eso no tiene fila en
     *              {@code card_pack_claim}: no hay nada que recordar
     */
    public enum Sobre {
        DIARIO(null, 0, 24, "sobre_diario", 0xFF3E63C8),
        PLATA(Currency.POKEDOLLAR, 900, 24, "sobre_plata", 0xFF9AA7BF),
        LUNA(Currency.REPORTCOIN, 50, 0, "sobre_luna", 0xFFD9A32B);

        public final Currency moneda;
        public final long precio;
        public final int horas;

        /**
         * Como se llama su PNG, sin carpeta ni extension.
         *
         * <h2>⚠⚠ ESTA AQUI Y NO EN LA PANTALLA, Y ES LO QUE IMPIDE UNA LISTA
         * PARALELA</h2>
         *
         * La pantalla recorre {@code Sobre.values()}, asi que anadir una zona
         * es anadir una constante y nada mas. Escritas aparte, las dos listas
         * podrian dejar de decir lo mismo: el boton mandaria un tipo y se
         * dibujaria el sobre de otro, <b>sin dar ningun error</b>. Es la
         * leccion de las tres listas de medallas y la del switch por indice de
         * {@code KitsScreen}.
         *
         * <p>De propina, el servidor puede comprobar que el arte existe -- lo
         * hace el autotest -- porque los recursos del cliente viajan en el
         * MISMO jar.
         */
        public final String arte;

        /**
         * El color de su banda, en ARGB.
         *
         * <p>⚠ Es <b>el de la moneda que lo compra</b>: la Plata es blanca
         * (D-034) y la LunaCoin dorada (D-033). El diario va azul justo porque
         * no lo compra ninguna — si se pareciera a una de las dos de pago, la
         * zona gratuita pareceria su version pobre.
         */
        public final int color;

        Sobre(Currency moneda, long precio, int horas, String arte, int color) {
            this.moneda = moneda;
            this.precio = precio;
            this.horas = horas;
            this.arte = arte;
            this.color = color;
        }

        /** Donde vive su PNG <b>dentro del jar</b>. */
        public String rutaArte() {
            return "/assets/lunaeternal/textures/gui/pokepad/" + arte + ".png";
        }

        public boolean llevaReloj() {
            return horas > 0;
        }

        public static Sobre de(String id) {
            for (Sobre s : values()) {
                if (s.name().equalsIgnoreCase(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * Cuándo vuelve a estar disponible cada sobre con reloj.
     *
     * <p>⚠ Esto es de USO INTERNO y guarda el instante en que toca. Lo que
     * viaja al cliente NO es este número: {@code Red.EstadoCartas} manda los
     * <b>segundos ya restados</b>, porque el reloj es de un jugador y no del
     * servidor — mandando el instante, un cliente con la hora adelantada
     * encendería el botón antes de tiempo. Ver el comentario de ese paquete.
     */
    public record Estado(Map<Sobre, Long> disponibleEn, long plata, long lunacoins) {}

    /** ¿Está instalado Cobblemon Cards? */
    public static boolean hayCartas() {
        return item() != Items.AIR;
    }

    private static Item item() {
        return Registries.ITEM.get(SOBRE);
    }

    // ---------------------------------------------------------------- leer

    /** El estado de las tres zonas. Se ejecuta en el hilo de E/S. */
    public static Estado estado(long playerId) throws SQLException {
        Map<Sobre, Long> cuando = new EnumMap<>(Sobre.class);
        for (Sobre s : Sobre.values()) {
            cuando.put(s, 0L);
        }
        try (Connection c = LunaEternal.database().connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT kind, claimed_ms FROM card_pack_claim WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sobre s = Sobre.de(rs.getString("kind"));
                    if (s != null && s.llevaReloj()) {
                        cuando.put(s, rs.getLong("claimed_ms") + s.horas * 3_600_000L);
                    }
                }
            }
        }
        return new Estado(cuando,
                LunaEternal.economy().balance(playerId, Currency.POKEDOLLAR),
                LunaEternal.economy().balance(playerId, Currency.REPORTCOIN));
    }

    // -------------------------------------------------------------- abrir

    /**
     * Lo que se le contesta al jugador.
     *
     * @param boleto si esta compra ha completado los diez y toca boleto divino
     */
    public record Resultado(boolean ok, String mensaje, boolean boleto) {
        static Resultado no(String mensaje) {
            return new Resultado(false, mensaje, false);
        }
    }

    /**
     * Entrega un sobre si toca.
     *
     * <h2>⚠⚠ EL ORDEN NO ES CASUAL, Y ES EL DE LA TIENDA</h2>
     *
     * El dinero vive en la base y el objeto en el inventario, así que no puede
     * haber una transacción de verdad entre los dos. Lo que sí puede haber es
     * un orden en el que ningún fallo deje al jugador peor que al empezar:
     *
     * <ol>
     *   <li>mirar el reloj, cobrar y <b>marcar el reloj</b> — las tres en la
     *       MISMA transacción, así que o pasan todas o no pasa ninguna;</li>
     *   <li>entregar, ya en el hilo del servidor;</li>
     *   <li>si el jugador se ha ido justo ahí, <b>deshacer</b>.</li>
     * </ol>
     *
     * <p>⚠⚠⚠ Mirar el reloj y marcarlo <b>tienen que ir juntos</b>. Separados,
     * dos clics rápidos leen los dos «disponible» antes de que ninguno escriba,
     * y salen <b>dos sobres con un reloj</b>. Y el que cobra es peor todavía:
     * paga dos veces. No daría ningún error.
     *
     * <p>⚠ Se entrega con {@code offerOrDrop} y no con {@code insertStack}:
     * insertar puede hacerlo <b>a medias</b> y devolver {@code false}, o sea
     * dejar al jugador con el objeto <i>y</i> con la devolución. Lo que no cabe
     * cae al suelo, y así la rama de «no cabía» deja de existir.
     */
    public static void abrir(ServerPlayerEntity player, Sobre sobre,
                             Consumer<Resultado> then) {
        var server = player.getServer();
        if (server == null) {
            return;
        }
        if (!hayCartas()) {
            then.accept(Resultado.no("§cLas cartas no están disponibles ahora mismo."));
            return;
        }
        var perfil = player.getGameProfile();
        String clave = UUID.randomUUID().toString();

        LunaEternal.submit(() -> {
            final Resultado r;
            final long id;
            try {
                id = LunaEternal.players().resolve(perfil.getId(), perfil.getName());
                r = cobrarYMarcar(id, sobre, clave);
            } catch (Exception e) {
                LunaEternal.LOG.error("Fallo entregando un sobre de cartas", e);
                server.execute(() -> then.accept(Resultado.no("§cNo se pudo abrir el sobre. No se te ha cobrado.")));
                return;
            }
            server.execute(() -> {
                if (!r.ok()) {
                    then.accept(r);
                    return;
                }
                // ⚠ Cobrado y marcado ya, así que irse ahora obliga a deshacer:
                //   si no, se paga por un sobre que no llega a ninguna parte.
                if (player.isRemoved()) {
                    deshacer(id, sobre, clave);
                    return;
                }
                player.getInventory().offerOrDrop(sobreDe(sobre));
                if (r.boleto()) {
                    // ⚠ Se entrega con el sobre y no en vez de el: los diez
                    //   sobres ya estan pagados, el boleto es lo de encima.
                    player.getInventory().offerOrDrop(
                            new ItemStack(Registries.ITEM.get(BOLETO)));
                }
                then.accept(r);
            });
        });
    }

    /**
     * Reloj, cobro y marca, en una transacción.
     *
     * <p>⚠ La fila del reloj se bloquea con {@code FOR UPDATE} antes de mirarla.
     * Sin eso, dos peticiones simultáneas la leen a la vez y las dos se creen
     * con derecho: es el mismo caso que dos clics rápidos, pero desde dos
     * conexiones, y ahí un {@code if} en Java no llega.
     */
    private static Resultado cobrarYMarcar(long id, Sobre sobre, String clave)
            throws SQLException {
        Connection c = LunaEternal.database().connection();
        boolean auto = c.getAutoCommit();
        try {
            c.setAutoCommit(false);
            long ahora = System.currentTimeMillis();

            if (sobre.llevaReloj()) {
                long ultima = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT claimed_ms FROM card_pack_claim "
                                + "WHERE player_id = ? AND kind = ? FOR UPDATE")) {
                    ps.setLong(1, id);
                    ps.setString(2, sobre.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ultima = rs.getLong(1);
                        }
                    }
                }
                long listo = ultima + sobre.horas * 3_600_000L;
                if (ahora < listo) {
                    c.rollback();
                    return Resultado.no("§cTodavía no. Vuelve en "
                            + falta(listo - ahora) + ".");
                }
            }

            if (sobre.moneda != null && sobre.precio > 0) {
                try {
                    LunaEternal.economy().applyInTransaction(c, id, sobre.moneda,
                            -sobre.precio, "cartas_sobre", null, null, clave);
                } catch (EconomyException e) {
                    c.rollback();
                    return Resultado.no(e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                            ? "§cNo tienes suficiente " + sobre.moneda.displayName + "."
                            : "§c" + e.getMessage());
                }
            }

            if (sobre.llevaReloj()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO card_pack_claim (player_id, kind, claimed_ms) "
                                + "VALUES (?,?,?) ON DUPLICATE KEY UPDATE claimed_ms = ?")) {
                    ps.setLong(1, id);
                    ps.setString(2, sobre.name());
                    ps.setLong(3, ahora);
                    ps.setLong(4, ahora);
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO card_pack_grant (player_id, kind, currency, price, idem) "
                            + "VALUES (?,?,?,?,?)")) {
                ps.setLong(1, id);
                ps.setString(2, sobre.name());
                if (sobre.moneda == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, sobre.moneda.name());
                }
                ps.setLong(4, sobre.precio);
                ps.setString(5, clave);
                ps.executeUpdate();
            }

            // ⚠⚠⚠ EL CONTADOR SE LEE DENTRO DE LA MISMA TRANSACCION que acaba
            //    de insertar la fila. Fuera, dos compras a la vez podrian
            //    contar las dos el mismo total y repartir DOS boletos por diez
            //    sobres -- y no daria ningun error, solo el doble de boletos.
            boolean boleto = false;
            if (sobre == Sobre.LUNA) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM card_pack_grant "
                                + "WHERE player_id = ? AND kind = ?")) {
                    ps.setLong(1, id);
                    ps.setString(2, Sobre.LUNA.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        boleto = rs.next() && rs.getLong(1) % SOBRES_POR_BOLETO == 0;
                    }
                }
            }

            c.commit();
            return new Resultado(true, boleto
                    ? "§a¡Sobre conseguido! §6¡Y un BOLETO DIVINO por tus "
                      + SOBRES_POR_BOLETO + " sobres dorados!"
                    : "§a¡Sobre conseguido! §7Ábrelo con clic derecho.", boleto);
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // Ya estamos en el camino de error; lo que importa es el de arriba.
            }
            throw e;
        } finally {
            try {
                c.setAutoCommit(auto);
            } finally {
                c.close();
            }
        }
    }

    /**
     * Devuelve el dinero y suelta el reloj.
     *
     * <p>⚠⚠ Las <b>dos</b> cosas, y la segunda es la que se olvida: si solo se
     * devolviera el dinero, el jugador que se desconecta en el instante justo
     * pierde su sobre diario —que no cuesta nada y por tanto no se puede
     * reembolsar— hasta el día siguiente.
     */
    private static void deshacer(long id, Sobre sobre, String clave) {
        LunaEternal.submit(() -> {
            try {
                if (sobre.moneda != null && sobre.precio > 0) {
                    LunaEternal.economy().credit(id, sobre.moneda, sobre.precio,
                            "cartas_sobre_devuelto", clave + ":dev");
                }
                if (sobre.llevaReloj()) {
                    try (Connection c = LunaEternal.database().connection();
                         PreparedStatement ps = c.prepareStatement(
                                 "DELETE FROM card_pack_claim "
                                         + "WHERE player_id = ? AND kind = ?")) {
                        ps.setLong(1, id);
                        ps.setString(2, sobre.name());
                        ps.executeUpdate();
                    }
                }
                LunaEternal.LOG.warn("Sobre {} deshecho: el jugador {} se fue antes"
                        + " de recibirlo", sobre, id);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo deshacer el sobre {} de {}", sobre, id, e);
            }
        });
    }

    /**
     * El sobre marcado con su calidad.
     *
     * <h2>⚠⚠ LA CALIDAD VIAJA EN EL OBJETO, NO EN UNA TABLA NUESTRA</h2>
     *
     * El sobre se puede guardar, dejar en un cofre o abrir tres días después.
     * Si la calidad viviera en una fila de la base, habría que atarla al objeto
     * de alguna forma —un identificador dentro, y una consulta al abrirlo— y
     * entonces un sobre duplicado o movido dejaría de saber lo que es.
     * Escrita <b>dentro del propio objeto</b>, viaja con él y no hay nada que
     * mantener sincronizado.
     *
     * <p>⚠⚠⚠ Y va en {@code minecraft:custom_data}, que es de <b>vainilla</b>.
     * Tres sobres distintos habrían sido tres entradas más en un registro que
     * se sincroniza —o sea tres razones más para echar a quien no actualice—
     * además de tres texturas, tres modelos y tres traducciones. Esto no
     * registra nada.
     *
     * <p>⚠ El nombre también se pone aquí: sin él los tres son el mismo icono
     * gris en el inventario y no hay forma de saber cuál es el dorado.
     */
    private static ItemStack sobreDe(Sobre sobre) {
        ItemStack pila = new ItemStack(item());
        var etiqueta = new net.minecraft.nbt.NbtCompound();
        etiqueta.putString("luna_calidad", sobre.name().toLowerCase(java.util.Locale.ROOT));
        pila.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(etiqueta));
        String clave = sobre.name().toLowerCase(java.util.Locale.ROOT);
        pila.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.translatable("item.lunaeternal.sobre." + clave)
                        .styled(e -> e.withItalic(false)));
        // ⚠⚠ LA DESCRIPCION VA EN EL SOBRE, no solo en la pantalla donde lo
        //    compraste. Un sobre se guarda y se abre tres dias despues, y para
        //    entonces nadie se acuerda de cual era el bueno. Decision del
        //    usuario: que cada uno diga lo que da.
        pila.set(net.minecraft.component.DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.translatable(
                                "item.lunaeternal.sobre." + clave + ".desc")
                                .styled(e -> e.withItalic(false)))));
        return pila;
    }

    /** «7 h 12 min» / «12 min» / «40 s». Solo para el mensaje de chat. */
    private static String falta(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s >= 3600) {
            return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
        }
        return s >= 60 ? (s / 60) + " min" : s + " s";
    }
}
