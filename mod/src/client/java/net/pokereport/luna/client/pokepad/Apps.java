package net.pokereport.luna.client.pokepad;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Qué pasa al pulsar cada aplicación del PokePad.
 *
 * <p><b>Vive aparte de {@link PokePadScreen} a propósito.</b> Esa clase dibuja;
 * esta decide a dónde se va. Cuando haya quince aplicaciones abiertas, mezclar
 * las dos cosas convertiría la pantalla en un archivo con dos temas dentro.
 *
 * <p><b>Y al cerrar la aplicación se vuelve al Pad</b>, no al juego. Es lo que
 * hace un teléfono, y es la mitad de lo que convierte quince iconos en un
 * sistema en vez de en quince atajos: si cerrar te devuelve al mundo, abrir dos
 * cosas seguidas obliga a sacar el Pad otra vez.
 */
public final class Apps {

    private static final Logger LOG = LoggerFactory.getLogger("LunaEternal");

    /**
     * La Pokédex de Cobblemon.
     *
     * <p><b>Se llama por reflexión y no compilando contra ella</b>, aunque el mod
     * sí compile contra su API. El motivo es el mismo que con el haz de la Poké
     * Ball: {@code PokedexGUI.open} no es una API que ellos publiquen ni se
     * comprometan a mantener, y con una llamada directa el día que la renombren
     * el jugador se encuentra con la pantalla reventada al pulsar el icono. Así,
     * como mucho, el icono no hace nada y queda una línea en el log.
     */
    private static final String CLASE_GUI =
            "com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI";
    private static final String CLASE_TIPO =
            "com.cobblemon.mod.common.client.pokedex.PokedexType";
    private static final String CLASE_CLIENTE =
            "com.cobblemon.mod.common.client.CobblemonClient";

    /** Está puesto solo mientras el Pad espera que le devuelvan el control. */
    private static boolean volverAlPad;
    private static boolean enganchado;

    private Apps() {}

    /**
     * Abre lo que haya detrás de una aplicación.
     *
     * @return {@code false} si no lleva a ningún sitio todavía, para que quien
     *         llama haga sonar el «bloqueado» en vez del clic normal
     */
    public static boolean abrir(App app) {
        if (!app.abierta()) {
            return false;
        }
        return switch (app.id()) {
            case "pokedex" -> abrirPokedex();
            default -> false;
        };
    }

    private static boolean abrirPokedex() {
        MinecraftClient cliente = MinecraftClient.getInstance();
        try {
            Class<?> gui = Class.forName(CLASE_GUI);
            Class<?> tipo = Class.forName(CLASE_TIPO);
            // El tipo es el COLOR de la carcasa, y se pide RED porque es el
            // mismo por defecto que usa Cobblemon al abrirla con el objeto: que
            // el Pad la abra de otro color haría creer que son dos Pokédex.
            Object rojo = Enum.valueOf(tipo.asSubclass(Enum.class), "RED");
            // ⚠ `CobblemonClient` es un `object` de Kotlin, no una clase con
            // estáticos: su getter es de INSTANCIA y se llega por `INSTANCE`.
            // Invocarlo con receptor nulo compila igual —es reflexión— y
            // revienta en la cara del jugador al pulsar el icono. Se comprobó
            // leyendo su fuente, no probándolo.
            Class<?> cc = Class.forName(CLASE_CLIENTE);
            Object datos = cc.getMethod("getClientPokedexData")
                    .invoke(cc.getField("INSTANCE").get(null));

            // `open` vive en el companion de Kotlin y tiene dos parámetros con
            // valor por defecto; desde fuera de Kotlin hay que pasarlos todos.
            Object companion = gui.getField("Companion").get(null);
            for (var m : companion.getClass().getMethods()) {
                if (!m.getName().equals("open") || m.getParameterCount() != 4) {
                    continue;
                }
                engancharVuelta();
                volverAlPad = true;
                m.invoke(companion, datos, rojo, null, null);
                return true;
            }
            LOG.warn("Cobblemon ya no tiene PokedexGUI.open(4 argumentos)");
        } catch (Throwable e) {
            LOG.warn("No se pudo abrir la Pokedex desde el PokePad: {}", e.toString());
        }
        volverAlPad = false;
        return false;
    }

    /**
     * Deja puesto el aviso de «cuando esta pantalla se cierre, vuelve al Pad».
     *
     * <p>Se engancha <b>una sola vez</b> y para siempre: {@code ScreenEvents}
     * no tiene forma de desenganchar, así que registrarlo en cada clic dejaría
     * una pila de oyentes que crece toda la partida.
     */
    private static void engancharVuelta() {
        if (enganchado) {
            return;
        }
        enganchado = true;
        ScreenEvents.AFTER_INIT.register((cliente, pantalla, ancho, alto) -> {
            if (!volverAlPad || !esPokedex(pantalla)) {
                return;
            }
            // Se consume aquí: si el jugador abre la Pokédex otra vez con el
            // objeto, esa no tiene por qué devolverle al Pad.
            volverAlPad = false;
            ScreenEvents.remove(pantalla).register(cerrada ->
                    // En el tick siguiente, no ahora: durante `remove` la
                    // pantalla nueva todavía no está puesta, y preguntar por
                    // ella aquí devuelve siempre la que se está yendo.
                    cliente.execute(() -> {
                        if (cliente.currentScreen == null) {
                            cliente.setScreen(new PokePadScreen());
                        }
                    }));
        });
    }

    private static boolean esPokedex(Screen pantalla) {
        return pantalla != null && pantalla.getClass().getName().equals(CLASE_GUI);
    }
}
