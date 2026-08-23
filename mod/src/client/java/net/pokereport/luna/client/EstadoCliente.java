package net.pokereport.luna.client;

import net.pokereport.luna.net.Red;

/**
 * Lo último que dijo el servidor, para poder dibujarlo.
 *
 * <p>Es una caché de <b>presentación</b>, no una fuente de verdad: aquí no se
 * decide nada. Cualquier operación real —comprar, vender, cobrar— la valida el
 * servidor con sus propios datos (P6). Si esto mintiera, lo peor que pasa es
 * que el jugador ve un número viejo un segundo.
 *
 * <p>Empieza sin saber nada a propósito. Mientras no llegue la respuesta, el
 * Pad enseña guiones en vez de un cero: <b>«no lo sé» y «tienes cero» no son lo
 * mismo</b>, y un cero falso en un saldo asusta.
 */
public final class EstadoCliente {

    private static Red.Saldo saldo;
    private static Red.Ficha ficha;
    private static Red.Cosmeticos cosmeticos;
    private static Red.Trabajos trabajos;
    private static Red.Misiones misiones;

    private EstadoCliente() {}

    public static void guardar(Red.Saldo nuevo) {
        saldo = nuevo;
    }

    public static void guardar(Red.Ficha nueva) {
        ficha = nueva;
    }

    /** {@code null} mientras no haya llegado nada del servidor. */
    public static Red.Ficha ficha() {
        return ficha;
    }

    /** {@code null} mientras no haya llegado nada del servidor. */
    public static Red.Saldo saldo() {
        return saldo;
    }

    public static void guardar(Red.Cosmeticos nuevos) {
        cosmeticos = nuevos;
    }

    /**
     * El catalogo de cosmeticos, o {@code null} si aun no ha llegado.
     *
     * <p>⚠ Que sea {@code null} NO es un error: es "todavia no". La tienda tiene
     * que saber distinguirlo de "no tienes nada", porque enseñar una tienda
     * vacia mientras el paquete esta en vuelo hace creer que no hay nada a la
     * venta.
     */
    public static void guardar(Red.Trabajos nuevos) {
        trabajos = nuevos;
    }

    /** Las Vias. {@code null} hasta que llega la respuesta a `PedirTrabajos`. */
    public static Red.Trabajos trabajos() {
        return trabajos;
    }

    public static void guardar(Red.Misiones nuevas) {
        misiones = nuevas;
    }

    /** El arbol. {@code null} hasta que llega la respuesta a `PedirMisiones`. */
    public static Red.Misiones misiones() {
        return misiones;
    }

    public static Red.Cosmeticos cosmeticos() {
        return cosmeticos;
    }

    /** Al salir del mundo se olvida: el saldo es de esa partida, no del cliente. */
    public static void olvidar() {
        trabajos = null;
        misiones = null;
        saldo = null;
        ficha = null;
        // ⚠ El catalogo tambien se olvida al salir del servidor. Guardarlo
        // entre partidas enseñaria en el servidor B lo que se compro en el A.
        cosmeticos = null;
    }
}
