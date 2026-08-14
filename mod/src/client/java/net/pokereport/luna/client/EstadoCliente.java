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

    private EstadoCliente() {}

    public static void guardar(Red.Saldo nuevo) {
        saldo = nuevo;
    }

    /** {@code null} mientras no haya llegado nada del servidor. */
    public static Red.Saldo saldo() {
        return saldo;
    }

    /** Al salir del mundo se olvida: el saldo es de esa partida, no del cliente. */
    public static void olvidar() {
        saldo = null;
    }
}
