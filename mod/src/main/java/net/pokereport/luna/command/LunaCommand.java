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
                    "§6PokeDolares: §f" + dollars + "  §b| Marcas: §f" + marks), false));
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
            src.sendError(Text.literal("Moneda desconocida. Usa POKEDOLLAR o MARK."));
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
                    : "§c¡DESCUADRE! PokeDolares: " + dd + "  Marcas: " + dm;
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
