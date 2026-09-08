package net.pokereport.luna.torrebatalla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class TorreRanking {

    public static final String MARCA = "luna_torre_holograma";
    public static final String MARCA_1VS1 = "luna_torre_holograma_1vs1";
    public static final String MARCA_2VS2 = "luna_torre_holograma_2vs2";
    public static final String MARCA_ALEATORIO = "luna_torre_holograma_aleatorio";

    public static final String MODO_1VS1 = "1vs1";
    public static final String MODO_2VS2 = "2vs2";
    public static final String MODO_ALEATORIO = "aleatorio";

    private static final String JSON_PATH = "config/luna_torre_ranking.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // modo -> (playerName -> maxRound)
    private static final Map<String, Map<String, Integer>> rankings = new HashMap<>();

    static {
        rankings.put(MODO_1VS1, new HashMap<>());
        rankings.put(MODO_2VS2, new HashMap<>());
        rankings.put(MODO_ALEATORIO, new HashMap<>());
    }

    public static String normalizarModo(String modo) {
        if (modo == null) return MODO_1VS1;
        String m = modo.trim().toLowerCase();
        if (m.equals("1") || m.contains("1v1") || m.contains("1vs1") || m.equals("single") || m.equals("individual")) {
            return MODO_1VS1;
        }
        if (m.equals("2") || m.contains("2v2") || m.contains("2vs2") || m.equals("doble") || m.equals("dobles")) {
            return MODO_2VS2;
        }
        if (m.equals("3") || m.contains("aleat") || m.contains("random") || m.contains("draft")) {
            return MODO_ALEATORIO;
        }
        return MODO_1VS1;
    }

    public static String modoDe(int modoId) {
        return switch (modoId) {
            case 1 -> MODO_2VS2;
            case 2 -> MODO_ALEATORIO;
            default -> MODO_1VS1;
        };
    }

    public static void load() {
        File file = new File(JSON_PATH);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type typeMap = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
            Map<String, Map<String, Integer>> data = null;
            try {
                data = GSON.fromJson(reader, typeMap);
            } catch (Exception ignored) {}

            if (data != null && (data.containsKey(MODO_1VS1) || data.containsKey(MODO_2VS2) || data.containsKey(MODO_ALEATORIO))) {
                rankings.get(MODO_1VS1).clear();
                rankings.get(MODO_2VS2).clear();
                rankings.get(MODO_ALEATORIO).clear();
                if (data.containsKey(MODO_1VS1) && data.get(MODO_1VS1) != null) rankings.get(MODO_1VS1).putAll(data.get(MODO_1VS1));
                if (data.containsKey(MODO_2VS2) && data.get(MODO_2VS2) != null) rankings.get(MODO_2VS2).putAll(data.get(MODO_2VS2));
                if (data.containsKey(MODO_ALEATORIO) && data.get(MODO_ALEATORIO) != null) rankings.get(MODO_ALEATORIO).putAll(data.get(MODO_ALEATORIO));
            } else {
                try (FileReader reader2 = new FileReader(file)) {
                    Type flatType = new TypeToken<Map<String, Integer>>() {}.getType();
                    Map<String, Integer> flat = GSON.fromJson(reader2, flatType);
                    if (flat != null) {
                        rankings.get(MODO_1VS1).putAll(flat);
                        save();
                    }
                }
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("Error cargando ranking de la torre", e);
        }
    }

    public static synchronized void save() {
        try (FileWriter writer = new FileWriter(JSON_PATH)) {
            GSON.toJson(rankings, writer);
        } catch (Exception e) {
            LunaEternal.LOG.error("Error guardando ranking de la torre", e);
        }
    }

    public static synchronized void actualizarRonda(MinecraftServer server, String modoStr, String jugador, int ronda) {
        String m = normalizarModo(modoStr);
        Map<String, Integer> tabla = rankings.computeIfAbsent(m, k -> new HashMap<>());
        int actual = tabla.getOrDefault(jugador, 0);
        if (ronda > actual) {
            tabla.put(jugador, ronda);
            save();
            actualizarHologramasEnMundos(server);
        }
    }

    public static synchronized int getRonda(String modoStr, String jugador) {
        String m = normalizarModo(modoStr);
        Map<String, Integer> tabla = rankings.get(m);
        return (tabla != null && jugador != null) ? tabla.getOrDefault(jugador, 0) : 0;
    }

    public static final int COLOR_FONDO_HOLOGRAMA = 0xF40A0E18; // ~96% opacidad, azul pizarra sólido

    public static void actualizarHologramasEnMundos(MinecraftServer server) {
        if (server == null) return;
        for (ServerWorld world : server.getWorlds()) {
            for (var e : world.iterateEntities()) {
                if (e instanceof DisplayEntity.TextDisplayEntity td && td.getCommandTags().contains(MARCA)) {
                    String modo = MODO_1VS1;
                    if (td.getCommandTags().contains(MARCA_2VS2)) {
                        modo = MODO_2VS2;
                    } else if (td.getCommandTags().contains(MARCA_ALEATORIO)) {
                        modo = MODO_ALEATORIO;
                    }
                    td.setText(generarTexto(modo));
                    td.setBackground(COLOR_FONDO_HOLOGRAMA);
                }
            }
        }
    }

    public static void colocarHolograma(ServerWorld mundo, Vec3d pies, String modoStr) {
        String modo = normalizarModo(modoStr);
        quitarCercano(mundo, pies);

        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) return;

        cartel.setPosition(pies.x, pies.y + 2.2, pies.z);
        cartel.setText(generarTexto(modo));
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        cartel.setBackground(COLOR_FONDO_HOLOGRAMA);
        cartel.setLineWidth(320);
        cartel.setViewRange(1.2f);
        cartel.setNoGravity(true);
        cartel.addCommandTag(MARCA);
        cartel.addCommandTag("luna_torre_holograma_" + modo);

        mundo.spawnEntity(cartel);
    }

    public static int quitarCercano(ServerWorld mundo, Vec3d donde) {
        double radio = 5.0;
        var caja = new Box(
                donde.x - radio, donde.y - radio, donde.z - radio,
                donde.x + radio, donde.y + radio, donde.z + radio);
        int eliminados = 0;
        for (var e : mundo.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, caja, x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            eliminados++;
        }
        return eliminados;
    }

    public static MutableText generarTexto(String modoStr) {
        String modo = normalizarModo(modoStr);
        MutableText t = Text.empty();

        switch (modo) {
            case MODO_2VS2 -> {
                t.append(Text.literal("§b§l╔═══════════════════════════════╗\n"));
                t.append(Text.literal("§b§l     ⚡ TORRE · COMBATES DOBLES 2VS2 ⚡\n"));
                t.append(Text.literal("§3§l            🏆 SALÓN DE LA FAMA 🏆\n"));
                t.append(Text.literal("§b§l╚═══════════════════════════════╝\n"));
                t.append(Text.literal("§b§l       ── TOP 10 ENTRENADORES ──\n\n"));
            }
            case MODO_ALEATORIO -> {
                t.append(Text.literal("§a§l╔═══════════════════════════════╗\n"));
                t.append(Text.literal("§a§l       🎲 TORRE · DRAFT ALEATORIO 🎲\n"));
                t.append(Text.literal("§2§l            🏆 SALÓN DE LA FAMA 🏆\n"));
                t.append(Text.literal("§a§l╚═══════════════════════════════╝\n"));
                t.append(Text.literal("§a§l       ── TOP 10 ENTRENADORES ──\n\n"));
            }
            default -> {
                t.append(Text.literal("§6§l╔═══════════════════════════════╗\n"));
                t.append(Text.literal("§6§l        ⚔ TORRE DE BATALLA · 1 VS 1 ⚔\n"));
                t.append(Text.literal("§e§l            🏆 SALÓN DE LA FAMA 🏆\n"));
                t.append(Text.literal("§6§l╚═══════════════════════════════╝\n"));
                t.append(Text.literal("§e§l       ── TOP 10 ENTRENADORES ──\n\n"));
            }
        }

        Map<String, Integer> tabla = rankings.getOrDefault(modo, Map.of());
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(tabla.entrySet());
        lista.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        if (lista.isEmpty()) {
            t.append(Text.literal("§c§l        ¡Aún no hay récords registrados!\n"));
            t.append(Text.literal("§f     Sé el primero en entrar a la historia.\n\n"));
        } else {
            for (int i = 0; i < Math.min(10, lista.size()); i++) {
                Map.Entry<String, Integer> entry = lista.get(i);
                int pos = i + 1;
                String medalla = switch (pos) {
                    case 1 -> "§e🥇 §6§l1º";
                    case 2 -> "§f🥈 §f§l2º";
                    case 3 -> "§6🥉 §e§l3º";
                    default -> "§b#" + (pos < 10 ? "0" + pos : pos) + " ";
                };
                String colorNombre = (pos == 1) ? "§f§l" : (pos <= 3) ? "§f§l" : "§f";
                String colorRonda = (pos == 1) ? "§a§l" : (pos <= 3) ? "§e§l" : "§b§l";
                String sep = (pos == 1) ? "§e»" : (pos <= 3) ? "§f»" : "§7»";

                t.append(Text.literal(medalla + " " + colorNombre + entry.getKey() + " " + sep + " " + colorRonda + "Ronda " + entry.getValue() + (pos == 1 ? " §6★\n" : "\n")));
            }
            t.append(Text.literal("\n"));
        }

        t.append(Text.literal("§8═══════════════════════════════\n"));
        t.append(Text.literal("§f▸ Abre el §6§lPokePad §fpara desafiar la Torre"));
        return t;
    }
}