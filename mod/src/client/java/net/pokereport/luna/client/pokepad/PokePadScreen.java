package net.pokereport.luna.client.pokepad;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/**
 * La pantalla principal del PokePad: el chasis y su rejilla de aplicaciones.
 *
 * <p><b>Las medidas no se eligen aquí, se heredan del arte.</b> Están medidas
 * sobre el chasis real por {@code tools/gen_pokepad.py} y anotadas en
 * {@code docs/ui/prompts-arte-pokepad.md} §8.
 */
public class PokePadScreen extends Screen {

    private static final Identifier CHASIS = tex("pokepad");
    // Las celdas NO son texturas: las dibuja el código.
    //
    // Lo eran, y era el motivo de que la pantalla se viera sucia: una celda con
    // bisel en relieve, estampada quince veces, es mucho ruido para un hueco de
    // 37 píxeles. El PokePad de referencia no tiene ni una textura de celda —su
    // fondo llega con la pantalla VACÍA— y las pinta como rectángulos planos un
    // tono más claros. Se ve limpio justo por eso.
    //
    // ⚠⚠ EN EL CHASIS v4 LA PANTALLA PASO DE AZUL OSCURO A CASI BLANCA, Y ESO
    // DA LA VUELTA A TODO LO DE DENTRO. No es un retoque de tono: es que cada
    // decision de contraste apuntaba al reves.
    //
    //   las celdas  eran mas CLARAS que el fondo -> ahora mas OSCURAS
    //   el texto    era BLANCO con contorno negro -> ahora NEGRO con contorno
    //               claro
    //   el resalte  era el ambar del chasis -> ahora el NARANJA FUERTE, el
    //               unico acento del v4 que contrasta sobre claro
    //
    // Todos los valores estan MUESTREADOS del propio arte, no elegidos a ojo.
    private static final int CELDA_FONDO = 0xFFBFCBE8;
    private static final int CELDA_BORDE = 0xFF7C89B4;
    // ⚠ EL RESALTE NO ES BLANCO PURO, Y ES POR UN MOTIVO MEDIDO.
    //
    // Lo fue, y sobre el candado se veia el fallo: su arco es blanco puro
    // (255,255,255), asi que sobre una celda blanca DESAPARECIA. Un tinte
    // calido --a juego con el naranja del bisel, que ya es el acento del
    // resalte-- baja la celda a luma 241 y deja que cualquier dibujo con
    // blancos siga leyendose encima.
    private static final int CELDA_ENCIMA = 0xFFFFF0DC;
    private static final int BORDE_ENCIMA = 0xFFF35C0C;

    /**
     * El chasis en PIXELES DEL ARTE. Todo lo demás se mide en estas unidades.
     *
     * <p>Los dos números son divisibles entre 1, 2, 3, 4 y 6, que son los
     * valores que puede tomar el ajuste <i>GUI Scale</i>. Dibujado a su tamaño
     * real, un texel cae en un píxel sea cual sea el ajuste del jugador.
     */
    private static final int NAT_ANCHO = 1380, NAT_ALTO = 828;

    /**
     * Cuánto puede DESBORDAR el Pad la ventana sin perder nada visible.
     *
     * <p>Medido sobre el chasis: sus 12 columnas de cada lado y sus 4 filas de
     * arriba son <b>enteramente transparentes</b> — la esquina redondeada. Lo
     * que caiga fuera dentro de ese margen no se ve porque no hay nada.
     *
     * <p><b>Y esto es lo que arregla el bisel naranja «de baja calidad».</b> El
     * problema no estaba en el arte: el v4 tiene el doble de colores que el v3 y
     * los bordes más suaves. Estaba en que una ventana de 1373 de ancho —siete
     * píxeles corta— obligaba a encoger el Pad un 0,5 %, y encoger enciende el
     * filtrado lineal, que mezcla cada píxel con su vecino. Sobre el bisel del
     * v4, que es una banda naranja enorme con esquinas achaflanadas duras, esa
     * mezcla se ve como escalones sucios; sobre el v3, cuyo acento eran líneas
     * finas, apenas se notaba.
     *
     * <p>Así que cuando lo que falta cabe en el margen transparente, <b>no se
     * encoge</b>: se dibuja a tamaño real y sobresale. Perder tres píxeles de
     * una esquina que ya era transparente no se ve; emborronar el Pad entero,
     * sí.
     */
    private static final int MARGEN_X = 12, MARGEN_Y = 4;

    /** El color de la pantalla, para morder las esquinas de las celdas. */
    private static final int PANTALLA = 0xFFE2EBFD;

    /** La rejilla, en píxeles del arte. */
    // Los da `tools/gen_pokepad.py` al preparar el arte: los imprime al final.
    // No se escriben a ojo, se copian de ahi.
    private static final int REJ_X = 488, REJ_Y = 230;
    private static final int CELDA = 128, HUECO_X = 26, HUECO_Y = 19, ICONO = 100;
    private static final int COLS = 5;

    /**
     * Filas de la rejilla, y con COLS lo que de verdad importa: <b>cuántas
     * celdas caben en una página</b>.
     *
     * <h2>⚠⚠ No tenerlo escrito costó una celda dibujada FUERA del marco</h2>
     *
     * El bucle recorría {@code orden.length} entero, así que con quince
     * aplicaciones cuadraba por casualidad —tres filas justas— y a la
     * decimosexta le tocaba la fila 4, que no existe: se dibujó suelta debajo
     * de la rejilla, encima del chasis.
     *
     * <p>Y no dio ningún error: {@code i / COLS} devuelve 3 tan tranquilo. La
     * rejilla «ya paginaba» en el sentido de que había un botón de página, pero
     * <b>nadie troceaba la lista</b>.
     */
    private static final int REJ_FILAS = 3;
    private static final int POR_PAGINA = COLS * REJ_FILAS;

    /**
     * El nombre de cada aplicación, debajo de su icono.
     *
     * <p><b>El alto va en píxeles del arte, no en unidades de interfaz.</b> Es
     * la diferencia entre un texto que mide siempre lo mismo respecto al Pad y
     * uno que cambia de tamaño según el <i>GUI Scale</i> de cada jugador — con
     * lo segundo, a escala 4 el nombre se sale de su celda.
     *
     * <p>18 es el doble exacto de los 9 que mide la fuente de Minecraft, así
     * que cae en píxeles enteros. Cualquier otro número la emborrona, que es
     * justo lo que costó una noche arreglar en el chasis.
     */
    private static final int TEXTO_ALTO = 18;

    /**
     * Cuánto SUBE el nombre dentro de su celda.
     *
     * <p>A la mitad de su alto, así que queda montado a caballo sobre la línea
     * de abajo de la celda en vez de colgando en el hueco. Es lo que lo ata a
     * su icono: suelto en medio de dos filas, el ojo duda de a cuál pertenece.
     */
    private static final int TEXTO_SOLAPE = TEXTO_ALTO / 2;

    // ⚠ EL NOMBRE SE INVIERTE CON EL CHASIS v4: OSCURO CON CONTORNO CLARO.
    //
    // Era blanco con contorno negro, y sigue siendo la misma decision del
    // usuario --"que se lea"-- solo que aplicada a un fondo que se ha dado la
    // vuelta: la pantalla del v4 es casi blanca, y blanco sobre blanco no es
    // legible con contorno ni sin el.
    //
    // Lo que NO cambia es que el color es el mismo este la aplicacion abierta o
    // cerrada. Hubo un tono apagado para las cerradas y salio mal por un motivo
    // que no se ve al escribirlo: HOY LAS QUINCE ESTAN CERRADAS, asi que los
    // quince nombres salian atenuados y no se leia ninguno. Atenuar algo solo
    // comunica si hay al lado un hermano encendido con el que compararlo.
    //
    // Lo que distingue una celda cerrada sigue siendo su FONDO, que ya recula
    // por su cuenta.
    private static final int TEXTO_COLOR = 0xFF16203A;
    private static final int TEXTO_CONTORNO = 0xFFF2F6FF;

    // El borde de la celda y la esquina mordida, tambien en pixeles del arte.
    // A 1 px sobre una celda de 124 no se ve ninguno de los dos.
    private static final int BORDE_GROSOR = 4, MORDIDA = 4;

    // Una celda cerrada se apaga a si misma; el ICONO va siempre a todo color.
    //
    // Antes se apagaba el icono, y tenia sentido cuando la celda llevaba un
    // candado encima: sin apagar, los dos se pisaban. Quitados los candados,
    // apagar el icono solo conseguia que la pantalla entera pareciera muerta
    // --y hoy las quince aplicaciones estan cerradas--. Es el fondo el que
    // debe recular, no el dibujo.
    private static final int CELDA_CERRADA = 0xFFC2CCE4;
    private static final int BORDE_CERRADA = 0xFF96A1C0;

    // Las tres ranuras del panel izquierdo, MEDIDAS sobre el chasis y no
    // puestas a ojo. Las mide `medir_cajas()` de gen_pokepad.py buscando el gris
    // de su moldura, y el script imprime estos números al terminar.
    //
    //   cara     x 114-322  y 115-324   hueco útil 181 x 182
    //   botones  x  80-356  y 360-595   hueco útil 249 x 208
    //   saldo    x  80-287  y 624-719   hueco útil 180 x  68
    //
    // La cara va a 168 porque la cabeza de la skin son 8x8 texeles y 168 es
    // múltiplo de 8: cada texel cae en 21 píxeles clavados. Con un lado que no
    // lo fuera saldría emborronada justo en lo único que es del jugador.
    private static final int CARA_X = 134, CARA_Y = 136, CARA_LADO = 168;

    /**
     * La moneda de los LunaCoins, a la izquierda de su saldo.
     *
     * <p>40 × 40 porque la ranura tiene <b>55 de alto útil medidos</b>. Y el
     * número no se centra en la ranura entera sino <b>en lo que sobra a la
     * derecha de la moneda</b>: centrado en toda la ranura quedaría descolocado
     * respecto a su propia moneda, que es lo primero que mira el ojo.
     */
    private static final Identifier MONEDA = tex("lunacoin");
    private static final int MONEDA_X = 118, MONEDA_Y = 658, MONEDA_LADO = 40;

    /**
     * La Plata, en el panel de cabecera y pegada a la izquierda.
     *
     * <p><b>Arriba y no abajo con la LunaCoin, y es deliberado.</b> Abajo se
     * pidió que quedara solo la LunaCoin con su «+», y tiene sentido: es la
     * única que se compra, así que es la única que necesita un botón al lado.
     * Pero la Plata es la moneda que se gana jugando —la que se mira
     * constantemente—, y no enseñarla en ningún sitio no era una opción.
     *
     * <p>El panel de cabecera era el único hueco grande que quedaba libre, y
     * además es donde se busca: dinero a la izquierda, controles de ventana a
     * la derecha. El número se centra entre la moneda y el primer botón, no en
     * el panel entero, o caería debajo de «ajustes».
     */
    private static final Identifier PLATA = tex("plata");
    private static final int PLATA_X = 954, PLATA_Y = 97;
    private static final int PLATA_CX = 1050, PLATA_CY = 117;
    private static final int COLOR_PLATA = 0xFFE2E8F2;

    /**
     * El color del saldo de LunaCoins.
     *
     * <p><b>Dorado desde que la moneda es dorada.</b> Era azul luna, y con la
     * moneda nueva quedaban dos cosas del mismo dato discutiendo: la moneda
     * decia "oro" y el numero al lado decia "luna". El color de un saldo tiene
     * que ser el de su moneda, o deja de leerse como el mismo dato.
     */
    private static final int LUNA = 0xFFFFD34A;

    /**
     * Los seis botones, cada uno en su sitio. <b>Ya no viven juntos</b>, y es
     * decisión del usuario sobre el chasis v4:
     *
     * <pre>
     *   atras / adelante        en el BISEL NARANJA de abajo, uno por mitad
     *   cerrar                  arriba a la derecha, junto al logo
     *   inicio / ajustes / mas  apilados en la ranura mediana
     * </pre>
     *
     * <p>Los tres sitios los <b>mide</b> {@code gen_pokepad.py} sobre el chasis
     * y de ahí sale esta tabla; no se escriben a ojo. Y son de dos tamaños
     * porque los manda el sitio: 80 × 64 —dos tercios del arte— en la ranura y
     * en el panel, y <b>45 × 36 en la banda</b>, que mide 37 px de alto medidos
     * y es lo que cabe sin invadir ni la pantalla ni el chasis. Esa escala ya la
     * proponía el propio arte: es el tamaño que tenía la carita verde que había
     * justo ahí.
     *
     * <p>{@code gen_pokepad.py} guarda cada uno <b>ya reducido</b> a su tamaño
     * para que se dibuje 1:1 (regla 2 de {@code docs/ui/dibujado.md}).
     */
    private static final String[] BOTONES =
            {"atras", "adelante", "ajustes", "cerrar"};

    /** x, y, ancho, alto — en píxeles del arte, en el orden de {@link #BOTONES}. */
    private static final int[][] BOTON = {
            { 610, 692, 60, 48},   // atras
            {1040, 692, 60, 48},   // adelante
            {1110,  85, 80, 64},   // ajustes
            {1210,  85, 80, 64},   // cerrar
    };

    private static final int ATRAS = 0, ADELANTE = 1, AJUSTES = 2, CERRAR = 3;

    /**
     * La barra de sesión, arriba a la derecha. Medida sobre el chasis v5.
     *
     * <pre>
     *   [moneda Plata][12,345][+]   [moneda Luna][48][+]   [ajustes][cerrar]
     * </pre>
     *
     * <p><b>Los dos saldos juntos y arriba.</b> Son el mismo tipo de dato y así
     * se comparan de una mirada; repartidos —uno arriba y otro abajo— obligaban
     * a buscar el segundo. La ranura de abajo a la izquierda queda <b>libre</b>.
     *
     * <p>Cada uno lleva su «+»: el de LunaCoins llevará a la tienda, y el de
     * Plata a donde se decida. Los dos van apagados mientras no haya destino.
     */
    /**
     * EL FONDO DEL PANEL IZQUIERDO, MEDIDO SOBRE EL CHASIS.
     *
     * <p>#222529, luma 37: es casi negro. Hace falta como color para las
     * esquinas mordidas de las celdas de sesion --lo que se veria si la celda
     * no estuviera-- igual que {@link #PANTALLA} lo es para las de la rejilla.
     */
    private static final int PANEL = 0xFF222529;

    /**
     * Las celdas del panel de sesion. <b>Mas CLARAS que su fondo</b>, al reves
     * que las de la rejilla, y no es una incoherencia: es la misma regla.
     *
     * <p>La regla no es "la celda va oscura", es "la celda tiene que separarse
     * de su fondo". En la pantalla, que es casi blanca, eso significa bajar; en
     * este panel, que es casi negro, significa subir. Se conserva el SALTO, que
     * es lo que se ve: la rejilla usa 29 de luma entre fondo y celda y 65 mas
     * hasta el borde, y aqui son 22 y 41 -- menos, porque sobre oscuro el mismo
     * salto numerico se percibe mas grande.
     */
    private static final int FILA_FONDO = 0xFF343B4D;
    private static final int FILA_BORDE = 0xFF59647F;

    /**
     * El "+" de las LunaCoins, <b>dibujado por codigo y no con una textura</b>.
     *
     * <p>Decision del usuario: el boton de arte "se veia sobrepuesto". Y tenia
     * razon por un motivo concreto: era la unica pieza del panel con luz,
     * volumen y bisel propios, encima de cinco filas planas. Dos lenguajes
     * distintos a dos centimetros uno de otro.
     *
     * <p>Dibujado sale plano como todo lo demas, hereda el color de la celda
     * que lo rodea y, de paso, deja de haber una textura que mantener.
     */
    private static final int MAS_LADO = 34, MAS_GROSOR = 6, MAS_BRAZO = 18;

    /**
     * EL PANEL DE SESION, bajo la cara. Todo en pixeles del arte y todo
     * <b>medido</b> por {@code gen_pokepad.py}, que lo imprime al terminar.
     *
     * <p><b>Sustituye a la tarjeta de entrenador</b> (decision del usuario,
     * 2026-08-17). Lo que se ensena ya no es el progreso en cinco Vias sino los
     * datos que se miran a diario, y en el orden que el pidio: lo que tienes
     * (Plata, LunaCoins), a quien perteneces (Clan, Trabajo, Division) y lo que
     * has ganado (Medallas).
     */
    private static final int FICHA_X0 = 95, FICHA_X1 = 360, FICHA_Y = 364;
    private static final int FILA_ALTO = 52, FILA_ICONO = 36;

    /** Alto de la CELDA dentro de la fila, y margen interior a los lados. Los
     *  6 px que sobran son el aire entre una celda y la siguiente. */
    private static final int FILA_CELDA = FILA_ALTO - 6, FILA_PAD = 10;

    /** Las tres filas con icono propio, en el orden en que se dibujan. */
    private static final String[] FILAS = {"clan", "trabajo", "division"};

    /**
     * Las medallas: ocho de Kanto y ocho de Johto, en <b>orden de gimnasio</b>
     * y no alfabetico, que es como se consiguen y como se recuerdan.
     *
     * <p><b>Las texturas son las del mod de medallas, referenciadas por
     * identificador y NO copiadas.</b> El mod va instalado en el cliente, asi
     * que sus texturas ya estan cargadas: apuntarlas cuesta cero bytes en
     * nuestro jar, no redistribuye nada suyo, y el dia que el cambie el dibujo
     * de una medalla el Pad ensena el nuevo sin que nadie regenere nada.
     *
     * <p>Se ensenan <b>siempre las dieciseis</b>, apagadas las que no se tienen:
     * un hueco vacio no dice cuantas faltan, y saber cuantas faltan es justo lo
     * que hace que alguien vaya a por la siguiente.
     */
    /**
     * ⚠⚠⚠ YA NO ES UN ARRAY AQUI: SALE DE {@code Gimnasio.insignias()}.
     *
     * <p>Estaba escrita a mano, y era la <b>tercera</b> copia del mismo orden —
     * la del servidor, la del dialogo del gimnasio y esta. Nada las obligaba a
     * coincidir, y si se desordenaran, ganar a Brock encenderia la medalla de
     * Misty <b>sin dar ningun error</b>.
     *
     * <p>El bit <i>i</i> de la mascara es el gimnasio de la sala <i>i</i>, asi
     * que el orden lo manda quien reparte las medallas. Ahora lo lee de ahi.
     */
    private static final java.util.List<String> MEDALLAS =
            net.pokereport.luna.gym.Gimnasio.insignias();
    private static final int MEDALLAS_X = 97, MEDALLAS_Y = 662;
    private static final int MEDALLA_SEP = 3;

    /**
     * LA REJILLA DE MEDALLAS SE CALCULA. NO SE ESCRIBE.
     *
     * <h2>⚠⚠⚠ Estaba a 8 columnas y 30 px, y con 23 medallas se salia del pad</h2>
     *
     * Con dieciseis cuadraba: 8x2 son dieciseis justos. Al entrar la Liga
     * Naranja pasaron a ser <b>veintitres</b>, y ocho columnas dan tres filas —
     * la tercera cae por debajo del hueco y se dibuja <b>encima del chasis</b>.
     * No da ningun error: se ve como medallas sueltas flotando sobre el borde.
     *
     * <p>Es exactamente lo que ya paso con la rejilla de aplicaciones cuando
     * llego la decimosexta, y con la paginacion de Cosmeticos. Tercera vez, asi
     * que esta vez <b>no hay numero que puedas dejar viejo</b>:
     *
     * <ul>
     *   <li>siempre DOS filas, que es el hueco que hay;</li>
     *   <li>las columnas salen de cuantas medallas haya;</li>
     *   <li>el paso sale del ancho disponible, que es el mismo que el de las
     *       filas de datos de encima ({@link #FICHA_X0}..{@link #FICHA_X1}).</li>
     * </ul>
     *
     * <p>Asi, una medalla mas encoge las que hay en vez de salirse. El autotest
     * comprueba que la tira quepa.
     */
    private static final int MEDALLA_FILAS = 2;
    private static final int MEDALLA_COLS =
            (MEDALLAS.size() + MEDALLA_FILAS - 1) / MEDALLA_FILAS;
    private static final int MEDALLA_PASO =
            (FICHA_X1 - MEDALLAS_X) / Math.max(1, MEDALLA_COLS);
    private static final int MEDALLA = MEDALLA_PASO - MEDALLA_SEP;

    /** El tinte de una medalla que no se tiene: oscura, pero se ve que es. */
    private static final int MEDALLA_APAGADA = 0xFF3C4258;


    /**
     * A dónde lleva el «+» de las LunaCoins.
     *
     * <p><b>Vacío hasta que exista la tienda.</b> Mientras lo esté, el botón se
     * dibuja apagado y responde «todavía no» — igual que las quince celdas y que
     * la segunda página. Un «+» de aspecto normal que no hace nada enseña a no
     * pulsar los botones, y eso se paga en las pantallas que sí funcionen.
     *
     * <p>Cuando la haya, se pone aquí la dirección y ya está: el botón abre
     * <b>la pantalla de confirmación de Minecraft</b>, no el navegador
     * directamente. Es la que avisa de que se va a salir del juego y deja copiar
     * el enlace, y saltársela para «ahorrar un clic» es justo lo que enseña a la
     * gente a confiar en enlaces que aparecen solos.
     */
    private static final String TIENDA = "";

    /**
     * Cuántas páginas tiene la rejilla.
     *
     * <p>La segunda está entera bloqueada: quince candados y «Próximamente».
     * <b>Enseñar que hay más sitio es información</b>; no enseñar nada haría
     * creer que el Pad se acaba en quince.
     */
    /**
     * Páginas que hay.
     *
     * <p>⚠ <b>Se calcula, no se escribe.</b> Estaba a 2 a mano, y con dieciséis
     * aplicaciones eso seguía valiendo por casualidad; con veintiuna habría
     * dejado cinco inalcanzables sin decir nada. Es el mismo fallo que tuvo la
     * paginación de Cosméticos, donde 54 de 62 no se podían ver.
     *
     * <p>El mínimo de 2 es deliberado: la segunda página enseña los candados de
     * «Próximamente», que es lo que dice que el Pad va a crecer.
     */
    private static final int PAGINAS = Math.max(2,
            (App.TODAS.length + POR_PAGINA - 1) / POR_PAGINA);

    /** Un solo candado repetido, no quince distintos: lo que dice es «aquí no
     *  hay nada todavía», y quince dibujos distintos dirían que hay quince
     *  cosas distintas esperando. */
    private static final Identifier CANDADO = tex("candado");

    private int x0, y0, ancho, alto;
    private float k;

    /** Qué página se está viendo. 0 = las aplicaciones, 1 = «Próximamente». */
    private int pagina;

    /** El orden del jugador, que puede no ser el de fábrica. */
    private App[] orden = App.TODAS.clone();

    /**
     * El modo de ordenar, que es para lo que sirve el botón de ajustes.
     *
     * <p>Funciona a dos clics —coges una y la sueltas sobre otra, y se
     * intercambian— y no arrastrando. No es por comodidad de programación: <b>a
     * dos clics no hay forma de soltar un icono fuera de la rejilla</b> y
     * perderlo, ni de que un tirón del ratón deshaga el orden entero sin
     * querer. Y sigue siendo todo clics (D-012).
     */
    private boolean ordenando;

    /** Qué celda está cogida, o -1. */
    private int cogida = -1;

    public PokePadScreen() {
        super(Text.translatable("pokepad.lunaeternal.titulo"));
    }

    private static Identifier tex(String nombre) {
        return Identifier.of("lunaeternal", "textures/gui/pokepad/" + nombre + ".png");
    }

    /**
     * Centra el Pad y decide a qué tamaño se dibuja.
     *
     * <p><b>El objetivo es un texel por píxel real de pantalla.</b> Antes se
     * dibujaba a 346 y se dejaba que Minecraft lo multiplicara por el
     * <i>GUI Scale</i>: eso es ampliar una imagen pequeña, y era exactamente lo
     * que se veía borroso. Ahora el arte mide 1380×828 y se pide ese mismo
     * tamaño en pantalla, así que no hay ampliación que emborrone.
     */
    @Override
    protected void init() {
        // Se pide el saldo cada vez que se abre, en vez de que el servidor lo
        // empuje en cada movimiento de la economia: asi el numero siempre esta
        // fresco y un Pad cerrado no cuesta nada.
        ClientPlayNetworking.send(new Red.PedirSaldo());
        orden = OrdenPad.leer();

        // ⚠⚠ DELEGADO EN `Escalado` (2026-08-26). Aqui vivia la version buena
        //    --con la leccion del borde transparente-- y en las otras DIEZ
        //    pantallas habia copias que ya habian derivado en seis variantes.
        //    Hoy hay una sola, y esta es la que se llevo alli.
        //
        //    Lo que cambia respecto a lo que habia: ya NO se limita a 1x. Antes
        //    el chasis se dibujaba siempre a 1.380 pixeles fisicos, asi que
        //    cuanto mas grande el monitor MAS PEQUEÑO se veia -- 36 % del ancho
        //    en un 4K frente al 72 % de 1080p. Ahora crece a medios pasos.
        var m = Escalado.aplicar(client, width, height);
        k = m.k();
        ancho = m.ancho();
        alto = m.alto();
        x0 = m.x0();
        y0 = m.y0();
        // ⚠ El PokePad filtra SUS texturas, que no son una lista fija: los
        //   iconos y los botones se descubren al vuelo. Por eso `Escalado`
        //   devuelve la decision en vez de aplicarla aqui.
        filtrar(!m.exacto());
    }

    /**
     * Elige cómo se remuestrean nuestras texturas: lineal o vecino más próximo.
     *
     * <p>Se aplica a todas de una vez —chasis, iconos y botón— porque todas se
     * dibujan con el mismo factor. Mezclar filtros dejaría el chasis suave y
     * los iconos con cerco, que es peor que cualquiera de las dos.
     */
    private void filtrar(boolean suave) {
        if (client == null) {
            return;
        }
        client.getTextureManager().getTexture(CHASIS).setFilter(suave, false);
        for (String b : BOTONES) {
            client.getTextureManager().getTexture(tex("boton_" + b)).setFilter(suave, false);
        }
        for (App app : App.TODAS) {
            client.getTextureManager().getTexture(app.icono()).setFilter(suave, false);
        }
        client.getTextureManager().getTexture(CANDADO).setFilter(suave, false);
        client.getTextureManager().getTexture(MONEDA).setFilter(suave, false);
        client.getTextureManager().getTexture(PLATA).setFilter(suave, false);
    }

    /** El juego sigue corriendo detrás: es un menú, no una pausa. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int ratonX, int ratonY, float delta) {
        super.render(ctx, ratonX, ratonY, delta);
        dibujar(ctx, CHASIS, x0, y0, ancho, alto,
                NAT_ANCHO, NAT_ALTO, 0xFFFFFFFF);

        int celda = Math.round(CELDA * k);
        int icono = Math.round(ICONO * k);
        // Nunca por debajo de 1: a GUI Scale alto y ventana pequena, redondear
        // a cero borraria el borde de todas las celdas.
        int grosor = Math.max(1, Math.round(BORDE_GROSOR * k));
        int mordida = Math.max(1, Math.round(MORDIDA * k));

        App bajoElRaton = null;
        // ⚠ SIEMPRE POR_PAGINA celdas, ni una más. `i` es la RANURA dentro de la
        //   página (0..14) y `real` el índice en la lista completa. Antes se
        //   usaba el mismo número para las dos cosas, y por eso la aplicación
        //   número 16 se dibujaba en una cuarta fila inexistente.
        for (int i = 0; i < POR_PAGINA; i++) {
            int real = pagina * POR_PAGINA + i;
            App app = real < orden.length ? orden[real] : null;
            boolean apps = app != null;
            int cx = celdaX(i), cy = celdaY(i);
            boolean encima = ratonX >= cx && ratonX < cx + celda
                    && ratonY >= cy && ratonY < cy + celda;
            if (encima && apps) {
                bajoElRaton = app;
            }

            // La celda cogida se queda resaltada aunque el ratón se haya ido:
            // es la que estás moviendo, y perderla de vista al apartar el ratón
            // haría dudar de si el clic contó.
            // ⚠ Se compara con `real` y no con la ranura: `cogida` es un índice
            //   de la lista completa, así que en la página 2 la ranura 0 sería
            //   la 15 — y se habría resaltado la celda equivocada.
            boolean marcada = encima || (ordenando && real == cogida);
            int fondo = marcada ? CELDA_ENCIMA
                    : app != null && app.abierta() ? CELDA_FONDO : CELDA_CERRADA;
            int borde = marcada ? BORDE_ENCIMA
                    : app != null && app.abierta() ? CELDA_BORDE : BORDE_CERRADA;
            celda(ctx, cx, cy, celda, grosor, mordida, fondo, borde);

            dibujar(ctx, apps ? app.icono() : CANDADO,
                    cx + (celda - icono) / 2, cy + (celda - icono) / 2,
                    icono, icono, ICONO, ICONO, 0xFFFFFFFF);

            // El nombre, debajo de su icono.
            //
            // Se pide en pixeles del ARTE --no en unidades de interfaz-- para
            // que mida siempre lo mismo respecto al Pad.
            int artX = REJ_X + (i % COLS) * (CELDA + HUECO_X) + CELDA / 2;
            int artY = REJ_Y + (i / COLS) * (CELDA + HUECO_Y) + CELDA - TEXTO_SOLAPE;
            texto(ctx, apps ? app.nombre() : PROXIMAMENTE,
                    artX, artY, TEXTO_ALTO, TEXTO_COLOR);
        }

        panelLateral(ctx, ratonX, ratonY);
        barra(ctx, ratonX, ratonY);

        // ⚠ LA PLACA DE ARRIBA YA NO LLEVA TEXTO, Y ES A PROPOSITO.
        //
        // Ahi se escribia el nombre de la aplicacion senalada. En el chasis v2
        // el disenador metio el LOGO del servidor en esa placa, asi que el
        // texto le caeria encima -- dos cosas peleandose por el mismo hueco.
        //
        // El nombre no se pierde: sigue en el tooltip al pasar el raton, que
        // es donde ya estaba la descripcion. Queda pendiente decidir donde va
        // de forma fija; hasta entonces, mejor nada que algo pisando el logo.

        // En modo de ordenar, la ayuda dice qué hacer en vez de qué es cada
        // aplicación: ahí no vas a abrir nada, vas a moverlo.
        if (ordenando) {
            ctx.drawTooltip(textRenderer,
                    Text.translatable(cogida < 0 ? "pokepad.lunaeternal.ordenar.coge"
                                                 : "pokepad.lunaeternal.ordenar.suelta"),
                    ratonX, ratonY);
        } else if (bajoElRaton != null) {
            ctx.drawTooltip(textRenderer, bajoElRaton.descripcion(), ratonX, ratonY);
        }
    }

    private static final Text PROXIMAMENTE =
            Text.translatable("pokepad.lunaeternal.proximamente");

    /**
     * Si un botón lleva a algún sitio ahora mismo.
     *
     * <p>Un botón apagado que responde «todavía no» informa; uno de aspecto
     * normal que no hace nada enseña a no pulsar los botones, y eso se paga
     * después en las pantallas que sí funcionen.
     */
    private boolean activo(int i) {
        return switch (i) {
            case ATRAS -> pagina > 0;
            case ADELANTE -> pagina < PAGINAS - 1;
            // Ordenar solo tiene sentido donde hay iconos que ordenar.
            case AJUSTES -> pagina == 0;
            case CERRAR -> true;
            default -> false;
        };
    }

    @Override
    public boolean mouseClicked(double ratonX, double ratonY, int boton) {
        int celda = Math.round(CELDA * k);
        if (boton == 0) {
            for (int i = 0; i < BOTONES.length; i++) {
                int bx = botonX(i), by = botonY(i);
                int w = Math.round(BOTON[i][2] * k), h = Math.round(BOTON[i][3] * k);
                if (ratonX < bx || ratonX >= bx + w
                        || ratonY < by || ratonY >= by + h) {
                    continue;
                }
                sonar(activo(i));
                if (!activo(i)) {
                    return true;
                }
                switch (i) {
                    case CERRAR -> close();
                    // Cambiar de pagina suelta lo que estuvieras moviendo: si
                    // no, cogerias en una pagina y soltarias en otra.
                    case ATRAS -> { pagina--; cogida = -1; }
                    case ADELANTE -> { pagina++; cogida = -1; }
                    case AJUSTES -> {
                        ordenando = !ordenando;
                        cogida = -1;
                        if (!ordenando) {
                            OrdenPad.guardar(orden);
                        }
                    }
                    default -> { }
                }
                return true;
            }
        }
        if (boton == 0 && enMas(ratonX, ratonY)) {
            sonar(!TIENDA.isEmpty());
            abrirTienda();
            return true;
        }
        if (boton == 0) {
            for (int i = 0; i < POR_PAGINA; i++) {
                int cx = celdaX(i), cy = celdaY(i);
                if (ratonX < cx || ratonX >= cx + celda
                        || ratonY < cy || ratonY >= cy + celda) {
                    continue;
                }
                // ⚠ MISMA CUENTA QUE AL DIBUJAR, y por eso está escrita igual:
                //   si el clic y el dibujado calcularan la ranura de dos formas
                //   distintas, pulsar un icono abriría el de al lado -- y en la
                //   página 2 abriría algo estando sobre un candado.
                int real = pagina * POR_PAGINA + i;
                if (real >= orden.length) {
                    sonar(false);
                    return true;
                }
                if (ordenando) {
                    intercambiar(real);
                } else {
                    // El sonido lo decide si LLEGÓ a abrirse, no si la
                    // aplicación se declara abierta: si Cobblemon cambia y la
                    // Pokédex no abre, el clic tiene que sonar a bloqueado en
                    // vez de mentir con el sonido de "hecho".
                    sonar(Apps.abrir(orden[real]));
                }
                return true;
            }
        }
        return super.mouseClicked(ratonX, ratonY, boton);
    }

    /** Abre la tienda de LunaCoins, pasando por el aviso de Minecraft. */
    private void abrirTienda() {
        if (client == null || TIENDA.isEmpty()) {
            return;
        }
        client.setScreen(new ConfirmLinkScreen(abrir -> {
            if (abrir) {
                Util.getOperatingSystem().open(TIENDA);
            }
            client.setScreen(this);
        }, TIENDA, false));
    }

    /**
     * Coge una celda, o la suelta sobre otra intercambiándolas.
     *
     * <p>Volver a pulsar la que ya está cogida la <b>suelta</b> en vez de
     * intercambiarla consigo misma: es la forma de arrepentirse sin tener que
     * salir del modo.
     */
    private void intercambiar(int i) {
        if (cogida < 0) {
            cogida = i;
            sonar(true);
            return;
        }
        if (cogida != i) {
            App tmp = orden[cogida];
            orden[cogida] = orden[i];
            orden[i] = tmp;
            // Se guarda en cada movimiento, no al salir del modo: si el juego
            // se cierra a lo bruto, el orden que el jugador ya veía en pantalla
            // es el que se encuentra al volver.
            OrdenPad.guardar(orden);
        }
        cogida = -1;
        sonar(true);
    }

    /**
     * El clic suena, lleve a algún sitio o no.
     *
     * <p>Lo bloqueado suena <b>distinto</b>, no en silencio: sin sonido el
     * jugador cree que el clic no se registró y repite. Lo usan las quince
     * celdas y los seis botones, que están en la misma situación.
     */
    private void sonar(boolean lleva) {
        if (client != null && client.player != null) {
            client.player.playSound(lleva
                    ? SoundEvents.UI_BUTTON_CLICK.value()
                    : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
        }
    }

    /**
     * El panel de la izquierda: quién eres y cuánto tienes.
     *
     * <p>Las tres ranuras ya vienen dibujadas en el chasis; aquí solo se rellena
     * lo que cambia.
     */
    private void panelLateral(DrawContext ctx, int ratonX, int ratonY) {
        if (client == null || client.player == null) {
            return;
        }
        // La cabeza del jugador. Sale de su propia skin, así que no hace falta
        // pedirle nada al servidor.
        int lado = Math.round(CARA_LADO * k);
        ctx.drawTexture(client.player.getSkinTextures().texture(),
                x0 + Math.round(CARA_X * k), y0 + Math.round(CARA_Y * k),
                lado, lado, 8f, 8f, 8, 8, 64, 64);

        // Y el sombrero, la segunda capa. Sin ella, a quien lleve gorra en la
        // skin se le ve la cabeza pelada.
        ctx.drawTexture(client.player.getSkinTextures().texture(),
                x0 + Math.round(CARA_X * k), y0 + Math.round(CARA_Y * k),
                lado, lado, 40f, 8f, 8, 8, 64, 64);

        panelSesion(ctx, ratonX, ratonY);
    }

    /**
     * La barra de sesión: los dos saldos con su moneda y su «+».
     *
     * <p>Guiones mientras no ha llegado la respuesta del servidor: <b>«no lo sé»
     * y «tienes cero» no son lo mismo</b>, y un cero falso en un saldo asusta.
     *
     * <p>Los números van a 27 y no a 18: es <b>tres veces exactas</b> los 9 que
     * mide la fuente de Minecraft, así que siguen cayendo en píxeles enteros y
     * no se emborronan.
     */
    private void panelSesion(DrawContext ctx, int ratonX, int ratonY) {
        Red.Saldo saldo = EstadoCliente.saldo();
        Red.Ficha ficha = EstadoCliente.ficha();

        int y = FICHA_Y;
        // Las dos monedas. Guiones mientras no ha llegado la respuesta del
        // servidor: "no lo se" y "tienes cero" no son lo mismo, y un cero falso
        // en un saldo asusta.
        y = moneda(ctx, PLATA, y, saldo == null ? null : saldo.pokedolares(),
                   COLOR_PLATA, false, ratonX, ratonY);
        y = moneda(ctx, MONEDA, y, saldo == null ? null : saldo.reportcoins(),
                   LUNA, true, ratonX, ratonY);

        // Clan, trabajo y division. Todavia no tienen sistema detras, asi que
        // llegan vacios y se dibuja un guion — ver Red.Ficha.
        for (int i = 0; i < FILAS.length; i++) {
            String valor = ficha == null ? "" : switch (i) {
                case 0 -> ficha.clan();
                case 1 -> ficha.trabajo();
                default -> ficha.division();
            };
            fila(ctx, tex("fila_" + FILAS[i]), y,
                 Text.translatable("pokepad.lunaeternal.fila." + FILAS[i]),
                 valor == null || valor.isBlank() ? "-" : valor, FILA_COLOR);
            y += FILA_ALTO;
        }

        medallas(ctx, ficha);
    }

    /** El color de las etiquetas del panel: mas apagado que su valor, para que
     *  lo que se lea primero sea el dato y no como se llama. */
    private static final int FILA_COLOR = 0xFFFFFFFF;
    private static final int FILA_ETIQUETA = 0xFF9FB0D4;

    /**
     * Una fila de saldo: moneda, cifra y —solo las LunaCoins— su boton «+».
     *
     * <p>La cifra va a 27 y no a 18 porque es <b>tres veces exactas</b> los 9
     * que mide la fuente de Minecraft, asi que sigue cayendo en pixeles enteros
     * y no se emborrona.
     *
     * @return la y de la fila siguiente
     */
    private int moneda(DrawContext ctx, Identifier icono, int y, Long valor,
                       int color, boolean conMas, int ratonX, int ratonY) {
        fondoFila(ctx, y);

        int lado = Math.round(MONEDA_LADO * k);
        dibujar(ctx, icono, x0 + Math.round((FICHA_X0 + FILA_PAD) * k),
                y0 + Math.round((y + (FILA_CELDA - MONEDA_LADO) / 2) * k),
                lado, lado, MONEDA_LADO, MONEDA_LADO, 0xFFFFFFFF);

        texto(ctx, Text.literal(valor == null ? "- - -" : String.format("%,d", valor)),
              FICHA_X0 + FILA_PAD + MONEDA_LADO + 12,
              y + (FILA_CELDA - 27) / 2, 27, color, false, false);

        if (conMas) {
            // El «+» va apagado mientras no haya a donde ir, igual que las
            // quince celdas. Cuando exista la tienda, se enciende solo.
            int[] c = cajaMas(y);
            boolean encima = ratonX >= c[0] && ratonX < c[0] + c[2]
                    && ratonY >= c[1] && ratonY < c[1] + c[3];
            mas(ctx, c, !TIENDA.isEmpty(), encima);
        }
        return y + FILA_ALTO;
    }

    /** La celda de una fila, del ancho util del panel. */
    private void fondoFila(DrawContext ctx, int y) {
        celda(ctx, x0 + Math.round(FICHA_X0 * k), y0 + Math.round(y * k),
              Math.round((FICHA_X1 - FICHA_X0) * k), Math.round(FILA_CELDA * k),
              Math.max(1, Math.round(BORDE_GROSOR * k)),
              Math.max(1, Math.round(MORDIDA * k)),
              FILA_FONDO, FILA_BORDE, PANEL);
    }

    /** {x, y, ancho, alto} del "+", ya en pixeles de pantalla. */
    private int[] cajaMas(int y) {
        int lado = Math.round(MAS_LADO * k);
        return new int[]{
                x0 + Math.round((FICHA_X1 - FILA_PAD - MAS_LADO) * k),
                y0 + Math.round((y + (FILA_CELDA - MAS_LADO) / 2) * k),
                lado, lado};
    }

    /**
     * El "+", dibujado: su celda y dos barras cruzadas.
     *
     * <p>Las barras se centran <b>sobre la caja ya escalada</b>, no con numeros
     * aparte: a GUI Scale 1 la celda mide 34 px y a escala 4 mide 136, y un
     * grosor escrito a mano se descentraria en una de las dos.
     */
    private void mas(DrawContext ctx, int[] c, boolean vivo, boolean encima) {
        celda(ctx, c[0], c[1], c[2], c[3],
              Math.max(1, Math.round(BORDE_GROSOR * k)),
              Math.max(1, Math.round(MORDIDA * k)),
              vivo && encima ? CELDA_ENCIMA : FILA_FONDO,
              vivo ? (encima ? BORDE_ENCIMA : FILA_BORDE) : BORDE_CERRADA,
              FILA_FONDO);

        int g = Math.max(1, Math.round(MAS_GROSOR * k));
        int b = Math.round(MAS_BRAZO * k);
        int cx = c[0] + c[2] / 2, cy = c[1] + c[3] / 2;
        int tinta = vivo ? (encima ? BORDE_ENCIMA : 0xFFE8EDF8) : 0xFF7C859B;
        ctx.fill(cx - b / 2, cy - g / 2, cx + b / 2, cy + g / 2, tinta);
        ctx.fill(cx - g / 2, cy - b / 2, cx + g / 2, cy + b / 2, tinta);
    }

    /**
     * Una fila de dato: icono, etiqueta a la izquierda y valor a la DERECHA.
     *
     * <p>El valor alineado a la derecha y no pegado a su etiqueta: asi las tres
     * cifras caen en la misma columna y se leen como una tabla, aunque «Clan» y
     * «Division» midan distinto.
     */
    private void fila(DrawContext ctx, Identifier icono, int y,
                      Text etiqueta, String valor, int color) {
        fondoFila(ctx, y);

        int lado = Math.round(FILA_ICONO * k);
        dibujar(ctx, icono, x0 + Math.round((FICHA_X0 + FILA_PAD) * k),
                y0 + Math.round((y + (FILA_CELDA - FILA_ICONO) / 2) * k),
                lado, lado, FILA_ICONO, FILA_ICONO, 0xFFFFFFFF);

        int alto = TEXTO_ALTO;
        int arriba = y + (FILA_CELDA - alto) / 2;
        texto(ctx, etiqueta, FICHA_X0 + FILA_PAD + FILA_ICONO + 12, arriba, alto,
              FILA_ETIQUETA, false, false);
        texto(ctx, Text.literal(valor), FICHA_X1 - FILA_PAD, arriba, alto, color,
              false, false, true);
    }

    /**
     * Las medallas, en dos filas que se ajustan solas.
     *
     * <p>Las que no se tienen se dibujan <b>oscurecidas, no ocultas</b>: se ve
     * cual es cada una y cuantas faltan. El tinte MULTIPLICA, asi que basta un
     * gris azulado para apagarlas sin repintar nada.
     */
    private void medallas(DrawContext ctx, Red.Ficha ficha) {
        texto(ctx, Text.translatable("pokepad.lunaeternal.medallas"),
              FICHA_X0, MEDALLAS_Y - TEXTO_ALTO - 6, TEXTO_ALTO, FILA_ETIQUETA,
              false, false);

        int lado = Math.round(MEDALLA * k);
        int paso = MEDALLA_PASO;
        for (int i = 0; i < MEDALLAS.size(); i++) {
            int artX = MEDALLAS_X + (i % MEDALLA_COLS) * paso;
            int artY = MEDALLAS_Y + (i / MEDALLA_COLS) * paso;
            boolean tiene = ficha != null && (ficha.medallas() & (1 << i)) != 0;
            dibujar(ctx, MEDALLA_TEX[i],
                    x0 + Math.round(artX * k), y0 + Math.round(artY * k),
                    lado, lado, MEDALLA_LADO[i], MEDALLA_LADO[i],
                    tiene ? 0xFFFFFFFF : MEDALLA_APAGADA);
        }
    }

    /** Los identificadores, resueltos una sola vez. */
    private static final Identifier[] MEDALLA_TEX =
            new Identifier[MEDALLAS.size()];

    /**
     * Cuanto mide la textura de cada una.
     *
     * <p>⚠ Las de Kanto y Johto son de 16 y las cinco de la Liga Naranja de 64.
     * Pedirle 16 a una de 64 no da error: dibuja su esquina de arriba a la
     * izquierda estirada, que se ve como una medalla borrosa y cortada.
     */
    private static final int[] MEDALLA_LADO = new int[MEDALLAS.size()];

    static {
        // ⚠⚠ LA RUTA LA DA EL PROPIO GIMNASIO, no se compone aqui. Pegar
        //    «_badge» detras funcionaba con las dieciseis de Kanto y Johto, y
        //    deja de funcionar con los TROFEOS de campeon --se llaman
        //    `kanto_league_trophy`, no `kanto_league_trophy_badge`-- y con las
        //    cinco de la Liga Naranja, que son NUESTRAS y viven en otro mod.
        //    Componer la ruta en dos sitios distintos es como se llega a una
        //    textura que no existe: se ve como el cuadro morado.
        var porBit = net.pokereport.luna.gym.Gimnasio.porBit();
        for (int i = 0; i < MEDALLAS.size() && i < porBit.size(); i++) {
            MEDALLA_TEX[i] = porBit.get(i).textura();
            MEDALLA_LADO[i] = porBit.get(i).lado();
        }
    }

    /** Esta el raton sobre el «+» de las LunaCoins? */
    private boolean enMas(double ratonX, double ratonY) {
        // La SEGUNDA fila es la de LunaCoins, y su caja se pide con la misma
        // funcion que la dibuja: asi el sitio donde se pulsa y el sitio donde
        // se ve no pueden separarse nunca.
        int[] c = cajaMas(FICHA_Y + FILA_ALTO);
        return ratonX >= c[0] && ratonX < c[0] + c[2]
                && ratonY >= c[1] && ratonY < c[1] + c[3];
    }

    /** Los seis botones, cada uno donde le toca. */
    private void barra(DrawContext ctx, int ratonX, int ratonY) {
        for (int i = 0; i < BOTONES.length; i++) {
            int bx = botonX(i), by = botonY(i);
            int w = Math.round(BOTON[i][2] * k), h = Math.round(BOTON[i][3] * k);
            boolean encima = ratonX >= bx && ratonX < bx + w
                    && ratonY >= by && ratonY < by + h;
            // Un boton sin destino se apaga; el senalado se aclara. El tinte
            // MULTIPLICA, asi que 0xFF808080 es "a media luz" y no un gris.
            //
            // Y `ajustes` se queda ENCENDIDO mientras dura el modo de ordenar,
            // aunque no tengas el raton encima: es lo unico que dice que estas
            // dentro de un modo y que hay que volver a pulsarlo para salir.
            boolean vivo = activo(i) || (i == AJUSTES && ordenando);
            int tinte = vivo
                    ? (encima || (i == AJUSTES && ordenando) ? 0xFFFFFFFF : 0xFFE0E0E0)
                    : (encima ? 0xFF9A9A9A : 0xFF808080);
            dibujar(ctx, tex("boton_" + BOTONES[i]), bx, by, w, h,
                    BOTON[i][2], BOTON[i][3], tinte);
        }
    }

    /** La esquina de un botón, en unidades de interfaz. */
    private int botonX(int i) {
        return x0 + Math.round(BOTON[i][0] * k);
    }

    private int botonY(int i) {
        return y0 + Math.round(BOTON[i][1] * k);
    }

    /**
     * Una celda: rectángulo plano con las esquinas mordidas.
     *
     * <p>Morder las cuatro esquinas es como se redondea en pixel art. Sin eso,
     * quince rectángulos de esquina viva se ven como una hoja de cálculo.
     */
    private static void celda(DrawContext ctx, int x, int y, int lado,
                              int grosor, int mordida, int fondo, int borde) {
        celda(ctx, x, y, lado, lado, grosor, mordida, fondo, borde, PANTALLA);
    }

    /**
     * La misma celda, pero RECTANGULAR y sobre el fondo que se le diga.
     *
     * <p>Las dos cosas hacen falta para el panel de sesion: sus filas son
     * anchas y bajas, y no viven sobre la pantalla clara sino sobre el panel
     * oscuro del chasis. El color de las esquinas mordidas tiene que ser
     * <b>el del fondo que hay debajo</b> — es lo que se veria si la celda no
     * estuviera— y en el panel ese fondo no es {@link #PANTALLA}.
     */
    private static void celda(DrawContext ctx, int x, int y, int w, int h,
                              int grosor, int mordida, int fondo, int borde,
                              int fuera) {
        ctx.fill(x, y, x + w, y + h, borde);
        ctx.fill(x + grosor, y + grosor, x + w - grosor, y + h - grosor, fondo);
        // Las cuatro esquinas, PINTADAS DEL COLOR DEL FONDO DE DEBAJO.
        //
        // Antes se rellenaban con 0x00000000 y no hacian nada: `fill` mezcla, y
        // mezclar un color con alfa cero deja el pixel exactamente igual. La
        // esquina no se mordia; solo lo parecia porque a 1 px no se distingue
        // el efecto de su ausencia. A 4 px si se distingue, asi que hay que
        // pintar de verdad.
        ctx.fill(x, y, x + mordida, y + mordida, fuera);
        ctx.fill(x + w - mordida, y, x + w, y + mordida, fuera);
        ctx.fill(x, y + h - mordida, x + mordida, y + h, fuera);
        ctx.fill(x + w - mordida, y + h - mordida, x + w, y + h, fuera);
    }


    /**
     * Dibuja una textura entera en el hueco indicado.
     *
     * <p>Hace falta la sobrecarga larga de {@code drawTexture}: la corta usa el
     * mismo número para el tamaño en pantalla y para la región de la textura,
     * así que no puede escalar.
     */
    private static void dibujar(DrawContext ctx, Identifier textura, int x, int y,
                                int ancho, int alto, int natW, int natH, int tinte) {
        boolean tenido = tinte != 0xFFFFFFFF;
        if (tenido) {
            // El color del shader MULTIPLICA a la textura, así que va antes de
            // dibujar. Después no tiñe nada.
            ctx.setShaderColor(((tinte >> 16) & 0xFF) / 255f,
                    ((tinte >> 8) & 0xFF) / 255f, (tinte & 0xFF) / 255f, 1f);
        }
        // ⚠ LA MEZCLA ALFA HAY QUE ENCENDERLA A MANO. Sin esto, el juego trata
        // CUALQUIER alfa mayor que cero como opaco: un pixel con alfa 1 se
        // dibuja a todo color.
        //
        // Medido sobre una captura del juego, icono a icono: con alfa 0 el
        // dibujado era correcto, y de alfa 1 en adelante el pixel salia con su
        // color CRUDO. Eso es lo que se veia primero como motas de colores
        // --el arte guardaba verde y rojo puros en pixeles invisibles-- y
        // despues, ya limpio el arte, como un cerco negro alrededor de cada
        // icono: el mismo fallo pintando el color del contorno.
        //
        // El arte no tenia la culpa. Cobblemon hace esto mismo en cada dibujo
        // de interfaz (api/gui/GuiUtils.kt), y por eso a ellos no les pasa.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // `natW/natH` son el tamaño REAL de la PNG, y hay que pasarlos: con el
        // tamaño en pantalla, Minecraft tomaría solo esa esquina de la textura
        // en lugar de la textura entera.
        ctx.drawTexture(textura, x, y, ancho, alto, 0f, 0f, natW, natH, natW, natH);
        RenderSystem.disableBlend();
        if (tenido) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * Escribe una línea centrada, medida en PIXELES DEL ARTE.
     *
     * <p>La fuente de Minecraft mide 9 y se dibuja en unidades de interfaz, así
     * que sin esto un mismo texto sale de un tamaño distinto para cada jugador
     * según su <i>GUI Scale</i>. Aquí se escala la matriz para que el texto
     * ocupe siempre {@code alto} píxeles del arte, que es lo que hace que
     * encaje bajo la celda pase lo que pase.
     *
     * @param cx    centro horizontal, en píxeles del arte
     * @param arriba borde superior del texto, en píxeles del arte
     */
    private void texto(DrawContext ctx, net.minecraft.text.Text linea,
                       int cx, int arriba, int alto, int color) {
        texto(ctx, linea, cx, arriba, alto, color, true, true);
    }

    /**
     * @param centrado {@code false} para alinear por la izquierda desde {@code cx}
     * @param contorno {@code false} sobre fondo oscuro, donde no hace falta y
     *                 además engorda la letra
     */
    private void texto(DrawContext ctx, net.minecraft.text.Text linea,
                       int cx, int arriba, int alto, int color,
                       boolean centrado, boolean contorno) {
        texto(ctx, linea, cx, arriba, alto, color, centrado, contorno, false);
    }

    /**
     * @param derecha {@code true} para que {@code cx} sea el borde DERECHO del
     *                texto. Es lo que alinea los valores del panel de sesion en
     *                una columna, midan lo que midan las etiquetas.
     */
    private void texto(DrawContext ctx, net.minecraft.text.Text linea,
                       int cx, int arriba, int alto, int color,
                       boolean centrado, boolean contorno, boolean derecha) {
        float escala = alto * k / textRenderer.fontHeight;
        if (escala <= 0) {
            return;
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.scale(escala, escala, 1f);

        // Ya dentro de la matriz escalada, las coordenadas van divididas por
        // ella: lo que se pide en pixeles del arte acaba cayendo donde toca.
        // Y el centrado se hace a mano porque la version "conSombra" no deja
        // apagar la sombra, y aqui hace falta contorno en vez de sombra.
        int ancho = textRenderer.getWidth(linea);
        int px = Math.round(cx * k / escala)
                - (centrado ? ancho / 2 : derecha ? ancho : 0);
        int py = Math.round(arriba * k / escala);

        // ⚠ CONTORNO, NO SOMBRA.
        //
        // La sombra de Minecraft es una copia desplazada en diagonal: sobre un
        // fondo oscuro se lee, pero estas celdas son CLARAS y el nombre quedaba
        // gris sobre claro, ilegible.
        //
        // Un contorno cierra la letra sobre CUALQUIER fondo, que es la unica
        // garantia que sirve aqui: la celda cambia de color al pasar el raton, y
        // encima cada aplicacion tendra el suyo algun dia.
        //
        // El color del contorno es el CONTRARIO del texto, y por eso los dos son
        // constantes: en el chasis v4 pasaron de negro/blanco a claro/oscuro de
        // golpe, y si el negro estuviera escrito aqui a mano se habria quedado.
        //
        // EN CRUZ Y NO EN LAS OCHO DIRECCIONES. Con las diagonales el contorno
        // sale grueso y el nombre se emborrona; en cruz cierra igual la letra y
        // pesa la mitad.
        if (contorno) {
            ctx.drawText(textRenderer, linea, px - 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px + 1, py, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py - 1, TEXTO_CONTORNO, false);
            ctx.drawText(textRenderer, linea, px, py + 1, TEXTO_CONTORNO, false);
        }
        ctx.drawText(textRenderer, linea, px, py, color, false);
        m.pop();
    }

    private int celdaX(int i) {
        return x0 + Math.round((REJ_X + (i % COLS) * (CELDA + HUECO_X)) * k);
    }

    private int celdaY(int i) {
        return y0 + Math.round((REJ_Y + (i / COLS) * (CELDA + HUECO_Y)) * k);
    }
}
