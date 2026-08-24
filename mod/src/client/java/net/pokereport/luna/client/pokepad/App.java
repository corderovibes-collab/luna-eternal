package net.pokereport.luna.client.pokepad;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Una aplicación del PokePad: la celda que se ve en la rejilla.
 *
 * @param id       identificador estable. Da el icono y la clave de traducción
 * @param abierta  {@code false} mientras la pantalla no exista: la celda sale
 *                 bloqueada, con su candado, en vez de desaparecer. Enseñar lo
 *                 que va a haber es información; esconderlo, no
 */
public record App(String id, boolean abierta) {

    /**
     * Las quince, en el orden en que se dibujan: cinco columnas, tres filas.
     *
     * <p>El orden es el del arte, y no es casualidad que coincida: las texturas
     * se trocean con {@code tools/gen_pokepad.py}, que usa esta misma lista.
     * Si aquí cambia el orden, hay que cambiarlo allí.
     */
    public static final App[] TODAS = {
        // La primera que se abre de verdad: lleva a la Pokédex de Cobblemon.
        new App("pokedex",    true),
        new App("cosmeticos", true),
        new App("trabajos",   true),
        new App("misiones",   true),
        new App("warps",      false),
        new App("clan",       true),
        new App("gts",        false),
        new App("tienda",     true),
        new App("tesoros",    false),
        new App("wiki",       false),
        new App("cazas",      false),
        new App("kits",       false),
        new App("mochila",    false),
        new App("gyms",       false),
        new App("explorar",   false),
        // ⚠ LA DECIMOSEXTA, y por eso cae en la PAGINA 2. No hubo que
        //   quitar ninguna: la rejilla ya pagina y reordena (OrdenPad).
        new App("curar",      true),
    };

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
