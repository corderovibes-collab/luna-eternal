package net.pokereport.luna.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UN CLIC ES UN CLIC, AUNQUE LLEGUE DOS VECES.
 *
 * <h2>&#9888;&#9888;&#9888; LA GUARDA DE LA MANO NO BASTA, Y ESO COSTO
 * ENTENDERLO</h2>
 *
 * Este proyecto ya tenia escrito que {@code UseEntityCallback} llega dos veces
 * --mano principal y secundaria-- y que se ataja con
 * {@code if (mano != MAIN_HAND)}. <b>Es cierto y no basta.</b>
 *
 * <p>Un solo clic derecho sobre una entidad hace que el cliente mande <b>DOS
 * paquetes distintos</b>: {@code INTERACT_AT} (con el punto exacto donde
 * tocaste) e {@code INTERACT}. Fabric dispara el evento en los dos, y en los dos
 * la mano es la <b>principal</b>: la guarda los deja pasar a ambos.
 *
 * <p>El sintoma lo vio el usuario en Oak: <i>«se duplica el mensaje en el chat
 * 2 veces»</i>. Y estaba en todos los demas sin que se notara, porque abrir dos
 * veces la misma pantalla se ve igual que abrirla una.
 *
 * <h2>&#9888;&#9888; SE CUENTA POR (JUGADOR, SITIO), NO POR JUGADOR</h2>
 *
 * Con una sola marca por jugador, tocar a Oak y correr a tocar a la Mew dentro
 * de la ventana se comeria el segundo clic -- y eso es peor que el fallo que
 * arregla: un NPC que a veces no responde.
 *
 * <p>&#9888; La ventana por defecto es corta a proposito ({@link #VENTANA_MS}):
 * lo que hay que tragarse son dos paquetes del <b>mismo</b> clic, que llegan en
 * el mismo tick. Media docena de milisegundos bastarian; 400 dan margen sin que
 * nadie note que no puede volver a pulsar.
 */
public final class Toque {

    private Toque() {
    }

    /** Lo que se ignora tras un toque, en milisegundos. */
    public static final long VENTANA_MS = 400L;

    private static final Map<String, Long> ULTIMO = new ConcurrentHashMap<>();

    /**
     * ¿Es este clic la repeticion del anterior?
     *
     * @param quien  el jugador
     * @param sitio  que ha tocado -- basta con un nombre corto y estable
     * @return {@code true} si hay que ignorarlo
     */
    public static boolean repetido(UUID quien, String sitio) {
        return repetido(quien, sitio, VENTANA_MS);
    }

    /**
     * Lo mismo con otra ventana.
     *
     * <p>&#9888; La ventana larga tiene otro uso: el guardian del lobby responde
     * tambien al clic <b>izquierdo</b>, y golpear se repite solo mientras
     * mantienes el boton. Ahi no se trata de dos paquetes del mismo clic sino de
     * veinte clics de verdad, y hace falta mas margen.
     */
    public static boolean repetido(UUID quien, String sitio, long ventanaMs) {
        String clave = quien + "|" + sitio;
        long ahora = System.currentTimeMillis();
        Long antes = ULTIMO.get(clave);
        if (antes != null && ahora - antes < ventanaMs) {
            return true;
        }
        ULTIMO.put(clave, ahora);
        return false;
    }

    /**
     * Se llama al desconectar.
     *
     * <p>&#9888; Sin esto el mapa crece con cada jugador que pasa por el
     * servidor y no se vacia nunca. Es pequeño, pero una fuga pequeña sigue
     * siendo una fuga.
     */
    public static void olvidar(UUID quien) {
        String prefijo = quien + "|";
        ULTIMO.keySet().removeIf(k -> k.startsWith(prefijo));
    }
}
