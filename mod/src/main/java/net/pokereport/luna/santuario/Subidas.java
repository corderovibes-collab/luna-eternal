package net.pokereport.luna.santuario;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El reensamblado de una foto que llega TROCEADA del cliente.
 *
 * <h2>⚠⚠⚠ EXISTE POR UN FALLO MUDO QUE YA PASO, y es la clase de fallo que
 * este proyecto mas teme</h2>
 *
 * La primera version vivia dentro del manejador de {@code Red} y comparaba
 * <b>el indice del trozo contra el tamano total en bytes</b>
 * ({@code indice == total - 1}). Para una foto de 500 KB --31 trozos-- el
 * indice 30 nunca iguala a 499.999, asi que la subida NUNCA se completaba:
 * sin error, sin fila en la base, sin respuesta al cliente. El sintoma lo
 * describio el usuario: «no sale nadie de pendientes».
 *
 * <p>Ninguna revision a ojo lo habria visto: el codigo era correcto de
 * sintaxis y el chequeo sonaba razonable. <b>Por eso esto ahora es una clase
 * propia con invariantes en el autotest</b> -- la comparacion de verdad es
 * {@code bytesRecibidos == total}, y se prueba trozo a trozo.
 *
 * <h2>⚠ EN MEMORIA Y ACOTADA</h2>
 *
 * Solo hay una subida en curso por jugador (empezar otra descarta la anterior)
 * y el total no puede pasar de {@code FOTO_MAX_BYTES}: sin las dos cotas, un
 * cliente modificado llenaria la memoria del servidor mandando trozos sin
 * parar (P6).
 */
public final class Subidas {

    /** Cuantos bytes por trozo. 16 KB caben en un payload con holgura. */
    public static final int TROZO = 16 * 1024;

    private record EnCurso(String idem, int total, ByteArrayOutputStream datos) {}

    private static final Map<UUID, EnCurso> EN_CURSO = new ConcurrentHashMap<>();

    private Subidas() {}

    /** Lo que toca hacer con el trozo que acaba de llegar. */
    public enum Resultado {
        /** Guardado; la foto aun no esta entera. */
        SEGUIR,
        /** Descarta la subida entera: el rompecabezas no tiene arreglo. */
        ROTA,
        /** La foto esta entera; los bytes viajan en {@code bytes}. */
        COMPLETA
    }

    /**
     * Recibe un trozo.
     *
     * @param completa solo se llama con {@link Resultado#COMPLETA}; el buffer
     *                 con los bytes enteros. Se invoca EN EL MISMO HILO que
     *                 recibe el trozo (el del servidor), antes de devolver.
     */
    public static Resultado recibir(UUID jugador, String idem, int total,
                                    int indice, byte[] trozo,
                                    java.util.function.Consumer<byte[]> completa) {
        if (total <= 0 || total > SantuarioService.FOTO_MAX_BYTES) {
            EN_CURSO.remove(jugador);
            return Resultado.ROTA;
        }
        EnCurso curso = EN_CURSO.compute(jugador, (u, anterior) ->
                anterior == null || !anterior.idem().equals(idem)
                        ? new EnCurso(idem, total, new ByteArrayOutputStream(total))
                        : anterior);
        // ⚠⚠ LA GUARDA DE ORDEN, y ahora por TAMAÑO ACUMULADO, no por indice
        //    contra bytes: el trozo que toca es el que empieza justo donde
        //    acaba lo que ya hay. Un trozo duplicado, desordenado o de otro
        //    tamano no se ensambla.
        int esperado = curso.datos().size() / TROZO;
        if (indice != esperado || trozo == null || trozo.length == 0
                || trozo.length > TROZO) {
            EN_CURSO.remove(jugador);
            return Resultado.ROTA;
        }
        curso.datos().write(trozo, 0, trozo.length);
        if (curso.datos().size() > total) {
            // ⚠ Un trozo final mas grande que el hueco que queda: subida rota.
            EN_CURSO.remove(jugador);
            return Resultado.ROTA;
        }
        if (curso.datos().size() == total) {
            EN_CURSO.remove(jugador);
            completa.accept(curso.datos().toByteArray());
            return Resultado.COMPLETA;
        }
        return Resultado.SEGUIR;
    }

    /** Se olvida la subida a medias cuando el jugador se va. */
    public static void olvidar(UUID jugador) {
        EN_CURSO.remove(jugador);
    }
}
