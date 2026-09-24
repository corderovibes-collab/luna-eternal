package net.pokereport.luna.tebex;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.net.Red;
import net.pokereport.luna.player.PlayerService;

import net.pokereport.luna.rank.RankService;
import net.pokereport.luna.traje.Traje;
import net.pokereport.luna.traje.TrajeService;
import net.pokereport.luna.ui.Tablist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio transaccional que gestiona el ciclo de vida de pagos y entregas de Tebex.
 *
 * <p>En Fase 4C, realiza el fulfillment real de {@link ProductType#LUNACOINS}, {@link ProductType#RANK}
 * y {@link ProductType#RANK_UPGRADE} integrándose con {@link EconomyService}, {@link RankService}
 * y {@link TrajeService}.
 */
public class TebexService {

    private final Database db;
    private final PackageRegistry registry;
    private final PlayerService playerService;
    private final EconomyService economyService;
    private final RankService rankService;
    private final TrajeService trajeService;

    public TebexService(Database db, PackageRegistry registry, PlayerService playerService) {
        this(db, registry, playerService, null, null, null);
    }

    public TebexService(Database db, PackageRegistry registry, PlayerService playerService, EconomyService economyService) {
        this(db, registry, playerService, economyService, null, null);
    }

    public TebexService(Database db, PackageRegistry registry, PlayerService playerService,
                        EconomyService economyService, RankService rankService, TrajeService trajeService) {
        this.db = db;
        this.registry = registry;
        this.playerService = playerService;
        this.economyService = economyService;
        this.rankService = rankService;
        this.trajeService = trajeService;
    }

    public enum ResultType {
        SUCCESS,
        ALREADY_PROCESSED,
        PENDING_IMPLEMENTATION,
        REQUIRES_REVIEW,
        FAILED
    }

    public record ExecutionResult(ResultType type, String status, String reason) {
        public boolean isSuccess() {
            return type == ResultType.SUCCESS || type == ResultType.ALREADY_PROCESSED;
        }
    }

    public record TebexActiveRankFulfillment(long id, String packageId, String productType, String productValue) {}

    public record PendingFulfillmentItem(
            long id,
            String transactionId,
            String packageId,
            String productType,
            String productValue,
            int quantity
    ) {}

    public record FulfillmentRow(
            long id,
            String transactionId,
            String packageId,
            String playerUuid,
            Long playerId,
            String usernameRaw,
            int purchaseQuantity,
            String productType,
            String productValue,
            TebexStatus status,
            TebexReversalStatus reversalStatus,
            TebexFinancialEvent financialEvent
    ) {
        public FulfillmentRow(
                long id,
                String transactionId,
                String packageId,
                String playerUuid,
                Long playerId,
                String productType,
                String productValue,
                TebexStatus status,
                TebexReversalStatus reversalStatus,
                TebexFinancialEvent financialEvent
        ) {
            this(id, transactionId, packageId, playerUuid, playerId, null, 1, productType, productValue, status, reversalStatus, financialEvent);
        }
    }

    /**
     * Procesa la recepción y crédito de un paquete comprado (Initial Command).
     */
    public ExecutionResult fulfill(String transactionId, UUID playerUuid, String packageId,
                                   int quantity, String usernameHint) {
        return fulfill(transactionId, playerUuid != null ? playerUuid.toString() : null, packageId, quantity, usernameHint);
    }

    public ExecutionResult fulfill(String transactionId, String playerIdentity, String packageId,
                                   int quantity, String usernameHint) {
        // 1. Validar transactionId (según regla 0: no blank, <= 255 chars, sin whitespace ni control chars)
        if (!TebexParser.isValidTransactionId(transactionId)) {
            LunaEternal.LOG.error("[TEBEX] operation=FULFILL status=FAILED reason=INVALID_TRANSACTION transaction={}", transactionId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_TRANSACTION");
        }

        // 2. Validar identidad
        if (playerIdentity == null || playerIdentity.isBlank()) {
            LunaEternal.LOG.error("[TEBEX] operation=FULFILL status=FAILED reason=INVALID_IDENTITY transaction={}", transactionId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_IDENTITY");
        }

        if (packageId == null || packageId.isBlank()) {
            LunaEternal.LOG.error("[TEBEX] operation=FULFILL status=FAILED reason=INVALID_PACKAGE_ID transaction={}", transactionId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_PACKAGE_ID");
        }
        String cleanPackageId = packageId.trim();

        // 3. Abrir transacción SQL única para garantizar atomicidad estricta entre tebex_payment,
        //    tebex_fulfillment, ledger_entry y player_economy.
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                // Comprobar si este paquete específico ya fue procesado con bloqueo de fila FOR UPDATE
                TebexStatus existingStatus = getFulfillmentStatusForUpdate(c, transactionId, cleanPackageId);
                if (existingStatus != null) {
                    if (existingStatus == TebexStatus.DELIVERED) {
                        c.rollback();
                        LunaEternal.LOG.info("[TEBEX] operation=FULFILL status=ALREADY_PROCESSED transaction={} package={} existingStatus=DELIVERED",
                                transactionId, cleanPackageId);
                        return new ExecutionResult(ResultType.ALREADY_PROCESSED, "ALREADY_PROCESSED", "DUPLICATE_TRANSACTION_PACKAGE");
                    } else if (existingStatus == TebexStatus.PENDING_PLAYER_RESOLUTION) {
                        c.rollback();
                        LunaEternal.LOG.info("[TEBEX] operation=FULFILL status=PENDING_PLAYER_RESOLUTION transaction={} package={} (ya en cola)",
                                transactionId, cleanPackageId);
                        return new ExecutionResult(ResultType.SUCCESS, "PENDING_PLAYER_RESOLUTION", "Entitlement en espera de primer join");
                    } else if (existingStatus == TebexStatus.REFUND || existingStatus == TebexStatus.REFUNDED || existingStatus == TebexStatus.CHARGEBACK) {
                        c.rollback();
                        LunaEternal.LOG.warn("[TEBEX] operation=FULFILL status=REQUIRES_REVIEW transaction={} package={} (orden ya cancelada/reembolsada previamente)",
                                transactionId, cleanPackageId);
                        return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "ORDER_ALREADY_CANCELLED_OR_REFUNDED");
                    } else if (existingStatus == TebexStatus.REQUIRES_REVIEW) {
                        c.rollback();
                        LunaEternal.LOG.warn("[TEBEX] operation=FULFILL status=REQUIRES_REVIEW transaction={} package={} (previamente retenido)",
                                transactionId, cleanPackageId);
                        return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "PREVIOUSLY_MARKED_REVIEW");
                    } else if (existingStatus == TebexStatus.PENDING_IMPLEMENTATION) {
                        // Transición segura de PENDING_IMPLEMENTATION -> PROCESSING para proceder en FASE 4C
                        markFulfillmentProcessing(c, transactionId, cleanPackageId);
                        LunaEternal.LOG.info("[TEBEX] Transicionando fulfillment previo PENDING_IMPLEMENTATION -> PROCESSING para tx={} pkg={}",
                                transactionId, cleanPackageId);
                    }
                }

                // 4. Validación de purchaseQuantity (únicamente 1 es válido)
                if (quantity != 1) {
                    String reason = "CANTIDAD_ANOMALA_RECIBIDA (quantity=" + quantity + ")";
                    LunaEternal.LOG.warn("[TEBEX] operation=FULFILL status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} identity={}",
                            reason, transactionId, cleanPackageId, playerIdentity);
                    recordFulfillmentReview(c, transactionId, cleanPackageId, playerIdentity, "UNKNOWN", "UNKNOWN", reason);
                    c.commit();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

                // 5. Resolver definición del paquete en PackageRegistry
                ProductDefinition def = registry.resolve(cleanPackageId);
                if (def == null) {
                    String reason = "UNKNOWN_PACKAGE (" + cleanPackageId + ")";
                    LunaEternal.LOG.warn("[TEBEX] operation=FULFILL status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} identity={}",
                            reason, transactionId, cleanPackageId, playerIdentity);
                    recordFulfillmentReview(c, transactionId, cleanPackageId, playerIdentity, "UNKNOWN", "UNKNOWN", reason);
                    c.commit();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

                if (cleanPackageId.startsWith("PLACEHOLDER_") || cleanPackageId.startsWith("PKG_")) {
                    String reason = "PLACEHOLDER_PACKAGE_ID_NOT_CONFIGURED";
                    LunaEternal.LOG.warn("[TEBEX] operation=FULFILL status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} identity={}",
                            reason, transactionId, cleanPackageId, playerIdentity);
                    recordFulfillmentReview(c, transactionId, cleanPackageId, playerIdentity, def.type().name(), resolveProductValue(def), reason);
                    c.commit();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

                // 6. Resolver identidad mediante TebexPlayerResolver
                TebexPlayerResolver.ResolutionResult resResult = resolveIdentity(c, playerIdentity, usernameHint);
                if (resResult.status() == TebexPlayerResolver.Status.REJECTED) {
                    c.rollback();
                    LunaEternal.LOG.error("[TEBEX] operation=FULFILL status=FAILED reason=PLAYER_RESOLVE_FAILED transaction={} identity={}",
                            transactionId, playerIdentity);
                    return new ExecutionResult(ResultType.FAILED, "FAILED", resResult.failureReason());
                }

                if (resResult.status() == TebexPlayerResolver.Status.PENDING_PLAYER_RESOLUTION) {
                    // CASO 4: Jugador nuevo offline -> Entitlement en espera de primer login
                    ensurePaymentRecord(c, transactionId, null, resResult.username());
                    recordFulfillmentPendingResolution(c, transactionId, cleanPackageId, resResult.username(), quantity, def);
                    c.commit();
                    LunaEternal.LOG.info("[TEBEX] operation=FULFILL status=PENDING_PLAYER_RESOLUTION transaction={} package={} username={} (esperando primer login)",
                            transactionId, cleanPackageId, resResult.username());
                    return new ExecutionResult(ResultType.SUCCESS, "PENDING_PLAYER_RESOLUTION", "Entitlement en espera de primer join");
                }

                // CASOS 1, 2 y 3: Jugador resuelto (UUID y player_id disponibles)
                UUID resolvedUuid = resResult.uuid();
                long resolvedPlayerId = resResult.playerId();
                String canonicalUsername = resResult.username();

                ensurePaymentRecord(c, transactionId, resolvedUuid != null ? resolvedUuid.toString() : null, canonicalUsername);

                // 7. Enrutamiento por tipo de producto
                if (def.type() == ProductType.LUNACOINS) {
                    return fulfillLunaCoins(c, transactionId, cleanPackageId, resolvedUuid, resolvedPlayerId, canonicalUsername, quantity, def);
                } else if (def.type() == ProductType.RANK) {
                    return fulfillRank(c, transactionId, cleanPackageId, resolvedUuid, resolvedPlayerId, canonicalUsername, quantity, def);
                } else if (def.type() == ProductType.RANK_UPGRADE) {
                    return fulfillRankUpgrade(c, transactionId, cleanPackageId, resolvedUuid, resolvedPlayerId, canonicalUsername, quantity, def);
                } else {
                    String reason = "UNSUPPORTED_PRODUCT_TYPE (" + def.type() + ")";
                    recordFulfillmentReview(c, transactionId, cleanPackageId, playerIdentity, def.type().name(), resolveProductValue(def), reason);
                    c.commit();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (SQLException rbEx) {
                    LunaEternal.LOG.error("[TEBEX] Error en rollback para tx={} pkg={}", transactionId, cleanPackageId, rbEx);
                }
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("[TEBEX] operation=FULFILL status=FAILED reason=DB_ERROR transaction={} package={}",
                    transactionId, cleanPackageId, e);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "DB_ERROR");
        }
    }

    protected TebexPlayerResolver.ResolutionResult resolveIdentity(Connection c, String playerIdentity, String usernameHint) throws SQLException {
        UUID parsedUuid = TebexParser.parseCanonicalUuid(playerIdentity);
        if (parsedUuid != null) {
            Long pid = resolvePlayerId(c, parsedUuid, usernameHint);
            if (pid != null) {
                String effectiveName = (usernameHint != null && !usernameHint.isBlank()) ? usernameHint.trim() : "TebexBuyer";
                return TebexPlayerResolver.ResolutionResult.resolved(parsedUuid, pid, effectiveName);
            }
        }
        return TebexPlayerResolver.resolve(c, LunaEternal.server(), playerService, playerIdentity, usernameHint);
    }

    private ExecutionResult fulfillLunaCoins(Connection c, String transactionId, String cleanPackageId,
                                             UUID playerUuid, long playerId, ProductDefinition def) throws SQLException {
        return fulfillLunaCoins(c, transactionId, cleanPackageId, playerUuid, playerId, null, 1, def);
    }

    private ExecutionResult fulfillLunaCoins(Connection c, String transactionId, String cleanPackageId,
                                             UUID playerUuid, long playerId, String usernameRaw, int quantity,
                                             ProductDefinition def) throws SQLException {
        long amountToCredit = def.amount();
        String idempotencyKey = "TEBEX:" + transactionId + ":" + cleanPackageId + ":CREDIT";
        String reason = "Tebex Store Purchase (" + cleanPackageId + ")";

        long balanceBefore = readEconomyBalanceForUpdate(c, playerId, Currency.REPORTCOIN);
        long balanceAfter;
        try {
            balanceAfter = applyEconomyCredit(c, playerId, amountToCredit, idempotencyKey, reason);
        } catch (EconomyException e) {
            if (e.kind == EconomyException.Kind.ALREADY_APPLIED) {
                markFulfillmentDeliveredAndGetId(c, transactionId, cleanPackageId, playerUuid, playerId, usernameRaw, quantity, "LUNACOINS", String.valueOf(amountToCredit));
                c.commit();
                LunaEternal.LOG.info("[TEBEX] operation=CREDIT status=ALREADY_PROCESSED transaction={} package={} uuid={} reason=LEDGER_IDEMPOTENT",
                        transactionId, cleanPackageId, playerUuid);
                return new ExecutionResult(ResultType.ALREADY_PROCESSED, "ALREADY_PROCESSED", "LEDGER_ALREADY_APPLIED");
            } else {
                c.rollback();
                LunaEternal.LOG.error("[TEBEX] operation=CREDIT status=FAILED transaction={} package={} uuid={} reason=ECONOMY_EXCEPTION_{}",
                        transactionId, cleanPackageId, playerUuid, e.kind, e);
                return new ExecutionResult(ResultType.FAILED, "FAILED", "ECONOMY_ERROR_" + e.kind);
            }
        }

        long fId = markFulfillmentDeliveredAndGetId(c, transactionId, cleanPackageId, playerUuid, playerId, usernameRaw, quantity, "LUNACOINS", String.valueOf(amountToCredit));
        if (fId > 0) {
            recordEffect(c, fId, TebexEffectType.COIN_CREDIT, "REPORTCOIN", String.valueOf(balanceBefore), String.valueOf(balanceAfter), true);
        }
        c.commit();

        LunaEternal.LOG.info("[TEBEX] operation=CREDIT transaction={} package={} uuid={} currency=REPORTCOIN amount={} balance_after={} status=DELIVERED",
                transactionId, cleanPackageId, playerUuid, amountToCredit, balanceAfter);

        notifyOnlinePlayerIfPresent(playerUuid, amountToCredit, balanceAfter);

        return new ExecutionResult(ResultType.SUCCESS, "DELIVERED", null);
    }

    private ExecutionResult fulfillRank(Connection c, String transactionId, String cleanPackageId,
                                        UUID playerUuid, long playerId, ProductDefinition def) throws SQLException {
        return fulfillRank(c, transactionId, cleanPackageId, playerUuid, playerId, null, 1, def);
    }

    private ExecutionResult fulfillRank(Connection c, String transactionId, String cleanPackageId,
                                        UUID playerUuid, long playerId, String usernameRaw, int quantity,
                                        ProductDefinition def) throws SQLException {
        Tablist.Rank targetRank = def.rank();
        if (targetRank == null || targetRank.esStaff()) {
            String reason = "INVALID_TARGET_RANK";
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // Bloqueo FOR UPDATE para serializar concurrencia sobre la fila del jugador
        Tablist.Rank currentRank = readRankForUpdate(c, playerId);

        // 1. Protección absoluta de Staff
        if (currentRank.esStaff()) {
            String reason = "STAFF_RANK_PROTECTED (current=" + currentRank.name() + ")";
            LunaEternal.LOG.warn("[TEBEX] Intento de aplicar rango comercial {} sobre cuenta Staff {} para uuid={}",
                    targetRank.name(), currentRank.name(), playerUuid);
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        int currentLevel = currentRank.nivelComercial();
        int targetLevel = targetRank.nivelComercial();

        // 2. Jugador ya posee exactamente el mismo rango (nueva transacción redundante)
        if (currentRank == targetRank) {
            String reason = "RANK_ALREADY_OWNED (current=" + currentRank.name() + ")";
            LunaEternal.LOG.warn("[TEBEX] Compra redundante de rango ya poseído {} para uuid={}", currentRank.name(), playerUuid);
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // 3. Protección anti-downgrade (jugador posee rango superior)
        if (currentLevel > targetLevel) {
            String reason = "LOWER_RANK_PURCHASED (current=" + currentRank.name() + ", target=" + targetRank.name() + ")";
            LunaEternal.LOG.warn("[TEBEX] Intento de compra inferior {} sobre rango superior {} para uuid={}",
                    targetRank.name(), currentRank.name(), playerUuid);
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // 4. Compra legítima hacia rango superior
        boolean rankUpdated = applyRankChange(c, playerId, targetRank);
        if (!rankUpdated) {
            c.rollback();
            LunaEternal.LOG.error("[TEBEX] Fallo al actualizar rank_id a {} para playerId={}", targetRank.name(), playerId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "RANK_UPDATE_FAILED");
        }

        // Conceder recompensa única (Traje de rango cosmético)
        Traje targetSuit = Traje.deRango(targetRank);
        boolean suitCreated = false;
        if (targetSuit != null) {
            boolean hadSuit = playerHadSuit(c, playerId, targetSuit);
            applySuitGrant(c, playerId, targetSuit);
            suitCreated = !hadSuit;
        }

        // Registrar DELIVERED en tebex_fulfillment
        long fId = markFulfillmentDeliveredAndGetId(c, transactionId, cleanPackageId, playerUuid, playerId, usernameRaw, quantity, "RANK", targetRank.name());
        if (fId > 0) {
            recordEffect(c, fId, TebexEffectType.RANK_CHANGE, "rank", currentRank.name(), targetRank.name(), true);
            if (targetSuit != null) {
                recordEffect(c, fId, TebexEffectType.SUIT_GRANT, targetSuit.id(), String.valueOf(!suitCreated), "true", suitCreated);
            }
        }

        // Commit atómico
        c.commit();

        // Efectos post-commit
        RankService.actualizarCache(playerUuid, targetRank);
        if (targetSuit != null) {
            TrajeService.actualizarCachePropiedad(playerUuid, targetSuit);
        }

        LunaEternal.LOG.info("[TEBEX] operation=RANK_GRANT transaction={} package={} uuid={} target_rank={} status=DELIVERED",
                transactionId, cleanPackageId, playerUuid, targetRank.name());

        notifyOnlinePlayerRank(playerUuid, targetRank, false);

        return new ExecutionResult(ResultType.SUCCESS, "DELIVERED", null);
    }

    private ExecutionResult fulfillRankUpgrade(Connection c, String transactionId, String cleanPackageId,
                                               UUID playerUuid, long playerId, ProductDefinition def) throws SQLException {
        return fulfillRankUpgrade(c, transactionId, cleanPackageId, playerUuid, playerId, null, 1, def);
    }

    private ExecutionResult fulfillRankUpgrade(Connection c, String transactionId, String cleanPackageId,
                                               UUID playerUuid, long playerId, String usernameRaw, int quantity,
                                               ProductDefinition def) throws SQLException {
        Tablist.Rank expectedPrevRank = def.requiredPreviousRank();
        Tablist.Rank targetRank = def.rank();

        if (expectedPrevRank == null || expectedPrevRank.esStaff() || targetRank == null || targetRank.esStaff()) {
            String reason = "INVALID_UPGRADE_DEFINITION";
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // Bloqueo FOR UPDATE para serializar concurrencia sobre la fila del jugador
        Tablist.Rank currentRank = readRankForUpdate(c, playerId);

        // 1. Protección absoluta de Staff
        if (currentRank.esStaff()) {
            String reason = "STAFF_RANK_PROTECTED (current=" + currentRank.name() + ")";
            LunaEternal.LOG.warn("[TEBEX] Intento de upgrade sobre cuenta Staff {} para uuid={}", currentRank.name(), playerUuid);
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // 2. Validación EXACTA de rango previo requerido
        if (currentRank != expectedPrevRank) {
            String reason = "UPGRADE_PREVIOUS_RANK_MISMATCH (expected=" + expectedPrevRank.name() + ", actual=" + currentRank.name() + ")";
            LunaEternal.LOG.warn("[TEBEX] Upgrade inválido para uuid={}: expectedPrev={} actual={}",
                    playerUuid, expectedPrevRank.name(), currentRank.name());
            recordFulfillmentReview(c, transactionId, cleanPackageId, playerUuid, def.type().name(), resolveProductValue(def), reason);
            c.commit();
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // 3. Upgrade legítimo (currentRank == expectedPrevRank)
        boolean rankUpdated = applyRankChange(c, playerId, targetRank);
        if (!rankUpdated) {
            c.rollback();
            LunaEternal.LOG.error("[TEBEX] Fallo al actualizar rank_id a {} para playerId={}", targetRank.name(), playerId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "RANK_UPDATE_FAILED");
        }

        // Conceder traje del nuevo rango adquirido
        Traje targetSuit = Traje.deRango(targetRank);
        boolean suitCreated = false;
        if (targetSuit != null) {
            boolean hadSuit = playerHadSuit(c, playerId, targetSuit);
            applySuitGrant(c, playerId, targetSuit);
            suitCreated = !hadSuit;
        }

        // Registrar DELIVERED en tebex_fulfillment
        long fId = markFulfillmentDeliveredAndGetId(c, transactionId, cleanPackageId, playerUuid, playerId, usernameRaw, quantity, "RANK_UPGRADE", expectedPrevRank.name() + "->" + targetRank.name());
        if (fId > 0) {
            recordEffect(c, fId, TebexEffectType.RANK_CHANGE, "rank", expectedPrevRank.name(), targetRank.name(), true);
            if (targetSuit != null) {
                recordEffect(c, fId, TebexEffectType.SUIT_GRANT, targetSuit.id(), String.valueOf(!suitCreated), "true", suitCreated);
            }
        }

        // Commit atómico
        c.commit();

        // Efectos post-commit
        RankService.actualizarCache(playerUuid, targetRank);
        if (targetSuit != null) {
            TrajeService.actualizarCachePropiedad(playerUuid, targetSuit);
        }

        LunaEternal.LOG.info("[TEBEX] operation=RANK_UPGRADE transaction={} package={} uuid={} from={} to={} status=DELIVERED",
                transactionId, cleanPackageId, playerUuid, expectedPrevRank.name(), targetRank.name());

        notifyOnlinePlayerRank(playerUuid, targetRank, true);

        return new ExecutionResult(ResultType.SUCCESS, "DELIVERED", null);
    }

    /**
     * Aplica el crédito en el libro de asientos reutilizando la conexión transaccional existente de MariaDB.
     */
    protected long applyEconomyCredit(Connection c, long playerId, long amount, String idempotencyKey, String reason)
            throws SQLException, EconomyException {
        if (economyService != null) {
            return economyService.applyInTransaction(
                    c,
                    playerId,
                    Currency.REPORTCOIN,
                    amount,
                    reason,
                    "tebex",
                    null,
                    idempotencyKey
            );
        }
        throw new IllegalStateException("EconomyService no configurado");
    }

    /**
     * Resuelve el player_id interno de MariaDB a partir del UUID de Mojang.
     */
    protected Long resolvePlayerId(Connection c, UUID playerUuid, String usernameHint) {
        if (playerService != null) {
            try {
                String hint = (usernameHint != null && !usernameHint.isBlank()) ? usernameHint.trim() : "TebexBuyer";
                return playerService.resolve(playerUuid, hint);
            } catch (Exception e) {
                LunaEternal.LOG.warn("[TEBEX] Error al resolver player_id vía PlayerService para {}: {}", playerUuid, e.getMessage());
            }
        }

        try {
            String hint = (usernameHint != null && !usernameHint.isBlank()) ? usernameHint.trim() : "TebexBuyer";
            String sql = """
                INSERT INTO player (mc_uuid, username, last_seen)
                VALUES (?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE username = VALUES(username), last_seen = CURRENT_TIMESTAMP(3)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, hint);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next() && keys.getLong(1) > 0) {
                        return keys.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT player_id FROM player WHERE mc_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("[TEBEX] Fallo al resolver player_id en BD para {}: {}", playerUuid, e.getMessage());
        }
        return null;
    }

    protected Connection getConnection() throws SQLException {
        if (db == null) {
            throw new SQLException("Database no disponible en TebexService");
        }
        return db.connection();
    }

    /**
     * Procesa la revocación por reembolso (Refund Command).
     */
    public ExecutionResult refund(String transactionId, UUID playerUuid, String packageId) {
        return refund(transactionId, playerUuid != null ? playerUuid.toString() : null, packageId);
    }

    public ExecutionResult refund(String transactionId, String playerIdentity, String packageId) {
        return processReversal(transactionId, playerIdentity, packageId, TebexFinancialEvent.REFUND);
    }

    /**
     * Procesa la alerta por contracargo bancario (Chargeback Command).
     */
    public ExecutionResult chargeback(String transactionId, UUID playerUuid, String packageId) {
        return chargeback(transactionId, playerUuid != null ? playerUuid.toString() : null, packageId);
    }

    public ExecutionResult chargeback(String transactionId, String playerIdentity, String packageId) {
        return processReversal(transactionId, playerIdentity, packageId, TebexFinancialEvent.CHARGEBACK);
    }

    private ExecutionResult processReversal(String transactionId, String playerIdentity, String packageId, TebexFinancialEvent eventType) {
        if (!TebexParser.isValidTransactionId(transactionId) || playerIdentity == null || playerIdentity.isBlank() || packageId == null || packageId.isBlank()) {
            String logPrefix = (eventType == TebexFinancialEvent.CHARGEBACK) ? "[TEBEX-FRAUD]" : "[TEBEX]";
            LunaEternal.LOG.error("{} operation={} status=FAILED reason=INVALID_ARGS transaction={}", logPrefix, eventType, transactionId);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "INVALID_ARGS");
        }
        String cleanPackageId = packageId.trim();

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                FulfillmentRow row = getFulfillmentRowForUpdate(c, transactionId, cleanPackageId);
                if (row == null) {
                    ensurePaymentRecord(c, transactionId, playerIdentity);
                    String reason = "ORIGINAL_FULFILLMENT_NOT_FOUND";
                    recordFulfillmentReview(c, transactionId, cleanPackageId, playerIdentity, "UNKNOWN", "UNKNOWN", reason);
                    updateFulfillmentReversalDirect(c, transactionId, cleanPackageId, eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
                    c.commit();
                    if (eventType == TebexFinancialEvent.CHARGEBACK) {
                        LunaEternal.LOG.error("[TEBEX-FRAUD] operation=CHARGEBACK status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={}",
                                reason, transactionId, cleanPackageId);
                    } else {
                        LunaEternal.LOG.warn("[TEBEX] operation=REFUND status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={}",
                                reason, transactionId, cleanPackageId);
                    }
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

                if (!isMatchingIdentity(c, row, playerIdentity)) {
                    String reason = "UUID_MISMATCH (expected=" + (row.playerUuid() != null ? row.playerUuid() : row.usernameRaw()) + ", received=" + playerIdentity + ")";
                    updateFulfillmentReversal(c, row.id(), eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
                    c.commit();
                    LunaEternal.LOG.error("[TEBEX] ALERTA ALTA: operation={} status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={}",
                            eventType, reason, transactionId, cleanPackageId);
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

                if (row.reversalStatus() == TebexReversalStatus.COMPLETED) {
                    if (eventType == TebexFinancialEvent.CHARGEBACK && row.financialEvent() == TebexFinancialEvent.REFUND) {
                        updateFulfillmentFinancialEventOnly(c, row.id(), TebexFinancialEvent.CHARGEBACK);
                        checkAndUpdatePaymentStatus(c, transactionId, "CHARGEBACK");
                        c.commit();
                        LunaEternal.LOG.warn("[TEBEX-FRAUD] operation=CHARGEBACK status=ALREADY_PROCESSED reason=ALREADY_REVERSED transaction={} package={}",
                                transactionId, cleanPackageId);
                        return new ExecutionResult(ResultType.ALREADY_PROCESSED, "COMPLETED", "ALREADY_REVERSED");
                    }
                    if (eventType == TebexFinancialEvent.REFUND && row.financialEvent() == TebexFinancialEvent.CHARGEBACK) {
                        c.rollback();
                        return new ExecutionResult(ResultType.ALREADY_PROCESSED, "COMPLETED", "ALREADY_REVERSED");
                    }
                    c.rollback();
                    return new ExecutionResult(ResultType.ALREADY_PROCESSED, "COMPLETED", "ALREADY_PROCESSED");
                }

                if (row.reversalStatus() == TebexReversalStatus.REQUIRES_REVIEW) {
                    c.rollback();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", "ALREADY_REQUIRES_REVIEW");
                }

                if (row.status() != TebexStatus.DELIVERED) {
                    updateFulfillmentStatus(c, row.id(), eventType.name(), TebexReversalStatus.NOT_APPLICABLE, "REFUND_BEFORE_DELIVERY", eventType);
                    checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
                    c.commit();
                    LunaEternal.LOG.info("[TEBEX] operation={} status={} transaction={} package={} (reembolso previo a entrega efectiva)",
                            eventType, eventType.name(), transactionId, cleanPackageId);
                    return new ExecutionResult(ResultType.SUCCESS, eventType.name(), null);
                }

                UUID targetUuid = null;
                if (row.playerUuid() != null && !row.playerUuid().isBlank()) {
                    try {
                        targetUuid = UUID.fromString(row.playerUuid());
                    } catch (IllegalArgumentException ignored) {}
                }
                Long resolvedPlayerId = row.playerId();
                if (resolvedPlayerId == null && targetUuid != null) {
                    resolvedPlayerId = resolvePlayerId(c, targetUuid, null);
                }
                if (resolvedPlayerId == null && row.usernameRaw() != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT player_id, mc_uuid FROM player WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                        ps.setString(1, row.usernameRaw());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                resolvedPlayerId = rs.getLong("player_id");
                                if (targetUuid == null && rs.getString("mc_uuid") != null) {
                                    targetUuid = UUID.fromString(rs.getString("mc_uuid"));
                                }
                            }
                        }
                    }
                }
                if (targetUuid == null && resolvedPlayerId != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT mc_uuid FROM player WHERE player_id = ? LIMIT 1")) {
                        ps.setLong(1, resolvedPlayerId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getString("mc_uuid") != null) {
                                targetUuid = UUID.fromString(rs.getString("mc_uuid"));
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (resolvedPlayerId == null) {
                    c.rollback();
                    return new ExecutionResult(ResultType.FAILED, "FAILED", "PLAYER_RESOLVE_FAILED");
                }

                if ("LUNACOINS".equals(row.productType())) {
                    long amount = Long.parseLong(row.productValue());
                    return revertLunaCoins(c, row.id(), transactionId, cleanPackageId, targetUuid, resolvedPlayerId, amount, eventType);
                } else if ("RANK".equals(row.productType()) || "RANK_UPGRADE".equals(row.productType())) {
                    return revertRank(c, row.id(), transactionId, cleanPackageId, targetUuid, resolvedPlayerId, row.productType(), row.productValue(), eventType);
                } else {
                    String reason = "UNSUPPORTED_PRODUCT_REVERSAL (" + row.productType() + ")";
                    updateFulfillmentReversal(c, row.id(), eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
                    c.commit();
                    return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                }

            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (SQLException rbEx) {
                    LunaEternal.LOG.error("[TEBEX] Error en rollback para reversión tx={} pkg={}", transactionId, cleanPackageId, rbEx);
                }
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            String logPrefix = (eventType == TebexFinancialEvent.CHARGEBACK) ? "[TEBEX-FRAUD]" : "[TEBEX]";
            LunaEternal.LOG.error("{} operation={} status=FAILED reason=DB_ERROR transaction={}", logPrefix, eventType, transactionId, e);
            return new ExecutionResult(ResultType.FAILED, "FAILED", "DB_ERROR");
        }
    }

    protected boolean isMatchingIdentity(Connection c, FulfillmentRow row, String playerIdentity) {
        if (row == null || playerIdentity == null || playerIdentity.isBlank()) {
            return false;
        }
        String id = playerIdentity.trim();

        // 1. Coincidencia directa con playerUuid registrado
        if (row.playerUuid() != null && row.playerUuid().equalsIgnoreCase(id)) {
            return true;
        }

        // 2. Coincidencia directa con usernameRaw registrado
        if (row.usernameRaw() != null && row.usernameRaw().equalsIgnoreCase(id)) {
            return true;
        }

        // 3. Coincidencia cruzada contra tabla player si existe player_id
        if (row.playerId() != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT mc_uuid, username FROM player WHERE player_id = ? LIMIT 1")) {
                ps.setLong(1, row.playerId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String mcUuid = rs.getString("mc_uuid");
                        String username = rs.getString("username");
                        if (mcUuid != null && mcUuid.equalsIgnoreCase(id)) {
                            return true;
                        }
                        if (username != null && username.equalsIgnoreCase(id)) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                LunaEternal.LOG.warn("[TEBEX] Error al verificar matching identity para playerId={}: {}", row.playerId(), e.getMessage());
            }
        }

        // 4. Si la identidad provista es un UUID y coincide con el formato canónico
        UUID parsed = TebexParser.parseCanonicalUuid(id);
        if (parsed != null && row.playerUuid() != null) {
            return parsed.toString().equalsIgnoreCase(row.playerUuid());
        }

        return false;
    }

    private ExecutionResult revertLunaCoins(Connection c, long fulfillmentId, String transactionId, String cleanPackageId,
                                            UUID playerUuid, long playerId, long amountToDebit, TebexFinancialEvent eventType)
            throws SQLException {
        long currentBalance = readEconomyBalanceForUpdate(c, playerId, Currency.REPORTCOIN);
        if (currentBalance < amountToDebit) {
            long deficit = amountToDebit - currentBalance;
            String reason = "INSUFFICIENT_LUNACOINS (purchased=" + amountToDebit + ", balance=" + currentBalance + ", deficit=" + deficit + ")";
            updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
            checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
            c.commit();
            if (eventType == TebexFinancialEvent.CHARGEBACK) {
                LunaEternal.LOG.error("[TEBEX-FRAUD] operation=CHARGEBACK status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} uuid={}",
                        reason, transactionId, cleanPackageId, playerUuid);
            } else {
                LunaEternal.LOG.warn("[TEBEX] operation=REFUND status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} uuid={}",
                        reason, transactionId, cleanPackageId, playerUuid);
            }
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        String idempotencyKey = "TEBEX:" + transactionId + ":" + cleanPackageId + ":" + (eventType == TebexFinancialEvent.CHARGEBACK ? "CHARGEBACK" : "REFUND");
        String reason = (eventType == TebexFinancialEvent.CHARGEBACK ? "Tebex Store Chargeback (" : "Tebex Store Refund (") + cleanPackageId + ")";

        long balanceAfter;
        try {
            balanceAfter = applyEconomyDebit(c, playerId, amountToDebit, idempotencyKey, reason);
        } catch (EconomyException e) {
            if (e.kind == EconomyException.Kind.ALREADY_APPLIED) {
                updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.COMPLETED, null, eventType);
                markEffectsReversed(c, fulfillmentId);
                checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
                c.commit();
                return new ExecutionResult(ResultType.ALREADY_PROCESSED, "COMPLETED", "LEDGER_ALREADY_APPLIED");
            } else {
                c.rollback();
                return new ExecutionResult(ResultType.FAILED, "FAILED", "ECONOMY_ERROR_" + e.kind);
            }
        }

        updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.COMPLETED, null, eventType);
        markEffectsReversed(c, fulfillmentId);
        checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
        c.commit();

        notifyOnlinePlayerBalanceReversal(playerUuid, amountToDebit, balanceAfter);

        if (eventType == TebexFinancialEvent.CHARGEBACK) {
            LunaEternal.LOG.warn("[TEBEX-FRAUD] operation=CHARGEBACK status=COMPLETED transaction={} package={} uuid={} amount={} balance_after={}",
                    transactionId, cleanPackageId, playerUuid, amountToDebit, balanceAfter);
        } else {
            LunaEternal.LOG.info("[TEBEX] operation=REFUND status=COMPLETED transaction={} package={} uuid={} amount={} balance_after={}",
                    transactionId, cleanPackageId, playerUuid, amountToDebit, balanceAfter);
        }

        return new ExecutionResult(ResultType.SUCCESS, "COMPLETED", null);
    }

    private ExecutionResult revertRank(Connection c, long fulfillmentId, String transactionId, String cleanPackageId,
                                       UUID playerUuid, long playerId, String productType, String productValue,
                                       TebexFinancialEvent eventType) throws SQLException {
        Tablist.Rank currentRank = readRankForUpdate(c, playerId);

        // 1. Protección de Staff
        if (currentRank.esStaff()) {
            String reason = "STAFF_RANK_PROTECTED (current=" + currentRank.name() + ")";
            updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
            checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
            c.commit();
            if (eventType == TebexFinancialEvent.CHARGEBACK) {
                LunaEternal.LOG.error("[TEBEX-FRAUD] operation=CHARGEBACK status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} uuid={}",
                        reason, transactionId, cleanPackageId, playerUuid);
            } else {
                LunaEternal.LOG.warn("[TEBEX] operation=REFUND status=REQUIRES_REVIEW reason=\"{}\" transaction={} package={} uuid={}",
                        reason, transactionId, cleanPackageId, playerUuid);
            }
            return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
        }

        // 2. Leer efectos del fulfillment a revertir
        TebexFulfillmentEffect rankEffect = readEffect(c, fulfillmentId, TebexEffectType.RANK_CHANGE);
        Tablist.Rank rankBefore;
        Tablist.Rank rankAfter;
        if (rankEffect != null && rankEffect.beforeValue() != null && rankEffect.afterValue() != null) {
            rankBefore = Tablist.Rank.de(rankEffect.beforeValue());
            rankAfter = Tablist.Rank.de(rankEffect.afterValue());
        } else {
            ProductDefinition def = registry.resolve(cleanPackageId);
            if ("RANK_UPGRADE".equals(productType)) {
                rankBefore = def != null ? def.requiredPreviousRank() : Tablist.Rank.ENTRENADOR;
                rankAfter = def != null ? def.rank() : Tablist.Rank.ENTRENADOR;
            } else {
                rankBefore = Tablist.Rank.ENTRENADOR;
                rankAfter = def != null ? def.rank() : Tablist.Rank.ENTRENADOR;
            }
        }

        // 3. Comprobar dependencias posteriores (Upgrades posteriores dependientes de este rango)
        List<TebexActiveRankFulfillment> subsequent = readSubsequentActiveRankFulfillments(c, playerId, fulfillmentId);
        for (TebexActiveRankFulfillment sub : subsequent) {
            if ("RANK_UPGRADE".equals(sub.productType())) {
                ProductDefinition subDef = registry.resolve(sub.packageId());
                if (subDef != null) {
                    if (subDef.requiredPreviousRank() == rankAfter || subDef.requiredPreviousRank().nivelComercial() >= rankAfter.nivelComercial()) {
                        String reason = "CHAIN_DEPENDENCY_PREVENTS_AUTO_REVERSAL (subsequent=" + sub.packageId() + " depends on rank " + rankAfter.name() + ")";
                        updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
                        checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
                        c.commit();
                        return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                    }
                }
            }
        }

        // 4. Reconstrucción del rango comercial efectivo restante
        Tablist.Rank baselineRank = readBaselineRank(c, playerId);
        List<TebexActiveRankFulfillment> remaining = readAllActiveRankFulfillmentsExcluding(c, playerId, fulfillmentId);
        Tablist.Rank effectiveRank = computeEffectiveRank(baselineRank, remaining);

        // 5. Verificación de ambigüedad
        boolean rankNeedsUpdate = (currentRank != effectiveRank);
        if (rankNeedsUpdate) {
            if (currentRank != rankAfter && currentRank.nivelComercial() < rankAfter.nivelComercial()) {
                String reason = "AMBIGUOUS_RANK_STATE (current=" + currentRank.name() + ", expected=" + rankAfter.name() + ")";
                updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.REQUIRES_REVIEW, reason, eventType);
                checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
                c.commit();
                return new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
            }
            applyRankChange(c, playerId, effectiveRank);
        }

        // 6. Reversión de traje cosmético si fue creado por esta entrega y no hay otra fuente activa
        TebexFulfillmentEffect suitEffect = readEffect(c, fulfillmentId, TebexEffectType.SUIT_GRANT);
        Traje suitToRemove = null;
        if (suitEffect != null && suitEffect.createdByFulfillment()) {
            Traje t = Traje.de(suitEffect.resourceId());
            if (t != null && !isSuitGrantedByOtherActiveFulfillment(c, playerId, fulfillmentId, t.id())) {
                applySuitRemoval(c, playerId, t);
                suitToRemove = t;
            }
        }

        updateFulfillmentReversal(c, fulfillmentId, eventType.name(), TebexReversalStatus.COMPLETED, null, eventType);
        markEffectsReversed(c, fulfillmentId);
        checkAndUpdatePaymentStatus(c, transactionId, eventType.name());
        c.commit();

        if (rankNeedsUpdate) {
            RankService.actualizarCache(playerUuid, effectiveRank);
        }
        if (suitToRemove != null) {
            TrajeService.actualizarCacheRetiro(playerUuid, suitToRemove);
        }
        notifyOnlinePlayerRankReversal(playerUuid, effectiveRank, eventType);

        if (eventType == TebexFinancialEvent.CHARGEBACK) {
            LunaEternal.LOG.warn("[TEBEX-FRAUD] operation=CHARGEBACK status=COMPLETED transaction={} package={} uuid={} effective_rank={}",
                    transactionId, cleanPackageId, playerUuid, effectiveRank.name());
        } else {
            LunaEternal.LOG.info("[TEBEX] operation=REFUND status=COMPLETED transaction={} package={} uuid={} effective_rank={}",
                    transactionId, cleanPackageId, playerUuid, effectiveRank.name());
        }

        return new ExecutionResult(ResultType.SUCCESS, "COMPLETED", null);
    }

    protected void ensurePaymentRecord(Connection c, String transactionId, String playerUuid) throws SQLException {
        ensurePaymentRecord(c, transactionId, playerUuid, null);
    }

    protected void ensurePaymentRecord(Connection c, String transactionId, String playerUuid, String usernameRaw) throws SQLException {
        String sql = """
            INSERT INTO tebex_payment (transaction_id, player_uuid, username_raw, status, created_at, updated_at)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                player_uuid = COALESCE(VALUES(player_uuid), player_uuid),
                username_raw = COALESCE(VALUES(username_raw), username_raw),
                updated_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, playerUuid);
            ps.setString(3, usernameRaw);
            ps.executeUpdate();
        }
    }

    protected void recordFulfillmentReview(Connection c, String transactionId, String packageId, String playerIdentity,
                                           String productType, String productValue, String reason) throws SQLException {
        UUID parsedUuid = TebexParser.parseCanonicalUuid(playerIdentity);
        String uuidStr = parsedUuid != null ? parsedUuid.toString() : null;
        String usernameRaw = parsedUuid == null ? playerIdentity : null;
        String sql = """
            INSERT INTO tebex_fulfillment (
                transaction_id, package_id, player_uuid, username_raw, product_type, product_value,
                status, failure_reason, received_at, processed_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'REQUIRES_REVIEW', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                status = 'REQUIRES_REVIEW',
                player_uuid = COALESCE(VALUES(player_uuid), player_uuid),
                username_raw = COALESCE(VALUES(username_raw), username_raw),
                failure_reason = VALUES(failure_reason),
                processed_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.setString(3, uuidStr);
            ps.setString(4, usernameRaw);
            ps.setString(5, productType);
            ps.setString(6, productValue);
            ps.setString(7, reason);
            ps.executeUpdate();
        }
        if (parsedUuid != null) {
            recordFulfillmentReview(c, transactionId, packageId, parsedUuid, productType, productValue, reason);
        }
    }

    protected void recordFulfillmentReview(Connection c, String transactionId, String packageId, UUID playerUuid,
                                           String productType, String productValue, String reason) throws SQLException {
        String sql = """
            INSERT INTO tebex_fulfillment (
                transaction_id, package_id, player_uuid, product_type, product_value,
                status, failure_reason, received_at, processed_at
            ) VALUES (?, ?, ?, ?, ?, 'REQUIRES_REVIEW', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                status = 'REQUIRES_REVIEW',
                failure_reason = VALUES(failure_reason),
                processed_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.setString(3, playerUuid != null ? playerUuid.toString() : null);
            ps.setString(4, productType);
            ps.setString(5, productValue);
            ps.setString(6, reason);
            ps.executeUpdate();
        }
    }

    protected void recordFulfillmentPendingResolution(Connection c, String transactionId, String packageId,
                                                      String usernameRaw, int quantity, ProductDefinition def) throws SQLException {
        String sql = """
            INSERT INTO tebex_fulfillment (
                transaction_id, package_id, player_uuid, player_id, username_raw, purchase_quantity,
                product_type, product_value, status, received_at, processed_at, failure_reason
            ) VALUES (?, ?, NULL, NULL, ?, ?, ?, ?, 'PENDING_PLAYER_RESOLUTION', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'WAITING_FIRST_JOIN')
            ON DUPLICATE KEY UPDATE
                username_raw = VALUES(username_raw),
                purchase_quantity = VALUES(purchase_quantity),
                status = 'PENDING_PLAYER_RESOLUTION',
                processed_at = CURRENT_TIMESTAMP(3),
                failure_reason = 'WAITING_FIRST_JOIN'
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.setString(3, usernameRaw);
            ps.setInt(4, quantity);
            ps.setString(5, def.type().name());
            ps.setString(6, resolveProductValue(def));
            ps.executeUpdate();
        }
    }

    private void recordFulfillmentPending(Connection c, String transactionId, String packageId,
                                          UUID playerUuid, Long playerId, ProductDefinition def)
            throws SQLException {
        String sql = """
            INSERT INTO tebex_fulfillment (
                transaction_id, package_id, player_uuid, player_id,
                product_type, product_value, status, received_at, processed_at, failure_reason
            ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING_IMPLEMENTATION', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'PHASE_4B_ONLY_LUNACOINS')
            ON DUPLICATE KEY UPDATE
                player_id = VALUES(player_id),
                status = 'PENDING_IMPLEMENTATION',
                processed_at = CURRENT_TIMESTAMP(3),
                failure_reason = 'PHASE_4B_ONLY_LUNACOINS'
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.setString(3, playerUuid != null ? playerUuid.toString() : null);
            if (playerId != null) {
                ps.setLong(4, playerId);
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            ps.setString(5, def.type().name());
            ps.setString(6, resolveProductValue(def));
            ps.executeUpdate();
        }
    }

    protected void markFulfillmentDelivered(Connection c, String transactionId, String packageId,
                                           UUID playerUuid, Long playerId, String usernameRaw, int quantity,
                                           String productType, String productValue) throws SQLException {
        String sql = """
            INSERT INTO tebex_fulfillment (
                transaction_id, package_id, player_uuid, player_id, username_raw, purchase_quantity,
                product_type, product_value, status, reversal_status, financial_event,
                received_at, processed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DELIVERED', 'NONE', 'INITIAL', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                player_uuid = COALESCE(VALUES(player_uuid), player_uuid),
                player_id = VALUES(player_id),
                username_raw = COALESCE(VALUES(username_raw), username_raw),
                purchase_quantity = VALUES(purchase_quantity),
                status = 'DELIVERED',
                reversal_status = 'NONE',
                financial_event = 'INITIAL',
                processed_at = CURRENT_TIMESTAMP(3),
                failure_reason = NULL
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.setString(3, playerUuid != null ? playerUuid.toString() : null);
            if (playerId != null) {
                ps.setLong(4, playerId);
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            ps.setString(5, usernameRaw);
            ps.setInt(6, quantity);
            ps.setString(7, productType);
            ps.setString(8, productValue);
            ps.executeUpdate();
        }
        markFulfillmentDelivered(c, transactionId, packageId, playerUuid, playerId, productType, productValue);
    }

    protected void markFulfillmentDelivered(Connection c, String transactionId, String packageId,
                                           UUID playerUuid, Long playerId, String productType, String productValue)
            throws SQLException {
        // Subclasses in tests override this method to update their mock state.
    }

    protected long markFulfillmentDeliveredAndGetId(Connection c, String transactionId, String packageId,
                                                     UUID playerUuid, Long playerId, String usernameRaw, int quantity,
                                                     String productType, String productValue) throws SQLException {
        markFulfillmentDelivered(c, transactionId, packageId, playerUuid, playerId, usernameRaw, quantity, productType, productValue);
        return getFulfillmentId(c, transactionId, packageId);
    }

    protected long markFulfillmentDeliveredAndGetId(Connection c, String transactionId, String packageId,
                                                     UUID playerUuid, Long playerId, String productType, String productValue)
            throws SQLException {
        return markFulfillmentDeliveredAndGetId(c, transactionId, packageId, playerUuid, playerId, null, 1, productType, productValue);
    }

    protected long getFulfillmentId(Connection c, String transactionId, String packageId) throws SQLException {
        String sql = "SELECT id FROM tebex_fulfillment WHERE transaction_id = ? AND package_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return -1L;
    }

    protected FulfillmentRow getFulfillmentRowForUpdate(Connection c, String transactionId, String packageId) throws SQLException {
        String sql = """
            SELECT id, transaction_id, package_id, player_uuid, player_id, username_raw, purchase_quantity,
                   product_type, product_value, status, reversal_status, financial_event
            FROM tebex_fulfillment
            WHERE transaction_id = ? AND package_id = ? FOR UPDATE
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String revStr = rs.getString("reversal_status");
                    TebexReversalStatus revStatus = (revStr != null && !revStr.isBlank())
                            ? TebexReversalStatus.valueOf(revStr) : TebexReversalStatus.NONE;
                    String finStr = rs.getString("financial_event");
                    TebexFinancialEvent finEvent = (finStr != null && !finStr.isBlank())
                            ? TebexFinancialEvent.valueOf(finStr) : TebexFinancialEvent.INITIAL;
                    return new FulfillmentRow(
                            rs.getLong("id"),
                            rs.getString("transaction_id"),
                            rs.getString("package_id"),
                            rs.getString("player_uuid"),
                            rs.getObject("player_id") != null ? rs.getLong("player_id") : null,
                            rs.getString("username_raw"),
                            rs.getInt("purchase_quantity"),
                            rs.getString("product_type"),
                            rs.getString("product_value"),
                            TebexStatus.de(rs.getString("status")),
                            revStatus,
                            finEvent
                    );
                }
            }
        }
        return null;
    }

    /**
     * Procesa todas las compras pendientes (PENDING_PLAYER_RESOLUTION) asociadas al nombre canónico
     * del jugador en su primer login o cuando entra al servidor.
     */
    public int processPendingFulfillmentsOnJoin(MinecraftServer server, UUID realUuid, String realUsername) {
        if (realUuid == null || realUsername == null || realUsername.isBlank()) {
            return 0;
        }

        List<PendingFulfillmentItem> pendingList = new ArrayList<>();
        try (Connection c = getConnection()) {
            String sql = """
                SELECT id, transaction_id, package_id, product_type, product_value, purchase_quantity
                FROM tebex_fulfillment
                WHERE status = 'PENDING_PLAYER_RESOLUTION' AND LOWER(username_raw) = LOWER(?)
                ORDER BY id ASC
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, realUsername.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pendingList.add(new PendingFulfillmentItem(
                                rs.getLong("id"),
                                rs.getString("transaction_id"),
                                rs.getString("package_id"),
                                rs.getString("product_type"),
                                rs.getString("product_value"),
                                rs.getInt("purchase_quantity")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("[TEBEX] Error consultando fulfillments pendientes para {}: {}", realUsername, e.getMessage(), e);
            return 0;
        }

        if (pendingList.isEmpty()) {
            return 0;
        }

        LunaEternal.LOG.info("[TEBEX] Detectados {} entitlement(s) pendientes para {} ({})",
                pendingList.size(), realUsername, realUuid);

        int deliveredCount = 0;
        for (PendingFulfillmentItem item : pendingList) {
            try (Connection c = getConnection()) {
                c.setAutoCommit(false);
                try {
                    // Bloqueo FOR UPDATE sobre la fila pendiente
                    String checkSql = "SELECT status FROM tebex_fulfillment WHERE id = ? FOR UPDATE";
                    TebexStatus currentStatus = null;
                    try (PreparedStatement ps = c.prepareStatement(checkSql)) {
                        ps.setLong(1, item.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                currentStatus = TebexStatus.de(rs.getString("status"));
                            }
                        }
                    }

                    if (currentStatus != TebexStatus.PENDING_PLAYER_RESOLUTION) {
                        c.rollback();
                        continue;
                    }

                    // Asegurar / resolver player_id en MariaDB con UUID y nombre real
                    long playerId = TebexPlayerResolver.resolveOrCreatePlayerInDb(c, playerService, realUuid, realUsername);

                    // Actualizar el registro del pago con el UUID real
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE tebex_payment SET player_uuid = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE transaction_id = ?")) {
                        ps.setString(1, realUuid.toString());
                        ps.setString(2, item.transactionId());
                        ps.executeUpdate();
                    }

                    ProductDefinition def = registry.resolve(item.packageId());
                    if (def == null) {
                        String reason = "UNKNOWN_PACKAGE (" + item.packageId() + ")";
                        recordFulfillmentReview(c, item.transactionId(), item.packageId(), realUuid, "UNKNOWN", "UNKNOWN", reason);
                        c.commit();
                        LunaEternal.LOG.warn("[TEBEX] Entitlement pendiente requiere revisión: {}", reason);
                        continue;
                    }

                    ExecutionResult result;
                    if (def.type() == ProductType.LUNACOINS) {
                        result = fulfillLunaCoins(c, item.transactionId(), item.packageId(), realUuid, playerId, realUsername, item.quantity(), def);
                    } else if (def.type() == ProductType.RANK) {
                        result = fulfillRank(c, item.transactionId(), item.packageId(), realUuid, playerId, realUsername, item.quantity(), def);
                    } else if (def.type() == ProductType.RANK_UPGRADE) {
                        result = fulfillRankUpgrade(c, item.transactionId(), item.packageId(), realUuid, playerId, realUsername, item.quantity(), def);
                    } else {
                        String reason = "UNSUPPORTED_PRODUCT_TYPE (" + def.type() + ")";
                        recordFulfillmentReview(c, item.transactionId(), item.packageId(), realUuid, def.type().name(), resolveProductValue(def), reason);
                        c.commit();
                        result = new ExecutionResult(ResultType.REQUIRES_REVIEW, "REQUIRES_REVIEW", reason);
                    }

                    if (result.isSuccess()) {
                        deliveredCount++;
                        LunaEternal.LOG.info("[TEBEX] Entitlement pendiente {} entregado exitosamente a {} ({})",
                                item.transactionId(), realUsername, realUuid);
                    } else {
                        LunaEternal.LOG.warn("[TEBEX] Entitlement pendiente {} finalizó con estado {} ({}) para {}",
                                item.transactionId(), result.status(), result.reason(), realUsername);
                    }
                } catch (Exception e) {
                    try {
                        c.rollback();
                    } catch (SQLException ignored) {}
                    LunaEternal.LOG.error("[TEBEX] Error procesando entitlement pendiente id={} para {}: {}",
                            item.id(), realUsername, e.getMessage(), e);
                } finally {
                    c.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LunaEternal.LOG.error("[TEBEX] Error de conexión procesando entitlement id={}: {}", item.id(), e.getMessage(), e);
            }
        }

        return deliveredCount;
    }

    protected void recordEffect(Connection c, long fulfillmentId, TebexEffectType type, String resourceId,
                               String beforeValue, String afterValue, boolean createdByFulfillment) throws SQLException {
        if (fulfillmentId <= 0) return;
        String sql = """
            INSERT INTO tebex_fulfillment_effect (
                fulfillment_id, effect_type, resource_id, before_value, after_value, created_by_fulfillment
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fulfillmentId);
            ps.setString(2, type.name());
            ps.setString(3, resourceId);
            ps.setString(4, beforeValue);
            ps.setString(5, afterValue);
            ps.setBoolean(6, createdByFulfillment);
            ps.executeUpdate();
        }
    }

    protected TebexFulfillmentEffect readEffect(Connection c, long fulfillmentId, TebexEffectType effectType) throws SQLException {
        String sql = """
            SELECT id, fulfillment_id, effect_type, resource_id, before_value, after_value,
                   created_by_fulfillment, reversed, reversed_at
            FROM tebex_fulfillment_effect
            WHERE fulfillment_id = ? AND effect_type = ? LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fulfillmentId);
            ps.setString(2, effectType.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp revAt = rs.getTimestamp("reversed_at");
                    return new TebexFulfillmentEffect(
                            rs.getLong("id"),
                            rs.getLong("fulfillment_id"),
                            TebexEffectType.valueOf(rs.getString("effect_type")),
                            rs.getString("resource_id"),
                            rs.getString("before_value"),
                            rs.getString("after_value"),
                            rs.getBoolean("created_by_fulfillment"),
                            rs.getBoolean("reversed"),
                            revAt != null ? revAt.toInstant() : null
                    );
                }
            }
        }
        return null;
    }

    protected void markEffectsReversed(Connection c, long fulfillmentId) throws SQLException {
        String sql = "UPDATE tebex_fulfillment_effect SET reversed = TRUE, reversed_at = CURRENT_TIMESTAMP(3) WHERE fulfillment_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fulfillmentId);
            ps.executeUpdate();
        }
    }

    protected Tablist.Rank readBaselineRank(Connection c, long playerId) throws SQLException {
        String sql = """
            SELECT e.before_value
            FROM tebex_fulfillment_effect e
            JOIN tebex_fulfillment f ON e.fulfillment_id = f.id
            WHERE f.player_id = ? AND e.effect_type = 'RANK_CHANGE'
            ORDER BY e.id ASC LIMIT 1
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("before_value");
                    if (val != null && !val.isBlank()) {
                        return Tablist.Rank.de(val);
                    }
                }
            }
        }
        return Tablist.Rank.ENTRENADOR;
    }

    protected List<TebexActiveRankFulfillment> readSubsequentActiveRankFulfillments(Connection c, long playerId, long fulfillmentId) throws SQLException {
        String sql = """
            SELECT id, package_id, product_type, product_value
            FROM tebex_fulfillment
            WHERE player_id = ? AND id > ? AND status = 'DELIVERED' AND reversal_status <> 'COMPLETED'
            ORDER BY id ASC
            """;
        List<TebexActiveRankFulfillment> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setLong(2, fulfillmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TebexActiveRankFulfillment(
                            rs.getLong("id"),
                            rs.getString("package_id"),
                            rs.getString("product_type"),
                            rs.getString("product_value")
                    ));
                }
            }
        }
        return list;
    }

    protected List<TebexActiveRankFulfillment> readAllActiveRankFulfillmentsExcluding(Connection c, long playerId, long excludedFulfillmentId) throws SQLException {
        String sql = """
            SELECT id, package_id, product_type, product_value
            FROM tebex_fulfillment
            WHERE player_id = ? AND id <> ? AND status = 'DELIVERED' AND reversal_status <> 'COMPLETED'
            ORDER BY id ASC
            """;
        List<TebexActiveRankFulfillment> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setLong(2, excludedFulfillmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TebexActiveRankFulfillment(
                            rs.getLong("id"),
                            rs.getString("package_id"),
                            rs.getString("product_type"),
                            rs.getString("product_value")
                    ));
                }
            }
        }
        return list;
    }

    protected Tablist.Rank computeEffectiveRank(Tablist.Rank baselineRank, List<TebexActiveRankFulfillment> activeFulfillments) {
        Tablist.Rank r = (baselineRank != null) ? baselineRank : Tablist.Rank.ENTRENADOR;
        if (activeFulfillments == null || activeFulfillments.isEmpty()) {
            return r;
        }
        for (TebexActiveRankFulfillment f : activeFulfillments) {
            ProductDefinition def = registry.resolve(f.packageId());
            if (def == null) continue;
            if (def.type() == ProductType.RANK) {
                if (def.rank().nivelComercial() > r.nivelComercial()) {
                    r = def.rank();
                }
            } else if (def.type() == ProductType.RANK_UPGRADE) {
                if (r == def.requiredPreviousRank()) {
                    r = def.rank();
                }
            }
        }
        return r;
    }

    protected boolean isSuitGrantedByOtherActiveFulfillment(Connection c, long playerId, long excludedFulfillmentId, String suitId) throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM tebex_fulfillment_effect e
            JOIN tebex_fulfillment f ON e.fulfillment_id = f.id
            WHERE f.player_id = ? AND f.id <> ? AND f.status = 'DELIVERED' AND f.reversal_status <> 'COMPLETED'
              AND e.effect_type = 'SUIT_GRANT' AND e.resource_id = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setLong(2, excludedFulfillmentId);
            ps.setString(3, suitId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    protected long applyEconomyDebit(Connection c, long playerId, long amount, String idempotencyKey, String reason)
            throws SQLException, EconomyException {
        if (economyService != null) {
            return economyService.applyInTransaction(
                    c,
                    playerId,
                    Currency.REPORTCOIN,
                    -amount,
                    reason,
                    "tebex_reversal",
                    null,
                    idempotencyKey
            );
        }
        throw new IllegalStateException("EconomyService no configurado");
    }

    protected long readEconomyBalanceForUpdate(Connection c, long playerId, Currency currency) throws SQLException {
        String sql = "SELECT balance FROM player_economy WHERE player_id = ? AND currency = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : 0L;
            }
        }
    }

    protected boolean playerHadSuit(Connection c, long playerId, Traje traje) throws SQLException {
        if (trajeService != null) {
            return trajeService.poseeInTransaction(c, playerId, traje);
        }
        if (traje == null || traje.gratis()) {
            return true;
        }
        String sql = "SELECT 1 FROM player_suit_owned WHERE player_id = ? AND suit = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, traje.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    protected boolean applySuitRemoval(Connection c, long playerId, Traje traje) throws SQLException {
        if (trajeService != null) {
            return trajeService.retirarInTransaction(c, playerId, traje);
        }
        if (traje == null || traje.gratis()) {
            return false;
        }
        String sql = "DELETE FROM player_suit_owned WHERE player_id = ? AND suit = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, traje.id());
            return ps.executeUpdate() > 0;
        }
    }

    protected void updateFulfillmentReversal(Connection c, long fulfillmentId, String status,
                                             TebexReversalStatus reversalStatus, String reason,
                                             TebexFinancialEvent financialEvent) throws SQLException {
        String sql = """
            UPDATE tebex_fulfillment
            SET status = ?,
                reversal_status = ?,
                reversal_reason = ?,
                failure_reason = ?,
                financial_event = ?,
                reversal_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reversalStatus.name());
            ps.setString(3, reason);
            ps.setString(4, reason);
            ps.setString(5, financialEvent.name());
            ps.setLong(6, fulfillmentId);
            ps.executeUpdate();
        }
    }

    protected void updateFulfillmentReversalDirect(Connection c, String transactionId, String packageId,
                                                   String status, TebexReversalStatus reversalStatus,
                                                   String reason, TebexFinancialEvent financialEvent) throws SQLException {
        String sql = """
            UPDATE tebex_fulfillment
            SET status = ?,
                reversal_status = ?,
                reversal_reason = ?,
                failure_reason = ?,
                financial_event = ?,
                reversal_at = CURRENT_TIMESTAMP(3)
            WHERE transaction_id = ? AND package_id = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reversalStatus.name());
            ps.setString(3, reason);
            ps.setString(4, reason);
            ps.setString(5, financialEvent.name());
            ps.setString(6, transactionId);
            ps.setString(7, packageId);
            ps.executeUpdate();
        }
    }

    protected void updateFulfillmentFinancialEventOnly(Connection c, long fulfillmentId, TebexFinancialEvent financialEvent) throws SQLException {
        String sql = "UPDATE tebex_fulfillment SET financial_event = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, financialEvent.name());
            ps.setString(2, financialEvent.name());
            ps.setLong(3, fulfillmentId);
            ps.executeUpdate();
        }
    }

    protected void updateFulfillmentStatus(Connection c, long fulfillmentId, String status,
                                           TebexReversalStatus reversalStatus, String reason,
                                           TebexFinancialEvent financialEvent) throws SQLException {
        String sql = """
            UPDATE tebex_fulfillment
            SET status = ?,
                reversal_status = ?,
                failure_reason = ?,
                financial_event = ?,
                reversal_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, reversalStatus.name());
            ps.setString(3, reason);
            ps.setString(4, financialEvent.name());
            ps.setLong(5, fulfillmentId);
            ps.executeUpdate();
        }
    }

    private void notifyOnlinePlayerBalanceReversal(UUID playerUuid, long amountDebited, long balanceAfter) {
        try {
            MinecraftServer server = LunaEternal.server();
            if (server != null) {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(playerUuid);
                if (sp != null && !sp.isRemoved()) {
                    server.execute(() -> {
                        sp.sendMessage(Text.literal("§c§l[TIENDA] §eSe han debitado §c" + amountDebited + " LunaCoins§e debido a un reembolso/contracargo."), false);
                        sp.sendMessage(Text.literal("§7Nuevo saldo de LunaCoins: §f" + balanceAfter), false);
                        Red.enviarSaldo(sp);
                    });
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.warn("[TEBEX] No se pudo enviar notificación in-game para {}: {}", playerUuid, t.getMessage());
        }
    }

    private void notifyOnlinePlayerRankReversal(UUID playerUuid, Tablist.Rank newRank, TebexFinancialEvent eventType) {
        try {
            MinecraftServer server = LunaEternal.server();
            if (server != null) {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(playerUuid);
                if (sp != null && !sp.isRemoved()) {
                    server.execute(() -> {
                        String eventName = (eventType == TebexFinancialEvent.CHARGEBACK) ? "contracargo" : "reembolso";
                        sp.sendMessage(Text.literal("§c§l[TIENDA] §eTu rango ha sido actualizado a §f" + newRank.titulo + "§e debido a un " + eventName + "."), false);
                        Tablist.refrescarClan(server, sp);
                    });
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.warn("[TEBEX] No se pudo enviar notificación in-game para {}: {}", playerUuid, t.getMessage());
        }
    }

    protected TebexStatus getFulfillmentStatusForUpdate(Connection c, String transactionId, String packageId) throws SQLException {
        String sql = "SELECT status FROM tebex_fulfillment WHERE transaction_id = ? AND package_id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return TebexStatus.de(rs.getString("status"));
                }
            }
        }
        return null;
    }

    private TebexStatus getFulfillmentStatus(String transactionId, String packageId) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status FROM tebex_fulfillment WHERE transaction_id = ? AND package_id = ?")) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return TebexStatus.de(rs.getString("status"));
                }
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("[TEBEX] Error al consultar status para tx={} pkg={}", transactionId, packageId, e);
        }
        return null;
    }

    protected void checkAndUpdatePaymentStatus(Connection c, String transactionId, String targetStatus) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM tebex_fulfillment WHERE transaction_id = ? AND status <> ?";
        try (PreparedStatement ps = c.prepareStatement(countSql)) {
            ps.setString(1, transactionId);
            ps.setString(2, targetStatus);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement up = c.prepareStatement(
                            "UPDATE tebex_payment SET status = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE transaction_id = ?")) {
                        up.setString(1, targetStatus);
                        up.setString(2, transactionId);
                        up.executeUpdate();
                    }
                }
            }
        }
    }

    private void notifyOnlinePlayerIfPresent(UUID playerUuid, long amount, long balanceAfter) {
        try {
            MinecraftServer server = LunaEternal.server();
            if (server != null) {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(playerUuid);
                if (sp != null && !sp.isRemoved()) {
                    server.execute(() -> {
                        sp.sendMessage(Text.literal("§6§l[TIENDA] §a¡Pago confirmado! Se han acreditado §e" + amount + " LunaCoins§a a tu cuenta."), false);
                        sp.sendMessage(Text.literal("§7Nuevo saldo de LunaCoins: §f" + balanceAfter), false);
                        try {
                            sp.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                        } catch (Throwable ignored) {}
                        Red.enviarSaldo(sp);
                    });
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.warn("[TEBEX] No se pudo enviar notificación in-game para {}: {}", playerUuid, t.getMessage());
        }
    }

    protected Tablist.Rank readRankForUpdate(Connection c, long playerId) throws SQLException {
        if (rankService != null) {
            return rankService.leerParaUpdate(c, playerId);
        }
        String sql = "SELECT rank_id FROM player WHERE player_id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Tablist.Rank.de(rs.getString(1)) : Tablist.Rank.porDefecto();
            }
        }
    }

    protected boolean applyRankChange(Connection c, long playerId, Tablist.Rank nuevo) throws SQLException {
        if (rankService != null) {
            return rankService.cambiarInTransaction(c, playerId, nuevo);
        }
        String sql = "UPDATE player SET rank_id = ? WHERE player_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nuevo.name());
            ps.setLong(2, playerId);
            return ps.executeUpdate() == 1;
        }
    }

    protected boolean applySuitGrant(Connection c, long playerId, Traje traje) throws SQLException {
        if (trajeService != null) {
            return trajeService.concederInTransaction(c, playerId, traje);
        }
        if (traje == null || traje.gratis()) {
            return false;
        }
        String sql = "INSERT IGNORE INTO player_suit_owned (player_id, suit) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            ps.setString(2, traje.id());
            return ps.executeUpdate() > 0;
        }
    }

    protected void markFulfillmentProcessing(Connection c, String transactionId, String packageId) throws SQLException {
        String sql = "UPDATE tebex_fulfillment SET status = 'PROCESSING', processed_at = CURRENT_TIMESTAMP(3) WHERE transaction_id = ? AND package_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            ps.setString(2, packageId);
            ps.executeUpdate();
        }
    }

    private void notifyOnlinePlayerRank(UUID playerUuid, Tablist.Rank rank, boolean isUpgrade) {
        try {
            MinecraftServer server = LunaEternal.server();
            if (server != null) {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(playerUuid);
                if (sp != null && !sp.isRemoved()) {
                    server.execute(() -> {
                        String verb = isUpgrade ? "¡Has mejorado tu rango a " : "¡Has adquirido el rango ";
                        sp.sendMessage(Text.literal("§6§l[TIENDA] §a" + verb + "§e" + rank.titulo + "§a!"), false);
                        sp.sendMessage(Text.literal("§7Se han desbloqueado tus beneficios correspondientes."), false);
                        try {
                            sp.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                        } catch (Throwable ignored) {}
                        Tablist.refrescarClan(server, sp);
                    });
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.warn("[TEBEX] No se pudo enviar notificación in-game para {}: {}", playerUuid, t.getMessage());
        }
    }

    private static String resolveProductValue(ProductDefinition def) {
        return switch (def.type()) {
            case LUNACOINS -> String.valueOf(def.amount());
            case RANK -> def.rank().name();
            case RANK_UPGRADE -> def.requiredPreviousRank().name() + "->" + def.rank().name();
        };
    }

    public PackageRegistry registry() {
        return registry;
    }
}
