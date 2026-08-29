package net.pokereport.luna.world;

import java.util.List;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.pokereport.luna.LunaEternal;

/**
 * Las dimensiones del servidor ({@code docs/world/worlds.md}).
 *
 * <p>Cuatro espacios, cada uno con una función:
 * <ul>
 *   <li>{@link #LOBBY} — vestíbulo. Vacío, noche fija, sin servicios.</li>
 *   <li>{@link #CIUDADELA} — servicios y comunidad. Vacío, mediodía fijo.</li>
 *   <li><b>Mundo Hogar</b> — es el {@code overworld} de siempre. Permanente.</li>
 *   <li>{@link #SALVAJE} — caza y exploración. Se reinicia por temporada.</li>
 * </ul>
 *
 * <p><b>Por qué el Hogar es el overworld y no una dimensión propia:</b> así el
 * reinicio del Salvaje se hace borrando <i>su</i> carpeta de regiones, sin
 * tocar en ningún momento el mundo donde la gente tiene la casa. La operación
 * más destructiva del servidor queda aislada del dato más valioso.
 */
public final class LunaDimensions {

    public static final RegistryKey<World> LOBBY     = key("lobby");
    public static final RegistryKey<World> CIUDADELA = key("ciudadela");
    /**
     * LOS SEIS MUNDOS SALVAJES.
     *
     * <h2>⚠⚠ SE DECLARAN LOS SEIS AUNQUE SOLO TRES SE USEN</h2>
     *
     * Fabric <b>no puede crear dimensiones en caliente</b>: o están declaradas
     * al arrancar o no existen. Con los seis declarados, la rotación semanal es
     * <b>cambiar cuáles son los activos</b> —tres números— en vez de crear
     * mundos, que no se puede hacer.
     *
     * <p>⚠ Y un mundo declarado y vacío no cuesta casi nada: Minecraft solo
     * mantiene cargados los chunks de aparición del <b>overworld</b>. Una
     * dimensión propia sin nadie dentro no tickea chunks.
     *
     * <p>⚠ El primero se sigue llamando {@code salvaje} y no {@code salvaje1}:
     * ese mundo <b>ya tiene datos en el disco del servidor</b>, y renombrarlo
     * los dejaría huérfanos.
     */
    public static final List<RegistryKey<World>> SALVAJES = List.of(
        key("salvaje"), key("salvaje2"), key("salvaje3"),
        key("salvaje4"), key("salvaje5"), key("salvaje6"));

    /** El primero. Lo usan los comandos que hablan de «el salvaje» a secas. */
    public static final RegistryKey<World> SALVAJE = SALVAJES.get(0);

    /** El Mundo Hogar es el overworld de toda la vida. */
    public static final RegistryKey<World> HOGAR = World.OVERWORLD;

    /**
     * LAS ARENAS DE LOS GIMNASIOS.
     *
     * <p>⚠⚠ UNA SOLA DIMENSION PARA LOS OCHO, y ademas para todas sus copias.
     * Fabric no crea dimensiones en caliente, asi que una por gimnasio serian
     * ocho ficheros hoy y un reinicio general el dia que haya un noveno. Y cada
     * dimension arrastra su carpeta de mundo en un disco que no tiene ni una
     * ranura de copia de seguridad.
     *
     * <p>Repartidas por coordenada: el gimnasio manda en X y la copia en Z.
     * Añadir un gimnasio es una linea en {@code Gimnasio.TODOS}.
     */
    public static final RegistryKey<World> GIMNASIOS = key("gimnasios");

    private LunaDimensions() {}

    private static RegistryKey<World> key(String path) {
        return RegistryKey.of(RegistryKeys.WORLD,
            Identifier.of(LunaEternal.MOD_ID, path));
    }
}
