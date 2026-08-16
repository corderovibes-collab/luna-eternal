package net.pokereport.neon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bloques de neón para la ciudadela.
 *
 * <p>Este mod va en el cliente <b>y</b> en el servidor, al revés que
 * {@code lunaeternal}, que es solo de servidor. No es una excepción caprichosa
 * a D-026: un bloque no existe hasta que las dos partes saben que existe. Sin
 * el jar en el cliente, quien mire un neón vería el cubo negro y morado de
 * «textura ausente», y Axiom —que es de cliente— no podría ni ofrecerlo en su
 * paleta, que es justo lo que se necesita para construir con él.
 *
 * <p>Es un mod aparte y no una carpeta más dentro de {@code mod/} por lo mismo:
 * así el jar que se reparte a los jugadores <b>solo tiene bloques</b>. Ni
 * economía, ni base de datos, ni una línea de lógica de juego que valga la pena
 * decompilar.
 *
 * <p>La lista de colores no está aquí: está en {@link Paleta}, que lo genera
 * {@code tools/gen_neon.py} junto con las texturas y los modelos. Ver ese
 * script para el porqué.
 */
public class LunaNeon implements ModInitializer {

    public static final String MOD_ID = "lunaneon";

    private static final Logger LOG = LoggerFactory.getLogger("LunaNeon");

    /**
     * Los objetos en el orden en que se registran, que es el orden en que
     * aparecen en la pestaña del inventario creativo: agrupados por color y no
     * por forma. Se construye una ciudad eligiendo primero la paleta y después
     * la pieza, no al revés.
     */
    private static final List<Item> ORDEN = new ArrayList<>();

    /**
     * Lo mismo para la obra, una lista por pestaña.
     *
     * <p>Seiscientos bloques en una sola pestaña no son una paleta, son un
     * listín. Van cuatro: hormigón, metal (con la rejilla dentro, que es el
     * mismo material), vidrio y pavimento.
     */
    private static final Map<String, List<Item>> ORDEN_CIUDAD = new LinkedHashMap<>();

    /** El icono de cada pestaña. Un material que se reconozca de un vistazo. */
    private static final Map<String, String> ICONOS = Map.of(
            "hormigon", "hormigon_pulido_cian",
            "metal", "metal_acero_cepillado",
            "vidrio", "vidrio_claro_cian",
            "pavimento", "pavimento_terrazo_claro");

    /**
     * Los bloques que el cliente tiene que dibujar en una capa especial.
     *
     * <p>Se llenan aquí porque aquí es donde se sabe de qué familia es cada
     * uno, y los lee {@link LunaNeonCliente}. Un bloque con transparencia
     * dibujado en la capa sólida se ve NEGRO, no transparente, y es un fallo
     * que el servidor no puede detectar porque el servidor no dibuja.
     */
    private static final List<Block> TRASLUCIDOS = new ArrayList<>();
    private static final List<Block> RECORTADOS = new ArrayList<>();

    public static List<Block> traslucidos() {
        return TRASLUCIDOS;
    }

    public static List<Block> recortados() {
        return RECORTADOS;
    }

    @Override
    public void onInitialize() {
        for (Paleta color : Paleta.COLORES) {
            String base = "neon_" + color.id();

            // El cubo entero se registra primero porque la escalera necesita su
            // estado: StairsBlock hereda de él el sonido, la dureza y la
            // herramienta correcta.
            Block entero = registrar(base, new NeonBloque(
                    Neon.ajustes(color.mapa(), BlockSoundGroup.AMETHYST_BLOCK, true)));

            registrar(base + "_losa", new NeonLosa(
                    Neon.ajustes(color.mapa(), BlockSoundGroup.AMETHYST_BLOCK, true)));

            registrar(base + "_escalera", new NeonEscalera(entero.getDefaultState(),
                    Neon.ajustes(color.mapa(), BlockSoundGroup.AMETHYST_BLOCK, true)));

            registrar(base + "_pilar", new NeonPilar(
                    Neon.ajustes(color.mapa(), BlockSoundGroup.AMETHYST_BLOCK, true)));

            // Panel y tubo suenan a cristal y no llenan el cubo.
            registrar(base + "_panel", new NeonPanel(
                    Neon.ajustes(color.mapa(), BlockSoundGroup.GLASS, false)));

            registrar(base + "_tubo", new NeonTubo(
                    Neon.ajustes(color.mapa(), BlockSoundGroup.GLASS, false)));
        }

        registrarObra();

        grupo("neon", "neon_cian", ORDEN);
        for (Map.Entry<String, List<Item>> pestana : ORDEN_CIUDAD.entrySet()) {
            grupo(pestana.getKey(), ICONOS.get(pestana.getKey()), pestana.getValue());
        }

        LOG.info("Neon: {} bloques en {} colores", ORDEN.size(), Paleta.COLORES.length);
        LOG.info("Obra: {} bloques en {} materiales, {} pestanas",
                ORDEN_CIUDAD.values().stream().mapToInt(List::size).sum(),
                Catalogo.MATERIALES.length, ORDEN_CIUDAD.size());

        registrarInterfazLuna();
    }

    /**
     * Da de alta los 506 bloques de obra.
     *
     * <p>El bucle es corto porque no hay nada que decidir: la tabla dice qué
     * formas lleva cada material y cada forma es una clase de vanilla. Lo único
     * con truco es el orden — <b>el cubo entero se registra primero</b> porque
     * la escalera necesita su estado, del que hereda el sonido, la dureza y la
     * herramienta correcta.
     */
    private static void registrarObra() {
        for (Catalogo material : Catalogo.MATERIALES) {
            Block entero = null;
            for (String forma : material.formas()) {
                var ajustes = Ciudad.ajustes(material.familia(), material.mapa());
                Block bloque = switch (forma) {
                    case "" -> material.familia() == Ciudad.Familia.VIDRIO
                            // El vidrio va con TransparentBlock y no con Block:
                            // es lo que hace que dos cristales pegados no
                            // dibujen la cara que tienen en medio. Sin eso, una
                            // torre acristalada son mil caras invisibles que la
                            // tarjeta gráfica pinta igualmente.
                            ? new TransparentBlock(ajustes)
                            : new Block(ajustes);
                    case "_losa" -> new SlabBlock(ajustes);
                    case "_escalera" -> new StairsBlock(Ciudad.base(entero), ajustes);
                    case "_muro" -> new WallBlock(ajustes);
                    case "_valla" -> new FenceBlock(ajustes);
                    case "_pilar" -> new PillarBlock(ajustes);
                    case "_panel" -> new PaneBlock(ajustes);
                    default -> throw new IllegalStateException(
                            "forma desconocida en Catalogo: " + forma);
                };

                Block puesto = registrar(material.id() + forma, bloque,
                        ORDEN_CIUDAD.computeIfAbsent(material.familia().grupo(),
                                clave -> new ArrayList<>()));
                if (forma.isEmpty()) {
                    entero = puesto;
                }
                switch (material.familia()) {
                    case VIDRIO -> TRASLUCIDOS.add(puesto);
                    case REJILLA -> RECORTADOS.add(puesto);
                    default -> { }
                }
            }
        }
    }

    private static void grupo(String id, String icono, List<Item> objetos) {
        Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, id),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(
                                Registries.ITEM.get(Identifier.of(MOD_ID, icono))))
                        .displayName(Text.translatable("itemGroup." + MOD_ID + "." + id))
                        .entries((contexto, entradas) -> objetos.forEach(entradas::add))
                        .build());
    }

    /**
     * Activa el revestido azul luna de la interfaz de Cobblemon.
     *
     * <p>Son 323 texturas: la Pokédex y su item, el resumen, el PC, el combate,
     * el equipo, el comercio, la interacción y los pastos. El pack vive
     * <b>dentro de este jar</b>, en {@code resourcepacks/interfaz_luna/}, y lo
     * genera {@code tools/gen_interfaz.py}.
     *
     * <p><b>Por qué aquí y no como un .zip suelto:</b> el primer intento fue un
     * zip en {@code resourcepacks/} más una línea en la plantilla
     * {@code config/yosbr/options.txt}. No funcionó, y no por un descuido: YOSBR
     * copia esa plantilla <b>solo si {@code options.txt} no existe</b>, así que a
     * quien ya había jugado no le llegaba nunca. El pack se instalaba y se
     * quedaba apagado. Cobblemon ya hacía lo correcto delante de nuestras
     * narices: {@code cobblemon:gyaradosjump} y {@code cobblemon:regionbiasforms}
     * son packs incrustados en su jar, y por eso aparecen solos.
     */
    private static void registrarInterfazLuna() {
        // En try/catch a propósito: esto es puramente cosmético y de cliente, y
        // el mismo jar corre en el servidor. Que falle aquí no puede llevarse
        // por delante el registro de los 96 bloques, que sí es esencial.
        try {
            boolean ok = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                            Identifier.of(MOD_ID, "interfaz_luna"), mod,
                            // ALWAYS_ENABLED y no DEFAULT_ENABLED: el javadoc de
                            // Fabric avisa de que "a resource pack cannot be
                            // enabled by default, only data packs can". Con
                            // DEFAULT_ENABLED el pack se registraba y se quedaba
                            // apagado, sin dar ni un aviso en el log.
                            ResourcePackActivationType.ALWAYS_ENABLED))
                    .orElse(false);
            // Se registra el resultado. La version anterior ignoraba este
            // booleano, y por eso el fallo fue mudo: ni error, ni pack.
            if (ok) {
                LOG.info("Interfaz: revestido de luna activado");
            } else {
                LOG.warn("Interfaz: el revestido NO se registro (resourcepacks/interfaz_luna)");
            }
        } catch (Throwable e) {
            LOG.warn("No se pudo registrar el revestido de la interfaz: {}", e.toString());
        }
    }

    private static <T extends Block> T registrar(String nombre, T bloque) {
        return registrar(nombre, bloque, ORDEN);
    }

    private static <T extends Block> T registrar(String nombre, T bloque,
                                                 List<Item> pestana) {
        Identifier id = Identifier.of(MOD_ID, nombre);
        Registry.register(Registries.BLOCK, id, bloque);
        pestana.add(Registry.register(Registries.ITEM, id,
                new BlockItem(bloque, new Item.Settings())));
        return bloque;
    }
}
