package net.pokereport.luna.client.pokepad;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.pokepad.CatalogoPad;

/**
 * Una aplicación del PokePad: la celda que se ve en la rejilla.
 *
 * <p><b>La lista y su orden NO están aquí</b>, están en
 * {@link CatalogoPad} — que vive en {@code main} para que el servidor pueda leerla.
 * Lo que hay aquí es lo que solo sabe el cliente: dónde está el icono y cómo se
 * llama la aplicación en el idioma del jugador.
 *
 * @param id       identificador estable. Da el icono y la clave de traducción
 * @param abierta  {@code false} mientras la pantalla no exista: la celda sale
 *                 apagada, en vez de desaparecer. Enseñar lo que va a haber es
 *                 información; esconderlo, no
 */
public record App(String id, boolean abierta) {

    /** Las mismas de {@link CatalogoPad#TODAS}, en el mismo orden. */
    public static final App[] TODAS = CatalogoPad.TODAS.stream()
            .map(f -> new App(f.id(), f.abierta()))
            .toArray(App[]::new);

    /** La aplicación con ese identificador, o {@code null} si no existe. */
    public static App de(String id) {
        for (App app : TODAS) {
            if (app.id().equals(id)) {
                return app;
            }
        }
        return null;
    }

    public Identifier icono() {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + id + ".png");
    }

    public Text nombre() {
        return Text.translatable("pokepad.lunaeternal.app." + id);
    }

    public Text descripcion() {
        return Text.translatable("pokepad.lunaeternal.app." + id + ".desc");
    }
}
