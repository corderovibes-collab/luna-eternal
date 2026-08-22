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
     * «Compra esto» o «ponte esto».
     *
     * <p>⚠ Viaja el <b>identificador</b> y nada más. Ni el precio, ni la
     * categoría, ni si se puede pagar: todo eso lo tiene el servidor, y aceptar
     * cualquiera de esos datos del cliente sería aceptar el precio que él diga.
     *
     * @param equipar {@code true} = ponérselo; {@code false} = comprarlo
     */
    public record AccionCosmetico(String id, boolean equipar) implements CustomPayload {
        public static final Id<AccionCosmetico> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "accion_cosmetico"));
        public static final PacketCodec<RegistryByteBuf, AccionCosmetico> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AccionCosmetico::id,
                        PacketCodecs.BOOL, AccionCosmetico::equipar,
                        AccionCosmetico::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * «Este jugador lleva puesta esta mascota». Va a TODO el mundo.
     *
     * <p>Es lo que convierte la tienda en algo que existe: un cosmetico que solo
     * ve su dueño no vale nada, y {@code monetization.md} lo dice con esas
     * palabras. Sin este paquete, comprar y equipar funcionan y no se nota.
     *
     * <p>⚠ Viaja <b>especie y aspecto</b>, no el identificador del catalogo. El
     * cliente que lo recibe solo necesita saber QUE dibujar, y asi no hace falta
     * que conozca el catalogo de otro jugador -- que ademas incluye precios y
     * que no tiene por que ver.
     *
     * <p>Especie vacia significa <b>no lleva nada</b>. Es el mismo paquete para
     * poner y para quitar: dos paquetes distintos permitirian que llegara el de
     * poner y se perdiera el de quitar, y el cosmetico se quedaria pegado.
     */
    public record CosmeticoPuesto(java.util.UUID jugador, String especie, String aspecto)
            implements CustomPayload {
        public static final Id<CosmeticoPuesto> ID =
                new Id<>(Identifier.of(LunaEternal.MOD_ID, "cosmetico_puesto"));
        public static final PacketCodec<RegistryByteBuf, CosmeticoPuesto> CODEC =
                PacketCodec.tuple(
                        net.minecraft.util.Uuids.PACKET_CODEC, CosmeticoPuesto::jugador,
                        PacketCodecs.STRING, CosmeticoPuesto::especie,
                        PacketCodecs.STRING, CosmeticoPuesto::aspecto,
                        CosmeticoPuesto::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
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
        PayloadTypeRegistry.playS2C().register(CosmeticoPuesto.ID, CosmeticoPuesto.CODEC);

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

        // ⚠ AL ENTRAR HAY QUE SINCRONIZAR EN LAS DOS DIRECCIONES, y es facil
        // dejarse una: cada una funciona a medias y el fallo solo aparece con
        // dos personas conectadas. Ver `Difusion.alEntrar`.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (manejador, remitente, servidor) ->
                        net.pokereport.luna.cosmetics.Difusion.alEntrar(manejador.player));

        ServerPlayNetworking.registerGlobalReceiver(PedirCosmeticos.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> enviarCosmeticos(jugador));
        });

        ServerPlayNetworking.registerGlobalReceiver(AccionCosmetico.ID, (carga, ctx) -> {
            var jugador = ctx.player();
            LunaEternal.submit(() -> {
                try {
                    long id = LunaEternal.players()
                            .resolve(jugador.getUuid(), jugador.getName().getString());
                    var svc = LunaEternal.cosmetics();
                    net.pokereport.luna.cosmetics.CosmeticsService.Resultado r;

                    if (carga.equipar()) {
                        var pieza = net.pokereport.luna.cosmetics.Catalogo.de(carga.id());
                        r = pieza == null
                                ? new net.pokereport.luna.cosmetics.CosmeticsService
                                        .Resultado(false, "Ese cosmetico no existe.")
                                : svc.equipar(id, pieza.categoria(), carga.id());
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

                    // Y si de verdad cambio lo que lleva puesto, se entera todo
                    // el mundo. Solo al equipar: comprar no cambia tu aspecto.
                    if (carga.equipar() && r.ok()) {
                        net.pokereport.luna.cosmetics.Difusion.difundir(jugador);
                    }
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
            var equipados = svc.equipados(id);
            long saldo = LunaEternal.economy().balance(id, Currency.REPORTCOIN);

            List<PiezaCosmetica> piezas = new ArrayList<>();
            for (var p : net.pokereport.luna.cosmetics.Catalogo.todas()) {
                int banderas = 0;
                if (poseidos.contains(p.id())) {
                    banderas |= PiezaCosmetica.POSEIDO;
                }
                if (p.id().equals(equipados.get(p.categoria()))) {
                    banderas |= PiezaCosmetica.EQUIPADO;
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
