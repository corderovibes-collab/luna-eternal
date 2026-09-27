package net.pokereport.luna.quienesepokemon;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Paquetes mínimos: los recursos ya viven en el JAR del cliente. */
public final class QuienEsEsePokemonNet {
    private QuienEsEsePokemonNet() {}

    public record Ronda(long id, int dex, long terminaEn) implements CustomPayload {
        public static final Id<Ronda> ID = new Id<>(Identifier.of("lunaeternal", "quien_es_ese_pokemon_ronda"));
        public static final PacketCodec<RegistryByteBuf, Ronda> CODEC = new PacketCodec<>() {
            public Ronda decode(RegistryByteBuf b) { return new Ronda(b.readLong(), b.readVarInt(), b.readLong()); }
            public void encode(RegistryByteBuf b, Ronda p) { b.writeLong(p.id); b.writeVarInt(p.dex); b.writeLong(p.terminaEn); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Revelacion(long id, int dex, String ganador, long premio, long ocultarEn) implements CustomPayload {
        public static final Id<Revelacion> ID = new Id<>(Identifier.of("lunaeternal", "quien_es_ese_pokemon_revelacion"));
        public static final PacketCodec<RegistryByteBuf, Revelacion> CODEC = new PacketCodec<>() {
            public Revelacion decode(RegistryByteBuf b) {
                return new Revelacion(b.readLong(), b.readVarInt(), b.readString(64), b.readVarLong(), b.readLong());
            }
            public void encode(RegistryByteBuf b, Revelacion p) {
                b.writeLong(p.id); b.writeVarInt(p.dex); b.writeString(p.ganador, 64); b.writeVarLong(p.premio); b.writeLong(p.ocultarEn);
            }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registrar() {
        PayloadTypeRegistry.playS2C().register(Ronda.ID, Ronda.CODEC);
        PayloadTypeRegistry.playS2C().register(Revelacion.ID, Revelacion.CODEC);
    }
}
