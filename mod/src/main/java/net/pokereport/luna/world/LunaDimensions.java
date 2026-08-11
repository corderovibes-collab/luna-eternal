package net.pokereport.luna.world;

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
    public static final RegistryKey<World> SALVAJE   = key("salvaje");

    /** El Mundo Hogar es el overworld de toda la vida. */
    public static final RegistryKey<World> HOGAR = World.OVERWORLD;

    private LunaDimensions() {}

    private static RegistryKey<World> key(String path) {
        return RegistryKey.of(RegistryKeys.WORLD,
            Identifier.of(LunaEternal.MOD_ID, path));
    }
}
