package net.pokereport.neon;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
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

        Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "neon"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(
                                Registries.ITEM.get(Identifier.of(MOD_ID, "neon_cian"))))
                        .displayName(Text.translatable("itemGroup." + MOD_ID + ".neon"))
                        .entries((contexto, entradas) -> ORDEN.forEach(entradas::add))
                        .build());

        LOG.info("Neon: {} bloques en {} colores", ORDEN.size(), Paleta.COLORES.length);

        registrarInterfazLuna();
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
        Identifier id = Identifier.of(MOD_ID, nombre);
        Registry.register(Registries.BLOCK, id, bloque);
        ORDEN.add(Registry.register(Registries.ITEM, id,
                new BlockItem(bloque, new Item.Settings())));
        return bloque;
    }
}
