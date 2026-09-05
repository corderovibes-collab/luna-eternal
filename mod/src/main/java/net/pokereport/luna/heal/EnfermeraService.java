package net.pokereport.luna.heal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.Cobblemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.net.Red;

public final class EnfermeraService {

    public static final String MARCA = "luna_enfermera";

    // Posiciones de las máquinas
    private static final Vec3d[] MAQUINAS = {
            new Vec3d(90.492, 68.75, 92.54),
            new Vec3d(90.32, 68.87, 93.37),
            new Vec3d(90.50, 68.75, 95.55),
            new Vec3d(90.49, 68.75, 96.45)
    };

    // La posición original de la enfermera (a donde debe volver)
    private static Vec3d ORIGEN = new Vec3d(86.300, 68, 94.5);
    private static float YAW_ORIGINAL = -90f;

    // Estado de cada enfermera
    private enum Estado {
        YENDO, CURANDO, VOLVIENDO
    }

    private static class Tarea {
        UUID jugadorId;
        Estado estado;
        Vec3d maquina;
        int ticksCurando;
        
        Tarea(UUID jugadorId, Vec3d maquina) {
            this.jugadorId = jugadorId;
            this.estado = Estado.YENDO;
            this.maquina = maquina;
            this.ticksCurando = 0;
        }
    }

    private static final Map<UUID, Tarea> TAREAS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> SESION_JUGADOR = new ConcurrentHashMap<>();

        public static boolean colocarEnfermera(ServerPlayerEntity p) {
        var mundo = p.getServerWorld();
        Vec3d donde = p.getPos();
        float yaw = p.getYaw();
        
        ORIGEN = donde;
        YAW_ORIGINAL = yaw;
        
        var previas = mundo.getEntitiesByClass(MobEntity.class, 
                new net.minecraft.util.math.Box(donde.x - 10, donde.y - 10, donde.z - 10, 
                                                donde.x + 10, donde.y + 10, donde.z + 10), 
                e -> e.getCommandTags().contains(MARCA));
        for (var e : previas) e.discard();

        var tipo = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.of("cobblemon", "npc"));
        if (tipo == null) {
            p.sendMessage(net.minecraft.text.Text.literal("§cError: No se encontró la entidad cobblemon:npc en el registro."), false);
            return false;
        }

        var entidad = (MobEntity) tipo.create(mundo);
        if (entidad == null) {
             p.sendMessage(net.minecraft.text.Text.literal("§cError: No se pudo crear el NPC."), false);
             return false;
        }

        // It might be possible to set the NPC variant via NBT, but by default it might be a generic NPC.
        // We will try to give it the nurse_joy preset.
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        entidad.writeNbt(nbt);
        nbt.putString("npcType", "cobblemon:nurse_joy");
        nbt.putString("preset", "cobblemon:nurse_joy");
        entidad.readNbt(nbt);

        entidad.refreshPositionAndAngles(donde.x, donde.y, donde.z, yaw, 0f);
        entidad.setHeadYaw(yaw);
        entidad.setBodyYaw(yaw);
        entidad.addCommandTag(MARCA);
        entidad.setInvulnerable(true);
        entidad.setPersistent();
        
        mundo.spawnEntity(entidad);
        
        return true;
    }


    private EnfermeraService() {}

    public static void registrar() {
        // Interceptar clic en la enfermera
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, golpe) -> {
            if (!entidad.getCommandTags().contains(MARCA) || !(entidad instanceof MobEntity)) {
                return ActionResult.PASS;
            }
            // Permitir a los administradores editar el NPC si se agachan (para usar la varita)
            if (jugador.isCreative() && jugador.isSneaking()) {
                return ActionResult.PASS;
            }
            if (mano != Hand.MAIN_HAND) {
                return ActionResult.SUCCESS;
            }
            if (jugador instanceof ServerPlayerEntity sp) {
                if (TAREAS.containsKey(entidad.getUuid())) {
                    var tarea = TAREAS.get(entidad.getUuid());
                    if (!tarea.jugadorId.equals(sp.getUuid())) {
                        sp.sendMessage(Text.literal("§cUn momento por favor, estoy atendiendo a otro entrenador."), true);
                        return ActionResult.SUCCESS;
                    }
                }
                
                SESION_JUGADOR.put(sp.getUuid(), entidad.getUuid());
                Red.enviarAbrirCentroPokemon(sp);
            }
            return ActionResult.SUCCESS;
        });

        // Bucle de ticks para mover a las enfermeras
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var it = TAREAS.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                UUID enfermeraId = entry.getKey();
                Tarea tarea = entry.getValue();

                MobEntity enfermera = null;
                for (var mundo : server.getWorlds()) {
                    var e = mundo.getEntity(enfermeraId);
                    if (e instanceof MobEntity mob) {
                        enfermera = mob;
                        break;
                    }
                }

                if (enfermera == null) {
                    it.remove();
                    continue;
                }

                ServerPlayerEntity jugador = server.getPlayerManager().getPlayer(tarea.jugadorId);

                switch (tarea.estado) {
                    case YENDO -> {
                        enfermera.getNavigation().startMovingTo(tarea.maquina.x, tarea.maquina.y, tarea.maquina.z, 1.0f);
                        if (enfermera.getPos().squaredDistanceTo(tarea.maquina) < 2.0) {
                            tarea.estado = Estado.CURANDO;
                            tarea.ticksCurando = 0;
                            enfermera.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, tarea.maquina.add(1, 0, 0));
                        }
                    }
                    case CURANDO -> {
                        enfermera.getNavigation().stop();
                        if (tarea.ticksCurando == 0) {
                            if (jugador != null) {
                                jugador.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1.0f, 1.0f);
                            }
                        }
                        tarea.ticksCurando++;
                        if (tarea.ticksCurando > 60) {
                            tarea.estado = Estado.VOLVIENDO;
                        }
                    }
                    case VOLVIENDO -> {
                        enfermera.getNavigation().startMovingTo(ORIGEN.x, ORIGEN.y, ORIGEN.z, 1.0f);
                        if (enfermera.getPos().squaredDistanceTo(ORIGEN) < 2.0) {
                            if (jugador != null) {
                                try {
                                    Cobblemon.INSTANCE.getStorage().getParty(jugador).heal();
                                    jugador.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 1.0f);
                                    jugador.sendMessage(Text.literal("§a¡Tus Pokémon están curados! Vuelve cuando quieras."), false);
                                } catch (Exception e) {
                                    jugador.sendMessage(Text.literal("§cHubo un problema al curar a tus Pokémon."), false);
                                }
                                SESION_JUGADOR.remove(jugador.getUuid());
                            }
                            enfermera.getNavigation().stop();
                            net.minecraft.util.math.Vec3d dir = net.minecraft.util.math.Vec3d.fromPolar(0, YAW_ORIGINAL);
                            enfermera.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, ORIGEN.add(dir));
                            it.remove();
                        }
                    }
                }
            }
        });
    }

    public static void confirmar(ServerPlayerEntity jugador) {
        UUID enfermeraId = SESION_JUGADOR.get(jugador.getUuid());
        if (enfermeraId == null) {
            jugador.sendMessage(Text.literal("§cLa enfermera ya no te está prestando atención."), false);
            return;
        }
        
        if (TAREAS.containsKey(enfermeraId)) {
            jugador.sendMessage(Text.literal("§cLa enfermera está ocupada."), false);
            return;
        }

        Vec3d maquina = MAQUINAS[jugador.getRandom().nextInt(MAQUINAS.length)];
        TAREAS.put(enfermeraId, new Tarea(jugador.getUuid(), maquina));
    }
}
