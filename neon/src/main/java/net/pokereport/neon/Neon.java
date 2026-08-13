package net.pokereport.neon;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lo que comparten las seis formas de neón.
 *
 * <p><b>Brillo y luz son dos cosas distintas, y esta es la idea entera del
 * mod.</b> Un bloque de neón siempre se <i>dibuja</i> a tope; lo que se regula
 * es cuánta luz <i>suelta</i> al mundo:
 *
 * <pre>
 *   luz=0   apagado   se ve encendido, no ilumina nada
 *   luz=1   suave     ilumina 7   — ambiente, callejones
 *   luz=2   pleno     ilumina 15  — farolas, plazas
 * </pre>
 *
 * <p>Sin esa separación, una ciudadela con mil neones deja de ser nocturna: si
 * cada bloque tuviera que iluminar para verse encendido, la plaza acabaría con
 * luz de mediodía y el cielo de noche fija ({@code fixed_time 18000}) no
 * serviría de nada. El truco no es nuestro: es literalmente lo que hace el
 * bloque de magma de vanilla, que se ve al rojo vivo y solo da luz 3.
 */
public final class Neon {

    /** Cuánta luz suelta el bloque. Ver la tabla de arriba. */
    public static final IntProperty LUZ = IntProperty.of("luz", 0, 2);

    /** Luz emitida por cada valor de {@link #LUZ}. El índice es el valor. */
    public static final int[] NIVELES = {0, 7, 15};

    /**
     * Un neón recién puesto va encendido del todo.
     *
     * <p>Es lo que espera quien coloca un bloque llamado «neón»; apagarlo
     * después es un clic, y descubrir por qué no ilumina es media tarde.
     */
    public static final int POR_DEFECTO = 2;

    private Neon() {}

    /**
     * Los ajustes comunes de un bloque de neón.
     *
     * @param solido {@code false} para las formas que no llenan el cubo (panel
     *               y tubo): sin eso Minecraft las trata como opacas, tapa las
     *               caras de los bloques vecinos y deja agujeros negros.
     */
    public static AbstractBlock.Settings ajustes(MapColor mapa, BlockSoundGroup sonido,
                                                 boolean solido) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create()
                .mapColor(mapa)
                .strength(0.9f)
                .sounds(sonido)
                .luminance(estado -> NIVELES[estado.get(LUZ)])
                // Dibujarse a brillo máximo pase lo que pase. Es la mitad del
                // efecto neón: sin esto, un bloque con luz=0 en una calle
                // oscura se vería gris, no encendido.
                .emissiveLighting((estado, mundo, pos) -> true);
        return solido ? s : s.nonOpaque();
    }

    /**
     * Clic derecho con la mano vacía: cambia el brillo.
     *
     * <p>Es la forma de tocarlo sin salir de la construcción. También se puede
     * por comando ({@code //set lunaneon:neon_cian[luz=0]}) o con el selector
     * de estados de Axiom, pero eso es para pintar cien de golpe; para ajustar
     * uno, un clic.
     */
    public static ActionResult ciclar(BlockState estado, World mundo, BlockPos pos,
                                      PlayerEntity jugador) {
        // Regular el brillo es una herramienta de construcción, no de juego. Se
        // pide el MISMO nivel que ya exige Axiom, el 2 (D-028), para no inventar
        // un segundo sistema de permisos.
        if (!jugador.getAbilities().creativeMode && !jugador.hasPermissionLevel(2)) {
            return ActionResult.PASS;
        }
        if (mundo.isClient) {
            return ActionResult.SUCCESS;
        }

        int siguiente = (estado.get(LUZ) + 1) % NIVELES.length;
        mundo.setBlockState(pos, estado.with(LUZ, siguiente), Block.NOTIFY_ALL);
        // El tono sube con el brillo: se oye cuál de los tres has puesto sin
        // tener que mirar el mensaje.
        mundo.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.BLOCKS, 0.6f, 0.9f + 0.25f * siguiente);
        jugador.sendMessage(Text.literal(nombre(siguiente)).formatted(Formatting.AQUA), true);
        return ActionResult.SUCCESS;
    }

    private static String nombre(int valor) {
        return switch (valor) {
            case 0 -> "Neón apagado · no ilumina";
            case 1 -> "Neón suave · luz " + NIVELES[1];
            default -> "Neón pleno · luz " + NIVELES[2];
        };
    }
}
