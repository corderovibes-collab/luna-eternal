package net.pokereport.luna.ui;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.net.PadPayloads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor del Pad (D-025).
 *
 * <p><b>Lo importante de esta clase es lo que NO se fía del cliente.</b> El
 * cliente solo puede decir «he pulsado el índice 7 de la pantalla X». Antes de
 * ejecutar nada, aquí se comprueba:
 *
 * <ol>
 *   <li>que ese jugador tenga esa pantalla abierta <b>ahora</b>;</li>
 *   <li>que el índice exista en la pantalla que el servidor envió;</li>
 *   <li>que la celda no estuviera bloqueada.</li>
 * </ol>
 *
 * <p>Sin eso, un cliente modificado podría pulsar botones que nunca vio —
 * incluidos los de otra pantalla. Es exactamente el agujero que P6 existe para
 * cerrar.
 */
public final class PadService {

    /** Qué pantalla tiene abierta cada jugador, y con qué celdas. */
    private record Abierta(String pantalla, List<Accion> acciones) {}

    /**
     * Historial de navegación por jugador.
     *
     * <p>Vive en el SERVIDOR, no en el cliente. Si el cliente decidiera a
     * dónde vuelve el botón «atrás», bastaría con modificarlo para saltar a
     * una pantalla que no te corresponde. Aquí, «atrás» solo puede llevarte
     * a algo que el servidor te abrió antes.
     */
    private static final Map<UUID, java.util.Deque<Runnable>> HISTORIAL =
        new ConcurrentHashMap<>();

    /** Qué hace una celda al pulsarla. */
    @FunctionalInterface
    public interface Accion {
        void ejecutar(ServerPlayerEntity jugador, boolean derecho);
    }

    private static final Map<UUID, Abierta> ABIERTAS = new ConcurrentHashMap<>();

    /** Cómo se vuelve al inicio. Lo fija el mod al arrancar. */
    private static java.util.function.Consumer<ServerPlayerEntity> inicio =
        j -> {};

    public static void inicio(java.util.function.Consumer<ServerPlayerEntity> r) {
        inicio = r;
    }

    private PadService() {}

    /**
     * Receptores del lado servidor.
     *
     * <p>Los TIPOS de paquete NO se declaran aqui: van en
     * {@link net.pokereport.luna.LunaCommon}, que corre en los dos lados.
     * Tenerlos aqui hacia que el cliente reventase al arrancar, porque este
     * metodo solo se ejecuta en un servidor dedicado.
     */
    public static void registrar() {
        ServerPlayNetworking.registerGlobalReceiver(
            PadPayloads.Pulsar.ID, (payload, context) ->
                context.server().execute(() ->
                    pulsar(context.player(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(
            PadPayloads.Cerrar.ID, (payload, context) ->
                ABIERTAS.remove(context.player().getUuid()));
    }

    /** ¿Tiene este jugador el mod de cliente? */
    public static boolean soportado(ServerPlayerEntity jugador) {
        return ServerPlayNetworking.canSend(jugador, PadPayloads.Abrir.ID);
    }

    /** Constructor de pantallas, para no montar listas a mano. */
    public static final class Pantalla {
        private final String id;
        private final String titulo;
        private final int columnas, filas;
        private final List<PadPayloads.Celda> celdas = new ArrayList<>();
        private final List<Accion> acciones = new ArrayList<>();
        private final List<String> pie = new ArrayList<>();
        private final List<String> izquierda = new ArrayList<>();
        private final List<String> derecha = new ArrayList<>();
        private String estilo = PadPayloads.REJILLA;
        private Runnable reabrir;

        public Pantalla(String id, String titulo, int columnas, int filas) {
            this.id = id;
            this.titulo = titulo;
            this.columnas = columnas;
            this.filas = filas;
        }

        public Pantalla celda(int col, int fila, String icono, String titulo,
                              List<String> descripcion, boolean bloqueada,
                              Accion accion) {
            if (col < 0 || col >= columnas || fila < 0 || fila >= filas) {
                throw new IllegalArgumentException(
                    "celda (" + col + "," + fila + ") fuera de la rejilla "
                    + columnas + "x" + filas);
            }
            celdas.add(new PadPayloads.Celda(icono, titulo, descripcion,
                                             bloqueada, col, fila));
            acciones.add(accion);
            return this;
        }

        /** Tarjetas grandes con texto, en vez de celdas pequeñas. */
        public Pantalla tarjetas() {
            this.estilo = PadPayloads.TARJETAS;
            return this;
        }

        /** Cómo se vuelve a ESTA pantalla desde una hija. */
        public Pantalla reabrirCon(Runnable r) {
            this.reabrir = r;
            return this;
        }

        public Pantalla pie(String linea) {
            pie.add(linea);
            return this;
        }

        /** Panel verde de la izquierda. Caben ~9 caracteres por linea. */
        public Pantalla izquierda(String linea) {
            izquierda.add(linea);
            return this;
        }

        /** Panel morado de la derecha. Mismo ancho que el verde. */
        public Pantalla derecha(String linea) {
            derecha.add(linea);
            return this;
        }

        /** Abre como pantalla raíz: borra el historial. */
        public void abrir(ServerPlayerEntity jugador) {
            HISTORIAL.remove(jugador.getUuid());
            enviar(jugador, false);
        }

        /** Abre como hija: se podrá volver a {@code desde}. */
        public void abrirDesde(ServerPlayerEntity jugador, Runnable desde) {
            HISTORIAL.computeIfAbsent(jugador.getUuid(),
                                      k -> new java.util.ArrayDeque<>()).push(desde);
            enviar(jugador, true);
        }

        private void enviar(ServerPlayerEntity jugador, boolean atras) {
            ABIERTAS.put(jugador.getUuid(), new Abierta(id, List.copyOf(acciones)));
            ServerPlayNetworking.send(jugador, new PadPayloads.Abrir(
                id, titulo, estilo, atras, columnas, filas,
                List.copyOf(celdas), List.copyOf(pie),
                List.copyOf(izquierda), List.copyOf(derecha)));
        }
    }

    // ------------------------------------------------------------ entrada

    private static void pulsar(ServerPlayerEntity jugador,
                               PadPayloads.Pulsar p) {
        Abierta abierta = ABIERTAS.get(jugador.getUuid());

        // 1. ¿Tiene alguna pantalla abierta, y es esta?
        if (abierta == null || !abierta.pantalla().equals(p.pantalla())) {
            LunaEternal.LOG.warn("{} pulsó en '{}' sin tenerla abierta",
                jugador.getGameProfile().getName(), p.pantalla());
            return;
        }

        // Navegación. Va antes de validar el índice porque estos dos son
        // reservados y no corresponden a ninguna celda.
        if (p.indice() == PadPayloads.ATRAS) {
            var pila = HISTORIAL.get(jugador.getUuid());
            if (pila != null && !pila.isEmpty()) pila.pop().run();
            else inicio.accept(jugador);
            return;
        }
        if (p.indice() == PadPayloads.INICIO) {
            HISTORIAL.remove(jugador.getUuid());
            inicio.accept(jugador);
            return;
        }

        // 2. ¿Existe ese índice en la pantalla que YO envié?
        if (p.indice() < 0 || p.indice() >= abierta.acciones().size()) {
            LunaEternal.LOG.warn("{} pulsó el índice {} en '{}', que tiene {}",
                jugador.getGameProfile().getName(), p.indice(), p.pantalla(),
                abierta.acciones().size());
            return;
        }

        abierta.acciones().get(p.indice()).ejecutar(jugador, p.derecho());
    }

    /** Al desconectar, se olvida: si no, el mapa crecería sin límite. */
    public static void olvidar(ServerPlayerEntity jugador) {
        ABIERTAS.remove(jugador.getUuid());
        HISTORIAL.remove(jugador.getUuid());
    }

    /** Para el autotest. */
    public static int abiertas() {
        return ABIERTAS.size();
    }
}
