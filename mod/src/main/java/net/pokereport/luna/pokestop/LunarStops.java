package net.pokereport.luna.pokestop;

import com.google.gson.Gson;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

/** SQL fuera del tick; inventario y mundo exclusivamente en el hilo servidor. */
public final class LunarStops {
    public static final Identifier ID = Identifier.of("lunaeternal", "pokeparada_lunar");
    public static final LunarStopBlock BLOCK = new LunarStopBlock(AbstractBlock.Settings.create()
            .strength(-1.0f, 3_600_000f).nonOpaque()
            .luminance(s -> s.get(TallPlantBlock.HALF) == DoubleBlockHalf.UPPER ? 15 : 7));
    private static final Gson JSON = new Gson();
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();
    private static ThreadPoolExecutor worker;
    private record Delivery(String id, StopRewards.Gift[] gifts) {}
    private record Claim(long count, long remaining, boolean bonus) {}

    public static void register() {
        Registry.register(Registries.BLOCK, ID, BLOCK);
        Registry.register(Registries.ITEM, ID, new BlockItem(BLOCK, new Item.Settings()));
    }

    public static void serverHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (String item : StopRewards.allowed()) {
                if (!Registries.ITEM.containsId(Identifier.of(item)))
                    throw new IllegalStateException("Recompensa de pokeparada inexistente: " + item);
            }
            worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), r -> { var t = new Thread(r, "luna-pokeparadas"); t.setDaemon(true); return t; });
            LunaEternal.LOG.info("Paradas lunares listas: 22 premios basicos, cooldown 24h, bono 100 LC cada 50 visitas");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (worker != null) worker.shutdownNow(); BUSY.clear(); });
    }

    public static void claim(ServerPlayerEntity player, BlockPos base) {
        if (player.isSpectator() || net.pokereport.luna.puerta.Puerta.bloqueado(player)) return;
        schedule(player, base.toImmutable());
    }

    private static void schedule(ServerPlayerEntity player, BlockPos base) {
        var executor = worker;
        if (executor == null || executor.isShutdown() || !BUSY.add(player.getUuid())) return;
        UUID uuid = player.getUuid();
        String name = player.getGameProfile().getName();
        MinecraftServer server = player.getServer();
        var dimension = player.getServerWorld().getRegistryKey();
        String station = base == null ? null : dimension.getValue() + ":" + base.getX() + ":" + base.getY() + ":" + base.getZ();
        try {
            executor.execute(() -> {
                try {
                    long id = LunaEternal.players().resolve(uuid, name);
                    // Reintenta el paquete existente antes de generar otra recompensa.
                    List<Delivery> pending = pending(id);
                    for (Delivery delivery : pending) {
                        if (!deliver(server, uuid, delivery)) return;
                    }
                    if (station == null) return;
                    boolean present = server.submit(() -> {
                        var current = server.getPlayerManager().getPlayer(uuid);
                        return current != null && !current.isRemoved() && !current.isSpectator()
                            && !net.pokereport.luna.puerta.Puerta.bloqueado(current)
                            && current.getServerWorld().getRegistryKey().equals(dimension)
                            && current.squaredDistanceTo(base.toCenterPos()) <= 36
                            && current.getServerWorld().getBlockState(base).isOf(BLOCK);
                    }).get(10, TimeUnit.SECONDS);
                    if (!present) return;
                    Claim claim = reserve(id, station);
                    if (claim.remaining > 0) {
                        long minutes = (claim.remaining + 59_999) / 60_000;
                        message(server, uuid, "§b☽ Parada lunar §7· Disponible en §f" + minutes / 60 + " h " + minutes % 60
                            + " min §7· Progreso §d" + claim.count % 50 + "/50", true);
                        return;
                    }
                    for (Delivery delivery : pending(id)) deliver(server, uuid, delivery);
                    message(server, uuid, "§b☽ Parada lunar §7· Visita §f" + claim.count + " §7· Próximo bono: §d"
                            + (50 - claim.count % 50) + " visitas §7· Vuelve en 24 horas.", false);
                    if (claim.bonus) message(server, uuid, "§e✦ ¡50 visitas a esta parada! Has recibido 100 Lunacoins.", false);
                    server.execute(() -> {
                        var current = server.getPlayerManager().getPlayer(uuid);
                        if (current != null) net.pokereport.luna.net.Red.enviarSaldo(current);
                    });
                } catch (Exception e) {
                    LunaEternal.LOG.error("No se pudo completar la pokeparada para {}", uuid, e);
                    message(server, uuid, "§cNo se pudo completar la entrega. Tus premios pendientes se conservan; vuelve a interactuar.", false);
                } finally { BUSY.remove(uuid); }
            });
        } catch (RejectedExecutionException e) {
            BUSY.remove(uuid);
            player.sendMessage(Text.literal("§eLas paradas están ocupadas. Inténtalo en unos segundos."), true);
        }
    }

    private static Claim reserve(long playerId, String station) throws Exception {
        try (Connection c = LunaEternal.database().connection()) {
            c.setAutoCommit(false);
            try {
                try (var ps = c.prepareStatement("INSERT IGNORE INTO lunar_stop_progress(player_id,station) VALUES (?,?)")) {
                    ps.setLong(1, playerId); ps.setString(2, station); ps.executeUpdate();
                }
                long now = System.currentTimeMillis(), count, last;
                try (var ps = c.prepareStatement("SELECT claims,last_claim_ms FROM lunar_stop_progress WHERE player_id=? AND station=? FOR UPDATE")) {
                    ps.setLong(1, playerId); ps.setString(2, station);
                    try (var rs = ps.executeQuery()) { if (!rs.next()) throw new SQLException("Parada ausente"); count = rs.getLong(1); last = rs.getLong(2); }
                }
                long remaining = count == 0 ? 0 : StopRewards.remaining(now, last);
                if (remaining > 0) { c.commit(); return new Claim(count, remaining, false); }
                count++;
                String delivery = UUID.randomUUID().toString();
                try (var ps = c.prepareStatement("INSERT INTO lunar_stop_delivery(id,player_id,gifts,created_ms) VALUES (?,?,?,?)")) {
                    ps.setString(1, delivery); ps.setLong(2, playerId);
                    ps.setString(3, JSON.toJson(StopRewards.roll(new Random()))); ps.setLong(4, now); ps.executeUpdate();
                }
                try (var ps = c.prepareStatement("UPDATE lunar_stop_progress SET claims=?,last_claim_ms=? WHERE player_id=? AND station=?")) {
                    ps.setLong(1, count); ps.setLong(2, now); ps.setLong(3, playerId); ps.setString(4, station); ps.executeUpdate();
                }
                boolean bonus = StopRewards.milestone(count);
                if (bonus) LunaEternal.economy().applyInTransaction(c, playerId, Currency.REPORTCOIN, 100,
                    "pokeparada_50", null, null, "stop:" + delivery);
                c.commit();
                return new Claim(count, 0, bonus);
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private static List<Delivery> pending(long playerId) throws SQLException {
        var result = new ArrayList<Delivery>();
        try (var c = LunaEternal.database().connection(); var ps = c.prepareStatement(
                "SELECT id,gifts FROM lunar_stop_delivery WHERE player_id=? AND delivered=FALSE ORDER BY created_ms LIMIT 64")) {
            ps.setLong(1, playerId);
            try (var rs = ps.executeQuery()) { while (rs.next()) result.add(new Delivery(rs.getString(1), JSON.fromJson(rs.getString(2), StopRewards.Gift[].class))); }
        }
        return result;
    }

    private static boolean deliver(MinecraftServer server, UUID uuid, Delivery delivery) throws Exception {
        String receipt = "luna_stop_" + delivery.id;
        boolean success = server.submit(() -> {
            var player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || player.isRemoved()) return false;
            if (player.getCommandTags().contains(receipt)) {
                ((net.pokereport.luna.mixin.PlayerSaveInvoker) server.getPlayerManager()).luna$savePlayerData(player);
                return true;
            }
            // Reserva conservadora: un hueco por tipo. No arrojar premios al suelo.
            long empty = player.getInventory().main.stream().filter(ItemStack::isEmpty).count();
            if (empty < delivery.gifts.length) {
                player.sendMessage(Text.literal("§e☽ Libera " + delivery.gifts.length + " huecos. Tus premios están guardados; toca una parada para recibirlos."), false);
                return false;
            }
            for (var gift : delivery.gifts) {
                Identifier item = Identifier.of(gift.item());
                if (!Registries.ITEM.containsId(item)) throw new IllegalStateException("Objeto inexistente: " + item);
            }
            if (!player.addCommandTag(receipt)) throw new IllegalStateException("No hay espacio para el recibo del jugador");
            for (var gift : delivery.gifts) {
                var stack = new ItemStack(Registries.ITEM.get(Identifier.of(gift.item())), gift.count());
                player.sendMessage(Text.literal("§7  + " + gift.count() + " × ").append(stack.getName()), false);
                player.getInventory().insertStack(stack);
            }
            // El recibo y los items viajan juntos en el guardado del jugador.
            ((net.pokereport.luna.mixin.PlayerSaveInvoker) server.getPlayerManager()).luna$savePlayerData(player);
            player.currentScreenHandler.sendContentUpdates();
            player.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1, player.getZ(), 18, .6, .6, .6, .02);
            return true;
        }).get(10, TimeUnit.SECONDS);
        if (!success) return false;
        // Vanilla puede registrar un error de guardado sin lanzarlo: no confirmar
        // SQL hasta comprobar que el recibo se encuentra realmente en playerdata.
        var file = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA).resolve(uuid + ".dat");
        var saved = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
        var tags = saved.getList("Tags", net.minecraft.nbt.NbtElement.STRING_TYPE);
        boolean persisted = false;
        for (int i = 0; i < tags.size(); i++) if (receipt.equals(tags.getString(i))) persisted = true;
        if (!persisted) throw new java.io.IOException("No se persistió el recibo de pokeparada " + delivery.id);
        try (var c = LunaEternal.database().connection(); var ps = c.prepareStatement("UPDATE lunar_stop_delivery SET delivered=TRUE WHERE id=?")) {
            ps.setString(1, delivery.id); ps.executeUpdate();
        }
        server.execute(() -> {
            var player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) player.removeCommandTag(receipt);
        });
        return true;
    }

    private static void message(MinecraftServer server, UUID uuid, String text, boolean actionbar) {
        server.execute(() -> { var player = server.getPlayerManager().getPlayer(uuid); if (player != null) player.sendMessage(Text.literal(text), actionbar); });
    }
}
