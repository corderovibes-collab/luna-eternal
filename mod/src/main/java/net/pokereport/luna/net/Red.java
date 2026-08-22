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

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PedirSaldo.ID, PedirSaldo.CODEC);
        PayloadTypeRegistry.playS2C().register(Saldo.ID, Saldo.CODEC);
        PayloadTypeRegistry.playS2C().register(Ficha.ID, Ficha.CODEC);
        PayloadTypeRegistry.playS2C().register(VozPokedex.ID, VozPokedex.CODEC);

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
    }
}
