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
    private static Red.Iniciales iniciales;
    private static Red.EstadoClan clan;
    private static Red.Tienda tienda;
    private static Red.EstadoCura cura;
    private static Red.EstadoProtecciones protecciones;
    private static Red.DetalleParcela parcela;
    private static Red.EstadoCartas cartas;
    private static Red.EstadoMercado mercado;
    private static Red.EstadoGts gts;
    private static Red.EstadoCazas cazas;
    private static Red.EstadoTesoros tesoros;
    /**
     * Lo ultimo que salio de un cofre.
     *
     * <p>⚠⚠ NO SE BORRA AL LEERLO, y por eso la ruleta lleva su propio testigo:
     * si se borrara aqui, un redibujado --que ocurre 60 veces por segundo--
     * podria llegar antes que la pantalla y el resultado se perderia. El fallo
     * seria «abri el cofre y no salio nada», con la llave ya gastada.
     */
    private static Red.ResultadoCofre resultado;
    private static Red.EstadoExplorar explorar;
    private static Red.EstadoViajes viajes;
    private static Red.EstadoGimnasio gimnasio;
    private static Red.EstadoTrajes trajes;
    private static Red.EstadoSantuario santuario;
    private static Red.ResultadoFoto fotoSubida;
    private static Red.EstadoFotos misFotos;
    private static Red.RespuestaHonor honor;
    private static Red.EstadoPendientes pendientes;

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

    public static void guardar(Red.Iniciales nuevos) {
        iniciales = nuevos;
    }

    /** Los iniciales y si ya se eligio. {@code null} hasta que contesta. */
    public static Red.Iniciales iniciales() {
        return iniciales;
    }

    public static void guardar(Red.EstadoClan nuevo) {
        clan = nuevo;
    }

    /**
     * El clan y lo que le rodea. {@code null} hasta que contesta el servidor.
     *
     * <p>⚠ Que sea {@code null} y que {@code mio()} sea {@code null} son cosas
     * DISTINTAS: lo primero es «todavía no lo sé», lo segundo es «no tienes
     * clan». Confundirlas enseñaría el formulario de fundar durante el medio
     * segundo que tarda la respuesta, aunque el jugador lleve meses en un clan.
     */
    public static Red.EstadoClan clan() {
        return clan;
    }

    public static void guardar(Red.Tienda nueva) {
        tienda = nueva;
    }

    /**
     * El catalogo de la tienda. {@code null} hasta que contesta el servidor.
     *
     * <p>⚠ Y una tienda VACIA no es lo mismo que «todavia no lo se». Enseñar
     * cero articulos mientras el paquete esta en vuelo hace creer que no hay
     * nada a la venta -- el mismo aviso que ya lleva el catalogo de cosmeticos.
     */
    public static Red.Tienda tienda() {
        return tienda;
    }

    public static void guardar(Red.EstadoCura nueva) {
        cura = nueva;
    }

    public static void guardar(Red.EstadoProtecciones nuevas) {
        protecciones = nuevas;
    }

    public static void guardar(Red.DetalleParcela nueva) {
        parcela = nueva;
    }

    /** El detalle de la parcela abierta. {@code null} si no hay ninguna. */
    public static Red.DetalleParcela parcela() {
        return parcela;
    }

    /** Se olvida al volver a la lista: si no, la siguiente que abras enseñaría
     *  un instante los miembros de la anterior. */
    public static void olvidarParcela() {
        parcela = null;
    }

    /** Las parcelas del jugador. {@code null} hasta que el servidor conteste. */
    public static Red.EstadoProtecciones protecciones() {
        return protecciones;
    }

    /**
     * El equipo y el reloj de la cura. {@code null} hasta que contesta.
     *
     * <p>⚠ Y un equipo VACIO no es lo mismo que «todavia no lo se»: guardar
     * todo en el PC es un estado real. La pantalla lo distingue.
     */
    public static Red.EstadoCura cura() {
        return cura;
    }

    public static void guardar(Red.EstadoCartas nueva) {
        cartas = nueva;
    }

    /**
     * Los dos relojes, los dos precios y los saldos de CARTAS. {@code null}
     * hasta que el servidor contesta.
     *
     * <p>⚠ Los segundos que trae son los que habia AL MANDARLO. La pantalla les
     * resta el tiempo que lleva en pantalla; no se vuelven a pedir cada
     * segundo.
     */
    public static Red.EstadoCartas cartas() {
        return cartas;
    }

    public static void guardar(Red.EstadoMercado nuevo) {
        mercado = nuevo;
    }

    /** El libro de ordenes. {@code null} hasta que contesta el servidor. */
    public static Red.EstadoMercado mercado() {
        return mercado;
    }

    public static void guardar(Red.EstadoTrajes nuevo) {
        trajes = nuevo;
    }

    public static Red.EstadoTrajes trajes() {
        return trajes;
    }

    public static void guardar(Red.EstadoViajes nuevo) {
        viajes = nuevo;
    }

    public static Red.EstadoViajes viajes() {
        return viajes;
    }

    public static void guardar(Red.EstadoGimnasio nuevo) {
        gimnasio = nuevo;
    }

    public static Red.EstadoGimnasio gimnasio() {
        return gimnasio;
    }

    public static void guardar(Red.EstadoExplorar nuevo) {
        explorar = nuevo;
    }

    public static Red.EstadoExplorar explorar() {
        return explorar;
    }

    public static void guardar(Red.EstadoCazas nuevo) {
        cazas = nuevo;
    }

    public static Red.EstadoCazas cazas() {
        return cazas;
    }

    public static void guardar(Red.EstadoTesoros nuevo) {
        tesoros = nuevo;
    }

    /** Llaves, piedad y tiempo de juego. {@code null} hasta que contesta. */
    public static Red.EstadoTesoros tesoros() {
        return tesoros;
    }

    public static void guardar(Red.ResultadoCofre nuevo) {
        resultado = nuevo;
    }

    public static Red.ResultadoCofre resultado() {
        return resultado;
    }

    public static void guardar(Red.EstadoGts nuevo) {
        gts = nuevo;
    }

    /** El GTS. {@code null} hasta que contesta el servidor. */
    public static Red.EstadoGts gts() {
        return gts;
    }

    public static Red.Cosmeticos cosmeticos() {
        return cosmeticos;
    }

    public static void guardar(Red.EstadoSantuario nuevo) {
        santuario = nuevo;
    }

    /**
     * Los nichos del santuario. {@code null} hasta que contesta el servidor.
     *
     * <p>⚠ Que sea {@code null} y que {@code hayNichos()} sea {@code false} son
     * cosas DISTINTAS: lo primero es «todavía no lo sé», lo segundo es «el
     * santuario aun no esta construido». Confundirlas dejaria la pantalla en
     * blanco durante el medio segundo que tarda la respuesta.
     */
    public static Red.EstadoSantuario santuario() {
        return santuario;
    }

    public static void guardar(Red.ResultadoFoto nuevo) {
        fotoSubida = nuevo;
    }

    /** La respuesta a la ultima subida de foto, o {@code null}. */
    public static Red.ResultadoFoto fotoSubida() {
        return fotoSubida;
    }

    public static void guardar(Red.EstadoFotos nuevo) {
        misFotos = nuevo;
    }

    /** Mis fotos. {@code null} hasta que contesta el servidor. */
    public static Red.EstadoFotos misFotos() {
        return misFotos;
    }

    public static void guardar(Red.RespuestaHonor nuevo) {
        honor = nuevo;
    }

    /**
     * La respuesta al ultimo honor. {@code null} si aun no ha habido ninguno.
     *
     * <p>⚠ NO SE BORRA AL LEERLO, como el resultado del cofre: la pantalla la
     * lee cuando puede, y un redibujado no puede comersela.
     */
    public static Red.RespuestaHonor honor() {
        return honor;
    }

    public static void guardar(Red.EstadoPendientes nuevo) {
        pendientes = nuevo;
    }

    /** Las fotos pendientes de moderar (solo staff). {@code null} si aun no. */
    public static Red.EstadoPendientes pendientes() {
        return pendientes;
    }

    /** Al salir del mundo se olvida: el saldo es de esa partida, no del cliente. */
    public static void olvidar() {
        tesoros = null;
        resultado = null;
        trabajos = null;
        misiones = null;
        iniciales = null;
        clan = null;
        tienda = null;
        cura = null;
        protecciones = null;
        parcela = null;
        cartas = null;
        mercado = null;
        gts = null;
        saldo = null;
        ficha = null;
        santuario = null;
        fotoSubida = null;
        misFotos = null;
        honor = null;
        pendientes = null;
        // ⚠ El catalogo tambien se olvida al salir del servidor. Guardarlo
        // entre partidas enseñaria en el servidor B lo que se compro en el A.
        cosmeticos = null;
    }
}
