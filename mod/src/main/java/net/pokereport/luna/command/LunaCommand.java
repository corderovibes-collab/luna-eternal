package net.pokereport.luna.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos de verificación del vertical slice.
 *
 * <p>No son la interfaz final —esa será la del cliente (D-026)—. Existen para
 * comprobar que persistencia, idempotencia y atomicidad funcionan de verdad
 * contra la base.
 *
 * <p><b>Mientras la interfaz nueva no exista, esto es lo único que hay.</b> No
 * es una excepción a P9 («interfaz, nunca comando»): P9 habla de lo que se le
 * ofrece al jugador, y a un jugador no se le está ofreciendo nada todavía.
 * Estos comandos son de verificación y siguen siéndolo.
 */
public final class LunaCommand {

    private LunaCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {

        d.register(literal("luna")
            .executes(ctx -> balance(ctx.getSource()))
            .then(literal("saldo")
                .executes(ctx -> balance(ctx.getSource())))

            // Viaje entre dimensiones, para CONSTRUCTORES (nivel 2).
            //
            // Existe porque la Puerta del Mundo se fue con los menús (D-026) y
            // sin esto la única forma de llegar a la ciudadela es escribir
            // `/execute in lunaeternal:ciudadela run tp @s 0 64 0`, que nadie
            // va a teclear veinte veces al día.
            //
            // No contradice P9: P9 protege al JUGADOR de tener que escribir
            // comandos. Un constructor con OP nivel 2 no es un jugador, y
            // cuando exista la interfaz esto seguirá siendo un atajo, no el
            // camino.
            .then(literal("ir")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("destino", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (String s : DESTINOS.keySet()) b.suggest(s);
                        return b.buildFuture();
                    })
                    .executes(ctx -> viajar(ctx.getSource(),
                        StringArgumentType.getString(ctx, "destino")))))

            // Alta de constructor con clave. Ver LunaConfig.builderKey.
            .then(literal("constructor")
                .then(argument("clave", StringArgumentType.word())
                    .executes(ctx -> altaConstructor(
                        ctx.getSource(), StringArgumentType.getString(ctx, "clave")))))

            .then(literal("auditar")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> audit(ctx.getSource())))

            .then(literal("dar")
                .requires(s -> s.hasPermissionLevel(3))
                .then(argument("moneda", StringArgumentType.word())
                .then(argument("cantidad", LongArgumentType.longArg(1))
                    .executes(ctx -> grant(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "moneda"),
                        LongArgumentType.getLong(ctx, "cantidad"))))))

            .then(literal("estado")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> status(ctx.getSource())))

            .then(literal("economia")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> { EconomyReport.send(ctx.getSource(), 24); return 1; })
                .then(argument("horas", com.mojang.brigadier.arguments.IntegerArgumentType
                        .integer(1, 720))
                    .executes(ctx -> {
                        EconomyReport.send(ctx.getSource(),
                            com.mojang.brigadier.arguments.IntegerArgumentType
                                .getInteger(ctx, "horas"));
                        return 1;
                    })))

            .then(literal("rotarcazas")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> rotarCazas(ctx.getSource())))

            .then(literal("cosmeticos")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> cosmeticos(ctx.getSource()))
                .then(literal("huerfanos")
                    .executes(ctx -> huerfanos(ctx.getSource()))))

            .then(literal("via")
                .requires(s -> s.hasPermissionLevel(3))
                .then(argument("via", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (var v : net.pokereport.luna.progression.Path.values()) {
                            b.suggest(v.name());
                        }
                        return b.buildFuture();
                    })
                    .then(argument("xp", com.mojang.brigadier.arguments.LongArgumentType
                            .longArg(1, 1_000_000))
                        .executes(ctx -> darVia(ctx.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType
                                        .getString(ctx, "via"),
                                com.mojang.brigadier.arguments.LongArgumentType
                                        .getLong(ctx, "xp"))))))

            .then(literal("reiniciarinicial")
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> reiniciarInicial(ctx.getSource())))

            .then(literal("autotest")
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> autotest(ctx.getSource())))
        );
    }

    // ------------------------------------------------------------------

    /** Nombre corto → dimensión. El orden es el que sale al autocompletar. */
    private static final java.util.Map<String, net.minecraft.registry.RegistryKey<net.minecraft.world.World>> DESTINOS =
        new java.util.LinkedHashMap<>() {{
            put("ciudadela", net.pokereport.luna.world.LunaDimensions.CIUDADELA);
            put("lobby", net.pokereport.luna.world.LunaDimensions.LOBBY);
            put("hogar", net.pokereport.luna.world.LunaDimensions.HOGAR);
            put("salvaje", net.pokereport.luna.world.LunaDimensions.SALVAJE);
        }};

    private static int viajar(ServerCommandSource src, String destino) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        var clave = DESTINOS.get(destino.toLowerCase(java.util.Locale.ROOT));
        if (clave == null) {
            src.sendError(Text.literal(
                "No conozco ese sitio. Hay: " + String.join(", ", DESTINOS.keySet())));
            return 0;
        }
        return net.pokereport.luna.world.TravelService.travel(
            p, clave, net.pokereport.luna.world.TravelService.nameOf(clave)) ? 1 : 0;
    }

    /** Nivel de operador de un constructor. NO subirlo: ver docs/world/construccion.md. */
    private static final int NIVEL_CONSTRUCTOR = 2;

    /**
     * Se da de alta como constructor presentando la clave.
     *
     * <p>Concede <b>nivel 2</b>: creativo, {@code /tp}, WorldEdit y Axiom
     * completo. NO concede nivel 3 ni 4, así que un constructor no puede
     * banear, ni dar OP, ni apagar el servidor — ni por accidente.
     *
     * <p>La comparación es de tiempo constante. Es exagerado para un servidor
     * de amigos, y cuesta una línea: comparar con {@code equals} filtra el
     * tiempo de respuesta y deja adivinar la clave carácter a carácter.
     */
    private static int altaConstructor(ServerCommandSource src, String clave) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }

        String esperada = LunaEternal.builderKey();
        if (esperada == null || esperada.isBlank()) {
            src.sendError(Text.literal("Las altas de constructor están cerradas."));
            return 0;
        }

        if (!java.security.MessageDigest.isEqual(
                clave.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                esperada.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            // Se registra: si alguien está probando claves, quiero verlo.
            LunaEternal.LOG.warn("Clave de constructor incorrecta de {}",
                p.getGameProfile().getName());
            src.sendError(Text.literal("Esa clave no vale."));
            return 0;
        }

        var server = p.getServer();
        if (p.hasPermissionLevel(NIVEL_CONSTRUCTOR)) {
            src.sendFeedback(() -> Text.literal("§aYa eres constructor."), false);
            return 1;
        }

        // addToOperators() daría el nivel de `op-permission-level`, que es 4.
        // Aquí se fuerza el 2 escribiendo la entrada a mano.
        server.getPlayerManager().getOpList().add(
            new net.minecraft.server.OperatorEntry(
                p.getGameProfile(), NIVEL_CONSTRUCTOR, false));
        // Sin esto el jugador no ve los comandos nuevos hasta reconectar.
        server.getPlayerManager().sendCommandTree(p);

        LunaEternal.LOG.info("{} es ahora constructor (nivel {})",
            p.getGameProfile().getName(), NIVEL_CONSTRUCTOR);
        src.sendFeedback(() -> Text.literal(
            "§a¡Listo! Ya eres constructor.\n"
            + "§7Ve a la ciudadela con §f/luna ir ciudadela\n"
            + "§7Ponte en creativo con §f/gamemode creative\n"
            + "§7Abre Axiom con §fShift derecho"), false);
        return 1;
    }

    private static int balance(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                long dollars = LunaEternal.economy().balance(id, Currency.POKEDOLLAR);
                long marks   = LunaEternal.economy().balance(id, Currency.MARK);
                // Volver al hilo del servidor para hablar con el jugador.
                p.getServer().execute(() -> p.sendMessage(Text.literal(
                    // El nombre sale del enum, no escrito aqui: cambiarlo en
                    // dos sitios es como se acaba con una pantalla que dice
                    // "Plata" y un comando que sigue diciendo otra cosa.
                    Currency.POKEDOLLAR.color + Currency.POKEDOLLAR.displayName
                    + ": §f" + dollars + "  " + Currency.MARK.color
                    + Currency.MARK.displayName + ": §f" + marks), false));
            } catch (Exception e) {
                reply(p, "§cError al consultar el saldo: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int grant(ServerCommandSource src, String currencyName, long amount) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        final Currency currency;
        try {
            currency = Currency.valueOf(currencyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // ⚠ EL MENSAJE SE CONSTRUYE DEL ENUM, no se escribe a mano. Decia
            //   "Usa POKEDOLLAR o MARK" desde antes de que existiera REPORTCOIN
            //   (D-013): la moneda funcionaba y el error juraba que no existia,
            //   que es la peor combinacion -- quien lo leyera dejaria de
            //   intentarlo. Sacandolo del enum, una moneda nueva se lista sola.
            String monedas = java.util.Arrays.stream(Currency.values())
                    .map(c -> c.name() + " (" + c.displayName + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            src.sendError(Text.literal("Moneda desconocida. Usa: " + monedas));
            return 0;
        }

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                // Clave de idempotencia nueva en cada uso: es un comando de
                // admin, no un reintento. En operaciones reales la clave
                // viene de la operación de origen (R4).
                long after = LunaEternal.economy().credit(
                    id, currency, amount, "admin_grant", UUID.randomUUID().toString());
                reply(p, "§aConcedido. Saldo " + currency.displayName + ": §f" + after);
            } catch (EconomyException e) {
                reply(p, "§c" + e.getMessage());
            } catch (Exception e) {
                reply(p, "§cError: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int audit(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                long dd = LunaEternal.economy()
                    .auditDiscrepancy(id, Currency.POKEDOLLAR);
                long dm = LunaEternal.economy()
                    .auditDiscrepancy(id, Currency.MARK);
                String msg = (dd == 0 && dm == 0)
                    ? "§aSaldo y libro de asientos cuadran."
                    : "§c¡DESCUADRE! " + Currency.POKEDOLLAR.displayName + ": " + dd
                      + "  " + Currency.MARK.displayName + ": " + dm;
                reply(p, msg);
            } catch (Exception e) {
                reply(p, "§cError al auditar: " + e.getMessage());
            }
        });
        return 1;
    }

    /**
     * Ejecuta la batería de invariantes económicos. Funciona desde la consola,
     * así que no hace falta ningún jugador conectado.
     */
    /**
     * Que cosmeticos tiene QUIEN LO ESCRIBE, leidos de la base.
     *
     * <p>Existe porque el usuario dijo «no compre el snorlax chef y dice que ya
     * lo tengo», y la unica forma de contestar a eso sin suponer es MIRAR. Salio
     * que si lo tenia --de las cuatro compras de prueba, con el catalogo viejo,
     * que usaba los mismos identificadores-- pero eso no se sabia hasta mirarlo.
     *
     * <p>Se queda porque la pregunta va a volver: cada vez que alguien diga «yo
     * no compre esto», la respuesta tiene que salir de la tabla, no de la
     * memoria de nadie.
     */
    private static int cosmeticos(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var tiene = LunaEternal.cosmetics().poseidos(id);
                var lineas = new java.util.ArrayList<String>();
                for (String c : new java.util.TreeSet<>(tiene)) {
                    // Se marca lo que YA NO ESTA en el catalogo. Es el caso que
                    // importa: un cosmetico comprado que despues se retiro --los
                    // 8 del pack que no traian arte, por ejemplo-- sigue en la
                    // tabla y no sale en la tienda. Sin esta marca, el recuento
                    // de la pantalla y el de aqui no cuadran y parece un fallo.
                    boolean vigente = net.pokereport.luna.cosmetics.Catalogo.de(c) != null;
                    lineas.add((vigente ? "§7  " : "§8  ") + c
                            + (vigente ? "" : " §8(ya no esta en el catalogo)"));
                }
                origen.getServer().execute(() -> {
                    origen.sendFeedback(() -> Text.literal(
                            "§7" + tiene.size() + " cosmeticos de "
                            + jugador.getName().getString()), false);
                    for (String l : lineas) {
                        origen.sendFeedback(() -> Text.literal(l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron leer los cosmeticos: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Cosmeticos que alguien TIENE y ya no estan en el catalogo.
     *
     * <p>⚠ EXISTE PORQUE EL CATALOGO PUEDE ENCOGER, y encoger es normal: se
     * retiraron los nueve que el pack declaraba sin arte, las cinco formas mega y
     * la categoria de capas entera. Cada vez que eso pasa, quien hubiera comprado
     * uno se queda con una fila en {@code player_cosmetics} que apunta a algo que
     * ya no existe.
     *
     * <p><b>No rompe nada</b> —el catalogo se recorre al reves, asi que la pieza
     * simplemente no sale— y ese es justo el problema: <b>es invisible</b>. El
     * jugador pago y no tiene nada, y nadie se entera hasta que pregunta.
     *
     * <p>Esto no devuelve el dinero: solo dice a quien hay que devolverselo.
     * Reembolsar automaticamente seria peor —una regeneracion del catalogo con un
     * fallo devolveria dinero a medio servidor— y {@code /luna dar} ya existe.
     */
    private static int huerfanos(ServerCommandSource origen) {
        LunaEternal.submit(() -> {
            try {
                var filas = LunaEternal.cosmetics().huerfanos();
                origen.getServer().execute(() -> {
                    if (filas.isEmpty()) {
                        origen.sendFeedback(() -> Text.literal(
                                "§aNadie tiene cosmeticos retirados."), false);
                        return;
                    }
                    origen.sendFeedback(() -> Text.literal(
                            "§e" + filas.size() + " compras de cosmeticos que ya "
                            + "no estan en el catalogo:"), false);
                    for (String l : filas) {
                        origen.sendFeedback(() -> Text.literal("§7  " + l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron buscar los huerfanos: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Dar XP de una Via. <b>Solo para probar la pantalla de Trabajos.</b>
     *
     * <p>⚠ La XP de Vias se gana JUGANDO --capturas, combates, ventas--, asi que
     * sin esto no habia forma de ver una barra a medias ni un nivel distinto de
     * cero sin echar horas. Con las barras siempre a cero no se puede juzgar si
     * la pantalla dibuja bien, que es justo lo que hay que comprobar.
     *
     * <p>⚠⚠ ESTO INYECTA PROGRESION, y la progresion NO SE VENDE NI SE REGALA
     * (P4, D-014). Va a nivel 3 --el mismo que `/luna dar`-- y queda anotado en
     * el libro de la Via como cualquier otra concesion, para que una prueba no se
     * confunda despues con progresion jugada.
     */
    private static int darVia(ServerCommandSource origen, String nombre, long xp) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        net.pokereport.luna.progression.Path via = null;
        for (var v : net.pokereport.luna.progression.Path.values()) {
            if (v.name().equalsIgnoreCase(nombre)) {
                via = v;
            }
        }
        if (via == null) {
            // Se dicen las que HAY, en vez de "via desconocida". Un error que no
            // ofrece la salida obliga a ir a buscarla al codigo.
            var sb = new StringBuilder("Vias: ");
            for (var v : net.pokereport.luna.progression.Path.values()) {
                sb.append(v.name()).append(' ');
            }
            origen.sendError(Text.literal(sb.toString().trim()));
            return 0;
        }
        final var elegida = via;
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                // ⚠⚠ POR `OficiosService`, NO POR `progression().grant()`.
                //
                //   Estaba llamando a `grant` directamente, que solo escribe la
                //   fila: SIN PAGO, SIN AVISO Y SIN SONIDO. Y yo le dije al
                //   usuario que probara con este comando, asi que probo justo el
                //   unico camino que no hace nada de lo que se acababa de
                //   construir. Su reporte fue exacto: "cuando subes de nivel no
                //   da plata ni nada".
                //
                //   Un comando de prueba que no recorre el mismo camino que el
                //   juego no prueba nada; solo da la falsa sensacion de haberlo
                //   probado.
                net.pokereport.luna.progression.OficiosService.ganar(jugador, id, elegida, xp);
                var estado = LunaEternal.progression().all(id).get(elegida);
                final int nivel = estado == null ? 0 : estado.level();
                final long tiene = estado == null ? 0 : estado.xp();
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "§a" + elegida.displayName + " -> nivel "
                        + net.pokereport.luna.progression.Path.roman(nivel)
                        + " (" + tiene + " XP)"), false));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo dar XP de via: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Borra la marca de «ya elegiste inicial». <b>Solo para probar.</b>
     *
     * <p>⚠ Nivel 4 --el mas alto-- y no 3 como los demas. Los otros comandos de
     * prueba dan cosas; este PERMITE VOLVER A COGER UN POKEMON GRATIS. No es lo
     * mismo, y la diferencia importa: un constructor con nivel 2 o un moderador
     * con nivel 3 no deberian poder repartir iniciales.
     *
     * <p>⚠ Y NO quita el Pokemon que ya se entrego. Borra solo la marca, asi que
     * quien lo use se queda con los dos. Es correcto para probar y seria un
     * agujero en produccion: por eso el nivel.
     */
    private static int reiniciarInicial(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                LunaEternal.kitService().undoOnce(id,
                        net.pokereport.luna.starter.StarterService.CLAVE);
                // ⚠ SE REENVIA EL ESTADO, y sin esto el comando "no servia":
                //   borraba la fila y no pasaba nada visible, porque el cliente
                //   guarda la ultima respuesta y seguia creyendo que ya habia
                //   elegido. Borrar en la base no cambia lo que el cliente cree.
                net.pokereport.luna.net.Red.refrescarInicial(jugador);
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "§aMarca borrada. La pantalla se abrira sola."), false));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo reiniciar el inicial: {}", e.toString());
            }
        });
        return 1;
    }

    private static int autotest(ServerCommandSource src) {
        var server = src.getServer();
        src.sendFeedback(() -> Text.literal("§7Ejecutando autotest…"), false);
        LunaEternal.submit(() -> {
            var test = new net.pokereport.luna.test.AutoTest(
                LunaEternal.database(),
                LunaEternal.players(),
                LunaEternal.economy(),
                line -> server.execute(() ->
                    src.sendFeedback(() -> Text.literal(line), false)));
            test.run();
        });
        return 1;
    }

    /** Fuerza la rotación de cazas. Herramienta de administración. */
    private static int rotarCazas(ServerCommandSource src) {
        var server = src.getServer();
        LunaEternal.submit(() -> {
            try {
                int n = LunaEternal.hunts().rotarYa();
                server.execute(() -> src.sendFeedback(() -> Text.literal(
                    "§aCiclos caducados: " + n
                    + ". El próximo vistazo sorteará cazas nuevas."), true));
            } catch (Exception e) {
                server.execute(() -> src.sendError(
                    Text.literal("No se pudo rotar: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int status(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
            "§6Luna Eternal §7· jugadores en cache: §f"
                + LunaEternal.players().cachedCount()), false);
        return 1;
    }

    private static void reply(ServerPlayerEntity p, String msg) {
        p.getServer().execute(() -> p.sendMessage(Text.literal(msg), false));
    }
}
