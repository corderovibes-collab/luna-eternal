package net.pokereport.neon;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.entity.EntityType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * De qué está hecho cada material de obra: dureza, sonido, herramienta y cómo
 * se dibuja.
 *
 * <p>Al revés que {@link Neon}, aquí no hay ni un truco: son cinco recetas de
 * {@code Settings} y el resto lo pone vanilla. Los bloques se registran con las
 * clases de siempre —{@code SlabBlock}, {@code StairsBlock}, {@code WallBlock},
 * {@code FenceBlock}, {@code PillarBlock}, {@code PaneBlock}— porque una
 * escalera de hormigón se comporta exactamente igual que una de piedra, y
 * escribir una clase propia para cada forma sería reimplementar el juego para
 * acabar en el mismo sitio.
 *
 * <p>La lista de materiales no está aquí: está en {@link Catalogo}, que lo
 * genera {@code tools/gen_bloques.py} junto con las texturas y los modelos.
 */
public final class Ciudad {

    /**
     * Las cinco familias.
     *
     * <p>El orden es el de la pestaña del inventario, y el nombre en minúscula
     * es además el de su grupo creativo ({@code itemGroup.lunaneon.hormigon}).
     */
    public enum Familia {
        HORMIGON, METAL, REJILLA, VIDRIO, PAVIMENTO;

        /** Rejilla y metal comparten pestaña: son el mismo material. */
        public String grupo() {
            return this == REJILLA ? "metal" : name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Ciudad() {}

    /** Nunca. Para las tres o cuatro preguntas que el vidrio contesta igual. */
    private static boolean nunca(BlockState estado, BlockView mundo, BlockPos pos) {
        return false;
    }

    private static boolean nunca(BlockState estado, BlockView mundo, BlockPos pos,
                                 EntityType<?> tipo) {
        return false;
    }

    /**
     * Los ajustes de un material.
     *
     * <p><b>Cada bloque necesita los suyos, recién creados.</b> {@code Settings}
     * es mutable y el bloque se queda con la instancia: compartir una entre la
     * losa y la escalera hace que la segunda herede cosas de la primera —la
     * tabla de botín, entre otras— y el bloque suelte el objeto equivocado al
     * romperse.
     */
    public static AbstractBlock.Settings ajustes(Familia familia, MapColor mapa) {
        return switch (familia) {
            // Hormigón y pavimento: los números del hormigón de vanilla. Se
            // copian a propósito — un constructor ya sabe cuánto tarda en picar
            // un bloque de hormigón, y cambiarlo solo sería una sorpresa.
            case HORMIGON, PAVIMENTO -> AbstractBlock.Settings.create()
                    .mapColor(mapa)
                    .requiresTool()
                    .strength(1.8F, 6.0F)
                    .sounds(BlockSoundGroup.STONE);

            // Metal: duro como el bloque de hierro, y suena a metal.
            case METAL -> AbstractBlock.Settings.create()
                    .mapColor(mapa)
                    .requiresTool()
                    .strength(3.0F, 6.0F)
                    .sounds(BlockSoundGroup.METAL);

            // Rejilla: metal con agujeros de verdad. `nonOpaque` es lo que
            // impide que Minecraft tape las caras de lo que hay detrás, y
            // `solidBlock(nunca)` lo que deja pasar la luz — sin eso, una
            // pasarela de rejilla proyecta una sombra maciza y se ve como una
            // chapa negra.
            case REJILLA -> AbstractBlock.Settings.create()
                    .mapColor(mapa)
                    .requiresTool()
                    .strength(3.0F, 6.0F)
                    .sounds(BlockSoundGroup.COPPER_GRATE)
                    .nonOpaque()
                    .solidBlock(Ciudad::nunca)
                    .blockVision(Ciudad::nunca)
                    .suffocates(Ciudad::nunca);

            // Vidrio: los ajustes del cristal de vanilla, uno por uno. Los
            // cuatro predicados van juntos o no van: sin `suffocates` el
            // jugador se asfixia dentro de una vidriera, y sin `blockVision` la
            // pantalla se le pone negra.
            case VIDRIO -> AbstractBlock.Settings.create()
                    .mapColor(mapa)
                    .strength(0.3F)
                    .sounds(BlockSoundGroup.GLASS)
                    .nonOpaque()
                    .solidBlock(Ciudad::nunca)
                    .blockVision(Ciudad::nunca)
                    .suffocates(Ciudad::nunca)
                    .allowsSpawning(Ciudad::nunca);
        };
    }

    /**
     * El estado del que una escalera hereda su comportamiento.
     *
     * <p>{@code StairsBlock} lo pide y lo usa para cosas de las que nadie se
     * acuerda: qué partículas suelta al romperse, si arde, cómo suena al
     * caminar. Se le pasa el cubo entero del mismo material, que es justo lo
     * que hace vanilla.
     */
    public static BlockState base(net.minecraft.block.Block entero) {
        return entero == null ? Blocks.STONE.getDefaultState() : entero.getDefaultState();
    }
}
