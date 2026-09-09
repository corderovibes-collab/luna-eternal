package net.pokereport.luna.santuario;

/**
 * LAS MEDIDAS DE LA FOTO FLOTANTE, EN UN SOLO SITIO.
 *
 * <h2>&#9888;&#9888;&#9888; ESTABAN EN DOS FICHEROS, Y UNO ERA EL AUTOTEST</h2>
 *
 * El dibujado vive en {@code client/HologramaSantuario} y la comprobacion de
 * que la foto cabe dentro de su nicho vive en {@code AutoTest}, que es
 * {@code main}. Al escribirla, el {@code 1,75} se copio a mano en la prueba.
 *
 * <p>Eso es <b>exactamente la forma del fallo de las tres listas de medallas</b>:
 * dos sitios con su propia copia de la misma verdad y nada que les obligue a
 * coincidir. La primera vez que alguien bajara la foto --que es lo que pidio el
 * usuario al dia siguiente-- la prueba habria seguido midiendo la altura vieja
 * y habria dicho «cabe» sobre una foto que ya no esta ahi. <b>Sin dar ningun
 * error</b>, y peor que no tener la prueba: dando confianza falsa.
 *
 * <p>&#9888;&#9888; Vive en {@code main} <b>porque cliente y servidor acaban en
 * el mismo jar</b>, asi que el dibujado de cliente y el autotest de servidor
 * pueden leer las mismas constantes. Es lo que ya se hizo con
 * {@code PanelTienda} cuando la tienda tuvo el mismo problema.
 */
public final class Holograma {

    /**
     * CUANTO MIDE DE ALTO LA FOTO, en bloques.
     *
     * <p>&#9888;&#9888; Era 1,6 y se veia pequeña (peticion del usuario): un
     * nicho es un hueco de 3 de ancho y la foto dejaba medio hueco vacio
     * alrededor. A 2,4 lo llena -- una apaisada 16:9 mide 4,27 de ancho, asi que
     * <b>se sale del 3x3 a proposito</b>: sobresalir por delante es lo que la
     * hace visible desde el pasillo, y por detras no hay nada que tapar porque
     * la pared queda a su espalda.
     */
    public static final float ALTO = 2.4f;

    /**
     * A que altura sobre el proyector flota su CENTRO.
     *
     * <p>&#9888; <b>1,75 -&gt; 1,40</b> (2026-09-08, con la captura del usuario
     * delante: «puedes bajarlos un poquito mas»). Con el pedestal a
     * {@link NichoEditor#ALTURA_PROYECTOR} sobre el suelo, la foto ocupa hoy de
     * <b>+1,20 a +3,60</b> del suelo del nicho, o sea centrada en el hueco en
     * vez de pegada a su dintel.
     *
     * <p>&#9888;&#9888; Este numero y {@link #ALTO} <b>no se pueden mover a
     * ojo</b>: el autotest cruza los dos contra la altura del nicho, asi que
     * subir la foto hasta que se salga por el techo se pone rojo antes de salir
     * en una captura.
     */
    public static final float ALTURA = 1.40f;

    /**
     * CUANTO SE SEPARA DE LA PARED DEL FONDO.
     *
     * <p>&#9888;&#9888;&#9888; Esto es «los hologramas estan mal posicionados».
     * El quad se dibujaba centrado en el bloque del proyector, que esta pegado
     * al fondo del nicho; como la foto <b>gira para mirar a la camara</b>, en
     * cuanto uno se pone de lado la mitad barre hacia dentro del muro y
     * desaparece. Adelantada, gira en el aire del hueco.
     */
    public static final double SALIENTE = 0.8;

    private Holograma() {
    }

    /** El borde de abajo de la foto, en bloques sobre el suelo del nicho. */
    public static double bordeInferior() {
        return NichoEditor.ALTURA_PROYECTOR + ALTURA - ALTO / 2.0;
    }

    /** El borde de arriba de la foto, en bloques sobre el suelo del nicho. */
    public static double bordeSuperior() {
        return NichoEditor.ALTURA_PROYECTOR + ALTURA + ALTO / 2.0;
    }
}
