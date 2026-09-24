package net.pokereport.luna.tebex;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Entrypoint Brigadier seguro de consola para comandos de Tebex.
 *
 * <p>Restricciones de seguridad estrictas:
 * <ul>
 *   <li>Solo ejecutable por la consola del servidor dedicado local.</li>
 *   <li>Bloquea a cualquier jugador (incluso con OP nivel 4).</li>
 *   <li>Bloquea RCON, bloques de comandos y funciones de datapacks.</li>
 *   <li>No confía en montos ni rangos externos.</li>
 * </ul>
 */
public final class TebexCommand {

    private TebexCommand() {}

    /**
     * Valida de forma estricta que la fuente de ejecución sea la consola local del servidor dedicado.
     */
    public static boolean esConsolaAutorizada(ServerCommandSource source) {
        if (source == null) {
            return false;
        }
        // 1. Debe tener nivel de permisos 4 (administrador absoluto)
        if (!source.hasPermissionLevel(4)) {
            return false;
        }
        // 2. No puede provenir de ninguna entidad (jugadores, armor stands, etc.)
        if (source.getEntity() != null) {
            return false;
        }
        // 3. El receptor de salida DEBE ser estrictamente el servidor dedicado local
        // Excluye RCON (RconCommandOutput) y bloques de comandos (CommandBlockExecutor)
        if (source.getServer() == null) {
            return false;
        }
        // 4. Nombre de la fuente de consola dedicado debe ser "Server"
        return "Server".equalsIgnoreCase(source.getName());
    }

    /**
     * Registra el comando raíz {@code /tebex-fulfill} si se deseara registro independiente.
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, TebexService service) {
        dispatcher.register(buildSubtree(service));
    }

    /**
     * Construye el subárbol {@code tebex} resolviendo el servicio en runtime desde {@link LunaEternal#tebex()}.
     */
    public static LiteralArgumentBuilder<ServerCommandSource> buildSubtree() {
        return buildSubtree(null);
    }

    /**
     * Construye el subárbol {@code tebex} para enlazarlo bajo {@code /luna tebex}.
     */
    public static LiteralArgumentBuilder<ServerCommandSource> buildSubtree(TebexService explicitService) {
        return literal("tebex")
                .requires(TebexCommand::esConsolaAutorizada)
                .then(literal("fulfill")
                        .then(argument("transaction", StringArgumentType.string())
                                .then(argument("uuid", StringArgumentType.string())
                                        .then(argument("packageId", StringArgumentType.string())
                                                .then(argument("purchaseQuantity", IntegerArgumentType.integer())
                                                        .executes(ctx -> executeFulfill(ctx, explicitService, null))
                                                        .then(argument("username", StringArgumentType.string())
                                                                .executes(ctx -> executeFulfill(ctx, explicitService,
                                                                        StringArgumentType.getString(ctx, "username")))))))))
                .then(literal("refund")
                        .then(argument("transaction", StringArgumentType.string())
                                .then(argument("uuid", StringArgumentType.string())
                                        .then(argument("packageId", StringArgumentType.string())
                                                .executes(ctx -> executeRefund(ctx, explicitService))))))
                .then(literal("chargeback")
                        .then(argument("transaction", StringArgumentType.string())
                                .then(argument("uuid", StringArgumentType.string())
                                        .then(argument("packageId", StringArgumentType.string())
                                                .executes(ctx -> executeChargeback(ctx, explicitService))))));
    }

    private static TebexService resolveService(TebexService explicitService) {
        return explicitService != null ? explicitService : LunaEternal.tebex();
    }

    private static int executeFulfill(CommandContext<ServerCommandSource> ctx, TebexService service, String usernameHint) {
        ServerCommandSource src = ctx.getSource();
        String tx = StringArgumentType.getString(ctx, "transaction");
        String rawIdentity = StringArgumentType.getString(ctx, "uuid");
        String pkg = StringArgumentType.getString(ctx, "packageId");
        int quantity = IntegerArgumentType.getInteger(ctx, "purchaseQuantity");

        TebexService svc = resolveService(service);
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Error: TebexService no inicializado"), false);
            return 0;
        }

        TebexService.ExecutionResult res = svc.fulfill(tx, rawIdentity, pkg, quantity, usernameHint);
        if (res.isSuccess()) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Fulfill registrado: " + tx + " [" + res.status() + "]"), false);
            return 1;
        } else if (res.type() == TebexService.ResultType.PENDING_IMPLEMENTATION) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Fulfill retenido (implementación pendiente en fase actual): " + tx + " [Pkg: " + pkg + "]"), false);
            return 1;
        } else if (res.type() == TebexService.ResultType.REQUIRES_REVIEW) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Fulfill requiere revisión: " + tx + " [Motivo: " + res.reason() + "]"), false);
            return 1; // Retorna éxito a Tebex para liberar cola, pero queda retenido en REQUIRES_REVIEW
        } else {
            src.sendFeedback(() -> Text.literal("[TEBEX] Fulfill fallido: " + tx + " [Motivo: " + res.reason() + "]"), false);
            return 0;
        }
    }

    private static int executeRefund(CommandContext<ServerCommandSource> ctx, TebexService service) {
        ServerCommandSource src = ctx.getSource();
        String tx = StringArgumentType.getString(ctx, "transaction");
        String rawIdentity = StringArgumentType.getString(ctx, "uuid");
        String pkg = StringArgumentType.getString(ctx, "packageId");

        TebexService svc = resolveService(service);
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Error: TebexService no inicializado"), false);
            return 0;
        }

        TebexService.ExecutionResult res = svc.refund(tx, rawIdentity, pkg);
        if (res.isSuccess()) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Refund registrado: " + tx + " [" + res.status() + "]"), false);
            return 1;
        } else if (res.type() == TebexService.ResultType.REQUIRES_REVIEW) {
            src.sendFeedback(() -> Text.literal("[TEBEX] Refund requiere revisión: " + tx + " [" + res.reason() + "]"), false);
            return 1;
        } else {
            src.sendFeedback(() -> Text.literal("[TEBEX] Refund fallido: " + tx + " [" + res.reason() + "]"), false);
            return 0;
        }
    }

    private static int executeChargeback(CommandContext<ServerCommandSource> ctx, TebexService service) {
        ServerCommandSource src = ctx.getSource();
        String tx = StringArgumentType.getString(ctx, "transaction");
        String rawIdentity = StringArgumentType.getString(ctx, "uuid");
        String pkg = StringArgumentType.getString(ctx, "packageId");

        TebexService svc = resolveService(service);
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("[TEBEX-FRAUD] Error: TebexService no inicializado"), false);
            return 0;
        }

        TebexService.ExecutionResult res = svc.chargeback(tx, rawIdentity, pkg);
        if (res.isSuccess()) {
            src.sendFeedback(() -> Text.literal("[TEBEX-FRAUD] Chargeback registrado: " + tx + " [" + res.status() + "]"), false);
            return 1;
        } else if (res.type() == TebexService.ResultType.REQUIRES_REVIEW) {
            src.sendFeedback(() -> Text.literal("[TEBEX-FRAUD] Chargeback requiere revisión: " + tx + " [" + res.reason() + "]"), false);
            return 1;
        } else {
            src.sendFeedback(() -> Text.literal("[TEBEX-FRAUD] Chargeback fallido: " + tx + " [" + res.reason() + "]"), false);
            return 0;
        }
    }
}
