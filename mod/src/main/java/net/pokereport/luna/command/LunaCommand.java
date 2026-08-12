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
 * <p>No son la interfaz final —esa es El Almanaque (ui/navigation.md)—.
 * Existen para comprobar que persistencia, idempotencia y atomicidad
 * funcionan de verdad contra la base.
 */
public final class LunaCommand {

    private LunaCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {

        // Atajo para quien prefiera el teclado. NO es el camino principal:
        // el Almanaque se abre con su objeto (docs/ui/navigation.md §2).
        d.register(literal("menu").executes(ctx -> openAlmanac(ctx.getSource())));
        d.register(literal("almanaque").executes(ctx -> openAlmanac(ctx.getSource())));

        d.register(literal("luna")
            .executes(ctx -> openAlmanac(ctx.getSource()))
            .then(literal("saldo")
                .executes(ctx -> balance(ctx.getSource())))

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

    private static int openAlmanac(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        net.pokereport.luna.ui.MenuService.openAlmanac(p);
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
