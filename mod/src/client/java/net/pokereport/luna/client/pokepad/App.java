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
        // ⚠ Se enciende con su pantalla (2026-08-27). Las paradas
        //   estaban en EXPLORAR y ahi estaban mal: Explorar responde a
        //   «¿a que mundo voy?» y esto a «¿a que esquina de la ciudadela
        //   voy?». Son dos preguntas distintas.
        new App("warps",      true),
        new App("clan",       true),
        // ⚠ EL MERCADO VA EN EL ICONO `gts`, que ya existe y ya esta
        //   dibujado: dos Poke Balls intercambiandose con una gema en
        //   medio. Es literalmente lo que hace esta pantalla.
        new App("gts",        true),
        new App("tienda",     true),
        new App("tesoros",    false),
        new App("wiki",       false),
        // ⚠ Se enciende con su pantalla (2026-08-25). La logica
        //   --HuntService-- llevaba escrita desde PHASE 5.
        new App("cazas",      true),
        // ⚠ Se enciende con su pantalla (2026-08-28): los TRAJES DE RANGO.
        //   El usuario los puso aqui, no en un icono propio, y tiene
        //   razon: un traje ES un kit de rango. De paso no hace falta
        //   arte nuevo -- este icono ya estaba dibujado desde el v4.
        new App("kits",       true),
        // ⚠ Se enciende con su pantalla (2026-08-26). Es la unica del Pad
        //   con CONTENEDOR: arrastrar objetos lo hace Minecraft, no nosotros.
        new App("mochila",    true),
        // ⚠ Se enciende con su pantalla: los DIECISEIS gimnasios, ocho por
        //   region. La logica --medallas, requisitos-- llevaba escrita desde
        //   que existe `Gimnasio`; lo unico que faltaba era la puerta.
        new App("gyms",       true),
        // ⚠ Se enciende con su pantalla (2026-08-27): los dos mundos.
        new App("explorar",   true),
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
