package net.pokereport.luna.pase;

/**
 * Una recompensa de un nivel del pase.
 *
 * <p>&#9888; El {@code id} es lo que se entrega, no un indice de una tabla: un
 * indice guardado se convierte en «salio otra cosa» retroactivamente el dia que
 * el catalogo se reordene. Es la misma leccion que {@code crate_open.prize} y
 * que el bit de la medalla.
 *
 * @param tipo     que clase de premio es
 * @param id       identificador de Cobblemon (objeto o ESPECIE)
 * @param cantidad cuantos. En un Pokemon es siempre 1
 * @param nivel    solo Pokemon: a que nivel se entrega
 * @param shiny    solo Pokemon
 * @param rareza   lo que decide COMO SE DIBUJA la tarjeta. Ver {@link Rareza}
 */
public record Recompensa(Tipo tipo, String id, int cantidad, int nivel,
                         boolean shiny, Rareza rareza) {

    public enum Tipo {
        /** Un objeto del juego. */
        OBJETO,
        /**
         * Un Pokemon, entregado al equipo.
         *
         * <p>&#9888;&#9888; ES LA UNICA RECOMPENSA QUE NO ES UN OBJETO, y por eso
         * lleva sus propios campos ({@code nivel}, {@code shiny}). Meterlos en el
         * {@code id} como texto --«charizard level=15 shiny»-- habria funcionado
         * y habria dejado el formato en DOS sitios: quien lo escribe y quien lo
         * lee. Es el fallo del payload del escaparate, que se come mercancia en
         * silencio.
         */
        POKEMON
    }

    /**
     * CUANTO PESA UN PREMIO, y es una decision de PRESENTACION.
     *
     * <p>&#9888;&#9888; No cambia lo que se entrega ni lo que cuesta: cambia el
     * color del marco, el brillo y si la tarjeta echa chispas. Existe porque un
     * carril de cien tarjetas iguales <b>no se lee</b>: el jugador no sabe donde
     * mirar. Con rareza, los hitos se ven desde la otra punta del carril.
     */
    public enum Rareza {
        COMUN, RARA, EPICA, LEGENDARIA;

        /** Color del marco de la tarjeta. */
        public int color() {
            return switch (this) {
                case COMUN -> 0xFF8FA0C8;
                case RARA -> 0xFF4FA8FF;
                case EPICA -> 0xFFB98CFF;
                case LEGENDARIA -> 0xFFFFD65C;
            };
        }

        /** Como se llama en la tarjeta. */
        public String nombre() {
            return switch (this) {
                case COMUN -> "COMUN";
                case RARA -> "RARA";
                case EPICA -> "EPICA";
                case LEGENDARIA -> "LEGENDARIA";
            };
        }
    }

    // ---- constructores comodos --------------------------------------------

    public static Recompensa objeto(String id, int cuantos, Rareza rareza) {
        return new Recompensa(Tipo.OBJETO, id, cuantos, 0, false, rareza);
    }

    public static Recompensa pokemon(String especie, int nivel, boolean shiny,
                                     Rareza rareza) {
        return new Recompensa(Tipo.POKEMON, especie, 1, nivel, shiny, rareza);
    }

    /**
     * El identificador de objeto con el que el cliente dibuja el icono.
     *
     * <p>&#9888; Un Pokemon NO tiene objeto: el cliente dibuja su modelo 3D. Se
     * devuelve una Poke Ball como respaldo por si algun dia se dibuja en un
     * sitio donde no quepa un modelo.
     */
    public String iconoObjeto() {
        return tipo == Tipo.POKEMON ? "cobblemon:poke_ball" : id;
    }

    /**
     * Las propiedades con las que Cobblemon crea el Pokemon.
     *
     * <p>&#9888; Se compone AQUI y en ningun otro sitio. `PokemonProperties.parse`
     * lee una cadena, asi que si la compusiera quien entrega, el formato viviria
     * en dos sitios -- y un desacuerdo entre ellos NO daria ningun error: daria
     * un Pokemon de nivel 1 sin shiny, con el jugador convencido de que le han
     * dado otra cosa.
     */
    public String propiedades() {
        return id + " level=" + Math.max(1, nivel) + (shiny ? " shiny=true" : "");
    }
}
