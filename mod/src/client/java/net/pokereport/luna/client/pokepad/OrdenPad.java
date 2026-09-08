package net.pokereport.luna.client.pokepad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * En qué orden ve cada jugador sus quince aplicaciones.
 *
 * <p><b>Esto vive en el CLIENTE y es deliberado</b>, aunque P6 diga que del
 * cliente no se fía uno. La regla habla de <i>validación económica</i>: aquí no
 * hay nada que ganar falsificando el fichero — lo peor que consigue quien lo
 * edite a mano es ver sus propios iconos en otro orden. Meterlo en la base de
 * datos costaría una migración, un par de paquetes de red y un viaje de ida y
 * vuelta cada vez que se abre el Pad, para resolver un problema que no existe.
 *
 * <p>Lo que sí se hace es <b>no fiarse del contenido</b>: el fichero se lee
 * como una sugerencia, no como la verdad. Se ignoran los identificadores que no
 * existen y se añaden al final los que falten, así que una lista corrupta, a
 * medias o de una versión antigua del Pad da como mucho un orden raro — nunca
 * una pantalla con huecos ni una excepción.
 */
public final class OrdenPad {

    private static final Logger LOG = LoggerFactory.getLogger("LunaEternal");
    private static final Gson GSON = new Gson();

    private static final Path FICHERO = FabricLoader.getInstance()
            .getConfigDir().resolve("lunaeternal-pokepad.json");

    private OrdenPad() {}

    /** Lo que se guarda. Un registro y no un array suelto: así el fichero puede
     *  crecer con otras preferencias sin romper al que ya lo tenga. */
    private record Guardado(String[] orden) {}

    /**
     * El orden del jugador, ya saneado. Siempre devuelve las quince.
     */
    public static App[] leer() {
        try {
            if (Files.exists(FICHERO)) {
                Guardado g = GSON.fromJson(Files.readString(FICHERO), Guardado.class);
                if (g != null && g.orden() != null) {
                    return completar(g.orden());
                }
            }
        } catch (IOException | RuntimeException e) {
            // Un fichero corrupto no puede dejar sin Pad a nadie: se avisa y se
            // sigue con el orden de fábrica.
            LOG.warn("No se pudo leer el orden del PokePad ({}), se usa el de "
                    + "fabrica: {}", FICHERO, e.toString());
        }
        return App.TODAS.clone();
    }

    public static void guardar(App[] orden) {
        String[] ids = new String[orden.length];
        for (int i = 0; i < orden.length; i++) {
            ids[i] = orden[i].id();
        }
        try {
            Files.createDirectories(FICHERO.getParent());
            Files.writeString(FICHERO, GSON.toJson(new Guardado(ids)));
        } catch (IOException e) {
            // Que no se pueda guardar no debe estropear la sesión en curso: el
            // orden nuevo sigue vivo en memoria hasta que se cierre el juego.
            LOG.warn("No se pudo guardar el orden del PokePad: {}", e.toString());
        }
    }

    /**
     * Convierte una lista de identificadores en las quince aplicaciones.
     *
     * <p>Tolerante a propósito, y en las dos direcciones: <b>fuera lo que no
     * existe</b> (una aplicación que se quitó del Pad) y <b>dentro lo que
     * falta</b> (una que se añadió después de guardar el fichero). Sin esto,
     * añadir la aplicación dieciséis dejaría un hueco negro en la pantalla de
     * todo el que ya hubiera tocado el orden.
     *
     * <h2>⚠⚠ UNA APLICACIÓN NUEVA ENTRA EN SU SITIO DE FÁBRICA, NO AL FINAL</h2>
     *
     * Antes se añadían al final, y eso tiene un fallo que no se ve hasta que
     * alguien decide dónde va algo: el 2026-09-02 se puso <b>CARTAS en la
     * primera página</b> y la <b>WIKI en la segunda</b>, y con el pegado al
     * final <b>quien hubiera tocado su orden alguna vez se habría encontrado
     * justo lo contrario</b> — cartas en la página 2 y la wiki donde estaba.
     * Sin ningún error, y solo para una parte de la gente, que es como esto se
     * descubre tarde.
     *
     * <p>Y no pisa nada que el jugador haya elegido: de una aplicación que
     * acaba de existir <b>no tenía ninguna opinión guardada</b>. Lo que él
     * movió sigue movido; lo nuevo aparece donde lo pusimos nosotros.
     */
    private static App[] completar(String[] ids) {
        List<App> salida = new ArrayList<>(App.TODAS.length);
        // LinkedHashSet y no List: además de conservar el orden, descarta los
        // repetidos, que es la otra forma de corromper el fichero a mano.
        for (String id : new LinkedHashSet<>(List.of(ids))) {
            App app = App.de(id);
            if (app != null) {
                salida.add(app);
            }
        }
        // Se recorre en orden de fábrica para que, si faltan varias, cada una
        // caiga por delante de la siguiente y no se adelanten entre ellas.
        for (int fabrica = 0; fabrica < App.TODAS.length; fabrica++) {
            App app = App.TODAS[fabrica];
            if (!salida.contains(app)) {
                // ⚠ Acotado: la lista guardada puede ser más corta que la de
                //   fábrica —le faltan varias— y ahí `fabrica` se sale.
                salida.add(Math.min(fabrica, salida.size()), app);
            }
        }
        return salida.toArray(new App[0]);
    }
}
