package net.pokereport.luna.santuario;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.santuario.NichoCatalogo.Nicho;

/**
 * DEFINIR LOS NICHOS DE PIE DENTRO DE ELLOS, EN VEZ DE ESCRIBIENDO COORDENADAS.
 *
 * <h2>&#9888;&#9888;&#9888; LA CONFIG DEL SANTUARIO REVIENTA EL ARRANQUE SI ESTA
 * MAL, Y ESO ES LO QUE HACE PELIGROSO UN COMANDO QUE LA ESCRIBA</h2>
 *
 * {@link NichoCatalogo} se niega a cargar --y con ella el servidor-- si hay dos
 * ids iguales, dos nichos solapados o un proyector fuera de su caja. Esa guarda
 * es correcta y no se toca: una coordenada mal puesta significa una zona
 * protegida que no es la construida, y eso <b>no da error, da un hueco que
 * alguien descubre rompiendo el memorial de otro</b>.
 *
 * <p><b>Pero un comando que escriba esa config puede dejar el servidor sin
 * arrancar, y quien lo ejecuta no se enteraria hasta el siguiente reinicio</b>
 * -- que es el peor momento posible, y es la misma familia de fallo que ya
 * mordio con {@code letmedespawn} y con la migracion V034. Por eso aqui:
 *
 * <ol>
 *   <li>se construye la lista NUEVA en memoria;</li>
 *   <li>se pasa por {@link NichoCatalogo#validar} <b>antes de tocar el disco</b>,
 *       y si no pasa <b>no se escribe nada</b> y se dice por que;</li>
 *   <li>se escribe a un fichero temporal y se MUEVE encima del bueno, para que
 *       una caida a mitad de escritura no deje un JSON truncado -- que es otra
 *       forma de no arrancar.</li>
 * </ol>
 *
 * <h2>&#9888;&#9888; LA FORMA DEL NICHO ES UNA CONSTANTE, Y SALE DE MEDIR EL QUE
 * YA ESTABA</h2>
 *
 * No se invento: se leyo el {@code nicho_prueba} que hay en el servidor
 * --{@code min [-112,70,155]}, {@code max [-110,74,157]},
 * {@code proyector [-111,73,156]}-- y de ahi salen los tres numeros de abajo. Un
 * nicho capturado hoy tiene <b>exactamente la misma forma</b> que el construido
 * a mano, asi que el primero que se coloque no obliga a rehacer el que ya hay.
 *
 * <p>&#9888; Si algun dia los nichos se construyen de otra forma, se cambian
 * <b>estos tres numeros y ya</b>: los nichos viejos siguen valiendo porque la
 * config guarda coordenadas, no formas.
 */
public final class NichoEditor {

    /**
     * Cuanto se extiende el nicho a cada lado del centro. 1 = una caja de 3x3.
     *
     * <p>&#9888; El documento del santuario dice «un nicho 3x3» en todas partes:
     * este numero es esa frase, escrita donde se puede comprobar.
     */
    public static final int RADIO = 1;

    /** Cuantos bloques de alto ocupa la caja protegida, contando el suelo. */
    public static final int ALTO = 5;

    /**
     * A que altura sobre el suelo del nicho va el proyector.
     *
     * <h2>&#9888;&#9888;&#9888; ESTE ES EL NUMERO DE LA HOLOGRAFICA, Y ESTABA EN 3</h2>
     *
     * La foto no tiene posicion propia: {@code HologramaSantuario} la dibuja
     * centrada a <b>{@code proyector.y + 1,75}</b> y mide 2,4 de alto. Con el
     * pedestal a 3 sobre el suelo, eso ponia la foto entre <b>+3,55 y +5,95</b>
     * en una caja de 5: <b>se salia por el techo del nicho</b>, medio metida en
     * el dintel. Es lo que el usuario vio y reporto como «estan mal
     * posicionados».
     *
     * <p>Con 1 --altura de cintura, que ademas es donde un pedestal <b>se puede
     * pulsar</b>-- la foto va de +1,55 a +3,95 y cabe entera.
     *
     * <p>&#9888;&#9888; <b>ESTO SOLO AFECTA A LO QUE SE CAPTURE A PARTIR DE
     * AHORA.</b> Los nichos ya escritos en la config siguen con su proyector
     * donde estaba, porque la config guarda coordenadas y no formas -- que es
     * justo lo que la hace sobrevivir a este cambio. Para los que ya estan:
     * {@code /luna santuario nicho recolocar}.
     */
    public static final int ALTURA_PROYECTOR = 1;

    /** Prefijo de los ids que se generan solos. */
    private static final String PREFIJO = "nicho_";

    private NichoEditor() {
    }

    // ------------------------------------------------------------- capturar

    /**
     * El nicho que sale de estar de pie en su centro.
     *
     * @param suelo el bloque sobre el que estan los pies de quien lo captura
     */
    public static Nicho capturar(BlockPos suelo, String id, String nombre) {
        BlockPos min = new BlockPos(suelo.getX() - RADIO, suelo.getY(),
                suelo.getZ() - RADIO);
        BlockPos max = new BlockPos(suelo.getX() + RADIO, suelo.getY() + ALTO - 1,
                suelo.getZ() + RADIO);
        BlockPos proyector = new BlockPos(suelo.getX(),
                suelo.getY() + ALTURA_PROYECTOR, suelo.getZ());
        return new Nicho(id, nombre, min, max, proyector);
    }

    /**
     * El primer id libre de la forma {@code nicho_01}, {@code nicho_02}...
     *
     * <p>&#9888;&#9888; <b>Existe porque el usuario dijo «son muchisimos».</b>
     * Teclear un identificador nuevo en cada nicho es la friccion que hace que
     * alguien acabe repitiendo uno -- y dos nichos con el mismo id comparten
     * <b>una sola fila</b> en la base: alquilar uno cobraria dos sitios. Aqui no
     * puede pasar, porque el numero lo pone el catalogo.
     *
     * <p>&#9888; Se busca el primer HUECO y no «el ultimo + 1»: asi, borrar el
     * nicho 7 y capturar otro vuelve a dar el 7, en vez de dejar un agujero en
     * la numeracion para siempre.
     *
     * @return el id, o {@code null} si ya no queda ninguno libre
     */
    public static String siguienteId(List<Nicho> actuales) {
        for (int n = 1; n <= 999; n++) {
            String id = PREFIJO + String.format("%02d", n);
            boolean libre = true;
            for (Nicho x : actuales) {
                if (x.id().equals(id)) {
                    libre = false;
                    break;
                }
            }
            if (libre) {
                return id;
            }
        }
        return null;
    }

    /** El nombre por defecto de un id generado: «Nicho 7» a partir de «nicho_07». */
    public static String nombrePara(String id) {
        String cola = id.startsWith(PREFIJO) ? id.substring(PREFIJO.length()) : id;
        try {
            return "Nicho " + Integer.parseInt(cola);
        } catch (NumberFormatException e) {
            return "Nicho " + cola;
        }
    }

    // -------------------------------------------------------------- guardar

    /**
     * VALIDA Y ESCRIBE LA CONFIG ENTERA.
     *
     * <p>&#9888;&#9888;&#9888; Este es el unico sitio del proyecto que escribe
     * {@code santuario.json}, y por eso la validacion vive aqui dentro y no en
     * quien llama: un segundo camino de escritura que se saltara la comprobacion
     * seria exactamente la forma de dejar el servidor sin arrancar.
     *
     * @return {@code null} si se guardo, o el motivo por el que NO se guardo
     */
    public static String guardar(List<Nicho> nichos) {
        try {
            NichoCatalogo.validar(nichos);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        Path destino = NichoCatalogo.ruta();
        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.createDirectories(destino.getParent());
            Files.writeString(temporal, json(nichos), StandardCharsets.UTF_8);
            // ⚠ MOVER y no escribir encima: si el proceso se cayera a mitad de
            //   un `writeString` sobre el fichero bueno, lo que quedaria en
            //   disco seria un JSON cortado -- y eso tampoco arranca. Mover es
            //   atomico donde el sistema de ficheros lo permite, y donde no, al
            //   menos deja la ventana en microsegundos en vez de en la escritura
            //   entera.
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return "no se pudo escribir " + destino + ": " + e.getMessage();
        }
        LunaEternal.LOG.info("Santuario: config escrita con {} nichos", nichos.size());
        return null;
    }

    /**
     * El JSON, escrito a mano y con las coordenadas EN UNA LINEA.
     *
     * <p>&#9888; Gson por defecto parte {@code [x, y, z]} en cinco lineas --es
     * lo que le paso al {@code nicho_prueba} que hay en el servidor-- y con
     * cuarenta nichos eso son seiscientas lineas para mirar tres numeros. Este
     * fichero se lee y se edita a mano mientras se construye, asi que se escribe
     * para leerlo.
     */
    public static String json(List<Nicho> nichos) {
        var sb = new StringBuilder("{\n  \"nichos\": [\n");
        for (int i = 0; i < nichos.size(); i++) {
            Nicho n = nichos.get(i);
            sb.append("    {")
              .append(" \"id\": \"").append(n.id()).append("\",")
              .append(" \"nombre\": \"").append(escapar(n.nombre())).append("\",")
              .append(" \"min\": ").append(tres(n.min())).append(",")
              .append(" \"max\": ").append(tres(n.max())).append(",")
              .append(" \"proyector\": ").append(tres(n.proyector()))
              .append(" }");
            if (i < nichos.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        return sb.append("  ]\n}\n").toString();
    }

    private static String tres(BlockPos p) {
        return "[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]";
    }

    /**
     * &#9888; El nombre lo escribe una persona por comando. {@code validar} ya
     * prohibe el {@code §} y limita la longitud, pero <b>no las comillas</b>: sin
     * escaparlas, un nicho llamado {@code El "Bueno"} escribiria un JSON roto --
     * y un JSON roto en este fichero es un servidor que no arranca.
     */
    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------ ayudantes

    /** La lista del catalogo, en una copia que se puede modificar. */
    public static List<Nicho> copia(NichoCatalogo catalogo) {
        return new ArrayList<>(catalogo.todos());
    }

    /** Quita de la lista el nicho con ese id. {@code true} si estaba. */
    public static boolean quitar(List<Nicho> lista, String id) {
        return lista.removeIf(n -> n.id().equals(id));
    }

    /**
     * PONE EL PROYECTOR DE UN NICHO DONDE HOY DICE LA FORMA, sin tocar su caja.
     *
     * <p>&#9888;&#9888; Existe porque {@link #ALTURA_PROYECTOR} bajo de 3 a 1, y
     * los nichos capturados con el numero viejo tienen la foto saliendose por el
     * techo. Recapturarlos uno a uno seria volver a andar hasta cada uno --con
     * «muchisimos» nichos, eso es la tarde entera-- y ademas <b>perderia su
     * nombre y su reclamacion</b>.
     *
     * <p>&#9888; Solo mueve el proyector: la caja, el id y el nombre se quedan
     * como estan. Un nicho cuyo proyector ya este bien no cambia.
     *
     * @return el nicho con el proyector recolocado
     */
    public static Nicho recolocar(Nicho n) {
        BlockPos centro = new BlockPos(
                (n.min().getX() + n.max().getX()) / 2,
                n.min().getY() + ALTURA_PROYECTOR,
                (n.min().getZ() + n.max().getZ()) / 2);
        return new Nicho(n.id(), n.nombre(), n.min(), n.max(), centro);
    }
}
