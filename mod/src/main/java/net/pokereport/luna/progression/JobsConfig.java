package net.pokereport.luna.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Configuración externa y límites anti-farm de los trabajos. */
public final class JobsConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long WINDOW_MS = 60_000L;
    private static volatile Data data = defaults();
    private static final Map<UUID, EnumMap<net.pokereport.luna.progression.Path, Window>> WINDOWS
            = new ConcurrentHashMap<>();

    private JobsConfig() {}

    public static final class Data {
        public Mining mining = new Mining();
        public Farming farming = new Farming();
        public int fishingXp = 12;
        public int hatchingXp = 40;
        public Limits maxRewardedActionsPerMinute = new Limits();
        public long[] silverPerLevel = {50, 150, 400, 1_000, 2_500};
    }

    public static final class Mining {
        public int stoneXp = 1;
        public int oreXp = 8;
        public int rareOreXp = 25;
    }

    public static final class Farming {
        public int matureCropXp = 3;
        public int berryXp = 10;
        public int apricornXp = 10;
    }

    public static final class Limits {
        public int miner = 180;
        public int farmer = 120;
        public int fisher = 30;
    }

    private static final class Window {
        long startedAt;
        int actions;
        boolean warned;
    }

    private static Data defaults() {
        return new Data();
    }

    /** Crea el fichero la primera vez y falla explícitamente si está corrupto. */
    public static void load() throws IOException {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("lunaeternal").resolve("jobs.json");
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.writeString(file, JSON.toJson(defaults()) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        }
        Data loaded;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded = JSON.fromJson(reader, Data.class);
        } catch (RuntimeException e) {
            throw new IOException("config/lunaeternal/jobs.json no es JSON válido", e);
        }
        validate(loaded);
        data = loaded;
        WINDOWS.clear();
        LunaEternal.LOG.info("Trabajos: configuración cargada (límites/min minero={}, agricultor={}, pescador={})",
                loaded.maxRewardedActionsPerMinute.miner,
                loaded.maxRewardedActionsPerMinute.farmer,
                loaded.maxRewardedActionsPerMinute.fisher);
    }

    private static void validate(Data d) throws IOException {
        if (d == null || d.mining == null || d.farming == null
                || d.maxRewardedActionsPerMinute == null
                || d.silverPerLevel == null || d.silverPerLevel.length != 5) {
            throw new IOException("jobs.json está incompleto; se requieren 5 recompensas de nivel");
        }
        positive("mining.stoneXp", d.mining.stoneXp);
        positive("mining.oreXp", d.mining.oreXp);
        positive("mining.rareOreXp", d.mining.rareOreXp);
        if (d.mining.oreXp < d.mining.stoneXp
                || d.mining.rareOreXp < d.mining.oreXp) {
            throw new IOException("La XP minera debe cumplir piedra <= mena <= mena rara");
        }
        positive("farming.matureCropXp", d.farming.matureCropXp);
        positive("farming.berryXp", d.farming.berryXp);
        positive("farming.apricornXp", d.farming.apricornXp);
        positive("fishingXp", d.fishingXp);
        positive("hatchingXp", d.hatchingXp);
        positive("maxRewardedActionsPerMinute.miner", d.maxRewardedActionsPerMinute.miner);
        positive("maxRewardedActionsPerMinute.farmer", d.maxRewardedActionsPerMinute.farmer);
        positive("maxRewardedActionsPerMinute.fisher", d.maxRewardedActionsPerMinute.fisher);
        for (int i = 0; i < d.silverPerLevel.length; i++) {
            if (d.silverPerLevel[i] < 0 || d.silverPerLevel[i] > 10_000_000L) {
                throw new IOException("silverPerLevel[" + i + "] fuera de rango");
            }
        }
    }

    private static void positive(String field, int value) throws IOException {
        if (value <= 0 || value > 100_000) {
            throw new IOException(field + " debe estar entre 1 y 100000");
        }
    }

    public static int stoneXp() { return data.mining.stoneXp; }
    public static int oreXp() { return data.mining.oreXp; }
    public static int rareOreXp() { return data.mining.rareOreXp; }
    public static int cropXp() { return data.farming.matureCropXp; }
    public static int berryXp() { return data.farming.berryXp; }
    public static int apricornXp() { return data.farming.apricornXp; }
    public static int fishingXp() { return data.fishingXp; }
    public static int hatchingXp() { return data.hatchingXp; }

    public static long silverForLevel(int level) {
        return level >= 1 && level <= data.silverPerLevel.length
                ? data.silverPerLevel[level - 1] : 0;
    }

    /**
     * Límite deslizante por jugador y oficio. Solo corta la recompensa del
     * trabajo; no cancela el bloque, captura o misión que produjo el evento.
     */
    public static boolean allow(ServerPlayerEntity player, net.pokereport.luna.progression.Path job) {
        if (player == null || job == null || !job.esOficio()) return false;
        int limit = switch (job) {
            case MINERO -> data.maxRewardedActionsPerMinute.miner;
            case AGRICULTOR -> data.maxRewardedActionsPerMinute.farmer;
            case PESCADOR -> data.maxRewardedActionsPerMinute.fisher;
            default -> 1;
        };
        long now = System.currentTimeMillis();
        EnumMap<net.pokereport.luna.progression.Path, Window> byJob = WINDOWS.computeIfAbsent(
                player.getUuid(), ignored -> new EnumMap<>(net.pokereport.luna.progression.Path.class));
        Window window = byJob.computeIfAbsent(job, ignored -> new Window());
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MS || now < window.startedAt) {
                window.startedAt = now;
                window.actions = 0;
                window.warned = false;
            }
            if (window.actions >= limit) {
                if (!window.warned) {
                    window.warned = true;
                    player.sendMessage(Text.literal("§eTrabajo: alcanzaste el límite anti-farm de este minuto."), true);
                    LunaEternal.LOG.warn("Trabajo limitado por anti-farm: jugador={} oficio={} limite={}",
                            player.getName().getString(), job.name(), limit);
                }
                return false;
            }
            window.actions++;
            return true;
        }
    }

    public static void forget(UUID player) {
        WINDOWS.remove(player);
    }
}
