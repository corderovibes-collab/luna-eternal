package net.pokereport.luna.torrebatalla;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.pokedex.ClaveEspecie;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.TravelService;

public class TorreBatallaService {
    
    // Estado de la partida para cada jugador
    public record Partida(int arenaId, int modo, int ronda) {
        public Partida avanzar() { return new Partida(arenaId, modo, ronda + 1); }
    }

    private static final ConcurrentHashMap<UUID, Partida> partidasActivas = new ConcurrentHashMap<>();
    private static final Set<UUID> enCombate = ConcurrentHashMap.newKeySet();
    
    private static final String PREFIJO_NPC = "luna_ladder_";
    private static final Map<UUID, TrainerMob> MOB_ACTUAL = new ConcurrentHashMap<>();

    // Coordenadas fijas de la plataforma mapeada por el usuario
    public static final Vec3d POS_JUGADOR = new Vec3d(50.489, 117.0, 62.56);
    public static final float YAW_JUGADOR = 180.0f; // Mira al norte (-Z)

    public static final Vec3d POS_NPC = new Vec3d(50.48, 117.0, 38.51);
    public static final float YAW_NPC = 0.0f; // Mira al sur (+Z)

    public static final BlockPos POS_BLOCK_JUGADOR_STAND = new BlockPos(50, 116, 62);
    public static final BlockPos POS_BLOCK_NPC_STAND = new BlockPos(50, 116, 38);
    public static final BlockPos POS_BLOCK_JUGADOR_POKEMON = new BlockPos(50, 115, 54);
    public static final BlockPos POS_BLOCK_NPC_POKEMON = new BlockPos(50, 115, 46);

    public static final Box ARENA_BOX = new Box(20, 100, 10, 80, 140, 90);

    private static final List<String> SKINS_RIVALES = List.of(
        "kanto_brock", "kanto_misty", "kanto_ltsurge", "kanto_erika", "kanto_koga",
        "kanto_sabrina", "kanto_blaine", "kanto_giovanni", "kanto_champion_blue",
        "kanto_league_lorelei", "kanto_league_bruno", "kanto_league_agatha", "kanto_league_lance",
        "johto_valerio", "johto_chiara", "johto_angelo", "johto_furio", "johto_jasmine",
        "johto_alfredo", "johto_sandra", "johto_champion_lance", "johto_league_karen",
        "johto_league_pino", "johto_raffaello",
        "hoenn_petra", "hoenn_fiammetta", "hoenn_norman", "hoenn_alice", "hoenn_adriano",
        "hoenn_champion_rocco", "hoenn_rudi", "hoenn_walter", "hoenn_lyris", "hoenn_pat",
        "hoenn_tell", "hoenn_league_drake", "hoenn_league_ester", "hoenn_league_fosco", "hoenn_league_frida",
        "sinnoh_pedro", "sinnoh_marzia", "sinnoh_fannie", "sinnoh_corrado", "sinnoh_champion_camilla",
        "sinnoh_bianca", "sinnoh_buck", "sinnoh_elfio", "sinnoh_ferruccio", "sinnoh_gardenia",
        "sinnoh_league_aaron", "sinnoh_league_luciano", "sinnoh_league_terrie", "sinnoh_league_vulcano", "sinnoh_omar",
        "team_rocket_duo_james", "team_rocket_duo_jessie", "team_rocket_admin_apollo", "team_rocket_admin_archer",
        "team_rocket_admin_atena", "team_rocket_agent_01", "team_rocket_member_01", "team_rocket_officer_01",
        "team_rocket_scientist_01", "team_galactic_grunt_01", "team_galactic_scientist_01",
        "team_galactic_commander_mars", "team_galactic_commander_jupiter", "team_galactic_commander_saturn",
        "pallet_ash", "rival_red", "old_sage", "hisui_damon", "hisui_perula", "team_aqua_ivan", "team_magma_max"
    );

    private static final List<String> TITULOS_ENTRENADOR = List.of(
        "Entrenador", "Entrenadora", "Joven", "Chica", "Líder", "Veterano", "Veterana",
        "Campista", "Karateka", "Experto", "Experta", "Aristócrata", "Científico", "Científica",
        "Montañero", "Nadador", "Nadadora", "Excursionista", "As de la Torre", "Guardián", "Guardiana"
    );

    private static final List<String> NOMBRES_ENTRENADOR = List.of(
        "Carlos", "Mateo", "Lucas", "Marcos", "Alejandro", "Sofía", "Valentina", "David",
        "Diego", "Gonzalo", "Fernando", "Andrés", "Camila", "Lucía", "Elena", "Javier",
        "Pablo", "Adrián", "Hugo", "Daniel", "Martina", "Paula", "Valeria", "Sara",
        "Manuel", "Joaquín", "Gabriel", "Rodrigo", "Samuel", "Ignacio", "Esteban", "Clara"
    );

    private static String generarNombreRival(int ronda) {
        var rng = ThreadLocalRandom.current();
        String titulo = TITULOS_ENTRENADOR.get(rng.nextInt(TITULOS_ENTRENADOR.size()));
        String nombre = NOMBRES_ENTRENADOR.get(rng.nextInt(NOMBRES_ENTRENADOR.size()));
        return titulo + " " + nombre;
    }

    private static String elegirSkinRival() {
        var rng = ThreadLocalRandom.current();
        return SKINS_RIVALES.get(rng.nextInt(SKINS_RIVALES.size()));
    }

    private static volatile List<String> POOL_GEN1_2 = null;

    public static List<String> obtenerEspeciesGen1y2() {
        if (POOL_GEN1_2 != null && !POOL_GEN1_2.isEmpty()) {
            return POOL_GEN1_2;
        }
        List<String> pool = new ArrayList<>();
        try {
            for (var s : PokemonSpecies.getImplemented()) {
                int dex = s.getNationalPokedexNumber();
                if (dex >= 1 && dex <= 251) {
                    boolean prohibido = false;
                    try {
                        for (String tag : s.getLabels()) {
                            String t = tag.toLowerCase();
                            if (t.equals("mythical") || t.equals("restricted") || t.equals("ultra_beast") || t.equals("paradox")) {
                                prohibido = true;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (!prohibido) {
                        String id = ClaveEspecie.de(s);
                        if (!id.isBlank() && !pool.contains(id)) {
                            pool.add(id);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("Torre de Batalla: error al consultar especies Gen 1 y 2", t);
        }

        if (pool.isEmpty()) {
            pool.addAll(List.of(
                "bulbasaur", "ivysaur", "venusaur", "charmander", "charmeleon", "charizard",
                "squirtle", "wartortle", "blastoise", "butterfree", "beedrill", "pidgeot",
                "raticate", "fearow", "arbok", "raichu", "sandslash", "nidoqueen", "nidoking",
                "clefable", "ninetales", "wigglytuff", "golbat", "vileplume", "parasect",
                "venomoth", "dugtrio", "persian", "golduck", "primeape", "arcanine", "poliwrath",
                "alakazam", "machamp", "victreebel", "tentacruel", "golem", "rapidash",
                "slowbro", "magneton", "farfetchd", "dodrio", "dewgong", "muk", "cloyster",
                "gengar", "onix", "hypno", "kingler", "electrode", "exeggutor", "marowak",
                "hitmonlee", "hitmonchan", "weezing", "rhydon", "chansey", "tangela", "kangaskhan",
                "seadra", "seaking", "starmie", "mr_mime", "scyther", "jynx", "electabuzz",
                "magmar", "pinsir", "tauros", "gyarados", "lapras", "ditto", "eevee", "vaporeon",
                "jolteon", "flareon", "porygon", "omastar", "kabutops", "aerodactyl", "snorlax",
                "dragonite", "meganium", "typhlosion", "feraligatr", "furret", "noctowl",
                "ledian", "ariados", "crobat", "lanturn", "xatu", "ampharos", "bellossom",
                "azumarill", "sudowoodo", "politoed", "jumpluff", "aipom", "sunflora", "yanma",
                "quagsire", "espeon", "umbreon", "slowking", "misdreavus", "wobbuffet",
                "girafarig", "forretress", "dunsparce", "gligar", "steelix", "granbull",
                "qwilfish", "scizor", "shuckle", "heracross", "sneasel", "teddiursa", "ursaring",
                "magcargo", "piloswine", "corsola", "octillery", "mantine", "skarmory",
                "houndoom", "kingdra", "donphan", "porygon2", "stantler", "smeargle", "hitmontop",
                "miltank", "blissey", "tyranitar"
            ));
        }

        POOL_GEN1_2 = Collections.unmodifiableList(pool);
        LunaEternal.LOG.info("Torre de Batalla: cargadas {} especies de Gen 1 y Gen 2", pool.size());
        return pool;
    }

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

        if (!partidasActivas.isEmpty()) {
            jugador.sendMessage(Text.literal("§c[Torre de Batalla] La arena está ocupada por otro entrenador en este momento. Por favor espera a que termine su desafío."));
            return;
        }

        partidasActivas.put(uuid, new Partida(0, modo, 1));
        
        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        if (mundoTorre == null) return;

        asegurarBloquesArena(mundoTorre);
        
        // Barrer la arena de cualquier TrainerMob previo antes de teletransportar
        for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, ARENA_BOX, e -> true)) {
            m.discard();
        }
        MOB_ACTUAL.remove(uuid);

        // Teletransportar al jugador a su posición fija en la plataforma mirando al norte
        jugador.teleport(mundoTorre, POS_JUGADOR.x, POS_JUGADOR.y, POS_JUGADOR.z, YAW_JUGADOR, 0f);
        
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

        List<String> pool = new ArrayList<>(obtenerEspeciesGen1y2());
        Collections.shuffle(pool);

        for (int i = 0; i < 6; i++) {
            var props = PokemonProperties.Companion.parse(pool.get(i) + " level=100");
            Pokemon poke = props.create();
            poke.getPersistentData().putBoolean(TorreReglas.TAG_TORRE, true);
            party.add(poke);
        }
        jugador.sendMessage(Text.literal("§a[Torre de Batalla] ¡Has recibido un equipo aleatorio de 6 Pokémon (Gen 1 y 2) Nivel 100!"));
    }

    private static void prepararRonda(ServerPlayerEntity jugador) {
        Partida partida = partidasActivas.get(jugador.getUuid());
        if (partida == null) return;

        ServerWorld mundoTorre = jugador.getServer().getWorld(LunaDimensions.TORRE);
        if (mundoTorre == null) return;

        // Limpiar el mob anterior si existe
        TrainerMob oldMob = MOB_ACTUAL.remove(jugador.getUuid());
        if (oldMob != null && !oldMob.isRemoved()) {
            oldMob.discard();
        }

        // Barrer la arena de cualquier TrainerMob residual
        for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, ARENA_BOX, e -> true)) {
            m.discard();
        }

        // Rival con nombre propio y skin único por ronda
        String nombreBase = generarNombreRival(partida.ronda());
        String bossDisplayName = "§e" + nombreBase + " §7(Ronda " + partida.ronda() + ")";
        String bossTrainerName = nombreBase + " (Ronda " + partida.ronda() + ")";
        String skinId = elegirSkinRival();

        TrainerMob mob = spawnOpponentMob(mundoTorre, POS_NPC, YAW_NPC, bossDisplayName, skinId);
        if (mob != null) {
            MOB_ACTUAL.put(jugador.getUuid(), mob);
        }

        // El rival SIEMPRE tiene 6 Pokémon nivel 100 de Gen 1 y Gen 2
        List<PokemonModel> opponentTeam = new ArrayList<>();
        List<String> pool = new ArrayList<>(obtenerEspeciesGen1y2());
        Collections.shuffle(pool);
        for (int i = 0; i < 6; i++) {
            opponentTeam.add(buildModel(pool.get(i)));
        }

        // Esperamos 2 segundos antes de iniciar el combate para que el jugador vea al mob
        net.pokereport.luna.gym.Programador.en(40, () -> {
            if (partidasActivas.containsKey(jugador.getUuid())) {
                startLadderBattle(jugador, mob, bossTrainerName, Math.min(5, partida.ronda() / 2), opponentTeam);
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
        String modoKeyVictoria = TorreRanking.modoDe(nueva.modo());
        TorreRanking.actualizarRonda(jugador.getServer(), modoKeyVictoria, jugador.getName().getString(), nueva.ronda() - 1);
        
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
        String modoKeyDerrota = TorreRanking.modoDe(partida.modo());
        TorreRanking.actualizarRonda(jugador.getServer(), modoKeyDerrota, jugador.getName().getString(), partida.ronda() - 1);
        salir(jugador);
    }

    public static void salir(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        enCombate.remove(uuid);
        Partida partida = partidasActivas.remove(uuid);
        if (partida != null) {
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
                for (TrainerMob m : mundoTorre.getEntitiesByClass(TrainerMob.class, ARENA_BOX, e -> true)) {
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

    private static TrainerMob spawnOpponentMob(ServerWorld world, Vec3d pos, float yaw, String name, String skinId) {
        TrainerMob mob = TrainerMob.getEntityType().create(world);
        if (mob == null) return null;

        mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        mob.setHeadYaw(yaw);
        mob.setBodyYaw(yaw);
        if (skinId != null && !skinId.isBlank()) {
            mob.setTrainerId(skinId);
        }
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
        p.getPersistentData().putBoolean(TorreReglas.TAG_TORRE, true);
        return new PokemonModel(p);
    }

    public static void asegurarBloquesArena(ServerWorld mundo) {
        try {
            var reg = Registries.BLOCK;
            var idPlayerStand = Identifier.of("cobblemonbattlepositions", "player_stand_position");
            var idTrainerStand = Identifier.of("cobblemonbattlepositions", "trainer_stand_position");
            var idPlayerPokemon = Identifier.of("cobblemonbattlepositions", "player_pokemon_position");
            var idTrainerPokemon = Identifier.of("cobblemonbattlepositions", "trainer_pokemon_position");

            var bPlayerStand = reg.get(idPlayerStand);
            var bTrainerStand = reg.get(idTrainerStand);
            var bPlayerPokemon = reg.get(idPlayerPokemon);
            var bTrainerPokemon = reg.get(idTrainerPokemon);

            if (bPlayerStand != null && bPlayerStand != Blocks.AIR) {
                mundo.setBlockState(POS_BLOCK_JUGADOR_STAND, bPlayerStand.getDefaultState());
            }
            if (bTrainerStand != null && bTrainerStand != Blocks.AIR) {
                mundo.setBlockState(POS_BLOCK_NPC_STAND, bTrainerStand.getDefaultState());
            }
            if (bPlayerPokemon != null && bPlayerPokemon != Blocks.AIR) {
                mundo.setBlockState(POS_BLOCK_JUGADOR_POKEMON, bPlayerPokemon.getDefaultState());
            }
            if (bTrainerPokemon != null && bTrainerPokemon != Blocks.AIR) {
                mundo.setBlockState(POS_BLOCK_NPC_POKEMON, bTrainerPokemon.getDefaultState());
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("Error asegurando bloques de posición en la Torre de Batalla", e);
        }
    }
}