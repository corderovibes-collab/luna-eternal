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

/** Tutorial devices with deterministic identities and independent media. */
public final class RotomTutorial {
    public static final Vec3d POSITION = new Vec3d(-113.479, 69, .500);
    public static final String TAG = "luna_tutorial_rotom_dex";
    public static final String VIDEO = "https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/rotom-gimnasios-v1.mp4";
    public static final String SHA256 = "906a33cf2aab7743601cff34c27742d708195e1510442e0090bc07352320167f";
    public static final long BYTES = 38198649L;
    public static final Vec3d TORRE_POSITION = new Vec3d(-1.552, 68, 82.717);
    public static final String TORRE_TAG = "luna_tutorial_rotom_torre";
    public static final String TORRE_VIDEO = "https://github.com/corderovibes-collab/luna-eternal-pack/releases/download/pack-assets/rotom-torre-comercial-v1.mp4";
    public static final String TORRE_SHA256 = "4858533874fc17efbca89a3915686e5f62cee0cf45aaf23e8c5d646eea06be75";
    public static final long TORRE_BYTES = 41016420L;

    public record Device(String id, String tag, Vec3d position, float yaw, String name) {
        java.util.UUID uuid() {
            return java.util.UUID.nameUUIDFromBytes(tag.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    public static final Device GIMNASIOS = new Device("gimnasios", TAG, POSITION, -90, "Rotom Dex · Gimnasios");
    public static final Device TORRE = new Device("torre", TORRE_TAG, TORRE_POSITION, 180, "Rotom Dex · Torre Comercial");
    private static final java.util.List<Device> DEVICES = java.util.List.of(GIMNASIOS, TORRE);

    public static final EntityType<RotomEntity> TYPE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of("lunaeternal", "rotom_dex"), EntityType.Builder
                    .create(RotomEntity::new, SpawnGroup.MISC).dimensions(.8f, 1.15f)
                    .maxTrackingRange(8).trackingTickInterval(3).makeFireImmune().build());

    public record Open(String tutorial) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of("lunaeternal", "rotom_tutorial"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = new PacketCodec<>() {
            @Override public Open decode(RegistryByteBuf b) { return new Open(b.readString(32)); }
            @Override public void encode(RegistryByteBuf b, Open p) { b.writeString(p.tutorial(), 32); }
        };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void common() {
        FabricDefaultAttributeRegistry.register(TYPE, RotomEntity.createMobAttributes());
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p,w,hand,e,hit) -> {
            if (e instanceof RotomEntity rotom) return rotom.interactMob(p,hand);
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public static void server() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            var world = server.getWorld(LunaDimensions.CIUDADELA);
            if (world != null) for (Device d : DEVICES)
                if (world.isChunkLoaded(BlockPos.ofFloored(d.position()))) ensure(world, d);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("luna").then(literal("rotom")
                .requires(s -> s.hasPermissionLevel(4))
                .then(literal("spawn").executes(c -> {
                    var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
                    if (w == null) return 0;
                    for (Device d : DEVICES) { w.getChunk(BlockPos.ofFloored(d.position())); ensure(w, d); }
                    return DEVICES.size();
                }))
                .then(literal("reset").executes(c -> {
                    var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
                    if (w == null) return 0;
                    for (Device d : DEVICES) {
                        w.getChunk(BlockPos.ofFloored(d.position()));
                        for (var e : devices(w, d)) e.discard();
                        ensure(w, d);
                    }
                    return DEVICES.size();
                }))
                .then(literal("tp").executes(c -> teleport(c, GIMNASIOS, 3, 0)))
                .then(literal("tptorre").executes(c -> teleport(c, TORRE, 0, 3)))
                .then(literal("testvideo").executes(c -> send(c, GIMNASIOS)))
                .then(literal("testtorre").executes(c -> send(c, TORRE))))));
    }

    private static int teleport(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> c,
                                Device d, double ox, double oz) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var w = c.getSource().getServer().getWorld(LunaDimensions.CIUDADELA);
        if (w == null) return 0;
        return net.pokereport.luna.world.Traslado.ir(c.getSource().getPlayerOrThrow(),
                w, d.position().add(ox, 0, oz), d.yaw(), 0) ? 1 : 0;
    }

    private static int send(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> c,
                            Device d) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(p, Open.ID)) return 0;
        ServerPlayNetworking.send(p, new Open(d.id()));
        return 1;
    }

    public static Device device(net.minecraft.entity.Entity e) {
        for (Device d : DEVICES) if (e.getCommandTags().contains(d.tag())) return d;
        return null;
    }

    private static java.util.List<RotomEntity> devices(ServerWorld w, Device d) {
        return w.getEntitiesByClass(RotomEntity.class, new Box(BlockPos.ofFloored(d.position())).expand(8),
                e -> e.getCommandTags().contains(d.tag()));
    }

    private static void ensure(ServerWorld w, Device d) {
        var found = devices(w, d);
        if (found.isEmpty()) {
            if (w.getEntity(d.uuid()) != null) return;
            var e = TYPE.create(w);
            if (e == null) return;
            e.setUuid(d.uuid());
            e.addCommandTag(d.tag());
            e.refreshPositionAndAngles(d.position().x, d.position().y, d.position().z, d.yaw(), 0);
            e.setCustomName(Text.literal(d.name()));
            w.spawnEntity(e);
        } else {
            RotomEntity keep = found.stream().filter(e -> e.getUuid().equals(d.uuid())).findFirst().orElse(found.get(0));
            for (RotomEntity e : found) if (e != keep) e.discard();
        }
    }
    private RotomTutorial() {}
}