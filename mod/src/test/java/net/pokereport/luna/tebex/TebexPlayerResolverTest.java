package net.pokereport.luna.tebex;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas unitarias para TebexPlayerResolver y el flujo de Pending Entitlements on Join.
 * Valida de forma rigurosa los 4 casos de resolución, protección Geyser/Floodgate y entrega diferida.
 */
public class TebexPlayerResolverTest {

    private static final String CATALOG_JSON = """
        {
          "packages": {
            "7682973": { "type": "LUNACOINS", "amount": 500 },
            "7682989": { "type": "LUNACOINS", "amount": 2010 },
            "7682993": { "type": "LUNACOINS", "amount": 4375 },
            "7683015": { "type": "LUNACOINS", "amount": 9850 },
            "7683200": { "type": "RANK", "rank": "ELITE" },
            "7683214": { "type": "RANK", "rank": "CAMPEON" },
            "7683222": { "type": "RANK", "rank": "MAESTRO" },
            "7683229": { "type": "RANK", "rank": "LEYENDA" },
            "7683235": { "type": "RANK_UPGRADE", "from": "ELITE", "to": "CAMPEON" },
            "7683245": { "type": "RANK_UPGRADE", "from": "CAMPEON", "to": "MAESTRO" },
            "7683251": { "type": "RANK_UPGRADE", "from": "MAESTRO", "to": "LEYENDA" }
          }
        }
        """;

    private PackageRegistry registry;
    private MockDatabaseState dbState;

    @BeforeEach
    void setUp() {
        registry = PackageRegistry.parseAndValidate(CATALOG_JSON, "TebexPlayerResolverTest");
        dbState = new MockDatabaseState();
    }

    // =========================================================================
    // CASO 1: Identidad es UUID canónico
    // =========================================================================

    @Test
    @DisplayName("CASO 1A: UUID canónico estándar de 36 caracteres de jugador existente en DB")
    void testCase1A_canonicalUuidExistingPlayer() throws SQLException {
        UUID uuid = UUID.randomUUID();
        long playerId = dbState.createPlayer(uuid, "TrainerQA");

        Connection c = dbState.createMockConnection();
        TebexPlayerResolver.ResolutionResult res = TebexPlayerResolver.resolve(
                c, (MinecraftServer) null, null, uuid.toString(), "HintName"
        );

        assertTrue(res.isResolved());
        assertEquals(uuid, res.uuid());
        assertEquals(playerId, res.playerId());
        assertEquals("TrainerQA", res.username());
    }

    @Test
    @DisplayName("CASO 1B: UUID compacto de 32 caracteres (sin guiones) se normaliza y registra")
    void testCase1B_compact32CharUuidRegistersWithHint() throws SQLException {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        String compact = "12345678123412341234123456789abc";

        Connection c = dbState.createMockConnection();
        TebexPlayerResolver.ResolutionResult res = TebexPlayerResolver.resolve(
                c, (MinecraftServer) null, null, compact, "NuevoComprador"
        );

        assertTrue(res.isResolved());
        assertEquals(uuid, res.uuid());
        assertNotNull(res.playerId());
        assertEquals("NuevoComprador", res.username());
        assertEquals(uuid, dbState.getUuidForPlayer(res.playerId()));
    }

    // =========================================================================
    // CASO 2: Identidad es Username y existe en DB (Case-Insensitive)
    // =========================================================================

    @Test
    @DisplayName("CASO 2: Username existente en DB offline se resuelve de forma insensible a mayúsculas")
    void testCase2_usernameExistingPlayerCaseInsensitive() throws SQLException {
        UUID storedUuid = UUID.randomUUID();
        long playerId = dbState.createPlayer(storedUuid, "TrainerQA");

        Connection c = dbState.createMockConnection();

        // Resolución pasando "trainerqa" en minúsculas
        TebexPlayerResolver.ResolutionResult resLower = TebexPlayerResolver.resolve(
                c, (MinecraftServer) null, null, "trainerqa", null
        );
        assertTrue(resLower.isResolved());
        assertEquals(storedUuid, resLower.uuid());
        assertEquals(playerId, resLower.playerId());
        assertEquals("TrainerQA", resLower.username());

        // Resolución pasando "TRAINERQA" en mayúsculas
        TebexPlayerResolver.ResolutionResult resUpper = TebexPlayerResolver.resolve(
                c, (MinecraftServer) null, null, "TRAINERQA", null
        );
        assertTrue(resUpper.isResolved());
        assertEquals(storedUuid, resUpper.uuid());
        assertEquals(playerId, resUpper.playerId());
    }

    // =========================================================================
    // CASO 3: Identidad es Username y el jugador está actualmente conectado
    // =========================================================================

    @Test
    @DisplayName("CASO 3: Username de jugador online obtiene UUID real en vivo y se sincroniza en DB")
    void testCase3_usernameOnlinePlayerResolvesLiveUuid() throws SQLException {
        UUID liveUuid = UUID.randomUUID();
        String onlineUsername = "BedrockTrainer";

        TebexPlayerResolver.OnlinePlayerLookup mockLookup = (query) -> {
            if (query.equalsIgnoreCase(onlineUsername)) {
                return new TebexPlayerResolver.OnlinePlayerLookup.OnlinePlayerInfo(liveUuid, onlineUsername);
            }
            return null;
        };
        Connection c = dbState.createMockConnection();

        TebexPlayerResolver.ResolutionResult res = TebexPlayerResolver.resolveWithLookup(
                c, mockLookup, null, onlineUsername, null
        );

        assertTrue(res.isResolved());
        assertEquals(liveUuid, res.uuid());
        assertNotNull(res.playerId());
        assertEquals(onlineUsername, res.username());

        // Verifica que se haya guardado en DB
        assertEquals(liveUuid, dbState.getUuidForPlayer(res.playerId()));
    }

    // =========================================================================
    // CASO 4: Identidad es Username y NUNCA ha entrado (Protección Floodgate)
    // =========================================================================

    @Test
    @DisplayName("CASO 4: Username desconocido offline queda en PENDING_PLAYER_RESOLUTION sin inventar UUID")
    void testCase4_unknownOfflinePlayerBecomesPending() throws SQLException {
        Connection c = dbState.createMockConnection();

        TebexPlayerResolver.ResolutionResult res = TebexPlayerResolver.resolve(
                c, (MinecraftServer) null, null, "PersonaTotalmenteNueva", null
        );

        assertFalse(res.isResolved());
        assertTrue(res.isPending());
        assertEquals(TebexPlayerResolver.Status.PENDING_PLAYER_RESOLUTION, res.status());
        assertNull(res.uuid(), "NUNCA debe inventar un UUID offline con nameUUIDFromBytes");
        assertNull(res.playerId());
        assertEquals("PersonaTotalmenteNueva", res.username());
    }

    // =========================================================================
    // Validaciones de Argumentos Inválidos
    // =========================================================================

    @Test
    @DisplayName("Validación: Identidad nula, vacía o con espacios es rechazada")
    void testInvalidIdentityRejected() throws SQLException {
        Connection c = dbState.createMockConnection();

        assertTrue(TebexPlayerResolver.resolve(c, (MinecraftServer) null, null, null, null).status() == TebexPlayerResolver.Status.REJECTED);
        assertTrue(TebexPlayerResolver.resolve(c, (MinecraftServer) null, null, "", null).status() == TebexPlayerResolver.Status.REJECTED);
        assertTrue(TebexPlayerResolver.resolve(c, (MinecraftServer) null, null, "   ", null).status() == TebexPlayerResolver.Status.REJECTED);
    }

    // =========================================================================
    // Flujo Completo: Fulfill con Username Offline -> Entrega Diferida en Join
    // =========================================================================

    @Test
    @DisplayName("Flujo End-to-End: Username desconocido queda pendiente y se entrega íntegro al unirse")
    void testFullPendingEntitlementDeliveredOnJoin() {
        TestableResolverTebexService service = new TestableResolverTebexService(registry, dbState);

        // 1. Llega compra desde Tebex con username nuevo offline
        TebexService.ExecutionResult initialResult = service.fulfill(
                "tx_pending_100", "NuevoJugador", "7682973", 1, "NuevoJugador"
        );

        assertTrue(initialResult.isSuccess());
        assertEquals("PENDING_PLAYER_RESOLUTION", initialResult.status());

        // Comprueba que la fila en tebex_fulfillment está registrada como PENDING_PLAYER_RESOLUTION
        MockDatabaseState.FulfillmentRecord rec = dbState.getFulfillment("tx_pending_100", "7682973");
        assertNotNull(rec);
        assertEquals("PENDING_PLAYER_RESOLUTION", rec.status);
        assertNull(rec.playerUuid);
        assertEquals("NuevoJugador", rec.usernameRaw);

        // 2. El jugador entra al servidor por primera vez con su UUID real de Bedrock/Java
        UUID authenticUuid = UUID.randomUUID();

        int delivered = service.processPendingFulfillmentsOnJoin(null, authenticUuid, "NuevoJugador");
        assertEquals(1, delivered, "Debe entregar exactamente 1 entitlement pendiente");

        // Comprueba que el registro ahora es DELIVERED con el UUID real
        MockDatabaseState.FulfillmentRecord updatedRec = dbState.getFulfillment("tx_pending_100", "7682973");
        assertNotNull(updatedRec);
        assertEquals("DELIVERED", updatedRec.status);
        assertEquals(authenticUuid.toString(), updatedRec.playerUuid);
        assertNotNull(updatedRec.playerId);

        // Comprueba que el saldo de LunaCoins del jugador es exactamente +500
        assertEquals(500L, dbState.getBalance(updatedRec.playerId));

        // 3. Prueba de Idempotencia: el jugador vuelve a conectarse o se procesa de nuevo
        int secondJoinDelivered = service.processPendingFulfillmentsOnJoin(null, authenticUuid, "NuevoJugador");
        assertEquals(0, secondJoinDelivered, "No debe entregar nada en un segundo join");
        assertEquals(500L, dbState.getBalance(updatedRec.playerId), "Saldo no debe duplicarse");
    }

    public static class MockDatabaseState {
        private final Map<Long, UUID> playerIdToUuid = new ConcurrentHashMap<>();
        private final Map<Long, String> playerIdToUsername = new ConcurrentHashMap<>();
        private final Map<String, Long> usernameLowerToPlayerId = new ConcurrentHashMap<>();
        private final Map<UUID, Long> uuidToPlayerId = new ConcurrentHashMap<>();
        private final Map<Long, AtomicLong> playerBalances = new ConcurrentHashMap<>();
        private final Map<String, FulfillmentRecord> fulfillments = new ConcurrentHashMap<>();
        private final AtomicLong playerIdSeq = new AtomicLong(100);
        private final AtomicLong fulfillmentIdSeq = new AtomicLong(1);

        public static class FulfillmentRecord {
            public final long id;
            public final String transactionId;
            public final String packageId;
            public String playerUuid;
            public Long playerId;
            public String usernameRaw;
            public int purchaseQuantity;
            public String productType;
            public String productValue;
            public String status;

            public FulfillmentRecord(long id, String transactionId, String packageId, String playerUuid,
                                     Long playerId, String usernameRaw, int purchaseQuantity,
                                     String productType, String productValue, String status) {
                this.id = id;
                this.transactionId = transactionId;
                this.packageId = packageId;
                this.playerUuid = playerUuid;
                this.playerId = playerId;
                this.usernameRaw = usernameRaw;
                this.purchaseQuantity = purchaseQuantity;
                this.productType = productType;
                this.productValue = productValue;
                this.status = status;
            }
        }

        public synchronized long createPlayer(UUID uuid, String username) {
            long id = playerIdSeq.incrementAndGet();
            playerIdToUuid.put(id, uuid);
            playerIdToUsername.put(id, username);
            usernameLowerToPlayerId.put(username.toLowerCase(Locale.ROOT), id);
            uuidToPlayerId.put(uuid, id);
            playerBalances.put(id, new AtomicLong(0));
            return id;
        }

        public UUID getUuidForPlayer(long id) {
            return playerIdToUuid.get(id);
        }

        public long getBalance(long playerId) {
            AtomicLong bal = playerBalances.get(playerId);
            return bal != null ? bal.get() : 0L;
        }

        public void creditBalance(long playerId, long amount) {
            playerBalances.computeIfAbsent(playerId, k -> new AtomicLong(0)).addAndGet(amount);
        }

        public FulfillmentRecord getFulfillment(String tx, String pkg) {
            return fulfillments.get(tx + ":" + pkg);
        }

        public Connection createMockConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("setAutoCommit".equals(method.getName())) return null;
                        if ("commit".equals(method.getName())) return null;
                        if ("rollback".equals(method.getName())) return null;
                        if ("close".equals(method.getName())) return null;
                        if ("prepareStatement".equals(method.getName())) {
                            return createMockPreparedStatement((String) args[0]);
                        }
                        return null;
                    }
            );
        }

        private PreparedStatement createMockPreparedStatement(String sql) {
            final List<Object> params = new ArrayList<>();
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("setString".equals(name) || "setLong".equals(name) || "setInt".equals(name)) {
                            int idx = (Integer) args[0];
                            while (params.size() < idx) params.add(null);
                            params.set(idx - 1, args[1]);
                            return null;
                        }
                        if ("setNull".equals(name)) {
                            int idx = (Integer) args[0];
                            while (params.size() < idx) params.add(null);
                            params.set(idx - 1, null);
                            return null;
                        }
                        if ("executeUpdate".equals(name)) {
                            handleExecuteUpdate(sql, params);
                            return 1;
                        }
                        if ("executeQuery".equals(name)) {
                            return handleExecuteQuery(sql, params);
                        }
                        if ("getGeneratedKeys".equals(name)) {
                            return createGeneratedKeysResultSet();
                        }
                        return null;
                    }
            );
        }

        private void handleExecuteUpdate(String sql, List<Object> params) {
            if (sql.contains("INSERT INTO player")) {
                UUID u = UUID.fromString((String) params.get(0));
                String name = (String) params.get(1);
                Long existing = uuidToPlayerId.get(u);
                if (existing == null) {
                    createPlayer(u, name);
                }
            } else if (sql.contains("INSERT INTO tebex_fulfillment")) {
                String tx = (String) params.get(0);
                String pkg = (String) params.get(1);
                String rawUser = params.size() > 2 && params.get(2) != null ? params.get(2).toString() : null;
                fulfillments.compute(tx + ":" + pkg, (k, existing) -> {
                    long id = existing != null ? existing.id : fulfillmentIdSeq.incrementAndGet();
                    if (sql.contains("PENDING_PLAYER_RESOLUTION")) {
                        return new FulfillmentRecord(id, tx, pkg, null, null, rawUser, 1, "LUNACOINS", "500", "PENDING_PLAYER_RESOLUTION");
                    }
                    return existing;
                });
            }
        }

        private ResultSet handleExecuteQuery(String sql, List<Object> params) {
            if (sql.contains("SELECT player_id, username FROM player WHERE mc_uuid = ?")) {
                UUID u = UUID.fromString((String) params.get(0));
                Long pid = uuidToPlayerId.get(u);
                if (pid != null) {
                    return createSingleRowResultSet(Map.of(
                            "player_id", pid,
                            "username", playerIdToUsername.get(pid)
                    ));
                }
            } else if (sql.contains("SELECT player_id, mc_uuid, username FROM player WHERE LOWER(username) = LOWER(?)")) {
                String uName = ((String) params.get(0)).toLowerCase(Locale.ROOT);
                Long pid = usernameLowerToPlayerId.get(uName);
                if (pid != null) {
                    return createSingleRowResultSet(Map.of(
                            "player_id", pid,
                            "mc_uuid", playerIdToUuid.get(pid).toString(),
                            "username", playerIdToUsername.get(pid)
                    ));
                }
            } else if (sql.contains("SELECT player_id FROM player WHERE mc_uuid = ?")) {
                UUID u = UUID.fromString((String) params.get(0));
                Long pid = uuidToPlayerId.get(u);
                if (pid != null) {
                    return createSingleRowResultSet(Map.of("player_id", pid));
                }
            } else if (sql.contains("WHERE status = 'PENDING_PLAYER_RESOLUTION' AND LOWER(username_raw) = LOWER(?)")) {
                String raw = ((String) params.get(0)).toLowerCase(Locale.ROOT);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (FulfillmentRecord f : fulfillments.values()) {
                    if ("PENDING_PLAYER_RESOLUTION".equals(f.status) && f.usernameRaw != null && f.usernameRaw.equalsIgnoreCase(raw)) {
                        rows.add(Map.of(
                                "id", f.id,
                                "transaction_id", f.transactionId,
                                "package_id", f.packageId,
                                "product_type", f.productType,
                                "product_value", f.productValue,
                                "purchase_quantity", f.purchaseQuantity
                        ));
                    }
                }
                return createMultiRowResultSet(rows);
            } else if (sql.contains("SELECT status FROM tebex_fulfillment WHERE id = ?")) {
                long fid = (Long) params.get(0);
                for (FulfillmentRecord f : fulfillments.values()) {
                    if (f.id == fid) {
                        return createSingleRowResultSet(Map.of("status", f.status));
                    }
                }
            }
            return createEmptyResultSet();
        }

        private ResultSet createSingleRowResultSet(Map<String, Object> data) {
            return createMultiRowResultSet(List.of(data));
        }

        private ResultSet createMultiRowResultSet(List<Map<String, Object>> rows) {
            final int[] cursor = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("next".equals(name)) {
                            cursor[0]++;
                            return cursor[0] < rows.size();
                        }
                        if ("getLong".equals(name)) {
                            Object v = rows.get(cursor[0]).get(args[0]);
                            return v instanceof Number n ? n.longValue() : 0L;
                        }
                        if ("getString".equals(name)) {
                            return (String) rows.get(cursor[0]).get(args[0]);
                        }
                        if ("getInt".equals(name)) {
                            Object v = rows.get(cursor[0]).get(args[0]);
                            return v instanceof Number n ? n.intValue() : 0;
                        }
                        if ("close".equals(name)) return null;
                        return null;
                    }
            );
        }

        private ResultSet createEmptyResultSet() {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> "next".equals(method.getName()) ? false : null
            );
        }

        private ResultSet createGeneratedKeysResultSet() {
            final boolean[] read = {false};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        if ("next".equals(method.getName())) {
                            if (!read[0]) {
                                read[0] = true;
                                return true;
                            }
                            return false;
                        }
                        if ("getLong".equals(method.getName())) return playerIdSeq.get();
                        return null;
                    }
            );
        }
    }

    static class TestableResolverTebexService extends TebexService {
        private final MockDatabaseState state;

        public TestableResolverTebexService(PackageRegistry registry, MockDatabaseState state) {
            super(null, registry, null, null, null, null);
            this.state = state;
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return state.createMockConnection();
        }

        @Override
        protected long applyEconomyCredit(Connection c, long playerId, long amount, String idempotencyKey, String reason)
                throws EconomyException {
            state.creditBalance(playerId, amount);
            return state.getBalance(playerId);
        }

        @Override
        protected void markFulfillmentDelivered(Connection c, String transactionId, String packageId,
                                               UUID playerUuid, Long playerId, String usernameRaw, int quantity,
                                               String productType, String productValue) throws SQLException {
            MockDatabaseState.FulfillmentRecord rec = state.getFulfillment(transactionId, packageId);
            if (rec != null) {
                rec.status = "DELIVERED";
                rec.playerUuid = playerUuid != null ? playerUuid.toString() : null;
                rec.playerId = playerId;
                rec.usernameRaw = usernameRaw;
            } else {
                long id = state.fulfillmentIdSeq.incrementAndGet();
                state.fulfillments.put(transactionId + ":" + packageId, new MockDatabaseState.FulfillmentRecord(
                        id, transactionId, packageId, playerUuid != null ? playerUuid.toString() : null,
                        playerId, usernameRaw, quantity, productType, productValue, "DELIVERED"
                ));
            }
        }
    }
}
