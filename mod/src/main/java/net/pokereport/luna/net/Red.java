package net.pokereport.luna.net;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.progression.Path;

/**
 * Lo que viaja entre el servidor y el PokePad.
 *
 * <p><b>Esta clase es el entrypoint {@code main} a propósito, y no es un
 * detalle.</b> Los tipos de paquete tienen que registrarse en los <i>dos</i>
 * lados, y {@code main} es el único que corre en ambos. Registrarlos solo en el
 * de servidor hacía que el cliente reventara al arrancar — ya pasó, y está
 * anotado en {@code docs/ui/interfaz-cliente.md} §5.
 *
 * <p><b>El saldo se pide, no se empuja.</b> El cliente lo solicita al abrir el
 * Pad y el servidor contesta. Así siempre se ve un número fresco sin tener que
 * avisar al cliente cada vez que la economía se mueve —que son muchos sitios y
 * es fácil olvidarse de uno—, y un Pad cerrado no cuesta nada.
 *
 * <p>P6 sigue intacto: esto es <b>solo para mostrar</b>. El cliente no decide
 * nada con estos números; cualquier operación real se valida en el servidor.
 */
public class Red implements ModInitializer {

    /** El cliente pide su saldo. Sin datos: el servidor ya sabe quién pregunta. */
    public record PedirSaldo() implements CustomPayload {
        public static final Id<PedirSaldo> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_saldo"));
        public static final PacketCodec<RegistryByteBuf, PedirSaldo> CODEC =
                PacketCodec.unit(new PedirSaldo());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** La respuesta: las tres monedas. */
    public record Saldo(long pokedolares, long marcas, long reportcoins)
            implements CustomPayload {
        public static final Id<Saldo> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "saldo"));
        public static final PacketCodec<RegistryByteBuf, Saldo> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, Saldo::pokedolares,
                        PacketCodecs.VAR_LONG, Saldo::marcas,
                        PacketCodecs.VAR_LONG, Saldo::reportcoins,
                        Saldo::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Los datos de sesión que el PokePad enseña bajo la cara del jugador.
     *
     * <p><b>Las Vías van como lista y no como cinco campos con nombre</b>, en el
     * orden de {@code Path.values()}. Añadir una Vía sexta sería entonces
     * cambiar el enum y ya; con cinco campos fijos habría que tocar el paquete,
     * el códec, la caché del cliente y el dibujado, y bastaría olvidarse de uno
     * para que la nueva no apareciera sin que nada fallara.
     *
     * <p><b>Clan, trabajo y división viajan aunque todavía no existan.</b> Hoy
     * el servidor manda cadena vacía en los tres y el Pad dibuja un guión. Es
     * deliberado: cuando esos sistemas se construyan, encenderlos es rellenar
     * estas tres líneas, y no volver a tocar el protocolo, el códec, la caché y
     * el dibujado — que es donde se pierde una tarde y se olvida un sitio.
     *
     * <p><b>Las medallas van como MÁSCARA DE BITS, no como lista.</b> Son
     * dieciséis —ocho de Kanto y ocho de Johto (D-017)— y lo único que hay que
     * saber de cada una es si se tiene. Un {@code int} lo dice entero, se
     * compara de un vistazo y no puede llegar a medias: una lista de dieciséis
     * booleanos ocuparía diecisiete bytes para decir lo mismo y permitiría que
     * llegara con quince.
     */
    public record Ficha(List<Integer> vias, String clan, String trabajo,
                        String division, int medallas) implements CustomPayload {
        public static final Id<Ficha> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "ficha"));
        public static final PacketCodec<RegistryByteBuf, Ficha> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT.collect(PacketCodecs.toList()),
                        Ficha::vias,
                        PacketCodecs.STRING, Ficha::clan,
                        PacketCodecs.STRING, Ficha::trabajo,
                        PacketCodecs.STRING, Ficha::division,
                        PacketCodecs.VAR_INT, Ficha::medallas,
                        Ficha::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * La voz de la Pokédex: «reproduce la descripción de esta especie».
     *
     * <p>Viaja <b>solo al jugador que ha escaneado</b>, que es todo el diseño:
     * el evento de Cobblemon trae su {@code ServerPlayer}, así que enviarlo por
     * su conexión hace que nadie más lo oiga. Si dos personas escanean a la vez,
     * cada una recibe el suyo y ninguna oye el de la otra.
     *
     * <p>Se manda el <b>nombre de la especie</b> y no una ruta de sonido: el
     * cliente compone el identificador. Así el servidor no sabe nada de cómo se
     * llaman los ficheros, y añadir voces nuevas no cambia el protocolo.
     */
    public record VozPokedex(String especie) implements CustomPayload {
        public static final Id<VozPokedex> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "voz_pokedex"));
        public static final PacketCodec<RegistryByteBuf, VozPokedex> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, VozPokedex::especie,
                        VozPokedex::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame el catálogo de cosméticos». Se pide al abrir la tienda. */
    public record PedirCosmeticos() implements CustomPayload {
        public static final Id<PedirCosmeticos> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_cosmeticos"));
        public static final PacketCodec<RegistryByteBuf, PedirCosmeticos> CODEC =
                PacketCodec.unit(new PedirCosmeticos());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una pieza del catálogo, tal y como la dibuja el Pad.
     *
     * <p>⚠ {@code banderas} es un mapa de bits y no dos booleanos, por el mismo
     * motivo que {@code Ficha.medallas}: el codec de tupla admite seis campos, y
     * gastar dos en dos booleanos obligaría a partir el paquete. Bit 0 =
     * poseído, bit 1 = equipado.
     *
     * <p>⚠ <b>Viaja el precio, no si te lo puedes permitir.</b> Comparar contra
     * el saldo es cosa del servidor al comprar: si el cliente decidiera qué
     * puede pagar, bastaría con mentirle.
     */
    public record PiezaCosmetica(String id, String categoria, String especie,
                                 String aspecto, int precio, int banderas) {
        public static final PacketCodec<RegistryByteBuf, PiezaCosmetica> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, PiezaCosmetica::id,
                        PacketCodecs.STRING, PiezaCosmetica::categoria,
                        PacketCodecs.STRING, PiezaCosmetica::especie,
                        PacketCodecs.STRING, PiezaCosmetica::aspecto,
                        PacketCodecs.VAR_INT, PiezaCosmetica::precio,
                        PacketCodecs.VAR_INT, PiezaCosmetica::banderas,
                        PiezaCosmetica::new);

        public static final int POSEIDO = 1;
        public static final int EQUIPADO = 2;
        /**
         * Tienes en el EQUIPO el Pokemon al que se le pone.
         *
         * <p>⚠ Lo decide el SERVIDOR y viaja como bandera, en vez de que el
         * cliente mire su propio equipo. Se intento al reves y fallo: el cliente
         * comparaba por `Species.getName()`, que es el nombre PARA MOSTRAR y se
         * traduce -- asi que el boton decia "no lo tienes" con el Pokemon
         * delante. Ademas P6 ya lo decia: el cliente dibuja, no decide.
         */
        public static final int EQUIPABLE = 4;
    }

    /**
     * El catálogo entero, con lo que posees y lo que llevas puesto.
     *
     * <p>Va <b>completo</b> y no por diferencias: son unas decenas de entradas y
     * se manda al abrir la tienda y después de cada compra. Mandar cambios
     * obligaría a que cliente y servidor coincidieran en el estado anterior, y
     * un solo paquete perdido dejaría la tienda mintiendo hasta reiniciar.
     */
    public record Cosmeticos(List<PiezaCosmetica> piezas, long lunacoins)
            implements CustomPayload {
        public static final Id<Cosmeticos> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "cosmeticos"));
        public static final PacketCodec<RegistryByteBuf, Cosmeticos> CODEC =
                PacketCodec.tuple(
                        PiezaCosmetica.CODEC.collect(PacketCodecs.toList()),
                        Cosmeticos::piezas,
                        PacketCodecs.VAR_LONG, Cosmeticos::lunacoins,
                        Cosmeticos::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Compra esto» o «ponle el disfraz a este Pokemon».
     *
     * <p>⚠ Viaja el <b>identificador</b> del cosmetico y la <b>ranura</b> del
     * equipo. Ni el precio, ni la categoria, ni si se puede pagar: todo eso lo
     * tiene el servidor, y aceptar cualquiera de esos datos del cliente seria
     * aceptar el precio que el diga.
     *
     * <p>⚠⚠ Y VIAJA LA RANURA, NO EL UUID DEL POKEMON. Con el UUID, un cliente
     * modificado podria mandar el de un Pokemon que no es suyo -- el de otro
     * jugador, uno visto en un combate-- y habria que comprobar la propiedad a
     * mano. La ranura se resuelve SIEMPRE contra el equipo de quien manda el
     * paquete, asi que "de otro" no es un estado que se pueda expresar.
     *
     * @param ranura {@code < 0} = comprar. {@code 0..5} = ponerselo al Pokemon
     *               de esa ranura del equipo
     */
    public record AccionCosmetico(String id, int ranura) implements CustomPayload {
        public static final Id<AccionCosmetico> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_cosmetico"));
        public static final PacketCodec<RegistryByteBuf, AccionCosmetico> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AccionCosmetico::id,
                        PacketCodecs.INTEGER, AccionCosmetico::ranura,
                        AccionCosmetico::new);

        /** Comprar. Sin ranura porque no se le pone a nadie. */
        public static final int COMPRAR = -1;
        /** Equipar y que el SERVIDOR elija la ranura: el cliente no lee su equipo. */
        public static final int AUTOMATICA = -2;
        /** Quitarselo al Pokemon que lo lleve puesto. Tampoco necesita ranura. */
        public static final int QUITAR = -3;

        /**
         * ⚠⚠ ESTO ERA `ranura < 0`, Y SE TRAGABA LAS DOS.
         *
         * Con -1 = comprar y -2 = equipar, "menor que cero" es verdad para
         * ambas: pulsar EQUIPAR mandaba -2, el servidor lo tomaba por una compra
         * y contestaba "ya tienes ese cosmetico". El mensaje era correcto para
         * lo que el servidor creia estar haciendo, y por eso despistaba: no
         * sonaba a fallo de codigo, sonaba a que la tienda te llevaba la
         * contraria.
         *
         * Dos centinelas negativos con una comprobacion de signo es un error
         * esperando: por eso ahora son constantes con nombre y la comparacion es
         * exacta.
         */
        public boolean esCompra() {
            return ranura == COMPRAR;
        }

        public boolean esQuitar() {
            return ranura == QUITAR;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    /**
     * Quién lleva qué aura. <b>Del servidor a TODOS los clientes.</b>
     *
     * <p>⚠⚠ ESTE ES EL PAQUETE QUE CONVIERTE UN COSMÉTICO EN UN PRODUCTO.
     * {@code monetization.md} lo avisa: «un cosmético sin nadie que lo vea no
     * vale nada». Los disfraces de Pokémon se ven solos porque el aspecto viaja
     * dentro del Pokémon; un aura <b>no viaja con nada</b>, así que si esto no
     * existe, el aura solo la ve su dueño en la tienda — que es exactamente
     * media función, y ya pasó una vez.
     *
     * <p>Viaja el <b>UUID</b> y no el nombre: un jugador puede cambiarse el
     * nombre y el UUID es lo que el cliente usa para encontrar la entidad.
     *
     * <p>Cadena vacía = <b>se lo ha quitado</b>. Hace falta un valor para eso: si
     * quitarse el aura se difundiera «no mandando nada», los demás seguirían
     * viéndosela hasta reconectar.
     */
    public record LlevaPuesto(java.util.UUID jugador, String categoria, String cosmetico)
            implements CustomPayload {
        public static final Id<LlevaPuesto> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "lleva_puesto"));
        public static final PacketCodec<RegistryByteBuf, LlevaPuesto> CODEC =
                PacketCodec.tuple(
                        // ⚠ Uuids.PACKET_CODEC, NO PacketCodecs.UUID: ese no
                        //   existe en 1.21.1 y el error de compilación no dice
                        //   cuál es el bueno.
                        net.minecraft.util.Uuids.PACKET_CODEC, LlevaPuesto::jugador,
                        PacketCodecs.STRING, LlevaPuesto::categoria,
                        PacketCodecs.STRING, LlevaPuesto::cosmetico,
                        LlevaPuesto::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Un LOGRO: sube a la esquina como un toast, y suena.
     *
     * <p>⚠ Viaja el TEXTO YA COMPUESTO y no las piezas. Podría mandarse
     * «MINERO, nivel 3, 4000 de Plata» y dejar que el cliente lo redacte, pero
     * entonces el formato viviría en dos sitios: el chat lo compone el servidor y
     * el toast el cliente, y el día que cambie una frase cambiaría en uno solo.
     *
     * <p>⚠ Y el <b>objeto</b> es un identificador, no una textura: el cliente
     * dibuja el objeto de verdad con su modelo. Así un oficio nuevo no necesita
     * arte, y el icono es el mismo que sale en la pantalla de Trabajos.
     */
    public record AvisoLogro(String titulo, String detalle, String objeto)
            implements CustomPayload {
        public static final Id<AvisoLogro> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "aviso_logro"));
        public static final PacketCodec<RegistryByteBuf, AvisoLogro> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AvisoLogro::titulo,
                        PacketCodecs.STRING, AvisoLogro::detalle,
                        PacketCodecs.STRING, AvisoLogro::objeto,
                        AvisoLogro::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame el mercado». `item` vacio = solo la lista de objetos. */
    public record PedirMercado(String item) implements CustomPayload {
        public static final Id<PedirMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_mercado"));
        public static final PacketCodec<RegistryByteBuf, PedirMercado> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, PedirMercado::item,
                        PedirMercado::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una fila del libro: un precio y cuanto hay a ese precio.
     *
     * <p>⚠ Va AGREGADO por precio y NO orden a orden. Es lo que se dibuja, y
     * ademas no dice de quien es cada orden -- que en un mercado es informacion
     * que no se da: saber que la unica venta barata es de fulano invita a
     * negociar por fuera y a acosarle.
     */
    public record NivelMercado(long precio, int unidades, int ordenes) {
        public static final PacketCodec<RegistryByteBuf, NivelMercado> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, NivelMercado::precio,
                        PacketCodecs.VAR_INT, NivelMercado::unidades,
                        PacketCodecs.VAR_INT, NivelMercado::ordenes,
                        NivelMercado::new);
    }

    /** Una orden mia. Aqui SI va el identificador: hace falta para cancelar. */
    public record OrdenMercado(long id, String lado, String item, long precio,
                               int total, int lleno) {
        public static final PacketCodec<RegistryByteBuf, OrdenMercado> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, OrdenMercado::id,
                        PacketCodecs.STRING, OrdenMercado::lado,
                        PacketCodecs.STRING, OrdenMercado::item,
                        PacketCodecs.VAR_LONG, OrdenMercado::precio,
                        PacketCodecs.VAR_INT, OrdenMercado::total,
                        PacketCodecs.VAR_INT, OrdenMercado::lleno,
                        OrdenMercado::new);
    }

    /** Una operacion ejecutada, para el historico. */
    public record TratoMercado(long precio, int qty, long cuando) {
        public static final PacketCodec<RegistryByteBuf, TratoMercado> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, TratoMercado::precio,
                        PacketCodecs.VAR_INT, TratoMercado::qty,
                        PacketCodecs.VAR_LONG, TratoMercado::cuando,
                        TratoMercado::new);
    }

    /**
     * Todo lo que dibuja la pantalla del mercado.
     *
     * <p>⚠ `catalogo` son los objetos que se pueden mirar: los que tienen
     * ordenes vivas MAS los que el jugador lleva encima. Sin lo segundo, un
     * mercado vacio no tendria NADA en que pulsar -- y el primero que quisiera
     * vender algo no encontraria como.
     */
    public record EstadoMercado(String item, List<String> catalogo,
                                List<NivelMercado> compras,
                                List<NivelMercado> ventas,
                                List<OrdenMercado> mias,
                                List<TratoMercado> historial,
                                long ultimoPrecio, int tengo, long saldo)
            implements CustomPayload {
        public static final Id<EstadoMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_mercado"));
        public static final PacketCodec<RegistryByteBuf, EstadoMercado> CODEC =
                PacketCodec.ofStatic(EstadoMercado::escribir, EstadoMercado::leer);

        private static void escribir(RegistryByteBuf buf, EstadoMercado e) {
            buf.writeString(e.item);
            buf.writeVarInt(e.catalogo.size());
            for (String s : e.catalogo) {
                buf.writeString(s);
            }
            escribirNiveles(buf, e.compras);
            escribirNiveles(buf, e.ventas);
            buf.writeVarInt(e.mias.size());
            for (OrdenMercado o : e.mias) {
                OrdenMercado.CODEC.encode(buf, o);
            }
            buf.writeVarInt(e.historial.size());
            for (TratoMercado x : e.historial) {
                TratoMercado.CODEC.encode(buf, x);
            }
            buf.writeVarLong(e.ultimoPrecio);
            buf.writeVarInt(e.tengo);
            buf.writeVarLong(e.saldo);
        }

        private static void escribirNiveles(RegistryByteBuf buf,
                                            List<NivelMercado> ns) {
            buf.writeVarInt(ns.size());
            for (NivelMercado n : ns) {
                NivelMercado.CODEC.encode(buf, n);
            }
        }

        private static List<NivelMercado> leerNiveles(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<NivelMercado> salida = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                salida.add(NivelMercado.CODEC.decode(buf));
            }
            return List.copyOf(salida);
        }

        private static EstadoMercado leer(RegistryByteBuf buf) {
            String item = buf.readString();
            int nc = buf.readVarInt();
            List<String> catalogo = new ArrayList<>(nc);
            for (int i = 0; i < nc; i++) {
                catalogo.add(buf.readString());
            }
            var compras = leerNiveles(buf);
            var ventas = leerNiveles(buf);
            int nm = buf.readVarInt();
            List<OrdenMercado> mias = new ArrayList<>(nm);
            for (int i = 0; i < nm; i++) {
                mias.add(OrdenMercado.CODEC.decode(buf));
            }
            int nh = buf.readVarInt();
            List<TratoMercado> hist = new ArrayList<>(nh);
            for (int i = 0; i < nh; i++) {
                hist.add(TratoMercado.CODEC.decode(buf));
            }
            return new EstadoMercado(item, List.copyOf(catalogo), compras, ventas,
                    List.copyOf(mias), List.copyOf(hist),
                    buf.readVarLong(), buf.readVarInt(), buf.readVarLong());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Poner o cancelar una orden.
     *
     * <p>⚠ EL PRECIO SI VIAJA AQUI, al contrario que en la tienda -- y es
     * correcto: en la tienda el precio lo pone el servidor porque es SU
     * catalogo; aqui lo pone el jugador, porque de eso va un mercado. Lo que el
     * servidor no acepta es que ese precio se salte los topes ni que la orden
     * se cree sin la contrapartida retenida.
     */
    public record AccionMercado(String accion, String item, long precio,
                                int cantidad, long orden) implements CustomPayload {
        public static final Id<AccionMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_mercado"));
        public static final PacketCodec<RegistryByteBuf, AccionMercado> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AccionMercado::accion,
                        PacketCodecs.STRING, AccionMercado::item,
                        PacketCodecs.VAR_LONG, AccionMercado::precio,
                        PacketCodecs.VAR_INT, AccionMercado::cantidad,
                        PacketCodecs.VAR_LONG, AccionMercado::orden,
                        AccionMercado::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame el catalogo», al abrir la tienda. */
    public record PedirTienda() implements CustomPayload {
        public static final Id<PedirTienda> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_tienda"));
        public static final PacketCodec<RegistryByteBuf, PedirTienda> CODEC =
                PacketCodec.unit(new PedirTienda());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Un articulo.
     *
     * <p>⚠ Viaja el IDENTIFICADOR del objeto, no un indice. Un indice ata al
     * cliente al orden exacto del JSON: cambiar el catalogo mientras alguien
     * tiene la tienda abierta le haria comprar el articulo de al lado.
     *
     * <p>⚠ Y NO viaja «cuantos tengo»: eso lo cuenta el cliente de su propio
     * inventario, que ya esta sincronizado. Mandarlo obligaria a reenviar el
     * catalogo entero cada vez que el jugador recoge algo del suelo.
     */
    public record EntradaTienda(String item, String etiqueta, long compra,
                                long venta, String moneda) {
        public static final PacketCodec<RegistryByteBuf, EntradaTienda> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, EntradaTienda::item,
                        PacketCodecs.STRING, EntradaTienda::etiqueta,
                        PacketCodecs.VAR_LONG, EntradaTienda::compra,
                        PacketCodecs.VAR_LONG, EntradaTienda::venta,
                        PacketCodecs.STRING, EntradaTienda::moneda,
                        EntradaTienda::new);
    }

    public record CategoriaTienda(String id, String nombre, String icono,
                                  String descripcion, List<EntradaTienda> entradas) {
        public static final PacketCodec<RegistryByteBuf, CategoriaTienda> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, CategoriaTienda::id,
                        PacketCodecs.STRING, CategoriaTienda::nombre,
                        PacketCodecs.STRING, CategoriaTienda::icono,
                        PacketCodecs.STRING, CategoriaTienda::descripcion,
                        EntradaTienda.CODEC.collect(PacketCodecs.toList()),
                        CategoriaTienda::entradas,
                        CategoriaTienda::new);
    }

    /**
     * El catalogo entero, con sus categorias.
     *
     * <p>⚠ Se manda COMPLETO y una sola vez al abrir. Son 28 articulos en 5
     * categorias: cabe de sobra en un paquete, y a cambio cambiar de categoria
     * es instantaneo en vez de una ida y vuelta por pestaña.
     */
    public record Tienda(List<CategoriaTienda> categorias) implements CustomPayload {
        public static final Id<Tienda> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "tienda"));
        public static final PacketCodec<RegistryByteBuf, Tienda> CODEC =
                PacketCodec.tuple(
                        CategoriaTienda.CODEC.collect(PacketCodecs.toList()),
                        Tienda::categorias,
                        Tienda::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Comprar o vender.
     *
     * <p>⚠ El precio NO viaja. Lo pone el servidor mirando SU catalogo (P6): si
     * viniera del cliente, un cliente modificado compraria Revivir por 1.
     *
     * <p>⚠ La categoria viaja ademas del objeto porque el mismo objeto podria
     * estar en dos categorias con precios distintos algun dia. Hoy no pasa, y
     * mandarla cuesta veinte bytes.
     */
    public record AccionTienda(String categoria, String item, int cantidad,
                               boolean comprar) implements CustomPayload {
        public static final Id<AccionTienda> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_tienda"));
        public static final PacketCodec<RegistryByteBuf, AccionTienda> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AccionTienda::categoria,
                        PacketCodecs.STRING, AccionTienda::item,
                        PacketCodecs.VAR_INT, AccionTienda::cantidad,
                        PacketCodecs.BOOL, AccionTienda::comprar,
                        AccionTienda::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    /** «Dame el estado de mi clan», al abrir la pantalla. */
    public record PedirClan() implements CustomPayload {
        public static final Id<PedirClan> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_clan"));
        public static final PacketCodec<RegistryByteBuf, PedirClan> CODEC =
                PacketCodec.unit(new PedirClan());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una acción sobre el clan. <b>Un solo paquete para todas.</b>
     *
     * <p>⚠ Podrían ser doce paquetes —fundar, invitar, aceptar, rechazar, salir,
     * echar, ascender, degradar, traspasar, disolver, aportar, sacar— y sería
     * peor: doce registros, doce receptores y doce sitios donde olvidarse de
     * comprobar los permisos. Con uno, <b>la comprobación vive en un solo
     * sitio</b>, que es donde tiene que estar en un sistema social.
     *
     * <p>Los campos sobran en casi todas las acciones y no pasa nada: una cadena
     * vacía y un cero ocupan un byte cada uno. Lo que <b>no</b> se hace es mandar
     * un mapa o un JSON: entonces el formato dejaría de estar declarado y el
     * servidor tendría que fiarse de las claves que le manden.
     */
    public record AccionClan(String accion, String texto, String texto2,
                             long objetivo, long cantidad) implements CustomPayload {
        public static final Id<AccionClan> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_clan"));
        public static final PacketCodec<RegistryByteBuf, AccionClan> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AccionClan::accion,
                        PacketCodecs.STRING, AccionClan::texto,
                        PacketCodecs.STRING, AccionClan::texto2,
                        PacketCodecs.VAR_LONG, AccionClan::objetivo,
                        PacketCodecs.VAR_LONG, AccionClan::cantidad,
                        AccionClan::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MiembroClan(long playerId, String nombre, String rol, boolean conectado) {
        public static final PacketCodec<RegistryByteBuf, MiembroClan> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, MiembroClan::playerId,
                        PacketCodecs.STRING, MiembroClan::nombre,
                        PacketCodecs.STRING, MiembroClan::rol,
                        PacketCodecs.BOOL, MiembroClan::conectado,
                        MiembroClan::new);
    }

    public record ClanResumen(long id, String nombre, String etiqueta, String color,
                              String descripcion, long tesoro, int miembros,
                              String lider) {
        // ⚠ A MANO. `PacketCodec.tuple` llega a SEIS campos en 1.21.1 y aquí
        //   hay ocho; el error que da no dice cuál es el límite, solo que «no
        //   hay método adecuado». Escribirlo así además quita el techo.
        public static final PacketCodec<RegistryByteBuf, ClanResumen> CODEC =
                PacketCodec.ofStatic(ClanResumen::escribir, ClanResumen::leer);

        private static void escribir(RegistryByteBuf buf, ClanResumen c) {
            buf.writeVarLong(c.id);
            buf.writeString(c.nombre);
            buf.writeString(c.etiqueta);
            buf.writeString(c.color);
            buf.writeString(c.descripcion);
            buf.writeVarLong(c.tesoro);
            buf.writeVarInt(c.miembros);
            buf.writeString(c.lider);
        }

        private static ClanResumen leer(RegistryByteBuf buf) {
            return new ClanResumen(buf.readVarLong(), buf.readString(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readVarLong(),
                    buf.readVarInt(), buf.readString());
        }
    }

    public record InvitacionClan(long clanId, String nombre, String etiqueta,
                                 String color, String invitadoPor) {
        public static final PacketCodec<RegistryByteBuf, InvitacionClan> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_LONG, InvitacionClan::clanId,
                        PacketCodecs.STRING, InvitacionClan::nombre,
                        PacketCodecs.STRING, InvitacionClan::etiqueta,
                        PacketCodecs.STRING, InvitacionClan::color,
                        PacketCodecs.STRING, InvitacionClan::invitadoPor,
                        InvitacionClan::new);
    }

    /** Un movimiento del tesoro. `delta` con signo: + entra, - sale. */
    public record MovimientoClan(String quien, long delta, long saldoDespues,
                                 String motivo, long cuando) {
        public static final PacketCodec<RegistryByteBuf, MovimientoClan> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, MovimientoClan::quien,
                        PacketCodecs.VAR_LONG, MovimientoClan::delta,
                        PacketCodecs.VAR_LONG, MovimientoClan::saldoDespues,
                        PacketCodecs.STRING, MovimientoClan::motivo,
                        PacketCodecs.VAR_LONG, MovimientoClan::cuando,
                        MovimientoClan::new);
    }

    /** Una linea del registro de acciones. */
    public record AnotacionClan(String quien, String aQuien, String accion,
                                String detalle, long cuando) {
        public static final PacketCodec<RegistryByteBuf, AnotacionClan> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AnotacionClan::quien,
                        PacketCodecs.STRING, AnotacionClan::aQuien,
                        PacketCodecs.STRING, AnotacionClan::accion,
                        PacketCodecs.STRING, AnotacionClan::detalle,
                        PacketCodecs.VAR_LONG, AnotacionClan::cuando,
                        AnotacionClan::new);
    }

    /**
     * Todo el estado del clan de un jugador.
     *
     * <p>⚠ Va <b>completo</b> y se reenvía entero tras cada acción, igual que el
     * catálogo de cosméticos y el árbol de misiones. Mandar diferencias en un
     * sistema donde varios jugadores cambian el mismo estado a la vez —dos
     * oficiales invitando, alguien saliendo— es cómo se acaba con dos clientes
     * enseñando listas distintas del mismo clan.
     *
     * <p>⚠ {@code miRol} lo decide el SERVIDOR. El cliente podría deducirlo de la
     * lista de miembros, y entonces la regla de quién puede echar a quién
     * viviría en dos sitios.
     */
    public record EstadoClan(long miClanId, ClanResumen mio, List<MiembroClan> miembros,
                             String miRol, List<InvitacionClan> invitaciones,
                             List<ClanResumen> otros, long costeFundar,
                             List<MovimientoClan> movimientos,
                             List<AnotacionClan> registro,
                             long topeOficial, long sacadoHoy)
            implements CustomPayload {
        public static final Id<EstadoClan> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_clan"));

        // ⚠ A MANO: `PacketCodec.tuple` no admite un campo opcional, y `mio` lo
        //   es. Se escribe un booleano delante y se lee igual.
        public static final PacketCodec<RegistryByteBuf, EstadoClan> CODEC =
                PacketCodec.ofStatic(EstadoClan::escribir, EstadoClan::leer);

        private static void escribir(RegistryByteBuf buf, EstadoClan e) {
            buf.writeVarLong(e.miClanId);
            buf.writeBoolean(e.mio != null);
            if (e.mio != null) {
                ClanResumen.CODEC.encode(buf, e.mio);
            }
            buf.writeVarInt(e.miembros.size());
            for (MiembroClan m : e.miembros) {
                MiembroClan.CODEC.encode(buf, m);
            }
            buf.writeString(e.miRol == null ? "" : e.miRol);
            buf.writeVarInt(e.invitaciones.size());
            for (InvitacionClan i : e.invitaciones) {
                InvitacionClan.CODEC.encode(buf, i);
            }
            buf.writeVarInt(e.otros.size());
            for (ClanResumen c : e.otros) {
                ClanResumen.CODEC.encode(buf, c);
            }
            buf.writeVarLong(e.costeFundar);
            buf.writeVarInt(e.movimientos.size());
            for (MovimientoClan m : e.movimientos) {
                MovimientoClan.CODEC.encode(buf, m);
            }
            buf.writeVarInt(e.registro.size());
            for (AnotacionClan a : e.registro) {
                AnotacionClan.CODEC.encode(buf, a);
            }
            buf.writeVarLong(e.topeOficial);
            buf.writeVarLong(e.sacadoHoy);
        }

        private static EstadoClan leer(RegistryByteBuf buf) {
            long id = buf.readVarLong();
            ClanResumen mio = buf.readBoolean() ? ClanResumen.CODEC.decode(buf) : null;
            int n = buf.readVarInt();
            List<MiembroClan> miembros = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                miembros.add(MiembroClan.CODEC.decode(buf));
            }
            String rol = buf.readString();
            int ni = buf.readVarInt();
            List<InvitacionClan> invs = new ArrayList<>(ni);
            for (int i = 0; i < ni; i++) {
                invs.add(InvitacionClan.CODEC.decode(buf));
            }
            int no = buf.readVarInt();
            List<ClanResumen> otros = new ArrayList<>(no);
            for (int i = 0; i < no; i++) {
                otros.add(ClanResumen.CODEC.decode(buf));
            }
            long coste = buf.readVarLong();
            int nm = buf.readVarInt();
            List<MovimientoClan> movs = new ArrayList<>(nm);
            for (int i = 0; i < nm; i++) {
                movs.add(MovimientoClan.CODEC.decode(buf));
            }
            int nr = buf.readVarInt();
            List<AnotacionClan> reg = new ArrayList<>(nr);
            for (int i = 0; i < nr; i++) {
                reg.add(AnotacionClan.CODEC.decode(buf));
            }
            return new EstadoClan(id, mio, List.copyOf(miembros), rol,
                    List.copyOf(invs), List.copyOf(otros), coste,
                    List.copyOf(movs), List.copyOf(reg),
                    buf.readVarLong(), buf.readVarLong());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «¿Tengo que elegir inicial?». Lo pregunta el cliente al entrar. */
    public record PedirInicial() implements CustomPayload {
        public static final Id<PedirInicial> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_inicial"));
        public static final PacketCodec<RegistryByteBuf, PedirInicial> CODEC =
                PacketCodec.unit(new PedirInicial());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Elijo este». */
    public record ElegirInicial(String especie) implements CustomPayload {
        public static final Id<ElegirInicial> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "elegir_inicial"));
        public static final PacketCodec<RegistryByteBuf, ElegirInicial> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, ElegirInicial::especie,
                        ElegirInicial::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record OpcionInicial(String especie, String nombre, String region,
                                String tipo, String consejo) {
        public static final PacketCodec<RegistryByteBuf, OpcionInicial> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, OpcionInicial::especie,
                        PacketCodecs.STRING, OpcionInicial::nombre,
                        PacketCodecs.STRING, OpcionInicial::region,
                        PacketCodecs.STRING, OpcionInicial::tipo,
                        PacketCodecs.STRING, OpcionInicial::consejo,
                        OpcionInicial::new);
    }

    /**
     * Los iniciales, y si ya se eligió.
     *
     * <p>⚠ {@code yaEligio} viaja aunque las opciones también, y no sobra: es lo
     * que decide si la pantalla <b>se abre sola</b> al entrar. Calcularlo en el
     * cliente («¿tengo algún Pokémon?») daría falsos positivos —alguien puede
     * soltar su equipo en el PC— y falsos negativos, y las dos formas de
     * equivocarse son malas: una le niega el inicial a quien no lo tiene, y la
     * otra abre la pantalla a quien ya eligió.
     *
     * <p>La verdad está en {@code kit_claim}, y es del servidor.
     */
    public record Iniciales(List<OpcionInicial> opciones, boolean yaEligio)
            implements CustomPayload {
        public static final Id<Iniciales> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "iniciales"));
        public static final PacketCodec<RegistryByteBuf, Iniciales> CODEC =
                PacketCodec.tuple(
                        OpcionInicial.CODEC.collect(PacketCodecs.toList()), Iniciales::opciones,
                        PacketCodecs.BOOL, Iniciales::yaEligio,
                        Iniciales::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Dime quién lleva qué». <b>Lo pide el CLIENTE al entrar.</b>
     *
     * <p>⚠⚠ EXISTE PORQUE EMPUJAR NO BASTABA, Y EL FALLO ERA EXACTAMENTE EL QUE
     * DESCRIBIO EL USUARIO: <i>«cuando me desconecto y me conecto yo no veo
     * nuevamente los cosméticos, pero los otros sí»</i>.
     *
     * <p>El servidor difundía al recibir {@code JOIN}, que es <b>su</b> idea de
     * «ya está dentro». Pero entre eso y que el cliente tenga mundo, entidades y
     * renderizadores listos hay una ventana, y un paquete que llega dentro de esa
     * ventana se pierde sin dar error. Los demás sí lo veían porque <i>ellos</i>
     * llevaban rato conectados: el paquete llegaba a un cliente ya despierto.
     *
     * <p>La solución no es empujar más tarde —¿cuánto más tarde?— sino <b>dejar
     * que pregunte quien sabe cuándo está listo</b>. Es el mismo trato que ya
     * tienen el saldo, el catálogo de cosméticos y las misiones: se piden.
     *
     * <p>El empujón del {@code JOIN} se mantiene, y no sobra: es lo que hace que
     * <b>los demás</b> vean al recién llegado. Las dos direcciones tienen dueños
     * distintos y por eso hacen falta las dos.
     */
    public record PedirLlevados() implements CustomPayload {
        public static final Id<PedirLlevados> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_llevados"));
        public static final PacketCodec<RegistryByteBuf, PedirLlevados> CODEC =
                PacketCodec.unit(new PedirLlevados());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame el estado de mi equipo», al abrir la pantalla de curar. */
    public record PedirCura() implements CustomPayload {
        public static final Id<PedirCura> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_cura"));
        public static final PacketCodec<RegistryByteBuf, PedirCura> CODEC =
                PacketCodec.unit(new PedirCura());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Un Pokémon del equipo, tal y como se dibuja en la pantalla de curar.
     *
     * <p>⚠ VIAJA LA VIDA, NO «ESTÁ HERIDO». Una barra de vida dice <i>cuánto</i>
     * te queda; un booleano solo dice que algo pasa. Y son los mismos bytes.
     *
     * <p>⚠ Y viaja el ESTADO como cadena («psn», «brn», …) y no como número:
     * los identificadores de estado los pone Cobblemon y pueden crecer. Un
     * número obligaría a mantener aquí una tabla que se queda vieja en silencio.
     * Cadena vacía = sano.
     */
    public record PokemonCura(String especie, String apodo, int nivel,
                              int vida, int vidaMax, String estado) {
        public static final PacketCodec<RegistryByteBuf, PokemonCura> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, PokemonCura::especie,
                        PacketCodecs.STRING, PokemonCura::apodo,
                        PacketCodecs.VAR_INT, PokemonCura::nivel,
                        PacketCodecs.VAR_INT, PokemonCura::vida,
                        PacketCodecs.VAR_INT, PokemonCura::vidaMax,
                        PacketCodecs.STRING, PokemonCura::estado,
                        PokemonCura::new);
    }

    /**
     * El equipo y cuánto falta para poder curar.
     *
     * <p>⚠ {@code segundos} lo calcula el SERVIDOR y viaja ya restado. El
     * cliente solo lo cuenta hacia atrás para dibujarlo: si mandáramos la marca
     * de tiempo de la última cura, cada reloj mal puesto daría una espera
     * distinta, y uno adelantado dejaría curar antes de tiempo en la pantalla —
     * el servidor lo rechazaría igual, pero el jugador vería un botón encendido
     * que no funciona, que es peor que uno apagado.
     *
     * <p>⚠ {@code haceFalta} también lo decide el servidor, y no sobra: sirve
     * para no gastar el cooldown curando a un equipo que está sano. El cliente
     * podría deducirlo de las barras, pero entonces la regla viviría en dos
     * sitios y un día dejarían de decir lo mismo.
     */
    public record EstadoCura(List<PokemonCura> equipo, long segundos,
                             boolean haceFalta) implements CustomPayload {
        public static final Id<EstadoCura> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_cura"));
        public static final PacketCodec<RegistryByteBuf, EstadoCura> CODEC =
                PacketCodec.tuple(
                        PokemonCura.CODEC.collect(PacketCodecs.toList()),
                        EstadoCura::equipo,
                        PacketCodecs.VAR_LONG, EstadoCura::segundos,
                        PacketCodecs.BOOL, EstadoCura::haceFalta,
                        EstadoCura::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Cura mi equipo».
     *
     * <p>⚠ NO LLEVA NADA DENTRO, y es lo correcto: qué se cura y si toca lo
     * decide el servidor entero (P6). Un paquete que dijera «cura esta ranura»
     * sería una superficie más que validar sin ganar nada — aquí se cura el
     * equipo o no se cura.
     */
    public record Curar() implements CustomPayload {
        public static final Id<Curar> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "curar"));
        public static final PacketCodec<RegistryByteBuf, Curar> CODEC =
                PacketCodec.unit(new Curar());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame mis misiones», al abrir la pantalla. */
    public record PedirMisiones() implements CustomPayload {
        public static final Id<PedirMisiones> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_misiones"));
        public static final PacketCodec<RegistryByteBuf, PedirMisiones> CODEC =
                PacketCodec.unit(new PedirMisiones());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Cobro la recompensa de esta». El servidor decide si puede. */
    public record ReclamarMision(String id) implements CustomPayload {
        public static final Id<ReclamarMision> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "reclamar_mision"));
        public static final PacketCodec<RegistryByteBuf, ReclamarMision> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, ReclamarMision::id,
                        ReclamarMision::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una misión con su estado.
     *
     * <p>⚠ Viaja {@code requires} porque <b>el árbol se dibuja con él</b>: es la
     * arista. Sin eso el cliente tendría una lista de nodos sueltos y no podría
     * saber qué cuelga de qué — que es justo lo que distingue esta pantalla de
     * una lista.
     *
     * <p>⚠ Y viaja {@code desbloqueada} <b>ya decidida por el servidor</b>. El
     * cliente podría calcularla —«mi padre está completo»— pero entonces la regla
     * viviría en dos sitios, y el día que se añada una condición (nivel mínimo,
     * fecha) el cliente enseñaría desbloqueado lo que el servidor rechaza.
     */
    public record MisionEstado(String id, String cadena, int orden, String requiere,
                               String nombre, String descripcion,
                               String objetivo, long meta, long progreso,
                               boolean completada, boolean cobrada, boolean desbloqueada,
                               long plata, long marcas, String via, long xp) {
        public static final PacketCodec<RegistryByteBuf, MisionEstado> CODEC =
                PacketCodec.ofStatic(MisionEstado::escribir, MisionEstado::leer);

        // ⚠ A MANO Y NO CON `PacketCodec.tuple`: esa fábrica llega hasta 16
        //   campos en 1.21.1 y aquí hay 16 justos, pero el día que se añada uno
        //   deja de compilar con un error que no dice por qué. Escribirlo así
        //   quita ese techo de golpe.
        private static void escribir(RegistryByteBuf buf, MisionEstado m) {
            buf.writeString(m.id);
            buf.writeString(m.cadena);
            buf.writeVarInt(m.orden);
            buf.writeString(m.requiere == null ? "" : m.requiere);
            buf.writeString(m.nombre);
            buf.writeString(m.descripcion);
            buf.writeString(m.objetivo);
            buf.writeVarLong(m.meta);
            buf.writeVarLong(m.progreso);
            buf.writeBoolean(m.completada);
            buf.writeBoolean(m.cobrada);
            buf.writeBoolean(m.desbloqueada);
            buf.writeVarLong(m.plata);
            buf.writeVarLong(m.marcas);
            buf.writeString(m.via == null ? "" : m.via);
            buf.writeVarLong(m.xp);
        }

        private static MisionEstado leer(RegistryByteBuf buf) {
            return new MisionEstado(
                    buf.readString(), buf.readString(), buf.readVarInt(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readString(),
                    buf.readVarLong(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarLong(), buf.readString(), buf.readVarLong());
        }

        public boolean cobrable() {
            return completada && !cobrada;
        }
    }

    /**
     * Todas las misiones del jugador.
     *
     * <p>⚠ Van TODAS, incluidas las bloqueadas. Enseñar solo las disponibles
     * escondería el árbol entero: lo que hace que una cadena se entienda es ver a
     * dónde lleva, no solo el siguiente paso.
     */
    public record Misiones(List<MisionEstado> misiones) implements CustomPayload {
        public static final Id<Misiones> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "misiones"));
        public static final PacketCodec<RegistryByteBuf, Misiones> CODEC =
                PacketCodec.tuple(
                        MisionEstado.CODEC.collect(PacketCodecs.toList()), Misiones::misiones,
                        Misiones::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** «Dame mis Vías», al abrir la pantalla de Trabajos. */
    public record PedirTrabajos() implements CustomPayload {
        public static final Id<PedirTrabajos> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_trabajos"));
        public static final PacketCodec<RegistryByteBuf, PedirTrabajos> CODEC =
                PacketCodec.unit(new PedirTrabajos());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una Vía con su progreso REAL.
     *
     * <p>⚠ Viaja la XP en bruto y el umbral, no un porcentaje. Un porcentaje ya
     * calculado impide enseñar «1.240 / 3.000», que es lo que deja ver cuánto
     * falta de verdad; y si algún día la curva cambia, el cliente seguiría
     * dibujando la barra bien sin tocar nada.
     *
     * <p>{@code xpSiguiente == 0} significa <b>nivel máximo</b>. Se manda un cero
     * en vez de {@code Long.MAX_VALUE} —que es lo que devuelve la curva— porque
     * el cliente tendría que conocer ese centinela para no dibujar una barra
     * llena al 0,0000001 %.
     */
    public record ViaEstado(String id, int nivel, long xp, long xpSiguiente) {
        public static final PacketCodec<RegistryByteBuf, ViaEstado> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ViaEstado::id,
                        PacketCodecs.VAR_INT, ViaEstado::nivel,
                        PacketCodecs.VAR_LONG, ViaEstado::xp,
                        PacketCodecs.VAR_LONG, ViaEstado::xpSiguiente,
                        ViaEstado::new);

        public boolean alMaximo() {
            return xpSiguiente <= 0;
        }
    }

    /**
     * Las cinco Vías del jugador.
     *
     * <p>⚠ <b>Van las cinco SIEMPRE</b>, incluidas las que están a cero. Mandar
     * solo las empezadas dejaría la pantalla con huecos cambiantes, y sobre todo
     * escondería las que aún no has tocado — que son justo las que el jugador
     * necesita ver para saber que existen.
     */
    public record Trabajos(List<ViaEstado> vias) implements CustomPayload {
        public static final Id<Trabajos> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "trabajos"));
        public static final PacketCodec<RegistryByteBuf, Trabajos> CODEC =
                PacketCodec.tuple(
                        ViaEstado.CODEC.collect(PacketCodecs.toList()), Trabajos::vias,
                        Trabajos::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Las categorías que <b>ven los demás</b> y por tanto hay que difundir.
     *
     * <p>⚠ Las mascotas NO están, y no es un olvido: el disfraz de un Pokémon
     * viaja <i>dentro del Pokémon</i> (es un aspecto suyo, y Cobblemon ya lo
     * sincroniza). Difundirlo aquí sería mandar dos veces lo mismo y abrir la
     * puerta a que las dos copias se contradigan.
     */
    private static final List<String> DIFUNDIDAS = List.of(
            net.pokereport.luna.cosmetics.Catalogo.AURAS,
            net.pokereport.luna.cosmetics.Catalogo.SOMBREROS);

    /**
     * Cuenta a todos lo que lleva un jugador, y a ese jugador lo de todos.
     *
     * <p>⚠⚠ ESTO ES LO QUE CONVIERTE UN COSMÉTICO EN UN PRODUCTO.
     * {@code monetization.md} lo avisa: «un cosmético sin nadie que lo vea no
     * vale nada». Un aura o un sombrero <b>no viajan con nada</b>: si esto no
     * existe, solo los ve su dueño en la tienda — media función, y ya pasó una
     * vez con las mascotas.
     *
     * <p>⚠ Las DOS direcciones hacen falta, y por motivos distintos. Al entrar,
     * el recién llegado no sabe nada de nadie —se perdió las difusiones de
     * antes— y los que ya estaban no saben nada de él. Mandar solo una deja un
     * servidor donde cada uno ve un subconjunto distinto, que es peor que no
     * verlas: dos jugadores no se pondrían de acuerdo sobre quién lleva qué.
     *
     * <p>Cadena vacía = <b>se lo ha quitado</b>. Hace falta un valor para eso: si
     * quitárselo se difundiera «no mandando nada», los demás seguirían viéndolo
     * hasta reconectar.
     */
    public static void difundir(net.minecraft.server.network.ServerPlayerEntity quien,
                                String categoria, String cosmetico) {
        var servidor = quien.getServer();
        if (servidor == null) {
            return;
        }
        var mio = new LlevaPuesto(quien.getUuid(), categoria,
                cosmetico == null ? "" : cosmetico);
        for (var otro : servidor.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(otro, mio);
        }
    }

    /** Todo lo de todos, para quien acaba de entrar; y lo suyo, para todos. */
    public static void difundirTodo(net.minecraft.server.network.ServerPlayerEntity quien) {
        var servidor = quien.getServer();
        if (servidor == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try {
                var svc = LunaEternal.cosmetics();
                var paquetes = new ArrayList<LlevaPuesto>();
                var paraTodos = new ArrayList<LlevaPuesto>();
                for (var otro : servidor.getPlayerManager().getPlayerList()) {
                    long id = LunaEternal.players()
                            .resolve(otro.getUuid(), otro.getName().getString());
                    var puestos = svc.equipados(id);
                    for (String categoria : DIFUNDIDAS) {
                        String suyo = puestos.get(categoria);
                        if (suyo == null || suyo.isEmpty()) {
                            continue;
                        }
                        var p = new LlevaPuesto(otro.getUuid(), categoria, suyo);
                        (otro == quien ? paraTodos : paquetes).add(p);
                    }
                }
                servidor.execute(() -> {
                    // Lo de los demás, solo a él.
                    for (LlevaPuesto p : paquetes) {
                        ServerPlayNetworking.send(quien, p);
                    }
                    // Lo suyo, a todos (incluido él: el previsualizador lo usa).
                    for (LlevaPuesto p : paraTodos) {
                        for (var otro : servidor.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(otro, p);
                        }
                    }
                });
            } catch (Exception e) {
                // Quedarse sin ver los cosméticos es feo; que falle el login por
                // eso sería mucho peor.
                LunaEternal.LOG.warn("No se pudieron difundir los cosmeticos: {}",
                        e.toString());
            }
        });
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PedirSaldo.ID, PedirSaldo.CODEC);
        PayloadTypeRegistry.playS2C().register(Saldo.ID, Saldo.CODEC);
        PayloadTypeRegistry.playS2C().register(Ficha.ID, Ficha.CODEC);
        PayloadTypeRegistry.playS2C().register(VozPokedex.ID, VozPokedex.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirCosmeticos.ID, PedirCosmeticos.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionCosmetico.ID, AccionCosmetico.CODEC);
        PayloadTypeRegistry.playS2C().register(Cosmeticos.ID, Cosmeticos.CODEC);
        PayloadTypeRegistry.playS2C().register(LlevaPuesto.ID, LlevaPuesto.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirTrabajos.ID, PedirTrabajos.CODEC);
        PayloadTypeRegistry.playS2C().register(Trabajos.ID, Trabajos.CODEC);
        PayloadTypeRegistry.playS2C().register(AvisoLogro.ID, AvisoLogro.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirMercado.ID, PedirMercado.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionMercado.ID, AccionMercado.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoMercado.ID, EstadoMercado.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirTienda.ID, PedirTienda.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionTienda.ID, AccionTienda.CODEC);
        PayloadTypeRegistry.playS2C().register(Tienda.ID, Tienda.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirClan.ID, PedirClan.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionClan.ID, AccionClan.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoClan.ID, EstadoClan.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirInicial.ID, PedirInicial.CODEC);
        PayloadTypeRegistry.playC2S().register(ElegirInicial.ID, ElegirInicial.CODEC);
        PayloadTypeRegistry.playS2C().register(Iniciales.ID, Iniciales.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirLlevados.ID, PedirLlevados.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirCura.ID, PedirCura.CODEC);
        PayloadTypeRegistry.playC2S().register(Curar.ID, Curar.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoCura.ID, EstadoCura.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirMisiones.ID, PedirMisiones.CODEC);
        PayloadTypeRegistry.playC2S().register(ReclamarMision.ID, ReclamarMision.CODEC);
        PayloadTypeRegistry.playS2C().register(Misiones.ID, Misiones.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PedirSaldo.ID, (carga, ctx) ->
                enviarSaldo(ctx.player()));


        ServerPlayNetworking.registerGlobalReceiver(PedirCosmeticos.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> enviarCosmeticos(jugador));
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirMercado.ID, (carga, ctx) ->
                enviarMercado(ctx.player(), carga.item()));

        ServerPlayNetworking.registerGlobalReceiver(AccionMercado.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            var servidor = jugador.getServer();
            if (servidor == null) {
                return;
            }
            var svc = LunaEternal.market();
            if (svc == null) {
                return;
            }

            // ⚠⚠ LA CUSTODIA DE LOS OBJETOS SE HACE AQUI Y EN EL HILO DEL
            //    SERVIDOR, porque tocar un inventario desde el executor de E/S
            //    es tocar el mundo desde fuera. Y va ANTES de crear la orden:
            //    si la orden existiera y los objetos no estuvieran retenidos,
            //    el mercado estaria vendiendo lo que no tiene -- que es el
            //    vector de duplicacion numero uno de todos los GTS mal hechos.
            String accion = carga.accion();
            int sacados = 0;
            net.minecraft.item.Item item = null;
            if ("vender".equals(accion)) {
                item = net.pokereport.luna.market.Inventarios.objeto(carga.item());
                if (item == null) {
                    jugador.sendMessage(net.minecraft.text.Text.literal(
                            "\u00a7cEse objeto no existe."), true);
                    return;
                }
                int piden = Math.max(1, Math.min(
                        net.pokereport.luna.market.MarketService.MAX_CANTIDAD,
                        carga.cantidad()));
                if (net.pokereport.luna.market.Inventarios.cuantos(jugador, item) < piden) {
                    jugador.sendMessage(net.minecraft.text.Text.literal(
                            "\u00a7cNo tienes tantos."), true);
                    return;
                }
                sacados = net.pokereport.luna.market.Inventarios.sacar(jugador, item, piden);
                if (sacados <= 0) {
                    jugador.sendMessage(net.minecraft.text.Text.literal(
                            "\u00a7cNo se pudieron retirar los objetos."), true);
                    return;
                }
            }

            final int retenidos = sacados;
            final net.minecraft.item.Item elItem = item;
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    net.pokereport.luna.market.MarketService.Resultado r;
                    switch (accion) {
                        case "comprar" -> r = svc.poner(id,
                                net.pokereport.luna.market.MarketService.Lado.COMPRA,
                                carga.item(), carga.precio(), carga.cantidad(),
                                java.util.UUID.randomUUID().toString());
                        case "vender" -> r = svc.poner(id,
                                net.pokereport.luna.market.MarketService.Lado.VENTA,
                                carga.item(), carga.precio(), retenidos,
                                java.util.UUID.randomUUID().toString());
                        case "cancelar" -> r = svc.cancelar(id, carga.orden());
                        default -> r = null;
                    }
                    if (r == null) {
                        return;
                    }

                    // ⚠⚠ SI LA ORDEN NO SALE, LOS OBJETOS VUELVEN. Es la unica
                    //    parte de la custodia que no puede vivir en una
                    //    transaccion de base de datos --el inventario no es una
                    //    tabla-- asi que se deshace a mano. Sin esto, un rechazo
                    //    por tope de ordenes se COME los objetos del jugador.
                    if (!r.ok() && retenidos > 0 && elItem != null) {
                        servidor.execute(() -> net.pokereport.luna.market.Inventarios
                                .meter(jugador, elItem, retenidos));
                    }

                    final var res = r;
                    servidor.execute(() -> jugador.sendMessage(
                            net.minecraft.text.Text.literal(
                                    (res.ok() ? "\u00a7a" : "\u00a7c") + res.mensaje()),
                            true));

                    // El saldo cambia en las tres acciones: comprar retiene,
                    // vender cobra y cancelar devuelve.
                    enviarSaldo(jugador);
                    // ⚠⚠ Y SE AVISA A TODOS LOS AFECTADOS, no solo a quien actuo.
                    //    Cuando tu orden se llena contra la mia, MI orden ha
                    //    cambiado y mi dinero tambien. Es el bug de los clanes, y
                    //    aqui es peor: alli se quedaba una etiqueta puesta; aqui
                    //    veo ordenes que ya no existen e intento cancelarlas.
                    refrescarMercadoA(servidor, res.afectados(), carga.item());

                } catch (Exception e) {
                    LunaEternal.LOG.warn("Fallo en la accion de mercado {}: {}",
                            accion, e.toString());
                    if (retenidos > 0 && elItem != null) {
                        servidor.execute(() -> net.pokereport.luna.market.Inventarios
                                .meter(jugador, elItem, retenidos));
                    }
                    enviarMercado(jugador, carga.item());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirTienda.ID, (carga, ctx) ->
                ServerPlayNetworking.send(ctx.player(), componerTienda()));

        ServerPlayNetworking.registerGlobalReceiver(AccionTienda.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            var catalogo = LunaEternal.shop();
            if (catalogo == null) {
                return;
            }
            var categoria = catalogo.category(carga.categoria());
            if (categoria == null) {
                return;
            }
            // ⚠ SE BUSCA LA ENTRADA EN EL CATALOGO DEL SERVIDOR. El paquete solo
            //   dice CUAL; el precio, la moneda y si se puede vender salen de
            //   aqui. Es la diferencia entre una tienda y un formulario de
            //   deseos (P6).
            net.pokereport.luna.shop.ShopCatalog.Entry entrada = null;
            for (var e : categoria.entries()) {
                if (net.minecraft.registry.Registries.ITEM.getId(e.item())
                        .toString().equals(carga.item())) {
                    entrada = e;
                    break;
                }
            }
            if (entrada == null) {
                return;
            }
            // ⚠ LA CANTIDAD SE ACOTA AQUI. Llega del cliente, y un 2.000 millones
            //   en `entry.buy() * amount` DESBORDA el long y sale NEGATIVO: cobrar
            //   una cantidad negativa es INGRESAR dinero. Se acota antes de
            //   multiplicar, que es el unico sitio donde sirve de algo.
            int cantidad = Math.max(1, Math.min(64, carga.cantidad()));
            final var laEntrada = entrada;
            java.util.function.Consumer<net.pokereport.luna.shop.ShopService.Result> luego =
                    r -> {
                        jugador.sendMessage(net.minecraft.text.Text.literal(r.message()), false);
                        // El saldo cambia con cada operacion, y la pantalla lo
                        // dibuja. Si el servidor no lo reenvia, el jugador ve el
                        // numero viejo hasta que reabra -- la leccion del 23-ago.
                        enviarSaldo(jugador);
                    };
            if (carga.comprar()) {
                net.pokereport.luna.shop.ShopService.buy(jugador, laEntrada, cantidad, luego);
            } else {
                net.pokereport.luna.shop.ShopService.sell(jugador, laEntrada, cantidad, luego);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirCura.ID, (carga, ctx) ->
                enviarCura(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(Curar.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            // ⚠ TODA la regla vive en HealService: el cooldown, si hace falta
            //   curar y el mensaje. Aqui no se comprueba nada -- repartir la
            //   comprobacion entre el manejador y el servicio es como acaban
            //   existiendo dos reglas que un dia dejan de decir lo mismo.
            net.pokereport.luna.heal.HealService.curar(jugador);

            // ⚠ Y SE REENVIA EL ESTADO, siempre, tanto si curo como si no.
            //   El servidor acaba de cambiar algo que la pantalla dibuja --las
            //   barras de vida y el reloj-- y si no lo reenvia, el jugador ve
            //   el equipo herido hasta que reabra. Es la leccion del 23-ago,
            //   que salio cuatro veces con cuatro caras distintas.
            enviarCura(jugador);
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirClan.ID, (carga, ctx) ->
                enviarClan(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(AccionClan.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            var servidor = jugador.getServer();
            if (servidor == null) {
                return;
            }
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    var svc = LunaEternal.clans();
                    net.pokereport.luna.clan.ClanService.Resultado r;

                    // ⚠ TODA la comprobación de permisos está DENTRO del
                    //   servicio, no aquí. Este switch solo traduce el nombre de
                    //   la acción a una llamada: si repartiera comprobaciones
                    //   entre los dos sitios, tarde o temprano una acción nueva
                    //   se añadiría solo en uno.
                    switch (carga.accion()) {
                        case "fundar" -> r = svc.fundar(id, carga.texto().trim(),
                                carga.texto2().trim(), 'b', "",
                                java.util.UUID.randomUUID().toString());
                        case "invitar" -> {
                            // ⚠ Viaja el NOMBRE, no el player_id: el cliente no
                            //   conoce identificadores internos y no tiene por
                            //   qué. Se resuelve aquí, y si no existe se dice.
                            Long otro = LunaEternal.players()
                                    .resolveByName(carga.texto().trim());
                            r = otro == null
                                    ? new net.pokereport.luna.clan.ClanService.Resultado(
                                            false, "No conozco a nadie con ese nombre.",
                                            java.util.Set.of())
                                    : svc.invitar(id, otro);
                        }
                        case "aceptar" -> r = svc.aceptar(id, carga.objetivo());
                        case "rechazar" -> r = svc.rechazar(id, carga.objetivo());
                        case "salir" -> r = svc.salir(id);
                        case "echar" -> r = svc.echar(id, carga.objetivo());
                        case "ascender" -> r = svc.cambiarRol(id, carga.objetivo(),
                                net.pokereport.luna.clan.ClanService.Rol.OFICIAL);
                        case "degradar" -> r = svc.cambiarRol(id, carga.objetivo(),
                                net.pokereport.luna.clan.ClanService.Rol.MIEMBRO);
                        case "traspasar" -> r = svc.traspasar(id, carga.objetivo());
                        case "disolver" -> r = svc.disolver(id);
                        case "aportar" -> r = svc.aportar(id, carga.cantidad(),
                                java.util.UUID.randomUUID().toString());
                        case "sacar" -> r = svc.sacar(id, carga.cantidad(),
                                java.util.UUID.randomUUID().toString());
                        case "tope" -> r = svc.cambiarTope(id, carga.cantidad());
                        default -> r = new net.pokereport.luna.clan.ClanService.Resultado(
                                false, "Acción desconocida.", java.util.Set.of());
                    }

                    final var res = r;
                    servidor.execute(() -> jugador.sendMessage(
                            net.minecraft.text.Text.literal(
                                    (res.ok() ? "\u00a7a" : "\u00a7c") + res.mensaje()), true));

                    // ⚠⚠ A QUIÉN SE AVISA LO DICE EL SERVICIO, NO EL CLAN.
                    //
                    //    Aquí estuvo el bug que reportó el usuario: «cuando sacas
                    //    a alguien del clan, al jugador le sigue apareciendo la
                    //    etiqueta». La versión anterior calculaba los destinatarios
                    //    mirando los miembros DE DESPUÉS -- y el echado ya no está
                    //    ahí. Se quedaba con la etiqueta puesta, con la pantalla
                    //    mintiéndole y creyéndose dentro.
                    //
                    //    El conjunto correcto es «miembros de antes ∪ miembros de
                    //    después ∪ el objetivo», y quien lo sabe es el servicio,
                    //    que acaba de hacer el trabajo. Por eso viaja en el
                    //    Resultado: así no se puede volver a olvidar.
                    // ⚠ REGLA UNIFORME: lo que mueve dinero reenvía el saldo.
                    //   Fundar cuesta 5.000, aportar y sacar mueven el bolsillo,
                    //   y la pantalla del clan enseña ese número. Sin esto se ve
                    //   el saldo viejo hasta reabrir -- que es literalmente la
                    //   lección que ya costó cuatro fallos el 23-ago.
                    if (res.ok() && MUEVEN_DINERO.contains(carga.accion())) {
                        enviarSaldo(jugador);
                    }
                    if (!res.afectados().isEmpty()) {
                        refrescarA(servidor, res.afectados());
                    } else {
                        // Un rechazo no cambia nada de nadie, pero el que lo
                        // intentó merece ver la verdad otra vez por si su
                        // pantalla iba retrasada.
                        enviarClan(jugador);
                    }

                } catch (Exception e) {
                    LunaEternal.LOG.warn("Fallo en la accion de clan {}: {}",
                            carga.accion(), e.toString());
                    enviarClan(jugador);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirInicial.ID, (carga, ctx) ->
                enviarIniciales(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(ElegirInicial.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    // ⚠ AQUI NO SE COMPRUEBA SI YA ELIGIO. `conceder` marca
                    //   primero y entrega despues, con vuelta atras si la entrega
                    //   falla, y `claimOnce` es lo unico que impide elegir dos
                    //   veces -- porque es atomico. Repetir la comprobacion aqui
                    //   invita a que las dos se separen.
                    //
                    // ⚠⚠ SE LE PASA UN AVISO, Y ESO ERA LO QUE FALTABA.
                    //    `conceder` es ASINCRONO: encola trabajo y vuelve al
                    //    instante. Antes se llamaba a `enviarIniciales` justo
                    //    despues, y leia el estado ANTERIOR --yaEligio false--,
                    //    asi que la pantalla se quedaba en ENTREGANDO para
                    //    siempre. Y como no se puede cerrar sin elegir, el
                    //    jugador se quedaba ATRAPADO.
                    net.pokereport.luna.starter.StarterService.conceder(
                            jugador, id, carga.especie(), () -> enviarIniciales(jugador));
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudo entregar el inicial a {}: {}",
                            jugador.getName().getString(), e.toString());
                    // Si ni siquiera se llego a llamar a `conceder`, el aviso no
                    // va a llegar nunca: se manda el estado aqui para que la
                    // pantalla salga de ENTREGANDO en vez de colgarse.
                    enviarIniciales(jugador);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirLlevados.ID, (carga, ctx) -> {
            // Todo lo de TODOS, a quien pregunta -- incluido lo suyo, que es
            // justo lo que se perdia.
            var jugador = ctx.player();
            var servidor = jugador.getServer();
            if (servidor == null) {
                return;
            }
            LunaEternal.submit(() -> {
                try {
                    var svc = LunaEternal.cosmetics();
                    var salida = new ArrayList<LlevaPuesto>();
                    for (var otro : servidor.getPlayerManager().getPlayerList()) {
                        long id = LunaEternal.players()
                                .resolve(otro.getUuid(), otro.getName().getString());
                        var puestos = svc.equipados(id);
                        for (String categoria : DIFUNDIDAS) {
                            String suyo = puestos.get(categoria);
                            if (suyo != null && !suyo.isEmpty()) {
                                salida.add(new LlevaPuesto(otro.getUuid(), categoria, suyo));
                            }
                        }
                    }
                    servidor.execute(() -> {
                        for (LlevaPuesto p : salida) {
                            ServerPlayNetworking.send(jugador, p);
                        }
                    });
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudo contestar a PedirLlevados: {}",
                            e.toString());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirMisiones.ID, (carga, ctx) ->
                enviarMisiones(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(ReclamarMision.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    var mision = LunaEternal.quests().byId(carga.id());
                    // ⚠ EL SERVIDOR DECIDE. `claim` comprueba que este completa y
                    //   sin cobrar, y paga en su propia transaccion. Aqui no se
                    //   comprueba nada: hacerlo dos veces invita a que las dos
                    //   comprobaciones se separen, y la que manda es la de alla.
                    if (mision != null && LunaEternal.quests().claim(id, mision)) {
                        String detalle = mision.rewards().pokedollar() > 0
                                ? String.format("+%,d Plata", mision.rewards().pokedollar())
                                : "Recompensa cobrada";
                        net.pokereport.luna.ui.Aviso.logro(jugador, "MISION COMPLETA",
                                detalle, "minecraft:written_book");
                    }
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudo cobrar la mision {}: {}",
                            carga.id(), e.toString());
                }
                // Se reenvia SIEMPRE, saliera bien o mal: es la forma de que la
                // pantalla vuelva a la verdad sin tener que adivinar que cambio.
                enviarMisiones(jugador);
                // Y el saldo, porque cobrar una mision PAGA. Hoy la pantalla de
                // misiones no enseña el saldo, asi que no se nota -- pero la
                // regla es «lo que mueve dinero reenvia el saldo», y una regla
                // que solo se aplica donde hoy se nota deja de aplicarse el dia
                // que se añada el numero a la pantalla.
                enviarSaldo(jugador);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirTrabajos.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    var niveles = LunaEternal.progression().all(id);
                    List<ViaEstado> vias = new ArrayList<>(Path.values().length);
                    for (Path via : Path.values()) {
                        var estado = niveles.get(via);
                        int nivel = estado == null ? 0 : estado.level();
                        long xp = estado == null ? 0 : estado.xp();
                        // ⚠ El cero de «nivel maximo» se decide AQUI y no en el
                        //   cliente: la curva es del servidor, y el centinela que
                        //   devuelve (Long.MAX_VALUE) es un detalle suyo.
                        long falta = nivel >= Path.MAX_LEVEL
                                ? 0 : Path.xpForNextLevel(nivel);
                        vias.add(new ViaEstado(via.name(), nivel, xp, falta));
                    }
                    var carga2 = new Trabajos(vias);
                    jugador.getServer().execute(
                            () -> ServerPlayNetworking.send(jugador, carga2));
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudieron leer las vias de {}: {}",
                            jugador.getName().getString(), e.toString());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AccionCosmetico.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    var svc = LunaEternal.cosmetics();
                    net.pokereport.luna.cosmetics.CosmeticsService.Resultado r;

                    // ⚠ LAS PIEZAS DEL JUGADOR NO PASAN POR `disfrazar`. Aquel
                    //   busca un Pokemon de la especie y le fuerza el aspecto;
                    //   una capa o un aura no tienen especie y no van en ningun
                    //   Pokemon. Se separan por LA PIEZA y no por la categoria:
                    //   la pieza ya lleva la respuesta (`especie` vacia), y una
                    //   comprobacion por nombre de categoria se olvida el dia que
                    //   se añada la quinta.
                    var pieza = net.pokereport.luna.cosmetics.Catalogo.de(carga.id());
                    boolean delJugador = pieza != null && !pieza.esDePokemon();

                    if (delJugador && !carga.esCompra()) {
                        // Quitar = equipar la cadena vacia. Es la misma
                        // operacion: «que categoria lleva que», y vacio es una
                        // respuesta valida.
                        r = svc.equipar(id, pieza.categoria(),
                                carga.esQuitar() ? "" : carga.id());
                        if (r.ok() && DIFUNDIDAS.contains(pieza.categoria())) {
                            String ahora = carga.esQuitar() ? "" : carga.id();
                            jugador.getServer().execute(
                                    () -> difundir(jugador, pieza.categoria(), ahora));
                        }
                    } else if (carga.esQuitar()) {
                        r = svc.desvestir(jugador, id, carga.id());
                    } else if (!carga.esCompra()) {
                        r = svc.disfrazar(jugador, id, carga.id(), carga.ranura());
                    } else {
                        // ⚠⚠ UUID NUEVO, Y NO UNA CLAVE DERIVADA DEL COSMETICO.
                        //
                        // Estuvo derivada --"cosm:<jugador>:<cosmetico>"-- con
                        // el razonamiento de que asi dos clics rapidos comparten
                        // clave. Era un error, y de los que solo se ven pensando
                        // en el caso raro:
                        //
                        //   si algun dia se le RETIRA un cosmetico a alguien
                        //   --reembolso, correccion de un evento-- y lo vuelve a
                        //   comprar, esa clave YA ESTA USADA. La economia
                        //   contesta ALREADY_APPLIED, el cobro se salta... y el
                        //   INSERT si entra. Cosmetico gratis, sin error.
                        //
                        // Y no hacia falta: el cobro y la anotacion van en la
                        // MISMA transaccion, asi que si el INSERT choca contra
                        // la clave primaria se deshace todo, cobro incluido. Los
                        // dos clics ya estaban cubiertos por ahi.
                        r = svc.comprar(id, carga.id(), java.util.UUID.randomUUID().toString());
                    }

                    if (!r.ok()) {
                        jugador.getServer().execute(() ->
                                jugador.sendMessage(net.minecraft.text.Text
                                        .literal("§c" + r.mensaje()), true));
                    }
                    // Se reenvia el catalogo SIEMPRE, saliera bien o mal: es la
                    // forma de que la tienda del cliente vuelva a la verdad sin
                    // tener que adivinar que ha cambiado.
                    enviarCosmeticos(jugador);
                } catch (Exception e) {
                    LunaEternal.LOG.warn("Cosmetico {} de {} fallo: {}",
                            carga.id(), jugador.getName().getString(), e.toString());
                }
            });
        });
    }

    /**
     * Manda el catalogo entero con lo que el jugador posee y lleva puesto.
     *
     * <p>⚠ Corre en el executor de E/S y vuelve al hilo del servidor para
     * enviar: consultar la base desde el hilo principal congela a todo el mundo,
     * y enviar desde un hilo cualquiera no es seguro.
     */
    /**
     * Manda el árbol entero con su estado.
     *
     * <p>⚠ <b>El desbloqueo se decide AQUI</b>, no en el cliente. Una misión está
     * desbloqueada si no pide nada, o si lo que pide está <i>completo</i> —no
     * cobrado: cobrar es del jugador, y dejar la cadena parada porque a alguien se
     * le olvidó pulsar el botón sería castigar por no mirar la pantalla.
     */
    /**
     * Vuelve a mandarle el estado del inicial.
     *
     * <p>⚠ Existe porque {@code /luna reiniciarinicial} borraba la marca y NO
     * PASABA NADA: el cliente guarda la ultima respuesta, asi que seguia
     * pensando que ya habia elegido hasta reconectar. Borrar en la base no
     * cambia lo que el cliente ya cree.
     */
    public static void refrescarInicial(net.minecraft.server.network.ServerPlayerEntity jugador) {
        enviarIniciales(jugador);
    }

    /**
     * Manda el saldo y la ficha a un jugador.
     *
     * <p>Se llama al abrir el Pad y <b>cada vez que el saldo cambia por algo que
     * el jugador acaba de hacer</b> --comprar, vender, aportar al tesoro--.
     *
     * <p>⚠ Existe como metodo y no copiado dentro de cada manejador porque
     * compone DOS cosas (saldo y ficha) y la ficha ya ha crecido una vez: el dia
     * que crezca otra, dos copias dejarian una pantalla enseñando el clan y otra
     * no.
     */
    public static void enviarSaldo(
            net.minecraft.server.network.ServerPlayerEntity jugador) {
        // A la base de datos NUNCA desde el hilo del servidor. Se responde
        // desde el executor de E/S y se envía cuando el dato ya está.
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var eco = LunaEternal.economy();
                var saldo = new Saldo(
                        eco.balance(id, Currency.POKEDOLLAR),
                        eco.balance(id, Currency.MARK),
                        eco.balance(id, Currency.REPORTCOIN));
                // La tarjeta viaja en la MISMA peticion. Podria ser otro
                // paquete con su propio viaje, pero se abren juntos y se
                // dibujan juntos: dos idas y vueltas para pintar un mismo
                // panel son dos formas de que llegue medio.
                var niveles = LunaEternal.progression().all(id);
                List<Integer> vias = new ArrayList<>(Path.values().length);
                for (Path via : Path.values()) {
                    var estado = niveles.get(via);
                    vias.add(estado == null ? 0 : estado.level());
                }
                // Trabajo, división y medallas todavía no tienen sistema
                // detrás. Se mandan vacíos a propósito en vez de inventar un
                // valor: el Pad dibuja un guión, que dice «esto aún no»,
                // mientras que un «Sin clan» diría «ya funciona y no tienes
                // ninguno» — que no sería verdad.
                // ⚠ EL CLAN YA NO VA VACIO. D-038 dejo este campo
                //   viajando con la cadena vacia a proposito y escribio que
                //   "encenderlos seria rellenar tres lineas en vez de tocar
                //   paquete, codec, cache y dibujado". Fue exacto: es esta.
                //
                //   Se manda "[TAG] Nombre" ya compuesto, para que el Pad no
                //   tenga que saber como se escribe un clan.
                String clan = "";
                try {
                    var c = LunaEternal.clans().clanDe(id);
                    if (c != null) {
                        clan = "\u00a7" + c.color() + "[" + c.etiqueta() + "] \u00a7f"
                                + c.nombre();
                    }
                } catch (Exception e) {
                    // Sin clan en la ficha es un guion; que falle la consulta
                    // no puede dejar al jugador sin saldo ni sin vias.
                    LunaEternal.LOG.debug("Sin clan para la ficha: {}", e.toString());
                }
                var ficha = new Ficha(vias, clan, "", "", 0);
                // Volver al hilo del servidor para enviar: la red no es
                // segura desde un hilo cualquiera.
                jugador.getServer().execute(() -> {
                    ServerPlayNetworking.send(jugador, saldo);
                    ServerPlayNetworking.send(jugador, ficha);
                });
            } catch (Exception e) {
                // Que no se pueda leer el saldo no es motivo para echar a
                // nadie: el Pad se queda con guiones donde iría el número.
                LunaEternal.LOG.warn("No se pudo leer la ficha de {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    /**
     * Manda el estado del mercado a un jugador.
     *
     * <p>⚠ Va por el executor de E/S: son cinco consultas. Y `tengo` se cuenta
     * en el hilo del SERVIDOR antes de salir, porque leer un inventario desde
     * otro hilo es leer el mundo desde fuera.
     */
    private static void enviarMercado(
            net.minecraft.server.network.ServerPlayerEntity jugador, String item) {
        var svc = LunaEternal.market();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        // Lo que lleva encima, contado AQUI y ya.
        final String elItem = item == null ? "" : item;
        var objeto = elItem.isEmpty()
                ? null : net.pokereport.luna.market.Inventarios.objeto(elItem);
        final int tengo = objeto == null
                ? 0 : net.pokereport.luna.market.Inventarios.cuantos(jugador, objeto);
        // Y lo que lleva de TODO, para poder ofrecerselo en la lista.
        final List<String> mochila = new ArrayList<>();
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            var pila = inv.main.get(i);
            if (pila.isEmpty()) {
                continue;
            }
            var suyo = pila.getItem();
            // ⚠ Solo lo CORRIENTE: un pico encantado no puede entrar en un libro
            //   de ordenes, asi que tampoco tiene por que salir en la lista y
            //   dar a entender que si.
            if (!net.pokereport.luna.market.Inventarios.corriente(pila, suyo)) {
                continue;
            }
            String id = net.minecraft.registry.Registries.ITEM.getId(suyo).toString();
            if (!mochila.contains(id)) {
                mochila.add(id);
            }
        }

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var carga = componerMercado(svc, id, elItem, tengo, mochila);
                servidor.execute(() -> {
                    if (!jugador.isRemoved()) {
                        ServerPlayNetworking.send(jugador, carga);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo enviar el mercado a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    /**
     * Refresca a todos los afectados por un cruce.
     *
     * <p>⚠⚠ Mismo patron que {@code refrescarA} de los clanes, y por el mismo
     * motivo: <b>el estado no es de quien lo mira</b>. Un cruce cambia la orden
     * y el dinero de OTRA persona, que no ha pulsado nada.
     *
     * <p>Se llama desde el hilo de E/S.
     */
    private static void refrescarMercadoA(net.minecraft.server.MinecraftServer servidor,
                                          java.util.Set<Long> afectados, String item) {
        if (afectados == null || afectados.isEmpty()) {
            return;
        }
        for (var otro : servidor.getPlayerManager().getPlayerList()) {
            try {
                long id = LunaEternal.players()
                        .resolve(otro.getUuid(), otro.getName().getString());
                if (afectados.contains(id)) {
                    // ⚠ A cada uno se le manda el objeto que EL estuviera
                    //   mirando no se sabe, asi que se le manda este: es el que
                    //   ha cambiado. Si estaba en otro, su pantalla lo pedira al
                    //   cambiar de objeto -- y el saldo, que es lo que de verdad
                    //   no puede quedarse viejo, va aparte.
                    enviarMercado(otro, item);
                    enviarSaldo(otro);
                }
            } catch (Exception e) {
                LunaEternal.LOG.debug("No se pudo refrescar el mercado de {}: {}",
                        otro.getName().getString(), e.toString());
            }
        }
    }

    /** Junta las consultas del mercado. Se llama desde el hilo de E/S. */
    private static EstadoMercado componerMercado(
            net.pokereport.luna.market.MarketService svc, long playerId,
            String item, int tengo, List<String> mochila) throws Exception {

        // El catalogo: lo que se negocia MAS lo que llevas encima.
        var catalogo = new ArrayList<String>(svc.masNegociados(40));
        for (String s : mochila) {
            if (!catalogo.contains(s)) {
                catalogo.add(s);
            }
        }

        List<NivelMercado> compras = new ArrayList<>();
        List<NivelMercado> ventas = new ArrayList<>();
        List<TratoMercado> historial = new ArrayList<>();
        long ultimo = 0;
        if (!item.isEmpty()) {
            for (var n : svc.libro(item,
                    net.pokereport.luna.market.MarketService.Lado.COMPRA)) {
                compras.add(new NivelMercado(n.precio(), n.unidades(), n.ordenes()));
            }
            for (var n : svc.libro(item,
                    net.pokereport.luna.market.MarketService.Lado.VENTA)) {
                ventas.add(new NivelMercado(n.precio(), n.unidades(), n.ordenes()));
            }
            for (var x : svc.historial(item, 20)) {
                historial.add(new TratoMercado(x.precio(), x.qty(), x.cuando()));
            }
            ultimo = svc.ultimoPrecio(item);
        }

        List<OrdenMercado> mias = new ArrayList<>();
        for (var o : svc.mias(playerId)) {
            mias.add(new OrdenMercado(o.id(), o.lado().name(), o.itemId(),
                    o.precio(), o.total(), o.lleno()));
        }

        long saldo = LunaEternal.economy().balance(playerId, Currency.POKEDOLLAR);
        return new EstadoMercado(item, List.copyOf(catalogo), List.copyOf(compras),
                List.copyOf(ventas), List.copyOf(mias), List.copyOf(historial),
                ultimo, tengo, saldo);
    }

    /**
     * El catalogo de la tienda, tal y como lo tiene el servidor.
     *
     * <p>No toca la base de datos: {@code ShopCatalog} se carga del JSON al
     * arrancar y se valida entonces. Por eso se puede componer aqui mismo, en el
     * hilo del servidor, sin pasar por el executor.
     */
    private static Tienda componerTienda() {
        var catalogo = LunaEternal.shop();
        List<CategoriaTienda> salida = new ArrayList<>();
        if (catalogo == null) {
            return new Tienda(List.of());
        }
        for (var c : catalogo.categories()) {
            List<EntradaTienda> entradas = new ArrayList<>();
            for (var e : c.entries()) {
                entradas.add(new EntradaTienda(
                        net.minecraft.registry.Registries.ITEM.getId(e.item()).toString(),
                        e.label() == null ? "" : e.label(),
                        e.buy(), e.sell(), e.currency().name()));
            }
            salida.add(new CategoriaTienda(c.id(), c.name(),
                    net.minecraft.registry.Registries.ITEM.getId(c.icon()).toString(),
                    c.description() == null ? "" : c.description(),
                    List.copyOf(entradas)));
        }
        return new Tienda(List.copyOf(salida));
    }

    /**
     * Manda el equipo y el reloj de la curación.
     *
     * <p>⚠ <b>Esto NO va por el executor de E/S</b>, al contrario que casi todo
     * lo demás de aquí, y es a propósito: no toca la base de datos ni una vez.
     * El equipo lo tiene Cobblemon en memoria y el cooldown es un mapa en
     * memoria ({@code HealService}). Mandarlo al executor solo añadiría un salto
     * de hilo —y leer el equipo de Cobblemon fuera del hilo del servidor es
     * justo lo que no se debe hacer.
     */
    private static void enviarCura(net.minecraft.server.network.ServerPlayerEntity jugador) {
        List<PokemonCura> equipo = new java.util.ArrayList<>();
        try {
            for (var p : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(jugador)) {
                if (p == null) {
                    continue;
                }
                var estado = p.getStatus();
                equipo.add(new PokemonCura(
                        p.getSpecies().getName(),
                        // El apodo puede no existir; entonces se dibuja la especie.
                        p.getNickname() == null ? "" : p.getNickname().getString(),
                        p.getLevel(),
                        p.getCurrentHealth(),
                        p.getMaxHealth(),
                        estado == null ? "" : estado.getStatus().getShowdownName()));
            }
        } catch (Throwable t) {
            // ⚠ SE MANDA EL PAQUETE IGUAL, con el equipo vacio. Si no se manda
            //   nada, la pantalla se queda en «cargando» para siempre y el
            //   jugador no sabe si es lento o esta roto.
            LunaEternal.LOG.error("No se pudo leer el equipo para la pantalla de curar", t);
        }

        ServerPlayNetworking.send(jugador, new EstadoCura(
                List.copyOf(equipo),
                net.pokereport.luna.heal.HealService.restante(jugador),
                net.pokereport.luna.heal.HealService.necesitaCura(jugador)));
    }

    /**
     * Manda el estado del clan a un jugador.
     *
     * <p>⚠ Va por el executor de E/S: son cuatro consultas y ninguna puede
     * ocurrir en el hilo del servidor.
     */
    private static void enviarClan(net.minecraft.server.network.ServerPlayerEntity jugador) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var carga = componerClan(jugador, id);
                jugador.getServer().execute(
                        () -> ServerPlayNetworking.send(jugador, carga));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo enviar el clan a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    /**
     * Reenvía el estado a TODOS los miembros conectados del clan.
     *
     * <p>⚠⚠ Esto es lo que separa un sistema social de una pantalla personal: el
     * estado no es de quien lo mira, es <b>compartido</b>. Si alguien echa a un
     * miembro y solo se refresca a sí mismo, los demás siguen viendo al echado en
     * la lista —y el echado sigue creyendo que está dentro— hasta que reabran.
     *
     * <p>Se llama <b>desde el hilo de E/S</b>, con el clan ya conocido.
     */
    /**
     * Acciones de clan que tocan el bolsillo del jugador.
     *
     * <p>⚠ Va como conjunto y no como un {@code if} con tres {@code ||} porque
     * la lista crece: el día que exista «comprar mejora de clan», olvidarse de
     * añadirla aquí se ve como un saldo que no baja, y eso se lee como un fallo
     * de la economía y no del dibujado.
     */
    private static final java.util.Set<String> MUEVEN_DINERO =
            java.util.Set.of("fundar", "aportar", "sacar");

    /**
     * Refresca a TODOS los afectados por un cambio de clan: pantalla y etiqueta.
     *
     * <h2>⚠⚠ Por qué recibe una lista y no un clan</h2>
     *
     * Porque <b>los afectados por un cambio no son los miembros que quedan</b>.
     * Al echar a alguien, al salirse alguien o al disolverse el clan, la gente
     * que más necesita enterarse es justo la que ya no aparece si preguntas por
     * los miembros. Ese fue el bug: el echado se quedaba con la etiqueta y con la
     * pantalla diciéndole que seguía dentro.
     *
     * <p>La lista la calcula {@code ClanService} —que acaba de hacer el trabajo y
     * sabe a quién ha tocado— y viaja en el {@code Resultado}.
     *
     * <h2>Se hacen las DOS cosas, y por eso están juntas</h2>
     *
     * El paquete arregla la pantalla; la etiqueta arregla el chat, el tablist y
     * lo que se ve sobre la cabeza. Estaban en dos sitios distintos y por eso una
     * se hacía y la otra no. Ahora quien refresca, refresca las dos.
     *
     * <p>⚠ Se llama <b>desde el hilo de E/S</b>: resuelve identificadores.
     */
    private static void refrescarA(net.minecraft.server.MinecraftServer servidor,
                                   java.util.Set<Long> afectados) {
        // Una sola pasada por los conectados. La versión anterior resolvía el
        // identificador de cada jugador DENTRO del bucle de miembros: con 30
        // miembros y 10 conectados eran 300 consultas para refrescar una lista.
        var porId = new java.util.HashMap<Long,
                net.minecraft.server.network.ServerPlayerEntity>();
        for (var jugador : servidor.getPlayerManager().getPlayerList()) {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                if (afectados.contains(id)) {
                    porId.put(id, jugador);
                }
            } catch (Exception e) {
                LunaEternal.LOG.debug("No se pudo resolver a {} para refrescar: {}",
                        jugador.getName().getString(), e.toString());
            }
        }
        // A quien no esté conectado no hay que avisarle: la pantalla pide el
        // estado al abrirse y la etiqueta se pone al entrar (LunaEternal.JOIN).
        for (var entrada : porId.entrySet()) {
            var jugador = entrada.getValue();
            try {
                var carga = componerClan(jugador, entrada.getKey());
                var clan = LunaEternal.clans().clanDe(entrada.getKey());
                String etiqueta = clan == null ? "" : clan.etiqueta();
                char color = clan == null ? 'b' : clan.color();
                servidor.execute(() -> {
                    if (jugador.isRemoved()) {
                        return;
                    }
                    ServerPlayNetworking.send(jugador, carga);
                    net.pokereport.luna.ui.Tablist.aplicarEtiqueta(
                            servidor, jugador, etiqueta, color);
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo refrescar el clan de {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        }
    }


    /**
     * Junta el estado del clan en el paquete. Se llama desde el hilo de E/S.
     *
     * <p>⚠ El historial y el registro <b>solo se mandan si estás dentro</b>. No
     * es ahorro de bytes: son quién metió dinero y quién echó a quién, y eso no
     * es asunto de alguien que solo está mirando la lista de clanes.
     */
    private static EstadoClan componerClan(
            net.minecraft.server.network.ServerPlayerEntity jugador, long playerId)
            throws Exception {
        var svc = LunaEternal.clans();
        var clan = svc.clanDe(playerId);
        var servidor = jugador.getServer();

        List<MiembroClan> miembros = new ArrayList<>();
        List<MovimientoClan> movimientos = new ArrayList<>();
        List<AnotacionClan> registro = new ArrayList<>();
        ClanResumen mio = null;
        String miRol = "";
        long tope = 0;
        long sacadoHoy = 0;

        if (clan != null) {
            var rol = svc.rolDe(playerId);
            miRol = rol == null ? "" : rol.name();
            String lider = "";
            for (var m : svc.miembros(clan.id())) {
                boolean conectado = servidor != null
                        && servidor.getPlayerManager().getPlayer(m.nombre()) != null;
                miembros.add(new MiembroClan(m.playerId(), m.nombre(),
                        m.rol().name(), conectado));
                if (m.playerId() == clan.liderId()) {
                    lider = m.nombre();
                }
            }
            mio = new ClanResumen(clan.id(), clan.nombre(), clan.etiqueta(),
                    String.valueOf(clan.color()), clan.descripcion(), clan.tesoro(),
                    clan.miembros(), lider);

            // ⚠ EL HISTORIAL LO VE TODO EL CLAN, no solo quien manda. Un
            //   registro que solo pueden leer los que podrían robar no vigila a
            //   nadie: lo que lo hace útil es que lo vean los demás.
            int n = net.pokereport.luna.clan.ClanService.HISTORIAL;
            for (var mv : svc.historial(clan.id(), n)) {
                movimientos.add(new MovimientoClan(mv.quien(), mv.delta(),
                        mv.saldoDespues(), mv.motivo(), mv.cuando()));
            }
            for (var an : svc.registro(clan.id(), n)) {
                registro.add(new AnotacionClan(an.quien(), an.aQuien(),
                        an.accion(), an.detalle(), an.cuando()));
            }
            tope = clan.topeOficial();
            sacadoHoy = svc.sacadoHoy(clan.id(), playerId);
        }

        List<InvitacionClan> invs = new ArrayList<>();
        for (var i : svc.invitaciones(playerId)) {
            invs.add(new InvitacionClan(i.clanId(), i.clanNombre(), i.clanEtiqueta(),
                    String.valueOf(i.color()), i.invitadoPor()));
        }

        // ⚠ La lista de OTROS clanes solo se manda si NO tienes clan. Quien ya
        //   está en uno no la necesita, y son 25 filas más en cada refresco de
        //   cada miembro cada vez que alguien aporta al tesoro.
        List<ClanResumen> otros = new ArrayList<>();
        if (clan == null) {
            for (var c : svc.listar(25)) {
                otros.add(new ClanResumen(c.id(), c.nombre(), c.etiqueta(),
                        String.valueOf(c.color()), c.descripcion(), 0, c.miembros(), ""));
            }
        }

        return new EstadoClan(clan == null ? 0 : clan.id(), mio, List.copyOf(miembros),
                miRol, List.copyOf(invs), List.copyOf(otros),
                net.pokereport.luna.clan.ClanService.COSTE_FUNDAR,
                List.copyOf(movimientos), List.copyOf(registro), tope, sacadoHoy);
    }

    private static void enviarIniciales(net.minecraft.server.network.ServerPlayerEntity jugador) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                boolean ya = net.pokereport.luna.starter.StarterService.yaEligio(id);
                List<OpcionInicial> ops = new ArrayList<>(6);
                for (var lista : List.of(
                        net.pokereport.luna.starter.StarterService.KANTO,
                        net.pokereport.luna.starter.StarterService.JOHTO)) {
                    for (var i : lista) {
                        ops.add(new OpcionInicial(i.especie(), i.nombre(), i.region(),
                                i.tipo(), i.consejo()));
                    }
                }
                var carga = new Iniciales(ops, ya);
                jugador.getServer().execute(
                        () -> ServerPlayNetworking.send(jugador, carga));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron enviar los iniciales a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    /**
     * Vuelve a mandarle el arbol.
     *
     * <p>Misma razon que {@code refrescarInicial}: cambiar la base no cambia lo
     * que el cliente ya tiene dibujado.
     */
    public static void refrescarMisiones(net.minecraft.server.network.ServerPlayerEntity jugador) {
        enviarMisiones(jugador);
    }

    private static void enviarMisiones(net.minecraft.server.network.ServerPlayerEntity jugador) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var estados = LunaEternal.quests().allStates(id);

                var completas = new java.util.HashSet<String>();
                for (var e : estados) {
                    if (e.completed()) {
                        completas.add(e.quest().id());
                    }
                }

                List<MisionEstado> salida = new ArrayList<>(estados.size());
                for (var e : estados) {
                    var q = e.quest();
                    String req = q.requires() == null ? "" : q.requires();
                    boolean abierta = req.isEmpty() || completas.contains(req);
                    salida.add(new MisionEstado(
                            q.id(), q.chain(), q.order(), req,
                            q.name(), q.description(),
                            q.objective().type().name(), q.objective().amount(),
                            e.progress(), e.completed(), e.claimed(), abierta,
                            q.rewards().pokedollar(), q.rewards().mark(),
                            q.rewards().path() == null ? "" : q.rewards().path().name(),
                            q.rewards().xp()));
                }
                var carga = new Misiones(salida);
                jugador.getServer().execute(
                        () -> ServerPlayNetworking.send(jugador, carga));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron enviar las misiones a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    private static void enviarCosmeticos(net.minecraft.server.network.ServerPlayerEntity jugador) {
        try {
            long id = LunaEternal.players()
                    .resolve(jugador.getUuid(), jugador.getName().getString());
            var svc = LunaEternal.cosmetics();
            var poseidos = svc.poseidos(id);
            // ⚠ LAS DOS FUENTES DE «LO LLEVA PUESTO» SON DISTINTAS, Y TIENE QUE
            //   SER ASI:
            //
            //     mascota   se lee del POKEMON (sus aspectos). La tabla se
            //               quedaria mintiendo en cuanto el jugador cambie de
            //               equipo o le quiten el disfraz por otra via
            //     jugador   se lee de la TABLA. Una capa o un aura no viven en
            //               ninguna entidad: si no esta anotada, no esta
            //
            //   Preguntarle a la fuente equivocada no da error: da un EQUIPADO
            //   que no se corresponde con nada, que es lo que ya paso con el
            //   Snorlax cuando el equipado de mascotas salia de la tabla.
            var equipados = svc.equipados(id);
            long saldo = LunaEternal.economy().balance(id, Currency.REPORTCOIN);

            List<PiezaCosmetica> piezas = new ArrayList<>();
            for (var p : net.pokereport.luna.cosmetics.Catalogo.todas()) {
                int banderas = 0;
                if (poseidos.contains(p.id())) {
                    banderas |= PiezaCosmetica.POSEIDO;
                }
                boolean puesto = p.esDePokemon()
                        ? net.pokereport.luna.cosmetics.CosmeticsService.loLleva(jugador, p)
                        : p.id().equals(equipados.get(p.categoria()));
                if (puesto) {
                    banderas |= PiezaCosmetica.EQUIPADO;
                }
                // Lo del jugador SIEMPRE se puede equipar: no depende de tener
                // ninguna especie. Lo de Pokemon, solo si hay uno que encaje.
                if (!p.esDePokemon()
                        || net.pokereport.luna.cosmetics.CosmeticsService
                                .primeraRanura(jugador, p) >= 0) {
                    banderas |= PiezaCosmetica.EQUIPABLE;
                }
                piezas.add(new PiezaCosmetica(p.id(), p.categoria(), p.especie(),
                        p.aspecto(), p.precio(), banderas));
            }
            var carga = new Cosmeticos(piezas, saldo);
            jugador.getServer().execute(() -> ServerPlayNetworking.send(jugador, carga));
        } catch (Exception e) {
            LunaEternal.LOG.warn("No se pudo enviar el catalogo de cosmeticos a {}: {}",
                    jugador.getName().getString(), e.toString());
        }
    }
}
