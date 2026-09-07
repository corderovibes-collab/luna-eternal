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
import net.minecraft.util.math.Box;
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
    private static final Set<UUID> enCombate = ConcurrentHashMap.newKeySet();
    
    private static final String PREFIJO_NPC = "luna_ladder_";
    private static final Map<UUID, TrainerMob> MOB_ACTUAL = new ConcurrentHashMap<>();

    // Inicia la escalera Mortal Kombat
    public static void iniciarCola(ServerPlayerEntity jugador, int modo) {
        UUID uuid = jugador.getUuid();
        if (partidasActivas.containsKey(uuid)) {
            salir(jugador);
        }

        var party = Cobblemon.INSTANCE.getStorage().getParty(jugador);

        // REGLAS ESTRICTAS DE EQUIPO:
        if (modo == 0 || modo == 1) { // 1vs1 o 2vs2
            int total = 0;
            int vivos = 0;
            if (party != null) {
                for (Pokemon p : party) {
                    if (p != null) {
                        total++;
                        if (p.getCurrentHealth() > 0) vivos++;
                    }
                }
            }
            if (total < 6) {
                jugador.sendMessage(Text.literal("§c[Torre de Batalla] ¡Debes tener tu equipo completo con 6 Pokémon en la barra!"));
                return;
            }
            if (vivos < 6) {
                jugador.sendMessage(Text.literal("§c[Torre de Batalla] ¡Todos tus 6 Pokémon deben tener vida! Cúralos antes de combatir."));
                return;
            }
        } else if (modo == 2) { // Aleatorio
            int total = 0;
            if (party != null) {
                for (Pokemon p : party) {
                    if (p != null) total++;
                }
            }
            if (total > 0) {
                jugador.sendMessage(Text.literal("§c[Torre de Batalla] ¡Tu equipo debe estar completamente vacío para el Modo Aleatorio! Guarda tus Pokémon en la PC y el sistema te asignará 6 Pokémon al azar."));
                return;
            }
            // Asignar el equipo aleatorio de 6 Pokémon de nivel 100 de inmediato
            asignarEquipoAleatorio(jugador);
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
        
        // Barrer la arena de cualquier TrainerMob previo antes de teletransportar
        Box arenaBox = new Box(cx - 20, cy - 5, cz - 20, cx + 20, cy + 20, cz + 20);
        for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, arenaBox, e -> true)) {
            m.discard();
        }
        MOB_ACTUAL.remove(uuid);

        // Teletransportar al jugador (centro)
        jugador.teleport(mundoTorre, cx + 0.5, cy + 1, cz + 2.5, 180, 0);
        
        jugador.sendMessage(Text.literal("§e¡La Torre de Batalla ha comenzado! Ronda 1."));
        
        // Empezar el bucle de rondas
        prepararRonda(jugador);
    }

    private static void asignarEquipoAleatorio(ServerPlayerEntity jugador) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(jugador);
        if (party == null) return;
        List<Pokemon> viejos = new ArrayList<>();
        for (Pokemon p : party) {
            if (p != null) viejos.add(p);
        }
        for (Pokemon p : viejos) {
            party.remove(p);
        }

        List<String> pool = new ArrayList<>(List.of(
            "charizard", "blastoise", "venusaur", "gengar", "dragonite",
            "snorlax", "lucario", "garchomp", "tyranitar", "metagross",
            "alakazam", "gyarados", "arcanine", "scizor", "salamence",
            "milotic", "togekiss", "electivire", "gardevoir", "weavile",
            "mamoswine", "gliscor", "gallade", "machamp"
        ));
        Collections.shuffle(pool);

        for (int i = 0; i < 6; i++) {
            var props = PokemonProperties.Companion.parse(pool.get(i) + " level=100");
            party.add(props.create());
        }
        jugador.sendMessage(Text.literal("§a[Torre de Batalla] ¡Has recibido un equipo sorpresa de 6 Pokémon Nivel 100!"));
    }

    private static void prepararRonda(ServerPlayerEntity jugador) {
        Partida partida = partidasActivas.get(jugador.getUuid());
        if (partida == null) return;

        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        if (mundoTorre == null) return;

        int cx = partida.arenaId() * 1000;
        int cy = 100;
        int cz = 0;

        // Limpiar el mob anterior si existe
        TrainerMob oldMob = MOB_ACTUAL.remove(jugador.getUuid());
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }

        // Barrer la arena de cualquier TrainerMob residual
        Box arenaBox = new Box(cx - 20, cy - 5, cz - 20, cx + 20, cy + 20, cz + 20);
        for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, arenaBox, e -> true)) {
            m.discard();
        }

        // Spawnear al nuevo rival
        String bossName = "Rival de Torre (Ronda " + partida.ronda() + ")";
        TrainerMob mob = spawnOpponentMob(mundoTorre, new Vec3d(cx + 0.5, cy + 1, cz - 2.5), 0f, bossName);
        if (mob != null) {
            MOB_ACTUAL.put(jugador.getUuid(), mob);
        }

        // El rival SIEMPRE tiene 6 Pokémon nivel 100
        List<PokemonModel> opponentTeam = new ArrayList<>();
        List<String> pool = new ArrayList<>(List.of(
            "charizard", "blastoise", "venusaur", "gengar", "dragonite",
            "snorlax", "lucario", "garchomp", "tyranitar", "metagross",
            "alakazam", "gyarados", "arcanine", "scizor", "salamence",
            "milotic", "togekiss", "electivire", "gardevoir", "weavile",
            "mamoswine", "gliscor", "gallade", "machamp"
        ));
        Collections.shuffle(pool);
        for (int i = 0; i < 6; i++) {
            opponentTeam.add(buildModel(pool.get(i)));
        }

        // Esperamos 2 segundos antes de iniciar el combate para que el jugador vea al mob
        net.pokereport.luna.gym.Programador.en(40, () -> {
            if (partidasActivas.containsKey(jugador.getUuid())) {
                startLadderBattle(jugador, mob, bossName, Math.min(5, partida.ronda() / 2), opponentTeam);
            }
        });
    }

    private static void victoria(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        if (!enCombate.remove(uuid)) {
            return; // Ya procesado, evitar ejecución duplicada
        }

        Partida partida = partidasActivas.get(uuid);
        if (partida == null) return;
        
        // Limpiar INMEDIATAMENTE el mob derrotado de la arena
        TrainerMob oldMob = MOB_ACTUAL.remove(uuid);
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }
        try {
            ModCommon.RCT.getTrainerRegistry().unregisterById(PREFIJO_NPC + uuid);
            RCTMod.getInstance().getTrainerManager().removeBattle(uuid);
        } catch (Exception ignored) {}

        jugador.sendMessage(Text.literal("§a¡Has superado la Ronda " + partida.ronda() + "!"));
        
        // Curar equipo (usando el Party)
        var party = Cobblemon.INSTANCE.getStorage().getParty(jugador);
        if (party != null) party.heal();

        // Avanzar ronda y actualizar record actual
        Partida nueva = partida.avanzar();
        partidasActivas.put(uuid, nueva);
        TorreRanking.actualizarRonda(jugador.getServer(), jugador.getName().getString(), nueva.ronda() - 1);
        
        // Iniciar la siguiente ronda tras 2 segundos
        net.pokereport.luna.gym.Programador.en(40, () -> {
            if (partidasActivas.containsKey(uuid)) {
                prepararRonda(jugador);
            }
        });
    }

    private static void derrota(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        if (!enCombate.remove(uuid)) {
            return;
        }

        Partida partida = partidasActivas.get(uuid);
        if (partida == null) return;
        
        jugador.sendMessage(Text.literal("§cHas caído en la Ronda " + partida.ronda() + ". Fin de tu intento."));
        TorreRanking.actualizarRonda(jugador.getServer(), jugador.getName().getString(), partida.ronda() - 1);
        salir(jugador);
    }

    public static void salir(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        enCombate.remove(uuid);
        Partida partida = partidasActivas.remove(uuid);
        if (partida != null) {
            arenasOcupadas.put(partida.arenaId(), false);
            // Si el modo era Aleatorio, retirar los 6 Pokémon prestados
            if (partida.modo() == 2) {
                var party = Cobblemon.INSTANCE.getStorage().getParty(jugador);
                if (party != null) {
                    List<Pokemon> viejos = new ArrayList<>();
                    for (Pokemon p : party) {
                        if (p != null) viejos.add(p);
                    }
                    for (Pokemon p : viejos) {
                        party.remove(p);
                    }
                }
                jugador.sendMessage(Text.literal("§e[Torre de Batalla] El equipo aleatorio prestado ha sido retirado."));
            }
            ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
            if (mundoTorre != null) {
                int cx = partida.arenaId() * 1000;
                int cy = 100;
                int cz = 0;
                Box arenaBox = new Box(cx - 20, cy - 5, cz - 20, cx + 20, cy + 20, cz + 20);
                for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, arenaBox, e -> true)) {
                    m.discard();
                }
            }
        }
        
        // Limpiar arena mob
        TrainerMob oldMob = MOB_ACTUAL.remove(uuid);
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }

        try {
            ModCommon.RCT.getTrainerRegistry().unregisterById(PREFIJO_NPC + uuid);
            RCTMod.getInstance().getTrainerManager().removeBattle(uuid);
        } catch (Exception ignored) {}

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
        mob.setPersistent(false);
        mob.setInvulnerable(true);
        mob.setSilent(true);

        world.spawnEntity(mob);
        return mob;
    }

    private static boolean startLadderBattle(
            ServerPlayerEntity player, TrainerMob opponentMob, String opponentName,
            int aiSkill, List<PokemonModel> opponentTeam) {
        
        UUID uuid = player.getUuid();
        String npcId = PREFIJO_NPC + uuid;
        var registry = ModCommon.RCT.getTrainerRegistry();

        try {
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

            var base = (partidasActivas.get(uuid).modo() == 1)
                    ? com.gitlab.srcmc.rctapi.api.battle.BattleFormat.GEN_9_DOUBLES.getCobblemonBattleFormat()
                    : com.gitlab.srcmc.rctapi.api.battle.BattleFormat.GEN_9_SINGLES.getCobblemonBattleFormat();

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
                LunaEternal.LOG.error("Torre de Batalla: startBattle devolvió null para {}", player.getName().getString());
                salir(player);
                return false;
            }

            LunaEternal.LOG.info("Torre de Batalla: combate iniciado con éxito para {} (ronda {})",
                    player.getName().getString(), partidasActivas.get(uuid).ronda());
            RCTMod.getInstance().getTrainerManager().addBattle(player, opponentMob);
            enCombate.add(player.getUuid());
            return true;
        } catch (Exception e) {
            LunaEternal.LOG.error("Torre de Batalla: error iniciando combate de escalera", e);
            salir(player);
            return false;
        }
    }

    public static void registrarEventos() {
        // Escuchar final de combate
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            Set<UUID> ganadores = new HashSet<>();
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor : event.getWinners()) {
                for (UUID u : actor.getPlayerUUIDs()) {
                    ganadores.add(u);
                }
            }
            for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                UUID u = p.getUuid();
                if (partidasActivas.containsKey(u)) {
                    if (ganadores.contains(u)) {
                        victoria(p);
                    } else {
                        derrota(p);
                    }
                }
            }
        });
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, event -> {
            for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                if (partidasActivas.containsKey(p.getUuid())) {
                    derrota(p);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            salir(handler.getPlayer());
        });
    }

    private static PokemonModel buildModel(String species) {
        Pokemon p = PokemonProperties.Companion.parse(species + " level=100").create();
        return new PokemonModel(p);
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