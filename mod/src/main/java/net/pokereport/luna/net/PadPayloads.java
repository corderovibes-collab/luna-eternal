package net.pokereport.luna.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Protocolo del Pad — la interfaz propia (D-025).
 *
 * <p><b>El servidor manda la pantalla entera; el cliente solo dibuja.</b> Esto
 * no es un detalle de implementación, es P6: el cliente nunca decide qué
 * puedes pulsar ni qué cuesta algo. Recibe una lista de celdas ya resuelta y
 * devuelve «he pulsado la número 7». Un cliente modificado no puede inventarse
 * una celda que el servidor no le mandó, porque el servidor valida el índice
 * contra la pantalla que él mismo envió.
 *
 * <p>Es además la razón de que el protocolo sea genérico: no hay un paquete
 * por pantalla. Añadir una pantalla nueva es escribir código de servidor y
 * nada más — el cliente no se recompila ni se redistribuye.
 */
public final class PadPayloads {

    private PadPayloads() {}

    public static final Identifier ABRIR  = Identifier.of("lunaeternal", "pad_abrir");
    public static final Identifier PULSAR = Identifier.of("lunaeternal", "pad_pulsar");
    public static final Identifier CERRAR = Identifier.of("lunaeternal", "pad_cerrar");

    /** Estilos de pantalla. Ver PadScreen. */
    public static final String REJILLA = "rejilla";   // celdas pequeñas
    public static final String TARJETAS = "tarjetas"; // tarjetas grandes

    /** Índices reservados que el cliente puede enviar sin ser una celda. */
    public static final int ATRAS = -1;
    public static final int INICIO = -2;

    /** Una celda del Pad: icono, texto y estado. */
    public record Celda(String icono, String titulo, List<String> descripcion,
                        boolean bloqueada, int columna, int fila) {

        public static final PacketCodec<RegistryByteBuf, Celda> CODEC =
            new PacketCodec<>() {
                @Override
                public Celda decode(RegistryByteBuf b) {
                    String icono = b.readString(64);
                    String titulo = b.readString(256);
                    int n = b.readVarInt();
                    List<String> desc = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) desc.add(b.readString(256));
                    return new Celda(icono, titulo, desc, b.readBoolean(),
                                     b.readVarInt(), b.readVarInt());
                }

                @Override
                public void encode(RegistryByteBuf b, Celda c) {
                    b.writeString(c.icono, 64);
                    b.writeString(c.titulo, 256);
                    b.writeVarInt(c.descripcion.size());
                    for (String s : c.descripcion) b.writeString(s, 256);
                    b.writeBoolean(c.bloqueada);
                    b.writeVarInt(c.columna);
                    b.writeVarInt(c.fila);
                }
            };
    }

    /** Servidor → cliente: abre esta pantalla. */
    /**
     * @param izquierda líneas del panel verde · @param derecha las del morado
     *
     * <p>Son los dos paneles laterales del arte. Cada linea se recorta al
     * ancho del panel en el cliente: ahi caben unos 47-62 px, o sea 8-10
     * caracteres. Lo que se mande mas largo se corta, no se desborda.
     */
    public record Abrir(String pantalla, String titulo, String estilo,
                        boolean hayAtras,
                        int columnas, int filas,
                        List<Celda> celdas, List<String> pie,
                        List<String> izquierda, List<String> derecha)
            implements CustomPayload {

        public static final CustomPayload.Id<Abrir> ID = new CustomPayload.Id<>(ABRIR);

        public static final PacketCodec<RegistryByteBuf, Abrir> CODEC =
            new PacketCodec<>() {
                @Override
                public Abrir decode(RegistryByteBuf b) {
                    String pantalla = b.readString(64);
                    String titulo = b.readString(256);
                    String estilo = b.readString(32);
                    boolean atras = b.readBoolean();
                    int cols = b.readVarInt(), fils = b.readVarInt();
                    int n = b.readVarInt();
                    List<Celda> celdas = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) celdas.add(Celda.CODEC.decode(b));
                    return new Abrir(pantalla, titulo, estilo, atras, cols,
                                     fils, celdas, lista(b), lista(b), lista(b));
                }

                @Override
                public void encode(RegistryByteBuf b, Abrir a) {
                    b.writeString(a.pantalla, 64);
                    b.writeString(a.titulo, 256);
                    b.writeString(a.estilo, 32);
                    b.writeBoolean(a.hayAtras);
                    b.writeVarInt(a.columnas);
                    b.writeVarInt(a.filas);
                    b.writeVarInt(a.celdas.size());
                    for (Celda c : a.celdas) Celda.CODEC.encode(b, c);
                    escribir(b, a.pie);
                    escribir(b, a.izquierda);
                    escribir(b, a.derecha);
                }
            };

        private static List<String> lista(RegistryByteBuf b) {
            int n = b.readVarInt();
            List<String> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(b.readString(256));
            return out;
        }

        private static void escribir(RegistryByteBuf b, List<String> l) {
            b.writeVarInt(l.size());
            for (String s : l) b.writeString(s, 256);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Cliente → servidor: he pulsado esta celda.
     *
     * <p>Solo viaja el índice. El servidor no se fía de él: comprueba que la
     * pantalla siga abierta y que el índice exista en la que <b>él</b> envió.
     */
    public record Pulsar(String pantalla, int indice, boolean derecho)
            implements CustomPayload {

        public static final CustomPayload.Id<Pulsar> ID = new CustomPayload.Id<>(PULSAR);

        public static final PacketCodec<RegistryByteBuf, Pulsar> CODEC =
            PacketCodec.tuple(
                PacketCodecs.STRING, Pulsar::pantalla,
                PacketCodecs.VAR_INT, Pulsar::indice,
                PacketCodecs.BOOL, Pulsar::derecho,
                Pulsar::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente → servidor: he cerrado la pantalla. */
    public record Cerrar(String pantalla) implements CustomPayload {

        public static final CustomPayload.Id<Cerrar> ID = new CustomPayload.Id<>(CERRAR);

        public static final PacketCodec<RegistryByteBuf, Cerrar> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, Cerrar::pantalla, Cerrar::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
}
