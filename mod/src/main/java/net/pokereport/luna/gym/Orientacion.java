package net.pokereport.luna.gym;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.api.FabricLoader;
import net.pokereport.luna.LunaEternal;

/**
 * HACIA DÓNDE MIRA CADA LÍDER EN LA CIUDADELA.
 *
 * <p>Petición del usuario: <i>«necesito que me ayudes a que Brock esté mirando,
 * o pueda personalizar a dónde está mirando»</i>.
 *
 * <h2>⚠⚠⚠ EL GIRO YA SE PODÍA PASAR, Y SE PERDÍA AL REINICIAR</h2>
 *
 * {@code /luna gimnasio ciudadela <grados>} existía desde el primer día y hacía
 * lo que dice — <b>una vez</b>. El número viajaba como parámetro, se aplicaba, y
 * no se guardaba en ninguna parte: al siguiente arranque el líder volvía a mirar
 * a donde dice el código, que es 0.
 *
 * <p>Y eso <b>no da ningún error</b>. El comando contesta «hecho», el líder gira
 * de verdad, y el fallo aparece días después con cara de otra cosa: «Brock se ha
 * vuelto a girar solo». Es la misma familia que el clan que no se refrescaba —
 * un estado que se cambia en un sitio y no se guarda en el otro.
 *
 * <h2>⚠⚠ Un fichero y no una tabla, y el motivo es cuándo se lee</h2>
 *
 * Esto se necesita <b>durante el arranque</b>, colocando entidades en el hilo
 * del servidor, y ahí consultar la base está prohibido (regla 1). Un fichero de
 * texto se lee una vez, cabe en memoria, y de propina <b>se puede editar a
 * mano</b> cuando alguien esté construyendo la sala y no quiera abrir el juego.
 *
 * <p>⚠ Y por eso no hay migración: no toca el esquema.
 */
public final class Orientacion {

    private Orientacion() {}

    private static final String FICHERO = "luna-recepciones.properties";

    /** El giro guardado de cada gimnasio, por identificador. */
    private static final Map<String, Float> GIROS = new ConcurrentHashMap<>();

    private static boolean cargado = false;

    private static Path ruta() {
        return FabricLoader.getInstance().getConfigDir().resolve(FICHERO);
    }

    /** Lee el fichero una vez. Si no existe, no pasa nada: valen los del código. */
    public static synchronized void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        Path f = ruta();
        if (!Files.exists(f)) {
            return;
        }
        var props = new Properties();
        try (var in = Files.newInputStream(f)) {
            props.load(in);
        } catch (IOException e) {
            LunaEternal.LOG.warn("No se pudo leer {}: {}", FICHERO, e.toString());
            return;
        }
        int n = 0;
        for (String clave : props.stringPropertyNames()) {
            if (!clave.endsWith(".giro")) {
                continue;
            }
            String id = clave.substring(0, clave.length() - ".giro".length());
            // ⚠⚠ SE COMPRUEBA QUE EL GIMNASIO EXISTA. Sin esto, una línea con el
            //    nombre mal escrito se guarda en el mapa y no la usa nadie: el
            //    usuario giraría a Brock, vería que no cambia, y no habría ni un
            //    aviso que lo relacionara con la errata.
            if (Gimnasio.de(id) == null) {
                LunaEternal.LOG.warn(
                        "{}: «{}» no es ningún gimnasio; esa línea no hace nada",
                        FICHERO, id);
                continue;
            }
            try {
                GIROS.put(id, normalizar(Float.parseFloat(props.getProperty(clave))));
                n++;
            } catch (NumberFormatException e) {
                LunaEternal.LOG.warn("{}: «{}» no es un número de grados",
                        FICHERO, props.getProperty(clave));
            }
        }
        if (n > 0) {
            LunaEternal.LOG.info("Gimnasios: {} orientaciones guardadas", n);
        }
    }

    /**
     * Hacia dónde mira ese líder: lo guardado, o lo que diga el código.
     *
     * @param porDefecto el giro de su {@link Gimnasio.Recepcion}
     */
    public static float giro(String gimnasio, float porDefecto) {
        cargar();
        Float g = GIROS.get(gimnasio);
        return g == null ? porDefecto : g;
    }

    /** ¿Se le ha puesto uno a mano? Para que el comando lo diga. */
    public static boolean guardado(String gimnasio) {
        cargar();
        return GIROS.containsKey(gimnasio);
    }

    /**
     * Guarda el giro de un líder y lo deja escrito en disco.
     *
     * <p>⚠ Escribe el fichero <b>entero</b> cada vez: son cinco líneas y
     * reescribirlo es lo único que garantiza que lo de memoria y lo del disco
     * digan lo mismo. Ir añadiendo al final dejaría claves repetidas, y entonces
     * mandaría la última — que es un orden que nadie ve.
     */
    public static synchronized boolean poner(String gimnasio, float grados) {
        cargar();
        GIROS.put(gimnasio, normalizar(grados));
        return escribir();
    }

    /** Olvida el de un líder: vuelve al del código. */
    public static synchronized boolean quitar(String gimnasio) {
        cargar();
        if (GIROS.remove(gimnasio) == null) {
            return false;
        }
        return escribir();
    }

    private static boolean escribir() {
        var props = new Properties();
        for (var e : GIROS.entrySet()) {
            props.setProperty(e.getKey() + ".giro", String.valueOf(e.getValue()));
        }
        try (var out = Files.newOutputStream(ruta())) {
            props.store(out, "Hacia donde mira cada lider en la ciudadela."
                    + " Grados: 0 sur, 90 oeste, 180 norte, -90 este.");
            return true;
        } catch (IOException e) {
            LunaEternal.LOG.error("No se pudo guardar {}", FICHERO, e);
            return false;
        }
    }

    /**
     * A −180..180, que es el rango en el que Minecraft guarda el giro.
     *
     * <p>⚠ Sin esto, escribir 270 en el fichero deja un valor que el cliente
     * interpola dando <b>una vuelta entera</b> al aparecer. Se ve como un tirón.
     */
    private static float normalizar(float grados) {
        float g = grados % 360f;
        if (g > 180f) {
            g -= 360f;
        }
        if (g < -180f) {
            g += 360f;
        }
        return g;
    }
}
