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
