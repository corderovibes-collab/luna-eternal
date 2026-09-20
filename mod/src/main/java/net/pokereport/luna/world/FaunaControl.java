package net.pokereport.luna.world;

import com.cobblemon.mod.common.PlayerSpawnerAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.pokereport.luna.LunaEternal;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public final class FaunaControl {
    private static final Queue<Entity> pendientes = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> enCola = ConcurrentHashMap.newKeySet();
    private static final Map<UUID,String> dimensiones = new HashMap<>();
    private static ThreadPoolExecutor archivos;
    private static volatile boolean activo;
    private static long retirados;
    private FaunaControl() {}

    public static boolean esHogarOSalvaje(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim) {
        return LunaDimensions.HOGAR.equals(dim) || LunaDimensions.SALVAJES.contains(dim);
    }

    public static boolean bloquear(Entity e) {
        if (e instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
            var p = pe.getPokemon();
            if (p != null && p.getOwnerUUID() == null && p.getOwnerNPC() == null) {
                var dim = e.getWorld().getRegistryKey();
                if (esHogarOSalvaje(dim)) {
                    return !pokemonPermitido(p, dim.getValue().toString());
                }
            }
            return false;
        }
        if (!(e instanceof MobEntity mob)) return false;
        var id = Registries.ENTITY_TYPE.getId(e.getType());
        boolean personalizado = e.getCommandTags().stream().anyMatch(t -> t.startsWith("luna_") || t.equals("custom_npc"))
                || (e.hasCustomName() && mob.isAiDisabled());
        boolean entrenador = id.getNamespace().equals("rctmod")
                && (id.getPath().equals("trainer") || id.getPath().equals("trainer_association"));
        return PoliticaFauna.bloquear(id.getNamespace(),true,personalizado,entrenador,mob.isPersistent());
    }
    public static void registrar() {
        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(event -> {
            var pe = event.getEntity();
            if (pe == null) return;
            var p = pe.getPokemon();
            if (p != null && p.getOwnerUUID() == null && p.getOwnerNPC() == null) {
                var dim = pe.getWorld().getRegistryKey();
                if (esHogarOSalvaje(dim)) {
                    if (!pokemonPermitido(p, dim.getValue().toString())) {
                        event.cancel();
                    }
                }
            }
        });
        // Un aumento deliberadamente pequeño (+10 %) del bucket ultrarraro
        // solo en Salvaje. Con la base actual pasa de peso 0.30 a 0.33: sigue
        // siendo excepcional y no convierte legendarios en fauna ordinaria.
        com.cobblemon.mod.common.api.events.CobblemonEvents.SPAWN_BUCKET_CHOSEN.subscribe(event -> {
            var cause = event.getSpawnCause();
            if (cause == null || cause.getEntityWorldId() == null) return;
            String dimension = cause.getEntityWorldId().getValue().toString();
            if (!PoliticaFauna.esSalvaje(dimension)) return;
            var weights = event.getBucketWeights();
            Float ultra = weights.get("ultra-rare");
            if (ultra != null && ultra > 0f) {
                weights.put("ultra-rare", ultra * PoliticaFauna.MULTIPLICADOR_ULTRARRARO_SALVAJE);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((e,w) -> {
            if (e instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
                var p = pe.getPokemon();
                if (p != null && p.getOwnerUUID() == null && p.getOwnerNPC() == null) {
                    var dim = w.getRegistryKey();
                    if (esHogarOSalvaje(dim)) {
                        if (!pokemonPermitido(p, dim.getValue().toString())) {
                            pe.discard();
                            return;
                        }
                    }
                }
            }
            if (bloquear(e) && enCola.add(e.getUuid())) pendientes.add(e);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            archivos = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r -> {
                Thread t = new Thread(r,"luna-fauna-archive");t.setDaemon(true);return t;
            },new ThreadPoolExecutor.AbortPolicy());
            activo = true;
            LunaEternal.LOG.info("Fauna configurada: vanilla bloqueada, NPC protegidos, Hogar=0.04 y Salvaje=3.0 Pokemon/chunk (Gen 1-2 estrictas)");
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!activo) return;
            boolean tickPeriodico = server.getTicks() % 20 == 0;
            for (var p : server.getPlayerManager().getPlayerList()) {
                String mundo = p.getServerWorld().getRegistryKey().getValue().toString();
                boolean cambio = !mundo.equals(dimensiones.put(p.getUuid(), mundo));
                if (cambio || tickPeriodico) {
                    var spawner = ((PlayerSpawnerAccessor)p).getPlayerSpawner();
                    spawner.setMaxPokemonPerChunk(PoliticaFauna.densidad(mundo));
                    spawner.setTicksBetweenSpawns(LunaDimensions.HOGAR.equals(p.getServerWorld().getRegistryKey()) ? 200f : 15f);
                }
            }
        });
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            if (!activo) return;
            var dim = world.getRegistryKey();
            if (!esHogarOSalvaje(dim)) return;
            if (world.getTime() % 40 != 0) return;

            List<Entity> descartar = null;
            for (var entity : world.iterateEntities()) {
                if (entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
                    var p = pe.getPokemon();
                    if (p != null && p.getOwnerUUID() == null && p.getOwnerNPC() == null) {
                        int dex = p.getSpecies().getNationalPokedexNumber();
                        if (!pokemonPermitido(p, dim.getValue().toString())) {
                            if (descartar == null) descartar = new ArrayList<>();
                            descartar.add(pe);
                        }
                    }
                }
            }
            if (descartar != null) {
                for (var e : descartar) {
                    e.discard();
                }
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s) -> dimensiones.remove(h.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!activo) return;
            for (int i=0;i<8 && !pendientes.isEmpty() && archivos.getQueue().remainingCapacity()>0;i++) {
                Entity e=pendientes.poll();
                if(e==null) break;
                if(e.isRemoved() || !bloquear(e)) {enCola.remove(e.getUuid());continue;}
                // Archivar antes de retirar: no hay /kill masivo, botín ni pérdida sin respaldo.
                NbtCompound copia=new NbtCompound(),datos=new NbtCompound();
                e.writeNbt(datos);datos.putString("id",Registries.ENTITY_TYPE.getId(e.getType()).toString());
                copia.put("entity",datos);
                copia.putString("dimension",e.getWorld().getRegistryKey().getValue().toString());
                var carpeta=server.getSavePath(WorldSavePath.ROOT).resolve("lunaeternal/fauna-archive");
                try {
                    archivos.execute(() -> {
                        try {
                            Files.createDirectories(carpeta);
                            var archivo=carpeta.resolve(e.getUuid()+".nbt");
                            if(!Files.exists(archivo)) {
                                var temporal=carpeta.resolve(e.getUuid()+".tmp");
                                NbtIo.writeCompressed(copia,temporal);
                                try {Files.move(temporal,archivo,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
                                catch(java.nio.file.AtomicMoveNotSupportedException ex) {Files.move(temporal,archivo);}
                            }
                            server.execute(() -> {
                                try {
                                    if(activo && !e.isRemoved() && bloquear(e)) {
                                        e.discard();retirados++;
                                        if(retirados==1 || retirados%100==0) LunaEternal.LOG.info("Fauna: {} entidades ambientales retiradas con respaldo NBT",retirados);
                                    }
                                } finally {enCola.remove(e.getUuid());}
                            });
                        } catch(Exception error) {
                            enCola.remove(e.getUuid());
                            LunaEternal.LOG.error("Fauna: no se retira {} porque falló su respaldo",e.getUuid(),error);
                        }
                    });
                } catch(RejectedExecutionException error) {pendientes.add(e);break;}
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            activo=false;if(archivos!=null)archivos.shutdown();
            pendientes.clear();enCola.clear();dimensiones.clear();
        });
    }

    private static boolean pokemonPermitido(com.cobblemon.mod.common.pokemon.Pokemon pokemon,
                                             String dimension) {
        return PoliticaFauna.permitirPokemon(
                dimension,
                pokemon.getSpecies().getNationalPokedexNumber(),
                pokemon.getShiny(),
                pokemon.getSpecies().getLabels());
    }
}
