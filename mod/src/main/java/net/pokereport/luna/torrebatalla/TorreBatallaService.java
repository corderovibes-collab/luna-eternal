package net.pokereport.luna.torrebatalla;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.battle.BattleRules;
import com.gitlab.srcmc.rctapi.api.models.PokemonModel;
import com.gitlab.srcmc.rctapi.api.models.TrainerModel;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;
import com.gitlab.srcmc.rctapi.api.util.JTO;
import com.gitlab.srcmc.rctmod.ModCommon;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.TravelService;

public class TorreBatallaService {
    
    // Estado de la partida para cada jugador
    public record Partida(int arenaId, int modo, int ronda) {
        public Partida avanzar() { return new Partida(arenaId, modo, ronda + 1); }
    }

    private static final ConcurrentHashMap<UUID, Partida> partidasActivas = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> arenasOcupadas = new ConcurrentHashMap<>();
    
    private static final String PREFIJO_NPC = "luna_ladder_";
    private static final Map<UUID, List<Pokemon>> PARTY_BACKUPS = new ConcurrentHashMap<>();
    private static final Map<UUID, TrainerMob> MOB_ACTUAL = new ConcurrentHashMap<>();

    // Inicia la escalera Mortal Kombat
    public static void iniciarCola(ServerPlayerEntity jugador, int modo) {
        UUID uuid = jugador.getUuid();
        if (partidasActivas.containsKey(uuid)) {
            salir(jugador);
        }

        // Encontrar una arena libre
        int arenaId = 0;
        while (arenasOcupadas.containsKey(arenaId) && arenasOcupadas.get(arenaId)) {
            arenaId++;
        }
        
        arenasOcupadas.put(arenaId, true);
        partidasActivas.put(uuid, new Partida(arenaId, modo, 1));
        
        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        if (mundoTorre == null) return;

        int cx = arenaId * 1000;
        int cy = 100;
        int cz = 0;

        construirBase(mundoTorre, cx, cy, cz);
        
        // Teletransportar al jugador (centro)
        jugador.teleport(mundoTorre, cx + 0.5, cy + 1, cz + 2.5, 180, 0);
        
        jugador.sendMessage(Text.literal("§e¡La Torre de Batalla ha comenzado! Ronda 1."));
        
        // Empezar el bucle de rondas
        prepararRonda(jugador);
    }

    private static void prepararRonda(ServerPlayerEntity jugador) {
        Partida partida = partidasActivas.get(jugador.getUuid());
        if (partida == null) return;

        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        int cx = partida.arenaId() * 1000;
        int cy = 100;
        int cz = 0;

        // Limpiar el mob anterior si existe
        TrainerMob oldMob = MOB_ACTUAL.remove(jugador.getUuid());
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }

        // Spawnear al nuevo rival
        String bossName = "Rival de Torre (Ronda " + partida.ronda() + ")";
        TrainerMob mob = spawnOpponentMob(mundoTorre, new Vec3d(cx + 0.5, cy + 1, cz - 2.5), 0f, bossName);
        if (mob != null) {
            MOB_ACTUAL.put(jugador.getUuid(), mob);
        }

        // Generar el equipo rival
        int count = (partida.modo() == 1) ? 2 : 1; // 2vs2 son 2 pokemons (esto lo refinaremos)
        if (partida.modo() == 0 || partida.modo() == 2) count = 1; // 1 pokemon por ronda al principio

        // TODO: Escalar la dificultad de los pokemon en base a la ronda
        List<PokemonModel> opponentTeam = new ArrayList<>();
        List<String> pool = List.of("charizard", "blastoise", "venusaur", "gengar", "machamp", "alakazam", "snorlax", "dragonite");
        String p1 = pool.get(new Random().nextInt(pool.size()));
        opponentTeam.add(buildModel(p1, Set.of("tackle"), ""));
        
        if (partida.modo() == 1) { // 2vs2 (Doble)
            String p2 = pool.get(new Random().nextInt(pool.size()));
            opponentTeam.add(buildModel(p2, Set.of("tackle"), ""));
        }

        boolean esAleatorio = (partida.modo() == 2);
        
        // Esperamos 2 segundos antes de iniciar el combate para que el jugador vea al mob
        net.pokereport.luna.gym.Programador.en(40, () -> {
            if (partidasActivas.containsKey(jugador.getUuid())) {
                startLadderBattle(jugador, mob, bossName, Math.min(5, partida.ronda() / 2), opponentTeam, esAleatorio);
            }
        });
    }

    private static void victoria(ServerPlayerEntity jugador) {
        Partida partida = partidasActivas.get(jugador.getUuid());
        if (partida == null) return;
        
        jugador.sendMessage(Text.literal("§a¡Has superado la Ronda " + partida.ronda() + "!"));
        
        // Curar equipo (usando el Party)
        var party = Cobblemon.INSTANCE.getStorage().getParty(jugador);
        if (party != null) party.heal();

        // Avanzar ronda
        partidasActivas.put(jugador.getUuid(), partida.avanzar());
        
        // Iniciar la siguiente
        prepararRonda(jugador);
    }

    private static void derrota(ServerPlayerEntity jugador) {
        Partida partida = partidasActivas.get(jugador.getUuid());
        if (partida == null) return;
        
        jugador.sendMessage(Text.literal("§cHas caído en la Ronda " + partida.ronda() + ". Fin de tu intento."));
        salir(jugador);
    }

    public static void salir(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        Partida partida = partidasActivas.remove(uuid);
        if (partida != null) {
            arenasOcupadas.put(partida.arenaId(), false);
        }
        
        cleanup(jugador);
        
        // Limpiar arena mob
        TrainerMob oldMob = MOB_ACTUAL.remove(jugador.getUuid());
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }

        // Devolver a la ciudadela si sigue en la torre
        if (jugador.getWorld().getRegistryKey().equals(LunaDimensions.TORRE)) {
            TravelService.travel(jugador, LunaDimensions.CIUDADELA, "la Ciudadela");
        }
    }

    private static TrainerMob spawnOpponentMob(ServerWorld world, Vec3d pos, float yaw, String name) {
        TrainerMob mob = TrainerMob.getEntityType().create(world);
        if (mob == null) return null;

        mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        mob.setHeadYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setCustomName(Text.literal(name));
        mob.setCustomNameVisible(true);
        mob.setAiDisabled(true);
        mob.setPersistent(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);

        world.spawnEntity(mob);
        return mob;
    }

    private static boolean startLadderBattle(
            ServerPlayerEntity player, TrainerMob opponentMob, String opponentName,
            int aiSkill, List<PokemonModel> opponentTeam, boolean isRandomMode) {
        
        UUID uuid = player.getUuid();
        String npcId = PREFIJO_NPC + uuid;
        var registry = ModCommon.RCT.getTrainerRegistry();

        try {
            if (isRandomMode) {
                applyRandomDraftTeam(player, 6);
            }

            registry.unregisterById(npcId);

            TrainerModel model = new TrainerModel(
                    opponentName,
                    JTO.of(() -> new StrongBattleAI(aiSkill)),
                    new ArrayList<>(), opponentTeam
            );

            TrainerNPC npc = registry.registerNPC(npcId, model);
            if (opponentMob != null) npc.setEntity(opponentMob);

            TrainerPlayer playerTrainer = registry.getById(
                    RCTMod.getInstance().getTrainerManager().getTrainerId(player), TrainerPlayer.class);
            
            if (playerTrainer == null) {
                salir(player);
                return false;
            }

            var base = com.gitlab.srcmc.rctapi.api.battle.BattleFormat.GEN_9_SINGLES.getCobblemonBattleFormat();
            if (opponentTeam.size() > 1 && partidasActivas.get(uuid).modo() == 1) { // 2vs2 (Doble)
                base = com.gitlab.srcmc.rctapi.api.battle.BattleFormat.GEN_9_DOUBLES.getCobblemonBattleFormat();
            }

            BattleFormat format100 = new BattleFormat(
                    base.getMod(), base.getBattleType(), new HashSet<>(base.getRuleSet()),
                    base.getGen(), 100);

            BattleRules rules = new BattleRules.Builder()
                    .withAdjustPlayerLevels(true)
                    .withAdjustNPCLevels(true)
                    .withHealPlayers(true)
                    .build();

            UUID battleId = ModCommon.RCT.getBattleManager().startBattle(
                    List.of(playerTrainer), List.of(npc), () -> format100, rules);

            if (battleId == null) {
                salir(player);
                return false;
            }

            RCTMod.getInstance().getTrainerManager().addBattle(player, opponentMob);
            return true;
        } catch (Exception e) {
            LunaEternal.LOG.error("Error starting ladder battle", e);
            salir(player);
            return false;
        }
    }

    private static void applyRandomDraftTeam(ServerPlayerEntity player, int count) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        UUID uuid = player.getUuid();

        List<Pokemon> backup = new ArrayList<>();
        for (Pokemon p : party) {
            if (p != null) backup.add(p);
        }
        PARTY_BACKUPS.put(uuid, backup);

        for (int i = 0; i < 6; i++) party.set(i, null);

        List<String> pool = new ArrayList<>(List.of("gengar", "lucario", "dragonite", "garchomp", "tyranitar", "metagross", "scizor", "gardevoir"));
        Collections.shuffle(pool);

        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            var props = PokemonProperties.Companion.parse(pool.get(i) + " level=100");
            party.add(props.create());
        }
    }

    private static void cleanup(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();

        List<Pokemon> backup = PARTY_BACKUPS.remove(uuid);
        if (backup != null) {
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (int i = 0; i < 6; i++) party.set(i, null);
            for (Pokemon original : backup) party.add(original);
        }

        try { ModCommon.RCT.getTrainerRegistry().unregisterById(PREFIJO_NPC + uuid); } catch (Exception ignored) {}
    }

    public static void registrarEventos() {
        // Escuchar final de combate
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            var server = net.minecraft.server.MinecraftServer.class; // just to make sure we don't use it directly if we don't have it
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor : event.getWinners()) {
                for (UUID u : actor.getPlayerUUIDs()) {
                    if (partidasActivas.containsKey(u)) {
                        // find player
                        for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                            if (p.getUuid().equals(u)) victoria(p);
                        }
                    }
                }
            }
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor : event.getLosers()) {
                for (UUID u : actor.getPlayerUUIDs()) {
                    if (partidasActivas.containsKey(u)) {
                        for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                            if (p.getUuid().equals(u)) derrota(p);
                        }
                    }
                }
            }
        });
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, event -> {
            for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                if (partidasActivas.containsKey(p.getUuid())) derrota(p);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            salir(handler.getPlayer());
        });
    }

    private static PokemonModel buildModel(String species, Set<String> moves, String heldItem) {
        var ivs = new PokemonModel.StatsModel(31, 31, 31, 31, 31, 31);
        var evs = new PokemonModel.StatsModel(252, 0, 0, 252, 4, 0);
        return new PokemonModel(species, null, 100, "jolly", "", new LinkedHashSet<>(moves), ivs, evs, false, heldItem, Set.of());
    }

    private static void construirBase(ServerWorld mundo, int cx, int cy, int cz) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos pos = new BlockPos(cx + dx, cy, cz + dz);
                if (mundo.isAir(pos)) {
                    mundo.setBlockState(pos, Blocks.SMOOTH_QUARTZ.getDefaultState());
                }
            }
        }
    }
}