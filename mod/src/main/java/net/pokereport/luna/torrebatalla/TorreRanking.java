package net.pokereport.luna.torrebatalla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class TorreRanking {

    private static final String MARCA = "luna_torre_holograma";
    private static final String JSON_PATH = "config/luna_torre_ranking.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // playerName -> maxRound
    private static final Map<String, Integer> rankings = new HashMap<>();

    public static void load() {
        File file = new File(JSON_PATH);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> data = GSON.fromJson(reader, type);
                if (data != null) {
                    rankings.clear();
                    rankings.putAll(data);
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Error cargando ranking de la torre", e);
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(JSON_PATH)) {
            GSON.toJson(rankings, writer);
        } catch (Exception e) {
            LunaEternal.LOG.error("Error guardando ranking de la torre", e);
        }
    }

    public static void actualizarRonda(String jugador, int ronda) {
        int actual = rankings.getOrDefault(jugador, 0);
        if (ronda > actual) {
            rankings.put(jugador, ronda);
            save();
            actualizarHologramasEnMundos();
        }
    }
    
    private static void actualizarHologramasEnMundos() {
        if (LunaEternal.getServer() == null) return;
        MutableText texto = generarTexto();
        for (ServerWorld world : LunaEternal.getServer().getWorlds()) {
            for (net.minecraft.entity.Entity e : world.iterateEntities()) {
                if (e instanceof DisplayEntity.TextDisplayEntity td && td.getCommandTags().contains(MARCA)) {
                    td.setText(texto);
                }
            }
        }
    }

    public static void colocarHolograma(ServerWorld mundo, Vec3d pies) {
        quitar(mundo, pies);

        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) return;
        
        cartel.setPosition(pies.x, pies.y + 2.45, pies.z);
        cartel.setText(generarTexto());
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        cartel.setBackground(0x40000000);
        cartel.setLineWidth(300);
        cartel.setViewRange(0.75f);
        cartel.setNoGravity(true);
        cartel.addCommandTag(MARCA);
        
        mundo.spawnEntity(cartel);
    }

    public static void quitar(ServerWorld mundo, Vec3d donde) {
        double radio = 6.0;
        var caja = new net.minecraft.util.math.Box(
                donde.x - radio, donde.y - radio, donde.z - radio,
                donde.x + radio, donde.y + radio, donde.z + radio);
        for (var e : mundo.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, caja, x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
        }
    }

    private static MutableText generarTexto() {
        MutableText t = Text.literal("§lTOP 10 - TORRE DE BATALLA§r\n").formatted(Formatting.GOLD);
        t.append(Text.literal("¿Hasta qué ronda podrás llegar?\n\n").formatted(Formatting.GRAY));
        
        List<Map.Entry<String, Integer>> list = new ArrayList<>(rankings.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        if (list.isEmpty()) {
            t.append(Text.literal("Aún no hay campeones.").formatted(Formatting.RED));
        } else {
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                Map.Entry<String, Integer> entry = list.get(i);
                Formatting color = (i == 0) ? Formatting.YELLOW : (i == 1) ? Formatting.WHITE : (i == 2) ? Formatting.GOLD : Formatting.AQUA;
                t.append(Text.literal((i + 1) + "º " + entry.getKey() + " - Ronda " + entry.getValue() + "\n").formatted(color));
            }
        }
        return t;
    }
}