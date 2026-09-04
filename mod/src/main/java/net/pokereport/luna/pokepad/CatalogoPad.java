package net.pokereport.luna.pokepad;

import java.util.List;

/**
 * Las aplicaciones del PokePad, <b>en el orden en que se dibujan</b>.
 *
 * <h2>⚠⚠ ESTA LISTA VIVE EN {@code main} Y NO EN {@code client}, A PROPÓSITO</h2>
 *
 * La rejilla la dibuja el cliente, así que el sitio natural de esto era
 * {@code client.pokepad.App}. Y ahí estaba, con un problema que no se ve hasta
 * que falla: <b>el servidor no puede leer una clase de cliente</b>, y por eso el
 * autotest no podía comprobar nada del Pad. La comprobación que hacía falta es
 * de las que ya nos han mordido dos veces:
 *
 * <ul>
 *   <li>Una celda dibuja <b>su propio icono</b> aunque esté bloqueada — el
 *       candado es solo para los huecos sin aplicación. Así que dar de alta una
 *       aplicación <b>sin su PNG</b> no da ningún error: da un <b>cuadro
 *       magenta</b> en la pantalla principal. Es literalmente lo que pasó el
 *       2026-08-23, cuando un generador borró tres PNG que no eran suyos y
 *       seis pantallas salieron en magenta — y solo se vio abriéndolas.</li>
 *   <li>Y el arte y la lista eran <b>dos sitios</b> que nada obligaba a
 *       coincidir, como las tres listas de medallas.</li>
 * </ul>
 *
 * <p>Los recursos del cliente acaban <b>dentro del mismo jar</b>, así que
 * {@code getResourceAsStream} los ve desde el servidor. Es el mismo detalle que
 * hace que el autotest pueda comprobar el arte de los trajes.
 *
 * <p>⚠ Sigue habiendo una segunda copia en {@code tools/gen_pokepad.py}
 * ({@code ORDEN}), que es la que monta la maqueta. Esa no se puede unificar
 * desde aquí — es Python — y por eso lo que hay es un aviso en los dos ficheros.
 */
public final class CatalogoPad {

    private CatalogoPad() {}

    /**
     * @param id       identificador estable. Da el icono y la clave de traducción
     * @param abierta  {@code false} mientras la pantalla no exista: la celda
     *                 sale apagada en vez de desaparecer. Enseñar lo que va a
     *                 haber es información; esconderlo, no
     */
    public record Ficha(String id, boolean abierta) {}

    /**
     * Las diecisiete, en el orden del arte: cinco columnas, tres filas, y de la
     * dieciséis en adelante la página 2.
     */
    public static final List<Ficha> TODAS = List.of(
            // La primera que se abre de verdad: lleva a la Pokédex de Cobblemon.
            new Ficha("pokedex",    true),
            new Ficha("cosmeticos", true),
            new Ficha("trabajos",   true),
            new Ficha("misiones",   true),
            // ⚠ Se enciende con su pantalla (2026-08-27). Las paradas estaban en
            //   EXPLORAR y ahí estaban mal: Explorar responde a «¿a qué mundo
            //   voy?» y esto a «¿a qué esquina de la ciudadela voy?».
            new Ficha("warps",      true),
            new Ficha("clan",       true),
            // ⚠ EL MERCADO VA EN EL ICONO `gts`, que ya existe y ya está
            //   dibujado: dos Poké Balls intercambiándose con una gema en medio.
            new Ficha("gts",        true),
            new Ficha("tienda",     true),
            // ⚠ Se enciende con su pantalla (2026-08-31). El sistema lo decidió
            //   D-020 y estaba diseñado desde PHASE 3: otra vez lógica esperando
            //   una puerta.
            new Ficha("tesoros",    true),
            // ⚠⚠ CARTAS OCUPA EL HUECO QUE TENÍA LA WIKI, y las dos mitades del
            //    cambio son decisión del usuario (2026-09-02): las cartas a la
            //    PRIMERA página y la wiki a la SEGUNDA.
            //    Tiene sentido más allá del gusto: `wiki` lleva `abierta=false`
            //    desde que existe el Pad —nunca ha llevado a ningún sitio— y
            //    estaba gastando uno de los quince huecos que se ven al abrir.
            //    Un candado en la primera pantalla y algo que funciona en la
            //    segunda es justo al revés de lo que hay que enseñar.
            new Ficha("cartas",     true),
            // ⚠ Se enciende con su pantalla (2026-08-25). La lógica
            //   --HuntService-- llevaba escrita desde PHASE 5.
            new Ficha("cazas",      true),
            // ⚠ Se enciende con su pantalla (2026-08-28): los TRAJES DE RANGO.
            //   El usuario los puso aquí, no en un icono propio, y tiene razón:
            //   un traje ES un kit de rango. De paso no hizo falta arte nuevo.
            new Ficha("kits",       true),
            // ⚠ Se enciende con su pantalla (2026-08-26). Es la única del Pad
            //   con CONTENEDOR: arrastrar objetos lo hace Minecraft, no nosotros.
            new Ficha("mochila",    true),
            // ⚠ Se enciende con su pantalla: los dieciséis gimnasios, ocho por
            //   región. La lógica llevaba escrita desde que existe `Gimnasio`.
            new Ficha("gyms",       true),
            // ⚠ Se enciende con su pantalla (2026-08-27): los dos mundos.
            new Ficha("explorar",   true),
            // ⚠ LA DECIMOSEXTA, y por eso cae en la PÁGINA 2. No hubo que quitar
            //   ninguna: la rejilla ya pagina y reordena (OrdenPad).
            new Ficha("curar",      true),
            // ⚠ La WIKI baja aquí desde el hueco 10. Sigue bloqueada, y ese es
            //   justo el motivo: lo que todavía no lleva a ningún sitio no ocupa
            //   sitio en la pantalla que se ve al abrir el Pad.
            new Ficha("wiki",       false)
    );

    /** Dónde vive el icono de esa aplicación <b>dentro del jar</b>. */
    public static String rutaIcono(String id) {
        return "/assets/lunaeternal/textures/gui/pokepad/" + id + ".png";
    }
}
