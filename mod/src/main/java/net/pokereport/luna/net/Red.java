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
                        CADENA, Ficha::clan,
                        CADENA, Ficha::trabajo,
                        CADENA, Ficha::division,
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
                PacketCodec.tuple(CADENA, VozPokedex::especie,
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
                        CADENA, PiezaCosmetica::id,
                        CADENA, PiezaCosmetica::categoria,
                        CADENA, PiezaCosmetica::especie,
                        CADENA, PiezaCosmetica::aspecto,
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
                        CADENA, AccionCosmetico::id,
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
                        CADENA, LlevaPuesto::categoria,
                        CADENA, LlevaPuesto::cosmetico,
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
                        CADENA, AvisoLogro::titulo,
                        CADENA, AvisoLogro::detalle,
                        CADENA, AvisoLogro::objeto,
                        AvisoLogro::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * {@code CADENA}, pero que <b>tolera nulos</b>.
     *
     * <h2>⚠⚠⚠ HAY DOS FAMILIAS DE CODIFICADORES Y ESTA ES LA SEGUNDA</h2>
     *
     * {@link #cad} arregló los {@code escribir} escritos a mano. Pero la mayoría
     * de los paquetes se declaran con {@code PacketCodec.tuple(...)}, y esos no
     * pasan por ningún método nuestro: van directos a {@code CADENA},
     * que <b>lanza con un nulo exactamente igual</b>.
     *
     * <p>Yo arreglé la primera familia y di el fallo por cerrado. <b>Lo destapó
     * la comprobación nueva</b>, que codifica un paquete de verdad con todo a
     * nulo: falló en {@code EstadoMercado}, que es de los de {@code tuple}.
     *
     * <p>Es la razón de ser de esa prueba. Un repaso a ojo encuentra lo que
     * buscas —«los writeString»— y no lo que no sabías que existía.
     */
    private static final PacketCodec<io.netty.buffer.ByteBuf, String> CADENA =
            new PacketCodec<>() {
                @Override
                public String decode(io.netty.buffer.ByteBuf buf) {
                    return PacketCodecs.STRING.decode(buf);
                }

                @Override
                public void encode(io.netty.buffer.ByteBuf buf, String valor) {
                    PacketCodecs.STRING.encode(buf, valor == null ? "" : valor);
                }
            };

    /**
     * Escribe una cadena que <b>puede ser nula</b>.
     *
     * <h2>⚠⚠⚠ UN {@code writeString(null)} ECHA AL JUGADOR DEL SERVIDOR</h2>
     *
     * Y lo hace en el peor sitio posible: <b>al codificar el paquete</b>, ya
     * fuera del hilo del servidor. El jugador ve
     * <i>«Failed to encode packet 'clientbound/custom_payload'»</i> y se le
     * corta la conexión. No es un aviso, no es un hueco en una pantalla: es una
     * desconexión, y el mensaje <b>no dice qué campo</b>.
     *
     * <p>Pasó de verdad el 2026-08-25: un Pokémon publicado <b>sin mote</b>
     * dejaba {@code display_name} a nulo, y abrir el GTS echaba del servidor a
     * quien lo abriera. Lo que hace grave el fallo no es la columna: es que
     * <b>cualquiera</b> de los 34 campos de texto del protocolo podía hacerlo, y
     * el remedio se había escrito a mano <b>solo en dos</b>.
     *
     * <p>Por eso esto no es una comprobación más: es que <b>el codificador ya no
     * puede escribir un nulo</b>. Un campo nuevo que se olvide de sanear enseña
     * una cadena vacía, que es un fallo visible y arreglable — no una patada.
     *
     * <p>⚠ Lo de arriba <b>no sustituye</b> a sanear en origen: un nulo que
     * llega hasta aquí sigue siendo un dato mal leído. Esto es la red debajo del
     * alambre, no el alambre.
     */
    private static void cad(RegistryByteBuf buf, String s) {
        buf.writeString(s == null ? "" : s);
    }

    /**
     * Un ejemplar publicado en el GTS.
     *
     * <p>⚠ Los IVs y los EVs viajan como SEIS numeros en un orden FIJO (PS, At,
     * Def, SpA, SpD, Vel). Ese orden es parte del formato: cambiarlo convertiria
     * el Ataque de todo el mundo en Defensa, en la base y en la pantalla, sin un
     * solo error.
     */
    public record EjemplarGts(long id, String vendedor, String especie, String mote,
                              int nivel, boolean shiny, String genero,
                              String naturaleza, String habilidad, String tera,
                              String rareza, List<Integer> ivs, List<Integer> evs,
                              long precio, long estimado, long expira) {

        static void escribir(RegistryByteBuf buf, EjemplarGts e) {
            buf.writeVarLong(e.id);
            cad(buf, e.vendedor);
            cad(buf, e.especie);
            cad(buf, e.mote);
            buf.writeVarInt(e.nivel);
            buf.writeBoolean(e.shiny);
            cad(buf, e.genero);
            cad(buf, e.naturaleza);
            cad(buf, e.habilidad);
            cad(buf, e.tera);
            cad(buf, e.rareza);
            seis(buf, e.ivs);
            seis(buf, e.evs);
            buf.writeVarLong(e.precio);
            buf.writeVarLong(e.estimado);
            buf.writeVarLong(e.expira);
        }

        static EjemplarGts leer(RegistryByteBuf buf) {
            return new EjemplarGts(buf.readVarLong(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readVarInt(),
                    buf.readBoolean(), buf.readString(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readString(),
                    leerSeis(buf), leerSeis(buf),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
        }
    }

    /** Uno de los tuyos, que puedes publicar. `donde` es EQUIPO o PC. */
    public record MioGts(String uuid, String especie, String mote, int nivel,
                         boolean shiny, String donde, List<Integer> ivs,
                         List<Integer> evs, String naturaleza, String habilidad,
                         long estimado) {

        static void escribir(RegistryByteBuf buf, MioGts m) {
            cad(buf, m.uuid);
            cad(buf, m.especie);
            cad(buf, m.mote);
            buf.writeVarInt(m.nivel);
            buf.writeBoolean(m.shiny);
            cad(buf, m.donde);
            seis(buf, m.ivs);
            seis(buf, m.evs);
            cad(buf, m.naturaleza);
            cad(buf, m.habilidad);
            buf.writeVarLong(m.estimado);
        }

        static MioGts leer(RegistryByteBuf buf) {
            return new MioGts(buf.readString(), buf.readString(), buf.readString(),
                    buf.readVarInt(), buf.readBoolean(), buf.readString(),
                    leerSeis(buf), leerSeis(buf), buf.readString(),
                    buf.readString(), buf.readVarLong());
        }
    }

    /**
     * ⚠ Seis y solo seis. Se escribe la cuenta igual, para que el formato sea
     * el mismo que el de cualquier otra lista y un cambio futuro no obligue a
     * tocar el lector.
     */
    private static void seis(RegistryByteBuf buf, List<Integer> xs) {
        buf.writeVarInt(6);
        for (int i = 0; i < 6; i++) {
            buf.writeVarInt(i < xs.size() ? Math.max(0, xs.get(i)) : 0);
        }
    }

    private static List<Integer> leerSeis(RegistryByteBuf buf) {
        int n = buf.readVarInt();
        List<Integer> xs = new ArrayList<>(6);
        for (int i = 0; i < n; i++) {
            int v = buf.readVarInt();
            if (i < 6) {
                xs.add(v);
            }
        }
        while (xs.size() < 6) {
            xs.add(0);
        }
        return List.copyOf(xs);
    }

    /**
     * «Dame el GTS», con los filtros puestos.
     *
     * <p>⚠ Los filtros viajan como TEXTO y el servidor los interpreta. Un cero
     * significaria «minimo 0», que no es lo mismo que «no filtres por esto» --
     * el mismo problema que «no lo se» y «tienes cero» del saldo del Pad. Con
     * cadena vacia la diferencia se mantiene.
     */
    public record PedirGts(String texto, String vendedor, String nivelMin,
                           String nivelMax, String precioMin, String precioMax,
                           List<Integer> ivMin, List<Integer> evMin,
                           String shiny, String orden) implements CustomPayload {
        public static final Id<PedirGts> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_gts"));
        public static final PacketCodec<RegistryByteBuf, PedirGts> CODEC =
                PacketCodec.ofStatic((buf, p) -> {
                    cad(buf, p.texto);
                    cad(buf, p.vendedor);
                    cad(buf, p.nivelMin);
                    cad(buf, p.nivelMax);
                    cad(buf, p.precioMin);
                    cad(buf, p.precioMax);
                    seis(buf, p.ivMin);
                    seis(buf, p.evMin);
                    cad(buf, p.shiny);
                    cad(buf, p.orden);
                }, buf -> new PedirGts(buf.readString(), buf.readString(),
                        buf.readString(), buf.readString(), buf.readString(),
                        buf.readString(), leerSeis(buf), leerSeis(buf),
                        buf.readString(), buf.readString()));

        /** Sin filtros. */
        public static PedirGts vacio() {
            var ceros = List.of(0, 0, 0, 0, 0, 0);
            return new PedirGts("", "", "", "", "", "", ceros, ceros, "", "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** El GTS entero: lo que hay, lo tuyo publicado y lo tuyo publicable. */
    public record EstadoGts(List<EjemplarGts> ofertas, List<EjemplarGts> mias,
                            List<MioGts> disponibles, long saldo)
            implements CustomPayload {
        public static final Id<EstadoGts> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_gts"));
        public static final PacketCodec<RegistryByteBuf, EstadoGts> CODEC =
                PacketCodec.ofStatic(EstadoGts::escribir, EstadoGts::leer);

        private static void escribir(RegistryByteBuf buf, EstadoGts e) {
            buf.writeVarInt(e.ofertas.size());
            for (EjemplarGts x : e.ofertas) {
                EjemplarGts.escribir(buf, x);
            }
            buf.writeVarInt(e.mias.size());
            for (EjemplarGts x : e.mias) {
                EjemplarGts.escribir(buf, x);
            }
            buf.writeVarInt(e.disponibles.size());
            for (MioGts m : e.disponibles) {
                MioGts.escribir(buf, m);
            }
            buf.writeVarLong(e.saldo);
        }

        private static EstadoGts leer(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<EjemplarGts> ofertas = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ofertas.add(EjemplarGts.leer(buf));
            }
            int nm = buf.readVarInt();
            List<EjemplarGts> mias = new ArrayList<>(nm);
            for (int i = 0; i < nm; i++) {
                mias.add(EjemplarGts.leer(buf));
            }
            int nd = buf.readVarInt();
            List<MioGts> disp = new ArrayList<>(nd);
            for (int i = 0; i < nd; i++) {
                disp.add(MioGts.leer(buf));
            }
            return new EstadoGts(List.copyOf(ofertas), List.copyOf(mias),
                    List.copyOf(disp), buf.readVarLong());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Comprar, publicar o retirar un ejemplar.
     *
     * <p>⚠ El precio VIAJA --lo pone el vendedor, de eso va un mercado-- pero al
     * COMPRAR no se manda: el servidor cobra el que dice SU fila. Si el precio
     * de compra viniera del cliente, un cliente modificado compraria un shiny
     * por 1 (P6).
     */
    public record AccionGts(String accion, long listado, String uuid,
                            long precio, int horas) implements CustomPayload {
        public static final Id<AccionGts> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_gts"));
        public static final PacketCodec<RegistryByteBuf, AccionGts> CODEC =
                PacketCodec.tuple(
                        CADENA, AccionGts::accion,
                        PacketCodecs.VAR_LONG, AccionGts::listado,
                        CADENA, AccionGts::uuid,
                        PacketCodecs.VAR_LONG, AccionGts::precio,
                        PacketCodecs.VAR_INT, AccionGts::horas,
                        AccionGts::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Dame el escaparate de objetos».
     *
     * <h2>⚠⚠ ESTO SUSTITUYE AL LIBRO DE ORDENES, Y ES UNA DECISION</h2>
     *
     * El libro (compras y ventas cruzandose por precio) es lo correcto para un
     * mercado con mucha gente: da liquidez y un precio de verdad. Con doce
     * personas <b>no cruza nada</b> — pones una orden de compra y se queda ahi
     * hasta que alguien pase por casualidad. El usuario lo dijo con otras
     * palabras: <i>«se pierde uno comprando alli»</i>.
     *
     * <p>Un escaparate es peor en teoria y muchisimo mejor de usar: cada uno
     * pone lo suyo con su precio y quien quiera lo compra de una. Y es
     * <b>exactamente el mismo mecanismo que los Pokemon</b>, asi que las dos
     * mitades del mercado se comportan igual — que es la mitad de lo que hacia
     * que costara entenderlo.
     *
     * <p>⚠ {@code MarketService} NO SE BORRA: sigue escrito, probado y con sus
     * comprobaciones. Lo que cambia es por donde entra el jugador.
     */
    public record PedirMercado(String texto, String orden) implements CustomPayload {
        public static final Id<PedirMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_mercado"));
        public static final PacketCodec<RegistryByteBuf, PedirMercado> CODEC =
                PacketCodec.tuple(CADENA, PedirMercado::texto,
                        CADENA, PedirMercado::orden,
                        PedirMercado::new);

        public static PedirMercado vacio() {
            return new PedirMercado("", "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Una oferta del escaparate.
     *
     * <p>⚠ Aqui SI va el vendedor, al reves que en el libro de ordenes. En un
     * libro el precio es anonimo a proposito —saber que la unica venta barata es
     * de fulano invita a negociar por fuera—, pero un escaparate <b>es</b> un
     * puesto con dueño: ver de quien compras es la mitad de la confianza.
     */
    public record OfertaObj(long id, String vendedor, String item, String nombre,
                            int cantidad, long precio, long expira) {
        static void escribir(RegistryByteBuf buf, OfertaObj o) {
            buf.writeVarLong(o.id);
            cad(buf, o.vendedor);
            cad(buf, o.item);
            cad(buf, o.nombre);
            buf.writeVarInt(o.cantidad);
            buf.writeVarLong(o.precio);
            buf.writeVarLong(o.expira);
        }

        static OfertaObj leer(RegistryByteBuf buf) {
            return new OfertaObj(buf.readVarLong(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarLong());
        }

        /** Lo unico comparable entre una pila de 64 y una de 1. */
        public long porUnidad() {
            return precio / Math.max(1, cantidad);
        }
    }

    /** Un objeto del inventario que se puede poner a la venta. */
    public record MioObj(String item, String nombre, int cantidad) {
        public static final PacketCodec<RegistryByteBuf, MioObj> CODEC =
                PacketCodec.tuple(
                        CADENA, MioObj::item,
                        CADENA, MioObj::nombre,
                        PacketCodecs.VAR_INT, MioObj::cantidad,
                        MioObj::new);
    }

    /**
     * Todo lo que dibuja el escaparate de objetos.
     *
     * <p>⚠ `disponibles` sale del INVENTARIO y solo de ahi (la barra rapida
     * cuenta: son los nueve primeros huecos de `main`). Es lo que dijo el
     * usuario, y ademas es lo que hace que la custodia se pueda cumplir: no se
     * puede retener lo que no esta a mano.
     */
    public record EstadoMercado(List<OfertaObj> ofertas, List<OfertaObj> mias,
                                List<MioObj> disponibles, long saldo)
            implements CustomPayload {
        public static final Id<EstadoMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_mercado"));
        public static final PacketCodec<RegistryByteBuf, EstadoMercado> CODEC =
                PacketCodec.ofStatic(EstadoMercado::escribir, EstadoMercado::leer);

        private static void escribir(RegistryByteBuf buf, EstadoMercado e) {
            buf.writeVarInt(e.ofertas.size());
            for (OfertaObj o : e.ofertas) {
                OfertaObj.escribir(buf, o);
            }
            buf.writeVarInt(e.mias.size());
            for (OfertaObj o : e.mias) {
                OfertaObj.escribir(buf, o);
            }
            buf.writeVarInt(e.disponibles.size());
            for (MioObj m : e.disponibles) {
                MioObj.CODEC.encode(buf, m);
            }
            buf.writeVarLong(e.saldo);
        }

        private static EstadoMercado leer(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<OfertaObj> ofertas = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ofertas.add(OfertaObj.leer(buf));
            }
            int nm = buf.readVarInt();
            List<OfertaObj> mias = new ArrayList<>(nm);
            for (int i = 0; i < nm; i++) {
                mias.add(OfertaObj.leer(buf));
            }
            int nd = buf.readVarInt();
            List<MioObj> disp = new ArrayList<>(nd);
            for (int i = 0; i < nd; i++) {
                disp.add(MioObj.CODEC.decode(buf));
            }
            return new EstadoMercado(List.copyOf(ofertas), List.copyOf(mias),
                    List.copyOf(disp), buf.readVarLong());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Publicar, comprar o retirar una oferta de objetos.
     *
     * <p>⚠ EL PRECIO VIAJA —lo pone el vendedor, de eso va un mercado— pero al
     * COMPRAR no: ahi solo viaja el identificador de la oferta y el precio sale
     * de la fila. Si el precio de compra viniera del cliente, un cliente
     * modificado compraria por 1 (P6).
     */
    public record AccionMercado(String accion, long listado, String item,
                                int cantidad, long precio, int horas)
            implements CustomPayload {
        public static final Id<AccionMercado> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_mercado"));
        public static final PacketCodec<RegistryByteBuf, AccionMercado> CODEC =
                PacketCodec.tuple(
                        CADENA, AccionMercado::accion,
                        PacketCodecs.VAR_LONG, AccionMercado::listado,
                        CADENA, AccionMercado::item,
                        PacketCodecs.VAR_INT, AccionMercado::cantidad,
                        PacketCodecs.VAR_LONG, AccionMercado::precio,
                        PacketCodecs.VAR_INT, AccionMercado::horas,
                        AccionMercado::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    /** «Dame las cazas», al abrir la pantalla. */
    public record PedirCazas() implements CustomPayload {
        public static final Id<PedirCazas> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "pedir_cazas"));
        public static final PacketCodec<RegistryByteBuf, PedirCazas> CODEC =
                PacketCodec.unit(new PedirCazas());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Un objetivo de caza o de crianza.
     *
     * <p>⚠ Viaja el <b>identificador</b> del objeto del premio, no su nombre:
     * el nombre lo pone el cliente, que sí sabe en qué idioma juega su dueño.
     * Ver {@link #cad} y la regla de idioma de CLAUDE.md.
     *
     * <p>⚠ Y viaja el premio <b>tal y como está guardado en la fila</b>. La
     * pantalla lo enseña al pasar el ratón y el servidor paga eso mismo: si
     * cada uno mirara su propia tabla, lo enseñado y lo cobrado podrían
     * separarse sin que nada fallara.
     */
    public record ObjetivoCaza(long id, String tipo, String especie,
                               int necesarios, int hechos, boolean cobrado,
                               int rareza, long dolar, long marca,
                               String objeto, int cantidad,
                               String objeto2, int cantidad2) {

        static void escribir(RegistryByteBuf buf, ObjetivoCaza o) {
            buf.writeVarLong(o.id);
            cad(buf, o.tipo);
            cad(buf, o.especie);
            buf.writeVarInt(o.necesarios);
            buf.writeVarInt(o.hechos);
            buf.writeBoolean(o.cobrado);
            buf.writeVarInt(o.rareza);
            buf.writeVarLong(o.dolar);
            buf.writeVarLong(o.marca);
            cad(buf, o.objeto);
            buf.writeVarInt(o.cantidad);
            cad(buf, o.objeto2);
            buf.writeVarInt(o.cantidad2);
        }

        static ObjetivoCaza leer(RegistryByteBuf buf) {
            return new ObjetivoCaza(buf.readVarLong(), buf.readString(),
                    buf.readString(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarLong(), buf.readString(), buf.readVarInt(),
                    buf.readString(), buf.readVarInt());
        }

        public boolean completo() {
            return hechos >= necesarios;
        }
    }

    /**
     * Las cazas del ciclo en curso.
     *
     * <p>⚠ {@code terminaEn} es un instante absoluto (epoch en milisegundos) y
     * <b>no «faltan N horas»</b>. Un número de horas se queda viejo en cuanto
     * pasa un minuto, y la pantalla estaría contando desde un valor que ya no
     * es cierto. Con el instante, el reloj lo lleva el cliente y siempre acierta.
     */
    public record EstadoCazas(List<ObjetivoCaza> objetivos, long terminaEn,
                              long saldo) implements CustomPayload {
        public static final Id<EstadoCazas> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "estado_cazas"));
        public static final PacketCodec<RegistryByteBuf, EstadoCazas> CODEC =
                PacketCodec.ofStatic(EstadoCazas::escribir, EstadoCazas::leer);

        private static void escribir(RegistryByteBuf buf, EstadoCazas e) {
            buf.writeVarInt(e.objetivos.size());
            for (ObjetivoCaza o : e.objetivos) {
                ObjetivoCaza.escribir(buf, o);
            }
            buf.writeVarLong(e.terminaEn);
            buf.writeVarLong(e.saldo);
        }

        private static EstadoCazas leer(RegistryByteBuf buf) {
            int n = buf.readVarInt();
            List<ObjetivoCaza> xs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                xs.add(ObjetivoCaza.leer(buf));
            }
            return new EstadoCazas(List.copyOf(xs), buf.readVarLong(),
                    buf.readVarLong());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Cóbrame este objetivo».
     *
     * <p>⚠ Solo viaja el identificador. El premio lo saca el servidor de su
     * fila, y comprueba ahí mismo que esté completo y sin cobrar (P6).
     */
    public record AccionCaza(long objetivo) implements CustomPayload {
        public static final Id<AccionCaza> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_caza"));
        public static final PacketCodec<RegistryByteBuf, AccionCaza> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_LONG, AccionCaza::objetivo,
                        AccionCaza::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Abreme la mochila».
     *
     * <p>⚠ NO lleva cuantas filas quiere: eso lo decide el servidor mirando el
     * rango guardado. Si viniera del cliente, un cliente modificado pediria
     * siete (P6).
     */
    public record AbrirMochila() implements CustomPayload {
        public static final Id<AbrirMochila> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "abrir_mochila"));
        public static final PacketCodec<RegistryByteBuf, AbrirMochila> CODEC =
                PacketCodec.unit(new AbrirMochila());

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
                        CADENA, EntradaTienda::item,
                        CADENA, EntradaTienda::etiqueta,
                        PacketCodecs.VAR_LONG, EntradaTienda::compra,
                        PacketCodecs.VAR_LONG, EntradaTienda::venta,
                        CADENA, EntradaTienda::moneda,
                        EntradaTienda::new);
    }

    public record CategoriaTienda(String id, String nombre, String icono,
                                  String descripcion, List<EntradaTienda> entradas) {
        public static final PacketCodec<RegistryByteBuf, CategoriaTienda> CODEC =
                PacketCodec.tuple(
                        CADENA, CategoriaTienda::id,
                        CADENA, CategoriaTienda::nombre,
                        CADENA, CategoriaTienda::icono,
                        CADENA, CategoriaTienda::descripcion,
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
                        CADENA, AccionTienda::categoria,
                        CADENA, AccionTienda::item,
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
                        CADENA, AccionClan::accion,
                        CADENA, AccionClan::texto,
                        CADENA, AccionClan::texto2,
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
                        CADENA, MiembroClan::nombre,
                        CADENA, MiembroClan::rol,
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
            cad(buf, c.nombre);
            cad(buf, c.etiqueta);
            cad(buf, c.color);
            cad(buf, c.descripcion);
            buf.writeVarLong(c.tesoro);
            buf.writeVarInt(c.miembros);
            cad(buf, c.lider);
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
                        CADENA, InvitacionClan::nombre,
                        CADENA, InvitacionClan::etiqueta,
                        CADENA, InvitacionClan::color,
                        CADENA, InvitacionClan::invitadoPor,
                        InvitacionClan::new);
    }

    /** Un movimiento del tesoro. `delta` con signo: + entra, - sale. */
    public record MovimientoClan(String quien, long delta, long saldoDespues,
                                 String motivo, long cuando) {
        public static final PacketCodec<RegistryByteBuf, MovimientoClan> CODEC =
                PacketCodec.tuple(
                        CADENA, MovimientoClan::quien,
                        PacketCodecs.VAR_LONG, MovimientoClan::delta,
                        PacketCodecs.VAR_LONG, MovimientoClan::saldoDespues,
                        CADENA, MovimientoClan::motivo,
                        PacketCodecs.VAR_LONG, MovimientoClan::cuando,
                        MovimientoClan::new);
    }

    /** Una linea del registro de acciones. */
    public record AnotacionClan(String quien, String aQuien, String accion,
                                String detalle, long cuando) {
        public static final PacketCodec<RegistryByteBuf, AnotacionClan> CODEC =
                PacketCodec.tuple(
                        CADENA, AnotacionClan::quien,
                        CADENA, AnotacionClan::aQuien,
                        CADENA, AnotacionClan::accion,
                        CADENA, AnotacionClan::detalle,
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
            cad(buf, e.miRol);
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
                PacketCodec.tuple(CADENA, ElegirInicial::especie,
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
                        CADENA, OpcionInicial::especie,
                        CADENA, OpcionInicial::nombre,
                        CADENA, OpcionInicial::region,
                        CADENA, OpcionInicial::tipo,
                        CADENA, OpcionInicial::consejo,
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
                        CADENA, PokemonCura::especie,
                        CADENA, PokemonCura::apodo,
                        PacketCodecs.VAR_INT, PokemonCura::nivel,
                        PacketCodecs.VAR_INT, PokemonCura::vida,
                        PacketCodecs.VAR_INT, PokemonCura::vidaMax,
                        CADENA, PokemonCura::estado,
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
                PacketCodec.tuple(CADENA, ReclamarMision::id,
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
            cad(buf, m.id);
            cad(buf, m.cadena);
            buf.writeVarInt(m.orden);
            cad(buf, m.requiere);
            cad(buf, m.nombre);
            cad(buf, m.descripcion);
            cad(buf, m.objetivo);
            buf.writeVarLong(m.meta);
            buf.writeVarLong(m.progreso);
            buf.writeBoolean(m.completada);
            buf.writeBoolean(m.cobrada);
            buf.writeBoolean(m.desbloqueada);
            buf.writeVarLong(m.plata);
            buf.writeVarLong(m.marcas);
            cad(buf, m.via);
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
                        CADENA, ViaEstado::id,
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
        // ⚠⚠⚠ EL TIPO DE CONTENEDOR DE LA MOCHILA VA AQUI, en el entrypoint
        //     `main`, que es el UNICO que corre en los dos lados. Vive en un
        //     registro QUE SE SINCRONIZA: el servidor abre el contenedor
        //     mandando un numero y el cliente lo busca en SU tabla. Registrarlo
        //     solo en un lado descuadra las dos tablas y ECHA AL JUGADOR con un
        //     error que no nombra la mochila -- la misma familia que los 5.687
        //     bloques de desfase que ya estan documentados.
        net.pokereport.luna.backpack.Registro.registrar();

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
        PayloadTypeRegistry.playC2S().register(PedirGts.ID, PedirGts.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionGts.ID, AccionGts.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoGts.ID, EstadoGts.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirMercado.ID, PedirMercado.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionMercado.ID, AccionMercado.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoMercado.ID, EstadoMercado.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirCazas.ID, PedirCazas.CODEC);
        PayloadTypeRegistry.playC2S().register(AbrirMochila.ID, AbrirMochila.CODEC);
        PayloadTypeRegistry.playC2S().register(AccionCaza.ID, AccionCaza.CODEC);
        PayloadTypeRegistry.playS2C().register(EstadoCazas.ID, EstadoCazas.CODEC);
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

        ServerPlayNetworking.registerGlobalReceiver(PedirGts.ID, (carga, ctx) ->
                enviarGts(ctx.player(), carga));

        ServerPlayNetworking.registerGlobalReceiver(AccionGts.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            var servidor = jugador.getServer();
            if (servidor == null || LunaEternal.gts() == null) {
                return;
            }
            switch (carga.accion()) {
                case "publicar" -> publicarPokemon(jugador, carga);
                case "comprar" -> comprarPokemon(jugador, carga);
                case "retirar" -> retirarPokemon(jugador, carga);
                default -> { }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirMercado.ID, (carga, ctx) ->
                enviarMercado(ctx.player(), carga));

        ServerPlayNetworking.registerGlobalReceiver(AccionMercado.ID, (carga, ctx) -> {
            switch (carga.accion()) {
                case "vender" -> venderObjeto(ctx.player(), carga);
                case "comprar" -> comprarObjeto(ctx.player(), carga);
                case "retirar" -> retirarObjeto(ctx.player(), carga);
                default -> { }
            }
        });


        ServerPlayNetworking.registerGlobalReceiver(PedirCazas.ID, (carga, ctx) ->
                enviarCazas(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(AbrirMochila.ID, (carga, ctx) ->
                net.pokereport.luna.backpack.Registro.abrir(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(AccionCaza.ID, (carga, ctx) ->
                cobrarCaza(ctx.player(), carga.objetivo()));

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
     * PUBLICAR UN POKEMON.
     *
     * <h2>⚠⚠⚠ El orden importa y no es el intuitivo</h2>
     *
     * <ol>
     *   <li>se lee y se <b>serializa</b> el Pokémon, en el hilo del servidor;</li>
     *   <li>se <b>retira</b> del equipo o del PC — eso es la custodia;</li>
     *   <li>y solo entonces se escribe la fila, ya en el hilo de E/S.</li>
     * </ol>
     *
     * <p>Si se escribiera la fila primero y la retirada fallara, habría un
     * Pokémon publicado <b>que sigue en el PC de su dueño</b>: podría
     * evolucionarlo, moverlo o soltarlo mientras se vende. Es el vector de
     * duplicación número uno de todos los mercados de Pokémon mal hechos.
     *
     * <p>⚠ Y si la fila NO sale, <b>el Pokémon vuelve</b>. Igual que con los
     * objetos: la custodia de algo vivo no puede vivir en una transacción de
     * base de datos, así que se deshace a mano.
     */
    private static void publicarPokemon(
            net.minecraft.server.network.ServerPlayerEntity jugador, AccionGts carga) {
        var servidor = jugador.getServer();
        var pokemon = net.pokereport.luna.market.PokemonMercado
                .buscar(jugador, carga.uuid());
        if (pokemon == null) {
            jugador.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cEse Pokémon ya no está en tu equipo ni en tu PC."), true);
            return;
        }
        // ⚠ NO SE PUEDE VENDER EL ULTIMO. Quedarse sin ninguno deja al jugador
        //   sin poder hacer nada -- y la pantalla del inicial no se le va a
        //   volver a abrir, porque ya eligió. Un mercado no puede dejar a
        //   alguien fuera del juego.
        int cuantos = net.pokereport.luna.market.PokemonMercado
                .disponibles(jugador).size();
        if (cuantos <= 1) {
            jugador.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cNo puedes vender tu último Pokémon."), true);
            return;
        }

        var resumen = net.pokereport.luna.market.PokemonMercado
                .disponibles(jugador).stream()
                .filter(r -> r.uuid().equals(carga.uuid())).findFirst().orElse(null);
        if (resumen == null) {
            return;
        }

        byte[] payload;
        try {
            var nbt = pokemon.saveToNBT(jugador.getRegistryManager(),
                    new net.minecraft.nbt.NbtCompound());
            var salida = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(nbt, salida);
            payload = salida.toByteArray();
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo serializar el Pokemon para el GTS", e);
            jugador.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cNo se pudo preparar ese Pokémon."), true);
            return;
        }

        if (!net.pokereport.luna.market.PokemonMercado.retirar(jugador, pokemon)) {
            jugador.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cNo se pudo retirar ese Pokémon."), true);
            return;
        }

        final byte[] datos = payload;
        LunaEternal.submit(() -> {
            boolean devolver = true;
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                long estimado = LunaEternal.tasador().tasar(resumen.ficha()).estimado();
                var r = LunaEternal.gts().publicarPokemon(id, datos, resumen,
                        carga.precio(), estimado, carga.horas());
                devolver = !r.ok();
                servidor.execute(() -> jugador.sendMessage(
                        net.minecraft.text.Text.literal(r.message()), true));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo publicar el Pokemon", e);
            } finally {
                if (devolver) {
                    // ⚠ Vuelve al EQUIPO, no al PC: si vino del PC y el PC
                    //   estuviera lleno, `offer` falla y se perdería. El equipo
                    //   tenía sitio hace un segundo, porque de ahí salió o
                    //   porque el jugador tiene menos de seis.
                    servidor.execute(() -> {
                        try {
                            com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage()
                                    .getParty(jugador).add(pokemon);
                        } catch (Throwable t) {
                            LunaEternal.LOG.error("NO SE PUDO DEVOLVER un Pokemon "
                                    + "retirado para el GTS: {}", carga.uuid(), t);
                        }
                    });
                }
                enviarGts(jugador, PedirGts.vacio());
                enviarSaldo(jugador);
            }
        });
    }

    /**
     * COMPRAR UN EJEMPLAR.
     *
     * <p>⚠ El precio NO viaja en el paquete: lo cobra el servidor mirando SU
     * fila. Si viniera del cliente, uno modificado compraría un shiny por 1 (P6).
     *
     * <p>⚠⚠ Y el vendedor se entera: {@code GtsService.buy} ya deja el dinero
     * abonado, pero su PANTALLA sigue enseñando el ejemplar. Es la lección de
     * los clanes -- el estado no es de quien lo mira.
     */
    private static void comprarPokemon(
            net.minecraft.server.network.ServerPlayerEntity jugador, AccionGts carga) {
        var servidor = jugador.getServer();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var r = LunaEternal.gts().buy(id, carga.listado());
                servidor.execute(() -> jugador.sendMessage(
                        net.minecraft.text.Text.literal(r.message()), true));
                if (r.ok() && r.payload() != null) {
                    // La entrega usa el camino de siempre, que ya sabe qué hacer
                    // si el equipo está lleno o si el jugador se va a mitad.
                    net.pokereport.luna.gts.GtsDelivery.claimAll(jugador, id);
                }
                enviarSaldo(jugador);
                enviarGts(jugador, PedirGts.vacio());
                refrescarGtsATodos(servidor);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo comprar el listado {}: {}",
                        carga.listado(), e.toString());
            }
        });
    }

    /** RETIRAR lo tuyo. El ejemplar vuelve por el camino de entrega diferida. */
    private static void retirarPokemon(
            net.minecraft.server.network.ServerPlayerEntity jugador, AccionGts carga) {
        var servidor = jugador.getServer();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var r = LunaEternal.gts().cancel(id, carga.listado());
                servidor.execute(() -> jugador.sendMessage(
                        net.minecraft.text.Text.literal(r.message()), true));
                if (r.ok()) {
                    net.pokereport.luna.gts.GtsDelivery.claimAll(jugador, id);
                }
                enviarGts(jugador, PedirGts.vacio());
                refrescarGtsATodos(servidor);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo retirar el listado {}: {}",
                        carga.listado(), e.toString());
            }
        });
    }

    /**
     * Refresca el GTS a todos los que estén dentro.
     *
     * <p>⚠⚠ Aquí NO se puede usar el patrón de «afectados» de los clanes, y hay
     * que decir por qué: en un escaparate <b>cualquiera</b> puede estar mirando
     * el ejemplar que acaba de venderse, no solo el comprador y el vendedor. La
     * lista de afectados sería «todo el mundo», así que se manda a todos y ya.
     *
     * <p>Con diez jugadores eso son diez paquetes; si algún día son doscientos,
     * habrá que mandarlo solo a quien tenga la pantalla abierta — y para eso
     * hará falta que el cliente avise al abrirla y al cerrarla.
     */
    private static void refrescarGtsATodos(net.minecraft.server.MinecraftServer servidor) {
        servidor.execute(() -> {
            for (var otro : servidor.getPlayerManager().getPlayerList()) {
                enviarGts(otro, PedirGts.vacio());
            }
        });
    }

    /** Manda el GTS: lo que hay, lo tuyo publicado y lo tuyo publicable. */
    private static void enviarGts(
            net.minecraft.server.network.ServerPlayerEntity jugador, PedirGts filtros) {
        var servidor = jugador.getServer();
        if (servidor == null || LunaEternal.gts() == null) {
            return;
        }
        // ⚠ Los Pokémon del jugador se leen AQUI, en el hilo del servidor: un
        //   almacén de Cobblemon no se toca desde el executor de E/S.
        final var mios = net.pokereport.luna.market.PokemonMercado.disponibles(jugador);

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());

                var f = new net.pokereport.luna.gts.GtsService.Filtro(
                        vacio(filtros.texto()), vacio(filtros.vendedor()),
                        entero(filtros.nivelMin()), entero(filtros.nivelMax()),
                        largo(filtros.precioMin()), largo(filtros.precioMax()),
                        aArray(filtros.ivMin()), aArray(filtros.evMin()),
                        "1".equals(filtros.shiny()) ? Boolean.TRUE : null,
                        null, null, null, filtros.orden());

                List<EjemplarGts> ofertas = new ArrayList<>();
                for (var e : LunaEternal.gts().buscar(f, 100)) {
                    ofertas.add(aPaquete(e));
                }
                List<EjemplarGts> mias = new ArrayList<>();
                for (var e : LunaEternal.gts().misEjemplares(id)) {
                    mias.add(aPaquete(e));
                }
                List<MioGts> disponibles = new ArrayList<>();
                for (var m : mios) {
                    long est = LunaEternal.tasador().tasar(m.ficha()).estimado();
                    disponibles.add(new MioGts(m.uuid(), m.especie(), m.mote(),
                            m.nivel(), m.shiny(), m.donde().name(),
                            aLista(m.ivs()), aLista(m.evs()), m.naturaleza(),
                            m.habilidad(), est));
                }
                long saldo = LunaEternal.economy().balance(id, Currency.POKEDOLLAR);
                var carga = new EstadoGts(List.copyOf(ofertas), List.copyOf(mias),
                        List.copyOf(disponibles), saldo);
                servidor.execute(() -> {
                    if (!jugador.isRemoved()) {
                        ServerPlayNetworking.send(jugador, carga);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo enviar el GTS a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    private static EjemplarGts aPaquete(
            net.pokereport.luna.gts.GtsService.Ejemplar e) {
        return new EjemplarGts(e.id(), e.vendedor(), e.especie(), e.mote(),
                e.nivel(), e.shiny(), e.genero(), e.naturaleza(), e.habilidad(),
                e.tera(), e.rareza(), aLista(e.ivs()), aLista(e.evs()),
                e.precio(), e.estimado(), e.expira());
    }

    private static List<Integer> aLista(int[] xs) {
        List<Integer> salida = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            salida.add(xs != null && i < xs.length ? xs[i] : 0);
        }
        return List.copyOf(salida);
    }

    private static int[] aArray(List<Integer> xs) {
        int[] salida = new int[6];
        for (int i = 0; i < 6 && xs != null && i < xs.size(); i++) {
            salida[i] = xs.get(i) == null ? 0 : xs.get(i);
        }
        return salida;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * ⚠ Cadena vacía es «no filtres», y un texto que no sea un número TAMBIEN.
     * Devolver 0 convertiría «no me importa el nivel» en «nivel mínimo 0», que
     * parece lo mismo y no lo es en cuanto se combina con otro filtro.
     */
    private static Integer entero(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long largo(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Manda el escaparate de objetos a un jugador.
     *
     * <p>⚠⚠ LA MOCHILA SE LEE EN EL HILO DEL SERVIDOR y las consultas van por el
     * executor de E/S. Leer un inventario desde otro hilo es leer el mundo desde
     * fuera, y no falla ruidosamente: falla devolviendo la foto de un instante
     * que no existió.
     */
    private static void enviarMercado(
            net.minecraft.server.network.ServerPlayerEntity jugador,
            PedirMercado filtro) {
        var svc = LunaEternal.gts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        final List<MioObj> mochila = mochilaVendible(jugador);
        final String texto = filtro == null ? "" : filtro.texto();
        final String orden = filtro == null ? "" : filtro.orden();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                List<OfertaObj> ofertas = new ArrayList<>();
                for (var o : svc.buscarObjetos(texto, orden, 120)) {
                    ofertas.add(aOferta(o));
                }
                List<OfertaObj> mias = new ArrayList<>();
                for (var o : svc.misObjetos(id)) {
                    mias.add(aOferta(o));
                }
                long saldo = LunaEternal.economy().balance(id, Currency.POKEDOLLAR);
                var carga = new EstadoMercado(List.copyOf(ofertas), List.copyOf(mias),
                        mochila, saldo);
                servidor.execute(() -> {
                    if (!jugador.isRemoved()) {
                        ServerPlayNetworking.send(jugador, carga);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo enviar el escaparate a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    private static OfertaObj aOferta(net.pokereport.luna.gts.GtsService.Oferta o) {
        return new OfertaObj(o.id(), o.vendedor() == null ? "?" : o.vendedor(),
                o.itemId(), o.nombre(), o.cantidad(), o.precio(), o.expira());
    }

    /**
     * Lo que el jugador puede poner a la venta, agrupado por objeto.
     *
     * <p>⚠ SOLO EL INVENTARIO —la barra rápida incluida, que son los nueve
     * primeros huecos de {@code main}—, que es lo que pidió el usuario. Y solo
     * lo CORRIENTE: un pico encantado no se describe con «identificador +
     * cantidad», así que enseñarlo aquí prometería algo que la publicación no
     * puede cumplir.
     *
     * <p>⚠ La mano secundaria hoy NO cuenta. No rompe la custodia —contar y
     * sacar miran el mismo sitio— pero confunde.
     */
    private static List<MioObj> mochilaVendible(
            net.minecraft.server.network.ServerPlayerEntity jugador) {
        var porItem = new java.util.LinkedHashMap<String, Integer>();
        var nombres = new java.util.HashMap<String, String>();
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            var pila = inv.main.get(i);
            if (pila.isEmpty()) {
                continue;
            }
            var suyo = pila.getItem();
            if (!net.pokereport.luna.market.Inventarios.corriente(pila, suyo)) {
                continue;
            }
            String id = net.minecraft.registry.Registries.ITEM.getId(suyo).toString();
            porItem.merge(id, pila.getCount(), Integer::sum);
            // ⚠ NO se resuelve el nombre aquí: el servidor solo tiene `en_us`.
            //   Viaja el identificador y el nombre lo pone el cliente, que sí
            //   sabe en qué idioma juega su dueño.
            nombres.putIfAbsent(id, "");
        }
        List<MioObj> salida = new ArrayList<>(porItem.size());
        for (var e : porItem.entrySet()) {
            salida.add(new MioObj(e.getKey(), nombres.get(e.getKey()), e.getValue()));
        }
        return List.copyOf(salida);
    }

    /**
     * Publica objetos en el escaparate.
     *
     * <h2>⚠⚠ LOS OBJETOS SE RETIRAN ANTES DE PUBLICAR, y en el hilo del servidor</h2>
     *
     * Si la oferta existiera y los objetos siguieran en el inventario, el
     * escaparate estaría vendiendo lo que su dueño todavía tiene — que es el
     * vector de duplicación número uno de todos los GTS mal hechos.
     *
     * <h2>⚠⚠ Y SI LA PUBLICACIÓN NO SALE, LOS OBJETOS VUELVEN</h2>
     *
     * Es la única parte de la custodia que no puede vivir en una transacción
     * —un inventario no es una tabla— así que se deshace a mano. Sin esto, un
     * rechazo por no poder pagar la tasa <b>se come los objetos</b>.
     */
    private static void venderObjeto(
            net.minecraft.server.network.ServerPlayerEntity jugador,
            AccionMercado carga) {
        var svc = LunaEternal.gts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        var item = net.pokereport.luna.market.Inventarios.objeto(carga.item());
        if (item == null) {
            aviso(jugador, "§cEse objeto no existe.");
            return;
        }
        // ⚠ La cantidad SE ACOTA ANTES de tocar nada: llega del cliente, y un
        //   número enorme desborda cualquier multiplicación que venga después.
        int piden = Math.max(1, Math.min(2304, carga.cantidad()));
        if (carga.precio() <= 0 || carga.precio() > 100_000_000L) {
            aviso(jugador, "§cEse precio no vale.");
            return;
        }
        if (net.pokereport.luna.market.Inventarios.cuantos(jugador, item) < piden) {
            aviso(jugador, "§cNo tienes tantos.");
            return;
        }
        final int sacados = net.pokereport.luna.market.Inventarios
                .sacar(jugador, item, piden);
        if (sacados <= 0) {
            aviso(jugador, "§cNo se pudieron retirar los objetos.");
            return;
        }
        // ⚠ Este nombre se guarda en la fila y NO se enseña a nadie: sale en
        //   `en_us` porque el servidor no tiene otro idioma. Vive ahí para los
        //   registros y para que un administrador sepa qué era mirando la base.
        //   Lo que el jugador ve lo compone su cliente, del identificador.
        final String nombre = new net.minecraft.item.ItemStack(item)
                .getName().getString();
        final net.minecraft.item.Item elItem = item;

        LunaEternal.submit(() -> {
            boolean devolver = true;
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var r = svc.publicarObjeto(id, carga.item(), nombre, sacados,
                        carga.precio(), carga.horas());
                devolver = !r.ok();
                servidor.execute(() -> jugador.sendMessage(
                        net.minecraft.text.Text.literal(r.message()), true));
                enviarSaldo(jugador);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo publicar el objeto de {}: {}",
                        jugador.getName().getString(), e.toString());
            } finally {
                if (devolver) {
                    servidor.execute(() -> net.pokereport.luna.market.Inventarios
                            .meter(jugador, elItem, sacados));
                }
                enviarMercado(jugador, PedirMercado.vacio());
            }
        });
    }

    /**
     * Compra una oferta entera.
     *
     * <p>⚠ El precio NO viaja: viaja el identificador de la oferta y el precio
     * sale de la fila, bloqueada. {@code GtsService.buy} ya hace el resto —
     * dinero, impuesto y estado en una sola transacción.
     *
     * <p>⚠⚠ LA ENTREGA VA DESPUÉS DEL COMMIT y en el hilo del servidor. Si la
     * mochila está llena, {@code meter} lo suelta al suelo: se ve caer y se
     * recoge. Perderlo en silencio sería cobrar por nada.
     */
    private static void comprarObjeto(
            net.minecraft.server.network.ServerPlayerEntity jugador,
            AccionMercado carga) {
        var svc = LunaEternal.gts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                Long vendedor = svc.duenoDe(carga.listado());
                var r = svc.buy(id, carga.listado());
                final long precio = r.ok() ? precioDe(r.message()) : 0;
                servidor.execute(() -> {
                    if (r.ok() && r.payload() != null) {
                        var entregado = entregarPila(jugador, r.payload());
                        // ⚠⚠ EL MENSAJE SE COMPONE AQUÍ Y NO SE USA EL DEL
                        //    SERVICIO, porque el del servicio lleva el nombre
                        //    YA RESUELTO -- y el servidor solo tiene `en_us`.
                        //    `getName()` devuelve un Text TRADUCIBLE: viaja sin
                        //    resolver y lo pinta el cliente EN SU IDIOMA.
                        jugador.sendMessage(net.minecraft.text.Text
                                .literal("\u00a7aComprado \u00a7f")
                                .append(entregado == null
                                        ? net.minecraft.text.Text.literal("?")
                                        : entregado)
                                .append(net.minecraft.text.Text.literal(
                                        "\u00a77 por \u00a7f" + precio)), true);
                    } else {
                        jugador.sendMessage(
                                net.minecraft.text.Text.literal(r.message()), true);
                    }
                });
                enviarSaldo(jugador);
                enviarMercado(jugador, PedirMercado.vacio());
                // ⚠⚠ Y AL VENDEDOR TAMBIÉN, que no ha pulsado nada: su oferta ha
                //    desaparecido y su dinero ha subido. Es la lección de los
                //    clanes — el estado no es de quien lo mira. Y el dueño se
                //    pregunta ANTES de comprar: después la fila ya está vendida.
                if (r.ok() && vendedor != null) {
                    refrescarMercadoA(servidor, java.util.Set.of(vendedor));
                }
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo comprar el objeto {}: {}",
                        carga.listado(), e.toString());
            }
        });
    }

    /** Retira una oferta propia y devuelve los objetos. La tasa no vuelve. */
    private static void retirarObjeto(
            net.minecraft.server.network.ServerPlayerEntity jugador,
            AccionMercado carga) {
        var svc = LunaEternal.gts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var r = svc.cancel(id, carga.listado());
                servidor.execute(() -> {
                    jugador.sendMessage(
                            net.minecraft.text.Text.literal(r.message()), true);
                    if (r.ok() && r.payload() != null) {
                        entregarPila(jugador, r.payload());
                    }
                });
                enviarMercado(jugador, PedirMercado.vacio());
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo retirar la oferta {}: {}",
                        carga.listado(), e.toString());
            }
        });
    }

    /**
     * Devuelve al inventario lo que iba en el {@code payload} de un listado.
     *
     * <p>⚠ El formato lo escribe {@code publicarObjeto} y lo lee esto, y nadie
     * más. Si lo escribiera uno y lo leyera otro con su propia idea del formato,
     * la primera compra que no cuadrara se comería los objetos <b>sin dar
     * ningún error</b>: el dinero ya habría cambiado de manos.
     */
    private static net.minecraft.text.Text entregarPila(
            net.minecraft.server.network.ServerPlayerEntity jugador, byte[] datos) {
        try {
            String s = new String(datos, java.nio.charset.StandardCharsets.UTF_8);
            int corte = s.indexOf((char) 0);
            if (corte < 0) {
                return null;
            }
            var item = net.pokereport.luna.market.Inventarios
                    .objeto(s.substring(0, corte));
            int cantidad = Integer.parseInt(s.substring(corte + 1).trim());
            if (item == null || cantidad <= 0) {
                return null;
            }
            net.pokereport.luna.market.Inventarios.meter(jugador, item, cantidad);
            // ⚠ Se devuelve un Text SIN RESOLVER. Llamar a `.getString()` aquí
            //   lo resolvería en `en_us` y perderíamos justo lo que buscamos.
            return new net.minecraft.item.ItemStack(item).getName().copy()
                    .append(net.minecraft.text.Text.literal(" x" + cantidad));
        } catch (Exception e) {
            LunaEternal.LOG.warn("Payload de listado ilegible: {}", e.toString());
            return null;
        }
    }

    /** El precio que el servicio metió en su mensaje. Solo para reescribirlo. */
    private static long precioDe(String mensaje) {
        var m = java.util.regex.Pattern.compile("([0-9][0-9.,]*)\\s*$")
                .matcher(mensaje == null ? "" : mensaje);
        if (!m.find()) {
            return 0;
        }
        try {
            return Long.parseLong(m.group(1).replaceAll("[.,]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Refresca el escaparate y el saldo de un puñado de jugadores. */
    private static void refrescarMercadoA(net.minecraft.server.MinecraftServer servidor,
                                          java.util.Set<Long> afectados) {
        if (afectados == null || afectados.isEmpty()) {
            return;
        }
        for (var otro : servidor.getPlayerManager().getPlayerList()) {
            try {
                long id = LunaEternal.players()
                        .resolve(otro.getUuid(), otro.getName().getString());
                if (afectados.contains(id)) {
                    enviarMercado(otro, PedirMercado.vacio());
                    enviarSaldo(otro);
                }
            } catch (Exception e) {
                LunaEternal.LOG.debug("No se pudo refrescar a {}: {}",
                        otro.getName().getString(), e.toString());
            }
        }
    }

    private static void aviso(
            net.minecraft.server.network.ServerPlayerEntity jugador, String texto) {
        jugador.sendMessage(net.minecraft.text.Text.literal(texto), true);
    }



    /** Manda las cazas del ciclo en curso. Va por el executor de E/S. */
    private static void enviarCazas(
            net.minecraft.server.network.ServerPlayerEntity jugador) {
        var svc = LunaEternal.hunts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                // ⚠ `cicloActual` CREA el ciclo si el anterior caducó. Por eso
                //   no hace falta ningún reloj: la primera persona que mira
                //   después de las 24 h provoca la rotación.
                var ciclo = svc.cicloActual(id);
                List<ObjetivoCaza> xs = new ArrayList<>();
                for (var o : ciclo.objetivos()) {
                    xs.add(new ObjetivoCaza(o.id(), o.tipo().name(), o.especie(),
                            o.necesarios(), o.hechos(), o.cobrado(), o.rareza(),
                            o.premioDolar(), o.premioMarca(),
                            o.premioObjeto(), o.premioCantidad(),
                            o.premioObjeto2(), o.premioCantidad2()));
                }
                long saldo = LunaEternal.economy().balance(id, Currency.POKEDOLLAR);
                // ⚠ `terminaEn` viene en SEGUNDOS de la base y el cliente
                //   trabaja en milisegundos. Sin el x1000, el reloj diría que
                //   caducó en 1970 -- y «—» en vez de una cuenta atrás.
                var carga = new EstadoCazas(List.copyOf(xs),
                        ciclo.terminaEn() * 1000L, saldo);
                servidor.execute(() -> {
                    if (!jugador.isRemoved()) {
                        ServerPlayNetworking.send(jugador, carga);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron enviar las cazas a {}: {}",
                        jugador.getName().getString(), e.toString());
            }
        });
    }

    /**
     * Cobra un objetivo.
     *
     * <p>⚠⚠ EL OBJETO SE ENTREGA DESPUÉS DEL COMMIT y en el hilo del servidor.
     * {@code cobrar} ya marca y paga en una sola transacción; lo que no puede
     * hacer dentro es tocar un inventario, porque un inventario no es una
     * tabla. Si la entrega falla, el dinero ya está cobrado — por eso
     * {@code meter} suelta al suelo cuando no cabe, en vez de perderlo.
     */
    private static void cobrarCaza(
            net.minecraft.server.network.ServerPlayerEntity jugador, long objetivo) {
        var svc = LunaEternal.hunts();
        var servidor = jugador.getServer();
        if (svc == null || servidor == null) {
            return;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var r = svc.cobrar(id, objetivo, java.util.UUID.randomUUID());
                var entregas = r == net.pokereport.luna.hunt.HuntService.Resultado.PAGADO
                        ? svc.entregaPendiente() : java.util.List
                            .<net.pokereport.luna.hunt.HuntService.Entrega>of();
                servidor.execute(() -> {
                    jugador.sendMessage(net.minecraft.text.Text.literal(
                            switch (r) {
                                case PAGADO -> "\u00a7a¡Recompensa cobrada!";
                                case NO_COMPLETO -> "\u00a7cTodavía no lo has completado.";
                                case YA_COBRADO -> "\u00a7eYa habías cobrado esto.";
                                case CADUCADO -> "\u00a7cEsa caza ya no está activa.";
                            }), true);
                    for (var entrega : entregas) {
                        var item = net.pokereport.luna.market.Inventarios
                                .objeto(entrega.objeto());
                        if (item != null) {
                            net.pokereport.luna.market.Inventarios
                                    .meter(jugador, item, entrega.cantidad());
                        } else {
                            // ⚠ Un identificador que no exista NO puede quedarse
                            //   callado: el jugador hizo el trabajo y no recibe
                            //   lo prometido, y nadie se enteraría.
                            LunaEternal.LOG.error(
                                "Premio de caza inexistente: {} x{} (objetivo {})",
                                entrega.objeto(), entrega.cantidad(), objetivo);
                        }
                    }
                });
                enviarSaldo(jugador);
                enviarCazas(jugador);
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo cobrar la caza {}: {}",
                        objetivo, e.toString());
            }
        });
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
