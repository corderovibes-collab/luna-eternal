package net.pokereport.luna.lunita;

import net.minecraft.particle.ParticleTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;

import java.util.UUID;

/** Puente servidor entre entidad, economía y recuperación. */
public final class LunitaManager {
    private static UUID unicaCargada;
    private LunitaManager() {}

    /** Instala únicamente los ganchos de recuperación; nunca genera un clon. */
    public static void registrar() {
        LunitaPokemon.register();
        try {
            LunitaAssets.verify();
            LunaEternal.LOG.info("[LUNITA] modelo, textura y referencias de animación verificados");
        } catch (Exception e) {
            throw new IllegalStateException("Recursos de Lunita incompatibles", e);
        }
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                LunaEternal.LOG.info("[LUNITA] sistema listo; esperando punto configurado o alta administrativa"));
        // Una segunda entidad guardada por accidente no se convierte en una
        // segunda fuente de recompensas. El primer UUID cargado es el canónico;
        // al descargarlo se libera el candado para no bloquear el siguiente chunk.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof LunitaEntity lunita) || world.isClient()) return;
            if (!LunaDimensions.CIUDADELA.equals(world.getRegistryKey())) {
                lunita.discard();
                LunaEternal.LOG.warn("[LUNITA] discarded outside Ciudadela uuid={}", lunita.getUuid());
            } else if (unicaCargada == null) {
                unicaCargada = lunita.getUuid();
            } else if (!unicaCargada.equals(lunita.getUuid())) {
                lunita.discard();
                LunaEternal.LOG.warn("[LUNITA] duplicate discarded uuid={}", lunita.getUuid());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LunitaEntity lunita && lunita.getUuid().equals(unicaCargada)) unicaCargada = null;
        });
    }

    public static boolean esCiudadela(LunitaEntity lunita) {
        return LunaDimensions.CIUDADELA.equals(lunita.getWorld().getRegistryKey());
    }

    public static void saludar(LunitaEntity lunita, ServerPlayerEntity jugador) {
        if (!esCiudadela(lunita)) return;
        Long playerId = LunaEternal.players().cachedId(jugador.getUuid());
        if (playerId == null) {
            jugador.sendMessage(Text.literal("§eLunita aún está recordando tu llegada."), true);
            return;
        }
        lunita.state(LunitaState.GREET);
        lunita.lookAtEntity(jugador, 30f, 30f);
        LunaEternal.submit(jugador, () -> {
            try {
                var r = new LunitaMemoryService(LunaEternal.database()).saludar(playerId);
                jugador.getServer().execute(() -> responder(lunita, jugador, r));
            } catch (Exception e) {
                LunaEternal.LOG.error("[LUNITA] No se pudo registrar saludo de {}", jugador.getUuid(), e);
                jugador.getServer().execute(() -> jugador.sendMessage(
                        Text.literal("§cLunita no pudo preparar su regalo. Inténtalo de nuevo."), false));
            }
        });
    }

    private static void responder(LunitaEntity l, ServerPlayerEntity p, LunitaMemoryService.Interaccion r) {
        if (l.isRemoved() || p.isRemoved()) return;
        ServerWorld w = (ServerWorld) l.getWorld();
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, l.getX(), l.getY() + .75, l.getZ(), 5, .25, .25, .25, 0.02);
        w.playSound(null, l.getBlockPos(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                net.minecraft.sound.SoundCategory.NEUTRAL, .7f, 1.25f);
        if (r.regalo()) {
            p.sendMessage(Text.literal("§d✦ §fLunita te saluda y te regala §a1.000 Plata§f."), false);
            LunaEternal.LOG.info("[LUNITA] reward granted player={} amount={}", p.getUuid(), LunitaMemoryService.RECOMPENSA_PLATA);
        } else {
            long m = Math.max(1L, r.restantesMillis() / 60_000L);
            p.sendMessage(Text.literal("§dLunita ya te dio un regalo. §7Vuelve en " + m + " min."), true);
        }
    }

    public static void recuperar(LunitaEntity lunita, String causa) {
        if (!(lunita.getWorld() instanceof ServerWorld w) || !esCiudadela(lunita)) return;
        BlockPos home = lunita.home();
        if (home == null) {
            LunaEternal.LOG.error("[LUNITA] recovery blocked: entity has no certified home ({})", causa);
            return;
        }
        w.getChunk(home);
        lunita.getNavigation().stop();
        lunita.refreshPositionAndAngles(home.getX() + .5, home.getY(), home.getZ() + .5,
                lunita.getYaw(), 0f);
        lunita.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        lunita.fallDistance = 0;
        lunita.state(LunitaState.IDLE);
        LunaEternal.LOG.warn("[LUNITA] recovery triggered: {}", causa);
    }

    /** Crea o reubica la única Lunita usando la posición real del administrador. */
    public static int respawn(ServerCommandSource source) {
        ServerPlayerEntity admin;
        try { admin = source.getPlayerOrThrow(); }
        catch (Exception e) { source.sendError(Text.literal("§cEste comando debe ejecutarlo un jugador en Ciudadela.")); return 0; }
        try {
            if (!LunaDimensions.CIUDADELA.equals(admin.getWorld().getRegistryKey())) {
                source.sendError(Text.literal("§cDebes estar en lunaeternal:ciudadela.")); return 0;
            }
            ServerWorld world = admin.getServerWorld();
            LunitaEntity existing = buscar(world);
            if (existing == null) {
                existing = LunitaEntities.LUNITA.create(world);
                if (existing == null) { source.sendError(Text.literal("§cNo se pudo crear Lunita.")); return 0; }
                world.spawnEntity(existing);
            }
            BlockPos home = admin.getBlockPos();
            world.getChunk(home);
            existing.setHome(home);
            existing.refreshPositionAndAngles(admin.getX(), admin.getY(), admin.getZ(), admin.getYaw(), 0f);
            existing.state(LunitaState.IDLE);
            source.sendFeedback(() -> Text.literal("§dLunita quedó ubicada en §f" + home.toShortString()), true);
            LunaEternal.LOG.info("[LUNITA] admin respawn player={} home={}", admin.getUuid(), home.toShortString());
            return 1;
        } catch (Exception e) {
            LunaEternal.LOG.error("[LUNITA] fallo al crear o reubicar la entidad para {}", admin.getUuid(), e);
            source.sendError(Text.literal("§cNo se pudo ubicar Lunita. La causa quedó registrada en consola."));
            return 0;
        }
    }

    public static int status(ServerCommandSource source) {
        ServerWorld city = source.getServer().getWorld(LunaDimensions.CIUDADELA);
        if (city == null) { source.sendError(Text.literal("§cLa dimensión Ciudadela no está disponible.")); return 0; }
        LunitaEntity l = buscar(city);
        if (l == null) { source.sendFeedback(() -> Text.literal("§eLunita aún no está creada."), false); return 0; }
        source.sendFeedback(() -> Text.literal("§dLunita §7estado=§f" + l.state()
                + " §7posición=§f" + l.getBlockPos().toShortString()
                + " §7hogar=§f" + (l.home() == null ? "sin configurar" : l.home().toShortString())), false);
        return 1;
    }

    public static int gotoLunita(ServerCommandSource source) {
        ServerPlayerEntity admin;
        try { admin = source.getPlayerOrThrow(); }
        catch (Exception e) { source.sendError(Text.literal("§cEste comando debe ejecutarlo un jugador.")); return 0; }
        ServerWorld city = admin.getServer().getWorld(LunaDimensions.CIUDADELA);
        if (city == null) { source.sendError(Text.literal("§cLa dimensión Ciudadela no está disponible.")); return 0; }
        LunitaEntity l = buscar(city);
        if (l == null) { source.sendError(Text.literal("§eLunita aún no está creada.")); return 0; }
        admin.teleport((ServerWorld) l.getWorld(), l.getX(), l.getY(), l.getZ(), java.util.Set.of(), l.getYaw(), 0f);
        return 1;
    }

    /** Diagnóstico sin mutar estado, pensado para revisar consola y soporte. */
    public static int debug(ServerCommandSource source) {
        var world = source.getServer().getWorld(LunaDimensions.CIUDADELA);
        if (world == null) { source.sendError(Text.literal("§cLa dimensión Ciudadela no está disponible.")); return 0; }
        long count = contar(world);
        source.sendFeedback(() -> Text.literal("§5[Lunita] §7entidades cargadas=§f" + count
                + " §7economía=§f" + (LunaEternal.economy() == null ? "no lista" : "lista")
                + " §7memoria=§fV048"), false);
        return count == 1 ? 1 : 0;
    }

    private static LunitaEntity buscar(ServerWorld world) {
        for (var entity : world.iterateEntities()) {
            if (entity instanceof LunitaEntity lunita) return lunita;
        }
        return null;
    }

    private static long contar(ServerWorld world) {
        long total = 0;
        for (var entity : world.iterateEntities()) if (entity instanceof LunitaEntity) total++;
        return total;
    }
}
