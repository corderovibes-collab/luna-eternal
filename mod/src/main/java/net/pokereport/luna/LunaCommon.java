package net.pokereport.luna;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.pokereport.luna.net.PadPayloads;

/**
 * Lo que tiene que ocurrir en <b>los dos lados</b>.
 *
 * <p>Existe por un fallo real: los tipos de paquete se registraban solo en
 * {@code onInitializeServer()}, que en un cliente <b>no se ejecuta nunca</b>
 * — es un {@code DedicatedServerModInitializer}. Resultado: el cliente
 * intentaba escuchar {@code lunaeternal:pad_abrir}, un tipo que para él no
 * existía, y el juego reventaba al arrancar:
 *
 * <pre>
 * Cannot register handler as no payload type has been registered
 * with name "lunaeternal:pad_abrir" for CLIENTBOUND PLAY
 * </pre>
 *
 * <p>Un tipo de paquete es un <b>contrato entre las dos partes</b>, así que
 * se declara en el entrypoint {@code main}, el único que corre en ambas.
 * Fabric lo invoca antes que {@code client} y {@code server}, de modo que
 * cuando cualquiera de los dos vaya a registrar sus receptores, el tipo ya
 * está.
 */
public final class LunaCommon implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(
            PadPayloads.Abrir.ID, PadPayloads.Abrir.CODEC);
        PayloadTypeRegistry.playC2S().register(
            PadPayloads.Pulsar.ID, PadPayloads.Pulsar.CODEC);
        PayloadTypeRegistry.playC2S().register(
            PadPayloads.Cerrar.ID, PadPayloads.Cerrar.CODEC);
        PayloadTypeRegistry.playS2C().register(
            PadPayloads.AbrirPokedex.ID, PadPayloads.AbrirPokedex.CODEC);
    }
}
