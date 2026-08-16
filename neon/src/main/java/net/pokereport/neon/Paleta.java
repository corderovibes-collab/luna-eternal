package net.pokereport.neon;

import net.minecraft.block.MapColor;

/**
 * Los 16 colores del neón.
 *
 * <p><b>GENERADO por {@code tools/gen_bloques.py} — no editar a mano.</b> La
 * lista viva está en la constante {@code PALETA} de
 * {@code tools/bloques/neon.py}, que además dibuja las texturas y escribe los
 * modelos. Tenerla en dos sitios es como se registra un bloque cuya textura no
 * existe.
 *
 * @param id    parte del identificador: {@code neon_<id>}, {@code neon_<id>_losa}...
 * @param mapa  color en el mapa y en la brújula de localizador
 */
public record Paleta(String id, MapColor mapa) {

    public static final Paleta[] COLORES = {
        new Paleta("blanco", MapColor.WHITE),
        new Paleta("gris_claro", MapColor.LIGHT_GRAY),
        new Paleta("gris", MapColor.GRAY),
        new Paleta("negro", MapColor.BLACK),
        new Paleta("marron", MapColor.BROWN),
        new Paleta("rojo", MapColor.RED),
        new Paleta("naranja", MapColor.ORANGE),
        new Paleta("amarillo", MapColor.YELLOW),
        new Paleta("lima", MapColor.LIME),
        new Paleta("verde", MapColor.GREEN),
        new Paleta("cian", MapColor.CYAN),
        new Paleta("azul_claro", MapColor.LIGHT_BLUE),
        new Paleta("azul", MapColor.BLUE),
        new Paleta("morado", MapColor.PURPLE),
        new Paleta("magenta", MapColor.MAGENTA),
        new Paleta("rosa", MapColor.PINK),
    };
}
