package net.pokereport.neon;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
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
    }

    private static <T extends Block> T registrar(String nombre, T bloque) {
        Identifier id = Identifier.of(MOD_ID, nombre);
        Registry.register(Registries.BLOCK, id, bloque);
        ORDEN.add(Registry.register(Registries.ITEM, id,
                new BlockItem(bloque, new Item.Settings())));
        return bloque;
    }
}
