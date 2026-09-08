package net.pokereport.luna.pase;

/**
 * Una recompensa de un nivel del pase.
 *
 * <p>⚠ El {@code id} es lo que se entrega, no un indice de una tabla: un indice
 * guardado se convierte en «salio otra cosa» retroactivamente el dia que el
 * catalogo se reordene. Es la misma leccion que {@code crate_open.prize} y que
 * el bit de la medalla.
 *
 * @param tipo     que clase de premio es
 * @param id       identificador de Cobblemon, de Minecraft, del cofre o del
 *                 cosmetico. Vacio en {@link Tipo#PLATA}
 * @param cantidad cuantos. En un cosmetico es siempre 1
 */
public record Recompensa(Tipo tipo, String id, int cantidad) {

    public enum Tipo {
        /** Plata (POKEDOLLAR). Solo en la via libre. */
        PLATA,
        /** Un objeto del juego. Solo en la via libre. */
        OBJETO,
        /** Llaves de un cofre de Tesoros. Solo en la via libre. */
        LLAVE,
        /**
         * Un cosmetico del catalogo. <b>Solo en la via Luna.</b>
         *
         * <p>Y esa separacion no es de estilo: ver {@link PaseCatalogo}.
         */
        COSMETICO
    }

    public static Recompensa plata(long cuanta) {
        return new Recompensa(Tipo.PLATA, "", (int) cuanta);
    }

    public static Recompensa objeto(String id, int cuantos) {
        return new Recompensa(Tipo.OBJETO, id, cuantos);
    }

    public static Recompensa llave(String cofre, int cuantas) {
        return new Recompensa(Tipo.LLAVE, cofre, cuantas);
    }

    public static Recompensa cosmetico(String id) {
        return new Recompensa(Tipo.COSMETICO, id, 1);
    }

    /**
     * El identificador de objeto con el que el cliente dibuja el icono.
     *
     * <p>⚠ Lo compone el SERVIDOR y viaja ya compuesto, igual que el texto de
     * los avisos: si lo compusiera el cliente, el formato viviria en dos sitios.
     */
    public String iconoObjeto() {
        return switch (tipo) {
            case PLATA -> "minecraft:gold_nugget";
            case OBJETO -> id;
            case LLAVE -> "minecraft:tripwire_hook";
            case COSMETICO -> "minecraft:player_head";
        };
    }
}
