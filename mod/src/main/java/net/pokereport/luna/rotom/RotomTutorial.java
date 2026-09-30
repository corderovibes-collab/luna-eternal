package net.pokereport.luna.rotom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.world.LunaDimensions;
import static net.minecraft.server.command.CommandManager.literal;

/** One explicitly identified tutorial device. Normal entity saving owns persistence. */
public final class RotomTutorial {
    public static final Vec3d POSITION = new Vec3d(-113.479, 69, .500);
    public static final String TAG = "luna_tutorial_rotom_dex";
    private static final java.util.UUID DEVICE_UUID = java.util.UUID.nameUUIDFromBytes(
            TAG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final String VIDEO = "https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/rotom-gimnasios-v1.mp4";
    public static final String SHA256 = "906a33cf2aab7743601cff34c27742d708195e1510442e0090bc07352320167f";
    public static final long BYTES = 38198649L;
    public static final EntityType<RotomEntity> TYPE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of("lunaeternal", "rotom_dex"), EntityType.Builder
                    .create(RotomEntity::new, SpawnGroup.MISC).dimensions(.8f, 1.15f)
                    .maxTrackingRange(8).trackingTickInterval(3).makeFireImmune().build());

    public record Open() implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of("lunaeternal", "rotom_tutorial"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.unit(new Open());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void common() {
        FabricDefaultAttributeRegistry.register(TYPE, RotomEntity.createMobAttributes());
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        // Consume before vanilla item interaction: a name tag must not rename
        // the device, and a lead must never create a second interaction path.
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p,w,hand,e,hit) -> {
            if (e instanceof RotomEntity rotom) return rotom.interactMob(p,hand);
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public static void server() {
        // Checking once per second allows entity NBT loading to finish first.
        // isChunkLoaded never acquires a chunk ticket or generates terrain.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            var world = server.getWorld(LunaDimensions.CIUDADELA);
            if (world != null && world.isChunkLoaded(BlockPos.ofFloored(POSITION))) ensure(world);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("luna").then(literal("rotom")
                .requires(s -> s.hasPermissionLevel(4))
                .then(literal("spawn").executes(c -> {
                    var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
                    if (w == null) return 0;
                    w.getChunk(BlockPos.ofFloored(POSITION)); ensure(w); return 1;
                }))
                .then(literal("reset").executes(c -> {
                    var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
                    if (w == null) return 0;
                    w.getChunk(BlockPos.ofFloored(POSITION));
                    for (var e : devices(w)) e.discard();
                    ensure(w); return 1;
                }))
                .then(literal("tp").executes(c -> {
                    var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
                    if (w == null) return 0;
                    return net.pokereport.luna.world.Traslado.ir(c.getSource().getPlayerOrThrow(),
                            w, POSITION.add(3, 0, 0), 90, 0) ? 1 : 0;
                }))
                .then(literal("testvideo").executes(c -> {
                    var p = c.getSource().getPlayerOrThrow();
                    if (!ServerPlayNetworking.canSend(p, Open.ID)) return 0;
                    ServerPlayNetworking.send(p, new Open()); return 1;
                })))));
    }

    private static java.util.List<RotomEntity> devices(ServerWorld w) {
        return w.getEntitiesByClass(RotomEntity.class, new Box(BlockPos.ofFloored(POSITION)).expand(8),
                e -> e.getCommandTags().contains(TAG));
    }

    private static void ensure(ServerWorld w) {
        var found = devices(w);
        if (found.isEmpty()) {
            if (w.getEntity(DEVICE_UUID) != null) return;
            var e = TYPE.create(w);
            if (e == null) return;
            e.setUuid(DEVICE_UUID);
            e.addCommandTag(TAG);
            e.refreshPositionAndAngles(POSITION.x, POSITION.y, POSITION.z, -90, 0);
            e.setCustomName(Text.literal("Rotom Dex"));
            w.spawnEntity(e);
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
        }
    }
    private RotomTutorial() {}
}
