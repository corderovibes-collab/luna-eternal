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
        PayloadTypeRegistry.playC2S().register(PedirInicial.ID, PedirInicial.CODEC);
        PayloadTypeRegistry.playC2S().register(ElegirInicial.ID, ElegirInicial.CODEC);
        PayloadTypeRegistry.playS2C().register(Iniciales.ID, Iniciales.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirLlevados.ID, PedirLlevados.CODEC);
        PayloadTypeRegistry.playC2S().register(PedirMisiones.ID, PedirMisiones.CODEC);
        PayloadTypeRegistry.playC2S().register(ReclamarMision.ID, ReclamarMision.CODEC);
        PayloadTypeRegistry.playS2C().register(Misiones.ID, Misiones.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PedirSaldo.ID, (carga, ctx) -> {
            var jugador = ctx.player();
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
                    // Clan, trabajo, división y medallas todavía no tienen
                    // sistema detrás. Se mandan vacíos a propósito en vez de
                    // inventar un valor: el Pad dibuja un guión, que dice «esto
                    // aún no», mientras que un «Sin clan» dice «ya funciona y no
                    // tienes ninguno» — que no es verdad.
                    var ficha = new Ficha(vias, "", "", "", 0);
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
        });


        ServerPlayNetworking.registerGlobalReceiver(PedirCosmeticos.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> enviarCosmeticos(jugador));
        });

        ServerPlayNetworking.registerGlobalReceiver(PedirInicial.ID, (carga, ctx) ->
                enviarIniciales(ctx.player()));

        ServerPlayNetworking.registerGlobalReceiver(ElegirInicial.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    // ⚠ AQUI NO SE COMPRUEBA NADA. `conceder` marca primero y
                    //   entrega despues, con vuelta atras si la entrega falla, y
                    //   `claimOnce` es lo que impide elegir dos veces. Repetir la
                    //   comprobacion aqui invita a que las dos se separen -- y la
                    //   que manda es la de alla, que ademas es atomica.
                    net.pokereport.luna.starter.StarterService.conceder(
                            jugador, id, carga.especie());
                } catch (Exception e) {
                    LunaEternal.LOG.warn("No se pudo entregar el inicial a {}: {}",
                            jugador.getName().getString(), e.toString());
                }
                // Se reenvia el estado: la pantalla se cierra sola al ver que ya
                // eligio, en vez de fiarse de haber pulsado.
                enviarIniciales(jugador);
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
