package net.pokereport.luna.pokedex;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Qué especies tienen voz grabada en la Pokédex.
 *
 * <p>Es el catálogo y nada más: no toca red, ni base de datos, ni sonido. Se
 * separa a propósito para poder comprobarlo en {@code /luna autotest} sin
 * necesidad de un jugador escaneando (MOD-006).
 *
 * <p><b>La lista NO se escribe a mano.</b> La genera {@code tools/gen_voces.py}
 * junto a los {@code .ogg} y al {@code sounds.json}, porque son tres sitios que
 * tienen que ir sincronizados y basta con olvidarse de uno para que el jugador
 * escanee y no suene nada — sin un solo error en ningún log.
 *
 * <p>Vive en {@code main/resources}, que se compila en los <b>dos</b> lados: el
 * servidor la usa para saber a quién mandarle voz y el cliente para saber si
 * pinta el botón encendido. La misma lista, leída una vez.
 */
public final class VozService {

    /** Generado por {@code tools/gen_voces.py}. Una clave por línea. */
    private static final String RECURSO = "/voces.txt";

    private static final Set<String> CON_VOZ = cargar();

    private VozService() {}

    private static Set<String> cargar() {
        Set<String> claves = new HashSet<>();
        try (InputStream in = VozService.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                return claves;      // sin fichero: todo mudo, nada roto
            }
            var lector = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String linea;
            while ((linea = lector.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty() && !linea.startsWith("#")) {
                    claves.add(linea);
                }
            }
        } catch (Exception e) {
            // Que no se pueda leer el catalogo no es motivo para tumbar nada:
            // se juega igual, solo que en silencio.
            System.err.println("Voces: no se pudo leer " + RECURSO + ": " + e);
        }
        return claves;
    }

    /**
     * Normaliza un nombre al que usan los ficheros.
     *
     * <p>Cobblemon los da de varias formas según de dónde se lean:
     * {@code cobblemon:bulbasaur}, {@code Bulbasaur}, {@code Nidoran-F}. Se
     * reduce todo a minúsculas con guion bajo.
     */
    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        String s = nombre.toLowerCase(Locale.ROOT).trim();
        int dosPuntos = s.indexOf(':');
        if (dosPuntos >= 0) {
            s = s.substring(dosPuntos + 1);
        }
        return s.replace(' ', '_').replace('-', '_').replace(".", "")
                .replace("'", "").replace("’", "");
    }

    /**
     * La clave de una especie y su forma, prefiriendo la más específica.
     *
     * <p><b>Las formas regionales no son especies aparte en Cobblemon</b>: un
     * Rattata de Alola es la especie {@code rattata} con la forma
     * {@code Alola}. Si esa forma tiene su propia grabación se usa; si no, cae
     * en la de la especie base, que es mejor que el silencio — describe al
     * mismo bicho.
     *
     * @return la clave con voz, o cadena vacía si no hay ninguna
     */
    public static String clave(String especie, String forma) {
        String base = normalizar(especie);
        if (forma != null && !forma.isBlank()) {
            String conForma = base + "_" + normalizar(forma);
            if (CON_VOZ.contains(conForma)) {
                return conForma;
            }
        }
        return CON_VOZ.contains(base) ? base : "";
    }

    /** ¿Hay voz grabada con esta clave exacta? */
    public static boolean tieneVoz(String clave) {
        return CON_VOZ.contains(normalizar(clave));
    }

    /** Cuántas voces hay. Se anuncia al arrancar. */
    public static int cuantas() {
        return CON_VOZ.size();
    }
}
