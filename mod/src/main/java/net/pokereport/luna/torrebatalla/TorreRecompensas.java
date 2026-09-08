package net.pokereport.luna.torrebatalla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SISTEMA DE RECOMPENSAS DE TEMPORADA DE LA TORRE DE BATALLA.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Las recompensas se desbloquean según la ronda más alta alcanzada en la temporada actual.</li>
 *   <li>Cada ronda se reclama UNA SOLA VEZ por temporada hasta el reinicio.</li>
 *   <li>Rondas 1 a 3: vacías.</li>
 *   <li>Rondas 4 a 100: catálogo específico.</li>
 *   <li>Ronda 101+: 1 Master Ball fija por cada ronda ganada (infinito).</li>
 *   <li>Bonificaciones: Cada 5 rondas -> +1,000 Plata. Cada 30 rondas -> +50 LunaCoins.</li>
 * </ul>
 */
public class TorreRecompensas {

    private static final String JSON_PATH = "config/luna_torre_recompensas.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ProgresoJugador {
        public int maxRonda = 0;
        public Set<Integer> reclamadas = new HashSet<>();

        public ProgresoJugador() {}

        public ProgresoJugador(int maxRonda, Set<Integer> reclamadas) {
            this.maxRonda = maxRonda;
            this.reclamadas = reclamadas != null ? reclamadas : new HashSet<>();
        }
    }

    private static class DatosTemporada {
        int temporada = 1;
        Map<String, ProgresoJugador> progreso = new HashMap<>();
    }

    private static int temporadaActual = 1;
    private static final Map<UUID, ProgresoJugador> datos = new ConcurrentHashMap<>();

    public record ItemPremio(String itemId, int cantidad, String nombreVisual, String lore) {
        public ItemPremio(String itemId, int cantidad) {
            this(itemId, cantidad, null, null);
        }

        public ItemStack crearStack() {
            if ("chapa_plateada".equalsIgnoreCase(itemId)) {
                ItemStack stack = new ItemStack(Items.IRON_NUGGET, cantidad);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f§lChapa Plateada").styled(s -> s.withItalic(false)));
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("§7Entrenamiento Especial para maximizar un IV").styled(s -> s.withItalic(false))
                )));
                return stack;
            }
            if ("chapa_dorada".equalsIgnoreCase(itemId)) {
                ItemStack stack = new ItemStack(Items.GOLD_NUGGET, cantidad);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lChapa Dorada").styled(s -> s.withItalic(false)));
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal("§eEntrenamiento Extremo para maximizar todos los IVs").styled(s -> s.withItalic(false))
                )));
                return stack;
            }

            var item = Registries.ITEM.get(Identifier.tryParse(itemId));
            if (item == null || item == Items.AIR) {
                item = Items.NETHER_STAR;
            }
            ItemStack stack = new ItemStack(item, cantidad);
            if (nombreVisual != null && !nombreVisual.isEmpty()) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(nombreVisual).styled(s -> s.withItalic(false)));
            }
            if (lore != null && !lore.isEmpty()) {
                stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                        Text.literal(lore).styled(s -> s.withItalic(false))
                )));
            }
            return stack;
        }
    }

    public record InfoRecompensa(
            int ronda,
            List<ItemPremio> items,
            int plata,
            int lunacoins,
            String tituloHito
    ) {}

    private static final Map<Integer, List<ItemPremio>> CATALOGO_RONDAS = new HashMap<>();

    static {
        // Catálogo fijo para rondas 1 a 100
        CATALOGO_RONDAS.put(1, List.of());
        CATALOGO_RONDAS.put(2, List.of());
        CATALOGO_RONDAS.put(3, List.of());
        CATALOGO_RONDAS.put(4, List.of(new ItemPremio("cobblemon:poke_ball", 1)));
        CATALOGO_RONDAS.put(5, List.of(new ItemPremio("cobblemon:great_ball", 2), new ItemPremio("cobblemon:oran_berry", 1), new ItemPremio("minecraft:emerald", 1)));
        CATALOGO_RONDAS.put(6, List.of(new ItemPremio("cobblemon:great_ball", 1), new ItemPremio("cobblemon:pecha_berry", 1)));
        CATALOGO_RONDAS.put(7, List.of(new ItemPremio("cobblemon:poke_ball", 2), new ItemPremio("minecraft:iron_ingot", 1)));
        CATALOGO_RONDAS.put(8, List.of(new ItemPremio("cobblemon:great_ball", 1), new ItemPremio("cobblemon:chesto_berry", 1)));
        CATALOGO_RONDAS.put(9, List.of(new ItemPremio("cobblemon:great_ball", 2), new ItemPremio("minecraft:diamond", 1)));
        CATALOGO_RONDAS.put(10, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:revive", 1), new ItemPremio("cobblemon:lum_berry", 1)));
        CATALOGO_RONDAS.put(11, List.of(new ItemPremio("cobblemon:great_ball", 1), new ItemPremio("cobblemon:salac_berry", 1)));
        CATALOGO_RONDAS.put(12, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(13, List.of(new ItemPremio("cobblemon:ether", 1), new ItemPremio("cobblemon:persim_berry", 1)));
        CATALOGO_RONDAS.put(14, List.of(new ItemPremio("cobblemon:great_ball", 2), new ItemPremio("cobblemon:cheri_berry", 1)));
        CATALOGO_RONDAS.put(15, List.of(new ItemPremio("cobblemon:rare_candy", 1), new ItemPremio("cobblemon:pp_up", 1), new ItemPremio("minecraft:emerald", 1)));
        CATALOGO_RONDAS.put(16, List.of(new ItemPremio("cobblemon:poke_ball", 2), new ItemPremio("cobblemon:max_potion", 1)));
        CATALOGO_RONDAS.put(17, List.of(new ItemPremio("cobblemon:great_ball", 1), new ItemPremio("cobblemon:rawst_berry", 1)));
        CATALOGO_RONDAS.put(18, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:diamond", 1)));
        CATALOGO_RONDAS.put(19, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("cobblemon:aspear_berry", 1)));
        CATALOGO_RONDAS.put(20, List.of(new ItemPremio("cobblemon:max_revive", 1), new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:rare_candy", 1)));
        CATALOGO_RONDAS.put(21, List.of(new ItemPremio("cobblemon:great_ball", 2), new ItemPremio("minecraft:gold_nugget", 1)));
        CATALOGO_RONDAS.put(22, List.of(new ItemPremio("cobblemon:elixir", 1), new ItemPremio("cobblemon:leppa_berry", 1)));
        CATALOGO_RONDAS.put(23, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(24, List.of(new ItemPremio("cobblemon:rare_candy", 1), new ItemPremio("cobblemon:payapa_berry", 1)));
        CATALOGO_RONDAS.put(25, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("cobblemon:pp_up", 1), new ItemPremio("minecraft:emerald", 2)));
        CATALOGO_RONDAS.put(26, List.of(new ItemPremio("cobblemon:great_ball", 3), new ItemPremio("cobblemon:full_heal", 1)));
        CATALOGO_RONDAS.put(27, List.of(new ItemPremio("cobblemon:max_elixir", 1), new ItemPremio("cobblemon:wiki_berry", 1)));
        CATALOGO_RONDAS.put(28, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:diamond", 2)));
        CATALOGO_RONDAS.put(29, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("cobblemon:aguav_berry", 1)));
        CATALOGO_RONDAS.put(30, List.of(new ItemPremio("cobblemon:max_revive", 1), new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(31, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:gold_nugget", 1)));
        CATALOGO_RONDAS.put(32, List.of(new ItemPremio("cobblemon:rare_candy", 1), new ItemPremio("cobblemon:iapapa_berry", 1)));
        CATALOGO_RONDAS.put(33, List.of(new ItemPremio("cobblemon:max_ether", 1), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(34, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:payapa_berry", 1)));
        CATALOGO_RONDAS.put(35, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("cobblemon:hp_up", 1), new ItemPremio("minecraft:emerald", 2)));
        CATALOGO_RONDAS.put(36, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("cobblemon:sitrus_berry", 1)));
        CATALOGO_RONDAS.put(37, List.of(new ItemPremio("cobblemon:ultra_ball", 2), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(38, List.of(new ItemPremio("cobblemon:rare_candy", 1), new ItemPremio("cobblemon:jaboca_berry", 1)));
        CATALOGO_RONDAS.put(39, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("minecraft:diamond", 2)));
        CATALOGO_RONDAS.put(40, List.of(new ItemPremio("chapa_plateada", 1), new ItemPremio("cobblemon:max_revive", 2), new ItemPremio("cobblemon:rare_candy", 3)));
        CATALOGO_RONDAS.put(41, List.of(new ItemPremio("cobblemon:rare_candy", 1), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(42, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:enigma_berry", 1)));
        CATALOGO_RONDAS.put(43, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(44, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:gold_nugget", 1)));
        CATALOGO_RONDAS.put(45, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("cobblemon:protein", 1), new ItemPremio("minecraft:emerald", 3)));
        CATALOGO_RONDAS.put(46, List.of(new ItemPremio("cobblemon:max_revive", 1), new ItemPremio("cobblemon:custap_berry", 1)));
        CATALOGO_RONDAS.put(47, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(48, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:diamond", 2)));
        CATALOGO_RONDAS.put(49, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("cobblemon:micle_berry", 1)));
        CATALOGO_RONDAS.put(50, List.of(new ItemPremio("pokeblocks:pokedoll_snorlax", 1, "§e§lPeluche Exclusivo Snorlax", "§7Premio de honor de la Torre de Batalla (Ronda 50)"), new ItemPremio("chapa_plateada", 1), new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(51, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(52, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:full_restore", 1)));
        CATALOGO_RONDAS.put(53, List.of(new ItemPremio("cobblemon:iron", 1), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(54, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:diamond", 2)));
        CATALOGO_RONDAS.put(55, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("cobblemon:carbos", 1), new ItemPremio("minecraft:emerald", 3)));
        CATALOGO_RONDAS.put(56, List.of(new ItemPremio("cobblemon:max_revive", 1), new ItemPremio("cobblemon:rowap_berry", 1)));
        CATALOGO_RONDAS.put(57, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(58, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:gold_nugget", 1)));
        CATALOGO_RONDAS.put(59, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(60, List.of(new ItemPremio("chapa_dorada", 1), new ItemPremio("cobblemon:max_revive", 3), new ItemPremio("cobblemon:rare_candy", 5)));
        CATALOGO_RONDAS.put(61, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(62, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("cobblemon:full_restore", 1)));
        CATALOGO_RONDAS.put(63, List.of(new ItemPremio("cobblemon:calcium", 1), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(64, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:diamond", 2)));
        CATALOGO_RONDAS.put(65, List.of(new ItemPremio("cobblemon:rare_candy", 4), new ItemPremio("cobblemon:zinc", 1), new ItemPremio("minecraft:emerald", 4)));
        CATALOGO_RONDAS.put(66, List.of(new ItemPremio("cobblemon:max_revive", 1), new ItemPremio("cobblemon:ultra_ball", 2)));
        CATALOGO_RONDAS.put(67, List.of(new ItemPremio("cobblemon:ultra_ball", 3), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(68, List.of(new ItemPremio("cobblemon:rare_candy", 2), new ItemPremio("minecraft:gold_nugget", 1)));
        CATALOGO_RONDAS.put(69, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(70, List.of(new ItemPremio("pokeblocks:pokedoll_gengar", 1, "§5§lPeluche Exclusivo Gengar", "§7Premio de honor de la Torre de Batalla (Ronda 70)"), new ItemPremio("cobblemon:ability_patch", 1), new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(71, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("minecraft:gold_ingot", 1)));
        CATALOGO_RONDAS.put(72, List.of(new ItemPremio("cobblemon:ultra_ball", 4), new ItemPremio("cobblemon:full_restore", 1)));
        CATALOGO_RONDAS.put(73, List.of(new ItemPremio("cobblemon:pp_up", 1), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(74, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("minecraft:diamond", 3)));
        CATALOGO_RONDAS.put(75, List.of(new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("cobblemon:ability_capsule", 1), new ItemPremio("minecraft:emerald", 5)));
        CATALOGO_RONDAS.put(76, List.of(new ItemPremio("cobblemon:max_revive", 2), new ItemPremio("cobblemon:ultra_ball", 3)));
        CATALOGO_RONDAS.put(77, List.of(new ItemPremio("cobblemon:ultra_ball", 4), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(78, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("minecraft:iron_block", 1)));
        CATALOGO_RONDAS.put(79, List.of(new ItemPremio("cobblemon:full_restore", 1), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(80, List.of(new ItemPremio("chapa_dorada", 1), new ItemPremio("cobblemon:ability_patch", 1), new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("minecraft:diamond_block", 2)));
        CATALOGO_RONDAS.put(81, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("minecraft:gold_ingot", 2)));
        CATALOGO_RONDAS.put(82, List.of(new ItemPremio("cobblemon:ultra_ball", 5), new ItemPremio("cobblemon:full_restore", 1)));
        CATALOGO_RONDAS.put(83, List.of(new ItemPremio("cobblemon:pp_up", 1), new ItemPremio("minecraft:gold_block", 1)));
        CATALOGO_RONDAS.put(84, List.of(new ItemPremio("cobblemon:rare_candy", 3), new ItemPremio("minecraft:diamond", 3)));
        CATALOGO_RONDAS.put(85, List.of(new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("cobblemon:max_revive", 2), new ItemPremio("minecraft:emerald", 5)));
        CATALOGO_RONDAS.put(86, List.of(new ItemPremio("cobblemon:max_revive", 2), new ItemPremio("cobblemon:ultra_ball", 5)));
        CATALOGO_RONDAS.put(87, List.of(new ItemPremio("cobblemon:ultra_ball", 5), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(88, List.of(new ItemPremio("cobblemon:rare_candy", 4), new ItemPremio("minecraft:iron_block", 2)));
        CATALOGO_RONDAS.put(89, List.of(new ItemPremio("cobblemon:full_restore", 2), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(90, List.of(new ItemPremio("pokeblocks:pokedoll_marshadow", 1, "§d§lPeluche Exclusivo Marshadow", "§7Premio de honor de la Torre de Batalla (Ronda 90)"), new ItemPremio("chapa_dorada", 1), new ItemPremio("cobblemon:ability_patch", 1), new ItemPremio("cobblemon:rare_candy", 7), new ItemPremio("minecraft:diamond_block", 3)));
        CATALOGO_RONDAS.put(91, List.of(new ItemPremio("cobblemon:rare_candy", 4), new ItemPremio("minecraft:gold_block", 2)));
        CATALOGO_RONDAS.put(92, List.of(new ItemPremio("cobblemon:ultra_ball", 5), new ItemPremio("cobblemon:full_restore", 2)));
        CATALOGO_RONDAS.put(93, List.of(new ItemPremio("cobblemon:pp_up", 1), new ItemPremio("minecraft:diamond_block", 1)));
        CATALOGO_RONDAS.put(94, List.of(new ItemPremio("cobblemon:rare_candy", 4), new ItemPremio("minecraft:diamond", 4)));
        CATALOGO_RONDAS.put(95, List.of(new ItemPremio("cobblemon:rare_candy", 7), new ItemPremio("cobblemon:max_revive", 3), new ItemPremio("minecraft:diamond_sword", 1)));
        CATALOGO_RONDAS.put(96, List.of(new ItemPremio("cobblemon:max_revive", 3), new ItemPremio("cobblemon:ultra_ball", 5)));
        CATALOGO_RONDAS.put(97, List.of(new ItemPremio("cobblemon:ultra_ball", 5), new ItemPremio("minecraft:diamond_block", 2)));
        CATALOGO_RONDAS.put(98, List.of(new ItemPremio("cobblemon:rare_candy", 5), new ItemPremio("chapa_plateada", 1)));
        CATALOGO_RONDAS.put(99, List.of(new ItemPremio("cobblemon:full_restore", 3), new ItemPremio("minecraft:diamond_block", 2)));
        CATALOGO_RONDAS.put(100, List.of(
                new ItemPremio("cobblemon:master_ball", 10),
                new ItemPremio("minecraft:diamond_block", 5),
                new ItemPremio("minecraft:diamond_sword", 1),
                new ItemPremio("cobblemon:rare_candy", 5),
                new ItemPremio("cobblemon:max_revive", 3),
                new ItemPremio("cobblemon:pp_up", 2),
                new ItemPremio("pokeblocks:pokedoll_kyogre", 1, "§3§lPeluche Legendario Kyogre", "§6§l🏆 Campeón de la Torre de Batalla (Ronda 100)"),
                new ItemPremio("pokeblocks:pokedoll_shiny_pokemon_trophy", 1, "§6§lTrofeo Exclusivo de la Torre", "§eEmblema sagrado otorgado al conquistar el piso 100")
        ));
    }

    public static InfoRecompensa obtenerInfo(int ronda) {
        if (ronda < 1) return new InfoRecompensa(ronda, List.of(), 0, 0, null);

        List<ItemPremio> items;
        if (ronda <= 100) {
            items = CATALOGO_RONDAS.getOrDefault(ronda, List.of());
        } else {
            // 101 en adelante (INFINITO): 1 Master Ball fija por cada ronda ganada
            items = List.of(new ItemPremio("cobblemon:master_ball", 1));
        }

        // Bonificaciones de moneda:
        // Cada 5 rondas: +1,000 Plata
        int plata = (ronda % 5 == 0) ? 1000 : 0;
        // Cada 30 rondas: +50 LunaCoins
        int lunacoins = (ronda % 30 == 0) ? 50 : 0;

        String hito = null;
        if (ronda == 100) {
            hito = "🏆 SUPERRECOMPENSA FINAL";
        } else if (ronda == 50 || ronda == 70 || ronda == 90) {
            hito = "🌟 PELUCHE EXCLUSIVO TORRE";
        } else if (ronda == 10 || ronda == 20 || ronda == 30 || ronda == 40 || ronda == 60 || ronda == 80) {
            hito = "🔥 RECOMPENSA ÉPICA";
        } else if (ronda % 5 == 0) {
            hito = "🎁 PACK DE TEMPORADA";
        } else if (ronda > 100) {
            hito = "⭐ MAESTRÍA INFINITA";
        }

        return new InfoRecompensa(ronda, items, plata, lunacoins, hito);
    }

    public static int getTemporada() {
        return temporadaActual;
    }

    public static synchronized void load() {
        File file = new File(JSON_PATH);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type tipo = new TypeToken<DatosTemporada>() {}.getType();
            DatosTemporada d = GSON.fromJson(reader, tipo);
            if (d != null) {
                temporadaActual = Math.max(1, d.temporada);
                datos.clear();
                if (d.progreso != null) {
                    for (Map.Entry<String, ProgresoJugador> e : d.progreso.entrySet()) {
                        try {
                            UUID u = UUID.fromString(e.getKey());
                            datos.put(u, e.getValue());
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("TorreRecompensas: error cargando progreso de recompensas", e);
        }
    }

    public static synchronized void save() {
        try {
            File file = new File(JSON_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                DatosTemporada d = new DatosTemporada();
                d.temporada = temporadaActual;
                for (Map.Entry<UUID, ProgresoJugador> e : datos.entrySet()) {
                    d.progreso.put(e.getKey().toString(), e.getValue());
                }
                GSON.toJson(d, writer);
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("TorreRecompensas: error guardando progreso de recompensas", e);
        }
    }

    public static ProgresoJugador obtenerProgreso(UUID uuid) {
        return datos.computeIfAbsent(uuid, k -> new ProgresoJugador());
    }

    public static int getMaxRonda(UUID uuid) {
        ProgresoJugador p = datos.get(uuid);
        return p != null ? p.maxRonda : 0;
    }

    public static Set<Integer> getReclamadas(UUID uuid) {
        ProgresoJugador p = datos.get(uuid);
        return p != null ? p.reclamadas : Set.of();
    }

    public static boolean puedeReclamar(UUID uuid, int ronda) {
        if (ronda < 4) return false;
        ProgresoJugador p = obtenerProgreso(uuid);
        return p.maxRonda >= ronda && !p.reclamadas.contains(ronda);
    }

    public static int recompensasDisponibles(UUID uuid) {
        ProgresoJugador p = obtenerProgreso(uuid);
        if (p.maxRonda < 4) return 0;
        int count = 0;
        for (int r = 4; r <= p.maxRonda; r++) {
            if (!p.reclamadas.contains(r)) {
                count++;
            }
        }
        return count;
    }

    public static synchronized void registrarVictoria(UUID uuid, int ronda) {
        ProgresoJugador p = obtenerProgreso(uuid);
        if (ronda > p.maxRonda) {
            p.maxRonda = ronda;
            save();
        }
    }

    /**
     * Reclama una recompensa individual por número de ronda.
     */
    public static synchronized boolean reclamar(ServerPlayerEntity jugador, int ronda) {
        UUID uuid = jugador.getUuid();
        if (!puedeReclamar(uuid, ronda)) {
            return false;
        }

        InfoRecompensa info = obtenerInfo(ronda);
        ProgresoJugador p = obtenerProgreso(uuid);
        p.reclamadas.add(ronda);
        save();

        // 1. Entregar objetos
        for (ItemPremio item : info.items()) {
            ItemStack stack = item.crearStack();
            jugador.getInventory().offerOrDrop(stack);
        }

        // 2. Acreditar divisas si corresponde
        if (info.plata() > 0 || info.lunacoins() > 0) {
            try {
                long playerId = LunaEternal.players().resolve(uuid, jugador.getName().getString());
                if (info.plata() > 0) {
                    String keyPlata = "torre:ronda:" + temporadaActual + ":" + ronda + ":" + uuid + ":plata";
                    LunaEternal.economy().credit(playerId, Currency.POKEDOLLAR, info.plata(), "torre_recompensa", keyPlata);
                }
                if (info.lunacoins() > 0) {
                    String keyCoins = "torre:ronda:" + temporadaActual + ":" + ronda + ":" + uuid + ":coins";
                    LunaEternal.economy().credit(playerId, Currency.REPORTCOIN, info.lunacoins(), "torre_recompensa", keyCoins);
                }
            } catch (Exception e) {
                LunaEternal.LOG.warn("Error acreditando divisas de Torre R{}: {}", ronda, e.getMessage());
            }
        }

        // 3. Sonido y mensaje
        jugador.getServerWorld().playSound(null, jugador.getX(), jugador.getY(), jugador.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        jugador.sendMessage(Text.literal("§a[Torre de Batalla] ¡Has reclamado la recompensa de la §eRonda " + ronda + "§a!"));

        return true;
    }

    /**
     * Reclama de golpe todas las recompensas disponibles acumuladas para el jugador.
     */
    public static synchronized int reclamarTodas(ServerPlayerEntity jugador) {
        UUID uuid = jugador.getUuid();
        ProgresoJugador p = obtenerProgreso(uuid);
        int max = p.maxRonda;
        if (max < 4) return 0;

        int reclamadasCount = 0;
        int totalPlata = 0;
        int totalCoins = 0;
        List<ItemStack> stacks = new ArrayList<>();

        for (int r = 4; r <= max; r++) {
            if (!p.reclamadas.contains(r)) {
                InfoRecompensa info = obtenerInfo(r);
                p.reclamadas.add(r);
                reclamadasCount++;

                for (ItemPremio it : info.items()) {
                    stacks.add(it.crearStack());
                }
                totalPlata += info.plata();
                totalCoins += info.lunacoins();
            }
        }

        if (reclamadasCount == 0) return 0;
        save();

        // 1. Entregar todos los items
        for (ItemStack st : stacks) {
            jugador.getInventory().offerOrDrop(st);
        }

        // 2. Acreditar economías
        if (totalPlata > 0 || totalCoins > 0) {
            try {
                long playerId = LunaEternal.players().resolve(uuid, jugador.getName().getString());
                if (totalPlata > 0) {
                    String keyPlata = "torre:batch:" + temporadaActual + ":" + max + ":" + uuid + ":plata:" + System.currentTimeMillis();
                    LunaEternal.economy().credit(playerId, Currency.POKEDOLLAR, totalPlata, "torre_recompensa_batch", keyPlata);
                }
                if (totalCoins > 0) {
                    String keyCoins = "torre:batch:" + temporadaActual + ":" + max + ":" + uuid + ":coins:" + System.currentTimeMillis();
                    LunaEternal.economy().credit(playerId, Currency.REPORTCOIN, totalCoins, "torre_recompensa_batch", keyCoins);
                }
            } catch (Exception e) {
                LunaEternal.LOG.warn("Error acreditando batch de divisas Torre: {}", e.getMessage());
            }
        }

        // 3. Notificación
        jugador.getServerWorld().playSound(null, jugador.getX(), jugador.getY(), jugador.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        jugador.sendMessage(Text.literal("§6§l[Torre de Batalla] §a¡Has reclamado §e" + reclamadasCount
                + " recompensas §ade temporada acumuladas!"));
        if (totalPlata > 0 || totalCoins > 0) {
            jugador.sendMessage(Text.literal("§7▸ Bonificaciones añadidas: §f+" + String.format("%,d", totalPlata)
                    + " Plata §7y §e+" + totalCoins + " LunaCoins§7."));
        }

        return reclamadasCount;
    }

    /**
     * Avanza la temporada de la Torre: reinicia las reclamaciones de todos los jugadores
     * e incrementa el contador de temporada.
     */
    public static synchronized int avanzarTemporada() {
        temporadaActual++;
        for (ProgresoJugador p : datos.values()) {
            p.reclamadas.clear();
        }
        save();
        LunaEternal.LOG.info("TorreRecompensas: ¡Iniciada la Temporada {}! Recompensas reiniciadas.", temporadaActual);
        return temporadaActual;
    }

    /**
     * Gancho preparado para conectar con el futuro sistema de Pase de Batalla.
     */
    public static void hookExpPaseBatalla(ServerPlayerEntity jugador, int ronda) {
        // En el próximo sistema de Pase de Batalla:
        // PaseBatallaService.agregarExp(jugador, calcularExpPase(ronda));
    }
}
