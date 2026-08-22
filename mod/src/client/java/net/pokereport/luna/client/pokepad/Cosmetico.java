package net.pokereport.luna.client.pokepad;

/**
 * Un artículo de la tienda de cosméticos, tal y como lo dibuja el Pad.
 *
 * <p><b>Esto es un espejo de lo que manda el servidor, no una fuente.</b> El
 * cliente no decide qué existe, qué vale ni qué posees: lo recibe y lo pinta
 * (P6). Y con {@code D-039} eso deja de ser una buena costumbre y pasa a ser un
 * invariante — si los cosméticos <b>solo</b> se consiguen comprándolos o en un
 * evento, el servidor es la única fuente que hay, y un cliente que pudiera
 * concederse uno sería la única forma de saltárselo.
 *
 * @param categoria  la pestaña donde sale: {@code mascotas}, {@code capas},
 *                   {@code sombreros} o {@code auras}
 * @param id         identificador estable, el que viaja al servidor al comprar
 *                   o equipar. <b>Nunca el nombre visible</b>: ese se traduce
 *                   y cambiaría el significado del dato
 * @param especie    para las mascotas, la especie de Cobblemon
 *                   ({@code cobblemon:charizard}). Vacío en las demás
 * @param aspecto    el aspecto cosmético de Cobblemon ({@code knight}), que es
 *                   el mecanismo con el que su renderizador cambia el modelo.
 *                   Vacío en las demás
 * @param precio     en LunaCoins. {@code 0} significa <b>no está a la venta</b>
 *                   — un cosmético de evento, que no se puede comprar aunque no
 *                   lo tengas
 * @param poseido    lo tienes en tu colección
 * @param equipado   además, lo llevas puesto ahora mismo
 */
public record Cosmetico(
        String categoria,
        String id,
        String especie,
        String aspecto,
        int precio,
        boolean poseido,
        boolean equipado) {

    /**
     * Qué botón toca dibujar. Son tres estados y no dos, y esa es justo la
     * distinción que evita que alguien pague dos veces por lo mismo.
     *
     * <p>El orden de las comprobaciones importa: <b>equipado implica poseído</b>,
     * así que preguntar por poseído primero taparía el estado de equipado y la
     * pantalla no sabría decir cuál llevas puesto.
     */
    public Estado estado() {
        if (equipado) {
            return Estado.EQUIPADO;
        }
        if (poseido) {
            return Estado.EQUIPAR;
        }
        return precio > 0 ? Estado.COMPRAR : Estado.DE_EVENTO;
    }

    public enum Estado {
        /** No lo tienes y se vende. */
        COMPRAR,
        /** No lo tienes y NO se vende: solo sale en eventos (D-039). */
        DE_EVENTO,
        /** Lo tienes, pero llevas puesto otro. */
        EQUIPAR,
        /** Lo llevas puesto. */
        EQUIPADO
    }

    /** Tiene algo que dibujar en 3D. */
    public boolean esMascota() {
        return !especie.isEmpty();
    }

    /**
     * Criatura de Minecraft en vez de Pokémon.
     *
     * <p>Se distingue por el <b>espacio de nombres</b>, no por una bandera
     * aparte: {@code minecraft:bee} y {@code cobblemon:eevee} ya llevan la
     * respuesta dentro, y una bandera extra podría contradecir al identificador.
     *
     * <p>Importa porque las dibuja código distinto: Cobblemon tiene su propio
     * renderizador de modelos, y estas van por el de entidades de vanilla.
     */
    public boolean esDeMinecraft() {
        return especie.startsWith("minecraft:");
    }
}
