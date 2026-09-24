package net.pokereport.luna.buhonero;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class BuhoneroNet {
    private BuhoneroNet() {}
    public record Pedir(long ciclo, boolean comprar) implements CustomPayload {
        public static final Id<Pedir> ID = new Id<>(Identifier.of("lunaeternal", "buhonero_pedir"));
        public static final PacketCodec<RegistryByteBuf, Pedir> CODEC = new PacketCodec<>() {
            public Pedir decode(RegistryByteBuf b) { return new Pedir(b.readLong(), b.readBoolean()); }
            public void encode(RegistryByteBuf b, Pedir p) { b.writeLong(p.ciclo); b.writeBoolean(p.comprar); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Oferta(long ciclo, String especie, int nivel, long precio, long saldo,
                         long ahora, long disponible, boolean comprada, boolean pendiente,
                         boolean abrir, String mensaje) implements CustomPayload {
        public static final Id<Oferta> ID = new Id<>(Identifier.of("lunaeternal", "buhonero_oferta"));
        public static final PacketCodec<RegistryByteBuf, Oferta> CODEC = new PacketCodec<>() {
            public Oferta decode(RegistryByteBuf b) {
                return new Oferta(b.readLong(), b.readString(64), b.readInt(), b.readLong(), b.readLong(),
                        b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readString(256));
            }
            public void encode(RegistryByteBuf b, Oferta p) {
                b.writeLong(p.ciclo); b.writeString(p.especie,64); b.writeInt(p.nivel); b.writeLong(p.precio);
                b.writeLong(p.saldo); b.writeLong(p.ahora); b.writeLong(p.disponible); b.writeBoolean(p.comprada);
                b.writeBoolean(p.pendiente); b.writeBoolean(p.abrir); b.writeString(p.mensaje,256);
            }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void registrar() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("rctmod")) BuhoneroNpc.clic();
        PayloadTypeRegistry.playC2S().register(Pedir.ID, Pedir.CODEC);
        PayloadTypeRegistry.playS2C().register(Oferta.ID, Oferta.CODEC);
    }
    public static void servidor() {
        ServerPlayNetworking.registerGlobalReceiver(Pedir.ID, (p,ctx) ->
                BuhoneroService.atender(ctx.player(), p.ciclo(), p.comprar(), false));
    }
}
