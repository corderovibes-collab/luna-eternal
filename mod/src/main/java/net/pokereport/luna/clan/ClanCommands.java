package net.pokereport.luna.clan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.Espera;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.Traslado;

/** Comandos server-authoritative del hogar del clan. */
public final class ClanCommands {
    private static final long COOLDOWN_MS = 60_000L;
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    private ClanCommands() {}

    public static void registrar(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("clan")
                .then(CommandManager.literal("home").executes(c -> home(c.getSource())))
                .then(CommandManager.literal("sethome").executes(c -> setHome(c.getSource())))
                .then(CommandManager.literal("delhome").executes(c -> delHome(c.getSource()))));
    }

    private static int setHome(ServerCommandSource source) {
        ServerPlayerEntity p = source.getPlayer();
        if (p == null) return 0;
        if (!p.getServerWorld().getRegistryKey().equals(LunaDimensions.HOGAR)) {
            p.sendMessage(Text.literal("§cEl hogar del clan solo puede establecerse en el Mundo Hogar."));
            return 0;
        }
        Vec3d pos = p.getPos();
        if (!Traslado.esDestinoSeguro(p.getServerWorld(), pos)) {
            p.sendMessage(Text.literal("§cEsa ubicación no es segura para un hogar de clan."));
            return 0;
        }
        LunaEternal.submit(p, () -> {
            try {
                long pid = LunaEternal.players().resolve(p.getUuid(), p.getName().getString());
                var clan = LunaEternal.clans().clanDe(pid);
                var rol = LunaEternal.clans().rolDe(pid);
                if (clan == null || rol != ClanService.Rol.LIDER) {
                    responder(p, "§cSolo el dueño del clan puede establecer su hogar.");
                    return;
                }
                LunaEternal.clanHomes().set(clan.id(), pid,
                        p.getServerWorld().getRegistryKey().getValue().toString(),
                        pos.x, pos.y, pos.z, p.getYaw(), p.getPitch());
                LunaEternal.LOG.info("Clan Home actualizado: clan={} jugador={}", clan.id(), pid);
                responder(p, "§aHogar del clan establecido correctamente.");
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo establecer Clan Home", e);
                responder(p, "§cNo se pudo guardar el hogar del clan.");
            }
        });
        return 1;
    }

    private static int delHome(ServerCommandSource source) {
        ServerPlayerEntity p = source.getPlayer();
        if (p == null) return 0;
        LunaEternal.submit(p, () -> {
            try {
                long pid = LunaEternal.players().resolve(p.getUuid(), p.getName().getString());
                var clan = LunaEternal.clans().clanDe(pid);
                var rol = LunaEternal.clans().rolDe(pid);
                if (clan == null || rol != ClanService.Rol.LIDER) {
                    responder(p, "§cSolo el dueño del clan puede eliminar su hogar.");
                    return;
                }
                boolean borrado = LunaEternal.clanHomes().delete(clan.id());
                LunaEternal.LOG.info("Clan Home eliminado: clan={} jugador={}", clan.id(), pid);
                responder(p, borrado ? "§aHogar del clan eliminado."
                        : "§eTu clan todavía no tenía un hogar.");
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo eliminar Clan Home", e);
                responder(p, "§cNo se pudo eliminar el hogar del clan.");
            }
        });
        return 1;
    }

    private static int home(ServerCommandSource source) {
        ServerPlayerEntity p = source.getPlayer();
        if (p == null) return 0;
        if (enCombate(p)) {
            p.sendMessage(Text.literal("§cNo puedes viajar durante un combate Pokémon."));
            return 0;
        }
        long restante = COOLDOWN.getOrDefault(p.getUuid(), 0L) - System.currentTimeMillis();
        if (restante > 0) {
            p.sendMessage(Text.literal("§ePodrás volver a usar Clan Home en "
                    + ((restante + 999) / 1000) + " s."));
            return 0;
        }
        LunaEternal.submit(p, () -> {
            try {
                long pid = LunaEternal.players().resolve(p.getUuid(), p.getName().getString());
                var clan = LunaEternal.clans().clanDe(pid);
                if (clan == null) {
                    responder(p, "§cNo perteneces a ningún clan.");
                    return;
                }
                var h = LunaEternal.clanHomes().get(clan.id());
                if (h == null) {
                    responder(p, "§eTu clan todavía no ha establecido un hogar.");
                    return;
                }
                p.getServer().execute(() -> iniciarViaje(p, h));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo leer Clan Home", e);
                responder(p, "§cNo se pudo cargar el hogar del clan.");
            }
        });
        return 1;
    }

    private static void iniciarViaje(ServerPlayerEntity p, ClanHomeService.ClanHome h) {
        if (p.isRemoved() || p.isDisconnected()) return;
        // Por política solo se admite el mundo permanente. También se valida lo
        // persistido para bloquear filas antiguas o manipuladas.
        if (!LunaDimensions.HOGAR.getValue().toString().equals(h.dimension())) {
            p.sendMessage(Text.literal("§cEl hogar del clan apunta a una dimensión no permitida."));
            return;
        }
        ServerWorld world = p.getServer().getWorld(LunaDimensions.HOGAR);
        Vec3d pos = new Vec3d(h.x(), h.y(), h.z());
        if (world == null || !Traslado.esDestinoSeguro(world, pos)) {
            p.sendMessage(Text.literal("§cEl destino del hogar ya no es seguro. Pide al dueño que lo cambie."));
            return;
        }
        Espera.pedir(p, "viajar al hogar del clan", () -> {
            if (Traslado.esDestinoSeguro(world, pos) && Traslado.ir(p, world, pos, h.yaw(), h.pitch())) {
                COOLDOWN.put(p.getUuid(), System.currentTimeMillis() + COOLDOWN_MS);
                p.sendMessage(Text.literal("§aHas llegado al hogar de tu clan."));
            } else {
                p.sendMessage(Text.literal("§cEl viaje se canceló porque el destino dejó de ser seguro."));
            }
        });
    }

    private static boolean enCombate(ServerPlayerEntity p) {
        try {
            return com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry()
                    .getBattleByParticipatingPlayer(p) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void responder(ServerPlayerEntity p, String mensaje) {
        p.getServer().execute(() -> {
            if (!p.isRemoved()) p.sendMessage(Text.literal(mensaje));
        });
    }
}
