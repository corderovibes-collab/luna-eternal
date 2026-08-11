package net.pokereport.luna.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.pokereport.luna.LunaEternal;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

/**
 * Fondos propios en los menús, sin mod de cliente (D-023).
 *
 * <p>Un menú de cofre se ve gris porque el cliente dibuja la textura del
 * cofre. No se puede cambiar esa textura desde el servidor… pero <b>sí se
 * puede dibujar encima</b>: el título del menú es texto, y un resource pack
 * puede definir una fuente cuyos caracteres sean imágenes y cuyos espacios
 * tengan anchura <b>negativa</b>.
 *
 * <p>Así, el título se convierte en: «retrocede 8 píxeles, pinta el fondo,
 * vuelve al principio, y ahora escribe el título de verdad». El servidor solo
 * envía texto, el cliente no instala nada y P6 queda intacto.
 *
 * <p>Las cadenas exactas las calcula {@code tools/gen_resourcepack.py} y las
 * deja en {@code gui_chars.json}. <b>La geometría no se duplica aquí</b>: si
 * cambia el tamaño de una textura, se regenera el pack y este código no se
 * toca.
 */
public final class Skin {

    private static final Identifier FONT = Identifier.of("lunaeternal", "gui");

    /** Nombre de pantalla → cadena que dibuja su fondo. */
    private static final Map<String, String> FONDOS = new HashMap<>();

    /** Si no hay mapa, los menús salen en gris y el servidor sigue vivo. */
    private static boolean disponible = false;

    private Skin() {}

    public static void load() {
        try (InputStream in = Skin.class.getResourceAsStream("/gui_chars.json")) {
            if (in == null) {
                LunaEternal.LOG.warn(
                    "gui_chars.json no está en el jar: los menús saldrán sin fondo. "
                  + "Genera el pack con tools/gen_resourcepack.py");
                return;
            }
            JsonObject o = JsonParser
                .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
            for (var e : o.entrySet()) {
                FONDOS.put(e.getKey(), e.getValue().getAsString());
            }
            disponible = !FONDOS.isEmpty();
            LunaEternal.LOG.info("Interfaz: {} fondos cargados", FONDOS.size());
        } catch (Exception e) {
            // Un fallo aquí es cosmético. Nunca debe impedir arrancar.
            LunaEternal.LOG.error("No se pudieron cargar los fondos de interfaz", e);
        }
    }

    /**
     * Título de un menú, con su fondo dibujado detrás.
     *
     * @param skin  nombre de la pantalla, o {@code null} para no dibujar fondo
     * @param title texto visible
     */
    public static Text title(String skin, String title) {
        MutableText plain = Text.literal(title)
            .setStyle(Style.EMPTY.withItalic(false));

        if (!disponible || skin == null) return plain;

        String fondo = FONDOS.get(skin);
        if (fondo == null) {
            LunaEternal.LOG.warn("No hay fondo llamado '{}'", skin);
            return plain;
        }

        // El glifo va en BLANCO explícito. Sin él, el juego pinta el título
        // de un cofre en gris oscuro (0x404040) y teñiría toda la textura.
        //
        // No hace falta desactivar la sombra: `HandledScreen` dibuja el
        // título con shadow=false, así que no la hay. En 1.21.1 tampoco
        // existe `Style.withShadowColor` — llegó en 1.21.4.
        MutableText fondoTexto = Text.literal(fondo).setStyle(
            Style.EMPTY.withFont(FONT)
                       .withColor(Formatting.WHITE)
                       .withItalic(false));

        return fondoTexto.append(plain);
    }

    /** Para el autotest: ¿se cargó el mapa de fondos? */
    public static boolean disponible() {
        return disponible;
    }

    /** Para el autotest: cuántos fondos hay. */
    public static int cuantos() {
        return FONDOS.size();
    }

    /** Para el autotest: ¿existe este fondo? */
    public static boolean tiene(String skin) {
        return FONDOS.containsKey(skin);
    }
}
