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
        POKEMON,
        /**
         * LunaCoins.
         *
         * <p>&#9888;&#9888;&#9888; ES EL UNICO PREMIO QUE NO SE ENTREGA: SE
         * INGRESA. Un objeto y un Pokemon van a un inventario, que no es una
         * tabla y por eso se reparten <b>despues</b> de apuntar el cobro. Esto
         * es <b>dinero</b>, asi que le aplica R3 y va <b>dentro de la misma
         * transaccion</b> que la fila de {@code pase_reclamo}: o se apunta y se
         * paga, o no pasa ninguna de las dos cosas.
         *
         * <p>&#9888;&#9888; Y NO CRUZA D-014, que es la regla de la que cuelga
         * todo el modelo de pago: <b>no convierte una moneda en otra</b>. El
         * pase se compra con LunaCoins y devuelve LunaCoins &mdash; es un
         * reembolso, no un tipo de cambio. La Torre ya hacia esto mismo (+50
         * cada 30 rondas), asi que no es una categoria nueva.
         */
        MONEDA
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
     * LunaCoins.
     *
     * <p>&#9888; El {@code id} se queda con el nombre de la moneda y NO se usa
     * para buscar nada: quien paga mira {@link Tipo#MONEDA}, no el texto. Esta
     * ahi para que una fila del catalogo se lea sola.
     */
    public static Recompensa luna(int cuantas, Rareza rareza) {
        return new Recompensa(Tipo.MONEDA, "lunacoins", cuantas, 0, false, rareza);
    }

    /**
     * El identificador de objeto con el que el cliente dibuja el icono.
     *
     * <p>&#9888; Un Pokemon NO tiene objeto: el cliente dibuja su modelo 3D. Se
     * devuelve una Poke Ball como respaldo por si algun dia se dibuja en un
     * sitio donde no quepa un modelo.
     */
    public String iconoObjeto() {
        return switch (tipo) {
            case POKEMON -> "cobblemon:poke_ball";
            // ⚠ Las LunaCoins no son un objeto del juego y no tienen ninguno
            //   que las represente: la pantalla las dibuja con SU PROPIA
            //   textura (`lunacoin_oro.png`), la misma del saldo. Esto es solo
            //   el respaldo por si algun dia se pintan donde no cabe.
            case MONEDA -> "minecraft:gold_nugget";
            case OBJETO -> id;
        };
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
