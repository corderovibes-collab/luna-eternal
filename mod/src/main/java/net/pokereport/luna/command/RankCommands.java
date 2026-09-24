package net.pokereport.luna.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.command.CheckSpawnsCommand;
import com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.heal.HealService;
import net.pokereport.luna.rank.RankService;
import net.pokereport.luna.ui.Tablist.Rank;
import net.pokereport.luna.world.Espera;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.Traslado;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro y ejecución de los 14 comandos exclusivos distribuidos por rango.
 */
public final class RankCommands {

    public record BackLocation(String dimension, double x, double y, double z, float yaw, float pitch) {}
    public record TpaRequest(UUID solicitanteUuid, String solicitanteNombre, UUID destinoUuid, long timestamp) {}

    private static final Map<UUID, BackLocation> ULTIMO_BACK = new ConcurrentHashMap<>();
    private static final Map<UUID, TpaRequest> TPA_RECIBIDAS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TPA_ENVIADAS = new ConcurrentHashMap<>();

    private RankCommands() {}

    public static void guardarBack(ServerPlayerEntity p) {
        if (p == null || p.isRemoved()) return;
        ULTIMO_BACK.put(p.getUuid(), new BackLocation(
            p.getServerWorld().getRegistryKey().getValue().toString(),
            p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch()
        ));
    }

    public static boolean cumpleRango(ServerPlayerEntity p, Rank minimo) {
        if (p.hasPermissionLevel(2)) return true;
        Rank actual = RankService.enCache(p.getUuid());
        if (actual.equipo) return true;
        return actual.escalon >= minimo.escalon;
    }

    public static void registrar(CommandDispatcher<ServerCommandSource> dispatcher) {

        // ==========================================
        // RANGO ÉLITE (2 Comandos)
        // ==========================================

        // 1. /pokeheal & /heal
        dispatcher.register(
            CommandManager.literal("pokeheal")
                .executes(RankCommands::ejecutarHeal)
        );
        dispatcher.register(
            CommandManager.literal("heal")
                .executes(RankCommands::ejecutarHeal)
        );

        // 2. /craft & /workbench
        dispatcher.register(
            CommandManager.literal("craft")
                .executes(RankCommands::ejecutarCraft)
        );
        dispatcher.register(
            CommandManager.literal("workbench")
                .executes(RankCommands::ejecutarCraft)
        );

        // ==========================================
        // RANGO CAMPEÓN (+3 Comandos = 5 Total)
        // ==========================================

        // 3. /checkspawns
        dispatcher.register(
            CommandManager.literal("checkspawns")
                .executes(RankCommands::ejecutarCheckSpawns)
        );
        dispatcher.register(
            CommandManager.literal("spawns")
                .executes(RankCommands::ejecutarCheckSpawns)
        );

        // 4. /enderchest & /ec
        dispatcher.register(
            CommandManager.literal("enderchest")
                .executes(RankCommands::ejecutarEnderChest)
        );
        dispatcher.register(
            CommandManager.literal("ec")
                .executes(RankCommands::ejecutarEnderChest)
        );

        // 5. /basura & /trash
        dispatcher.register(
            CommandManager.literal("basura")
                .executes(RankCommands::ejecutarBasura)
        );
        dispatcher.register(
            CommandManager.literal("trash")
                .executes(RankCommands::ejecutarBasura)
        );

        // /tpa <jugador> (Campeón, Maestro, Leyenda)
        dispatcher.register(
            CommandManager.literal("tpa")
                .then(CommandManager.argument("jugador", EntityArgumentType.player())
                    .executes(RankCommands::ejecutarTpa))
        );

        // /tpaccept & /tpyes (Responder a solicitud recibida)
        dispatcher.register(
            CommandManager.literal("tpaccept")
                .executes(RankCommands::ejecutarTpAccept)
        );
        dispatcher.register(
            CommandManager.literal("tpyes")
                .executes(RankCommands::ejecutarTpAccept)
        );

        // /tpdeny & /tpno (Rechazar solicitud recibida)
        dispatcher.register(
            CommandManager.literal("tpdeny")
                .executes(RankCommands::ejecutarTpDeny)
        );
        dispatcher.register(
            CommandManager.literal("tpno")
                .executes(RankCommands::ejecutarTpDeny)
        );

        // /tpcancel (Cancelar solicitud enviada)
        dispatcher.register(
            CommandManager.literal("tpcancel")
                .executes(RankCommands::ejecutarTpCancel)
        );

        // ==========================================
        // RANGO MAESTRO (+5 Comandos = 10 Total)
        // ==========================================

        // 6. /pc (EXCLUSIVO MAESTRO Y LEYENDA)
        dispatcher.register(
            CommandManager.literal("pc")
                .executes(RankCommands::ejecutarPC)
        );

        // 7. /ivs [slot 1-6]
        dispatcher.register(
            CommandManager.literal("ivs")
                .executes(ctx -> ejecutarIVs(ctx, 1))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                    .executes(ctx -> ejecutarIVs(ctx, IntegerArgumentType.getInteger(ctx, "slot"))))
        );
        dispatcher.register(
            CommandManager.literal("pokeivs")
                .executes(ctx -> ejecutarIVs(ctx, 1))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                    .executes(ctx -> ejecutarIVs(ctx, IntegerArgumentType.getInteger(ctx, "slot"))))
        );

        // 8. /evs [slot 1-6]
        dispatcher.register(
            CommandManager.literal("evs")
                .executes(ctx -> ejecutarEVs(ctx, 1))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                    .executes(ctx -> ejecutarEVs(ctx, IntegerArgumentType.getInteger(ctx, "slot"))))
        );
        dispatcher.register(
            CommandManager.literal("pokeevs")
                .executes(ctx -> ejecutarEVs(ctx, 1))
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                    .executes(ctx -> ejecutarEVs(ctx, IntegerArgumentType.getInteger(ctx, "slot"))))
        );

        // 9. /anvil
        dispatcher.register(
            CommandManager.literal("anvil")
                .executes(RankCommands::ejecutarAnvil)
        );

        // 10. /back
        dispatcher.register(
            CommandManager.literal("back")
                .executes(RankCommands::ejecutarBack)
        );

        // ==========================================
        // RANGO LEYENDA (+4 Comandos = 14 Total)
        // ==========================================

        // 11. /stonecutter
        dispatcher.register(
            CommandManager.literal("stonecutter")
                .executes(RankCommands::ejecutarStonecutter)
        );

        // 12. /smithing
        dispatcher.register(
            CommandManager.literal("smithing")
                .executes(RankCommands::ejecutarSmithing)
        );

        // 13. /grindstone
        dispatcher.register(
            CommandManager.literal("grindstone")
                .executes(RankCommands::ejecutarGrindstone)
        );

        // 14. /rename <texto>
        dispatcher.register(
            CommandManager.literal("rename")
                .then(CommandManager.argument("nombre", StringArgumentType.greedyString())
                    .executes(ctx -> ejecutarRename(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );
    }

    // -------------------------------------------------------------
    // IMPLEMENTACIONES DE COMANDOS
    // -------------------------------------------------------------

    private static int ejecutarHeal(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.ELITE)) {
            p.sendMessage(Text.literal("§cEl comando /heal requiere rango Élite o superior."), false);
            return 0;
        }

        HealService.curar(p);
        return 1;
    }

    private static int ejecutarCraft(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.ELITE)) {
            p.sendMessage(Text.literal("§cEl comando /craft requiere rango Élite o superior."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> new CraftingScreenHandler(syncId, inv, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                @Override
                public boolean canUse(PlayerEntity entity) {
                    return true;
                }
            },
            Text.literal("Mesa de Trabajo Portátil")
        ));
        return 1;
    }

    private static int ejecutarCheckSpawns(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.CAMPEON)) {
            p.sendMessage(Text.literal("§cEl comando /checkspawns requiere rango Campeón o superior."), false);
            return 0;
        }

        try {
            Method m = CheckSpawnsCommand.class.getDeclaredMethod("execute", CommandContext.class, ServerPlayerEntity.class);
            m.setAccessible(true);
            return (int) m.invoke(CheckSpawnsCommand.INSTANCE, ctx, p);
        } catch (Throwable t) {
            LunaEternal.LOG.error("Error al ejecutar CheckSpawnsCommand de Cobblemon", t);
            p.sendMessage(Text.literal("§cNo se pudo consultar los spawns en este momento."), false);
            return 0;
        }
    }

    private static int ejecutarEnderChest(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.CAMPEON)) {
            p.sendMessage(Text.literal("§cEl comando /enderchest requiere rango Campeón o superior."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, player.getEnderChestInventory()),
            Text.literal("Cofre de Ender")
        ));
        return 1;
    }

    private static int ejecutarBasura(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.CAMPEON)) {
            p.sendMessage(Text.literal("§cEl comando /basura requiere rango Campeón o superior."), false);
            return 0;
        }

        SimpleInventory trashInv = new SimpleInventory(27);
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inv, trashInv),
            Text.literal("§4Papelera (Los objetos se destruirán)")
        ));
        return 1;
    }

    private static int ejecutarPC(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.MAESTRO)) {
            p.sendMessage(Text.literal("§cEl acceso remoto a la PC es exclusivo para rangos Maestro y Leyenda. Los demás jugadores pueden usar las PCs del Centro Pokémon o craftear una física."), false);
            return 0;
        }

        try {
            new OpenPCPacket(p.getUuid()).sendToPlayer(p);
            p.sendMessage(Text.literal("§aAbriendo tu PC Pokémon..."), false);
            return 1;
        } catch (Throwable t) {
            LunaEternal.LOG.error("Error al abrir PC remota para {}", p.getName().getString(), t);
            p.sendMessage(Text.literal("§cNo se pudo abrir la PC en este momento."), false);
            return 0;
        }
    }

    private static int ejecutarIVs(CommandContext<ServerCommandSource> ctx, int slot) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.MAESTRO)) {
            p.sendMessage(Text.literal("§cEl comando /ivs requiere rango Maestro o superior."), false);
            return 0;
        }

        Pokemon bicho = obtenerPokemon(p, slot);
        if (bicho == null) {
            p.sendMessage(Text.literal("§cNo tienes ningún Pokémon en la posición " + slot + " de tu equipo."), false);
            return 0;
        }

        IVs ivs = bicho.getIvs();
        int hp = ivs.getOrDefault(Stats.HP);
        int atk = ivs.getOrDefault(Stats.ATTACK);
        int def = ivs.getOrDefault(Stats.DEFENCE);
        int spa = ivs.getOrDefault(Stats.SPECIAL_ATTACK);
        int spd = ivs.getOrDefault(Stats.SPECIAL_DEFENCE);
        int spe = ivs.getOrDefault(Stats.SPEED);
        int total = hp + atk + def + spa + spd + spe;
        double pct = (total / 186.0) * 100.0;

        String nombre = bicho.getNickname() != null ? bicho.getNickname().getString() : bicho.getSpecies().getName();
        p.sendMessage(Text.literal("§6§m----------------§r §e§lIVs de " + nombre + " §6§m----------------"), false);
        p.sendMessage(Text.literal("§7PS:              " + formatearIV(hp)), false);
        p.sendMessage(Text.literal("§7Ataque:          " + formatearIV(atk)), false);
        p.sendMessage(Text.literal("§7Defensa:         " + formatearIV(def)), false);
        p.sendMessage(Text.literal("§7Atq. Especial:   " + formatearIV(spa)), false);
        p.sendMessage(Text.literal("§7Def. Especial:   " + formatearIV(spd)), false);
        p.sendMessage(Text.literal("§7Velocidad:       " + formatearIV(spe)), false);
        p.sendMessage(Text.literal("§7Potencial Total: §e" + total + "/186 §a(" + String.format("%.1f", pct) + "%)"), false);
        p.sendMessage(Text.literal("§6§m---------------------------------------------"), false);
        return 1;
    }

    private static int ejecutarEVs(CommandContext<ServerCommandSource> ctx, int slot) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.MAESTRO)) {
            p.sendMessage(Text.literal("§cEl comando /evs requiere rango Maestro o superior."), false);
            return 0;
        }

        Pokemon bicho = obtenerPokemon(p, slot);
        if (bicho == null) {
            p.sendMessage(Text.literal("§cNo tienes ningún Pokémon en la posición " + slot + " de tu equipo."), false);
            return 0;
        }

        EVs evs = bicho.getEvs();
        int hp = evs.getOrDefault(Stats.HP);
        int atk = evs.getOrDefault(Stats.ATTACK);
        int def = evs.getOrDefault(Stats.DEFENCE);
        int spa = evs.getOrDefault(Stats.SPECIAL_ATTACK);
        int spd = evs.getOrDefault(Stats.SPECIAL_DEFENCE);
        int spe = evs.getOrDefault(Stats.SPEED);
        int total = hp + atk + def + spa + spd + spe;

        String nombre = bicho.getNickname() != null ? bicho.getNickname().getString() : bicho.getSpecies().getName();
        p.sendMessage(Text.literal("§6§m----------------§r §b§lEVs de " + nombre + " §6§m----------------"), false);
        p.sendMessage(Text.literal("§7PS:              " + formatearEV(hp)), false);
        p.sendMessage(Text.literal("§7Ataque:          " + formatearEV(atk)), false);
        p.sendMessage(Text.literal("§7Defensa:         " + formatearEV(def)), false);
        p.sendMessage(Text.literal("§7Atq. Especial:   " + formatearEV(spa)), false);
        p.sendMessage(Text.literal("§7Def. Especial:   " + formatearEV(spd)), false);
        p.sendMessage(Text.literal("§7Velocidad:       " + formatearEV(spe)), false);
        p.sendMessage(Text.literal("§7Total Invertido: §b" + total + "/510 puntos"), false);
        p.sendMessage(Text.literal("§6§m---------------------------------------------"), false);
        return 1;
    }

    private static int ejecutarAnvil(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.MAESTRO)) {
            p.sendMessage(Text.literal("§cEl comando /anvil requiere rango Maestro o superior."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> new AnvilScreenHandler(syncId, inv, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                @Override
                public boolean canUse(PlayerEntity entity) {
                    return true;
                }
            },
            Text.literal("Yunque Portátil")
        ));
        return 1;
    }

    private static int ejecutarBack(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.MAESTRO)) {
            p.sendMessage(Text.literal("§cEl comando /back requiere rango Maestro o superior."), false);
            return 0;
        }

        BackLocation loc = ULTIMO_BACK.get(p.getUuid());
        if (loc == null) {
            p.sendMessage(Text.literal("§cNo tienes ninguna ubicación previa guardada para regresar."), false);
            return 0;
        }

        // Restricción: destino en Hogar
        if (!loc.dimension().equals(LunaDimensions.HOGAR.getValue().toString())) {
            p.sendMessage(Text.literal("§cEl comando /back solo permite regresar dentro de la dimensión Hogar."), false);
            return 0;
        }

        ServerWorld targetWorld = p.getServer().getWorld(LunaDimensions.HOGAR);
        if (targetWorld == null) {
            p.sendMessage(Text.literal("§cEl mundo Hogar no está disponible."), false);
            return 0;
        }

        // Si está en combate
        try {
            if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(p) != null) {
                p.sendMessage(Text.literal("§cNo puedes usar /back mientras estás en combate Pokémon."), false);
                return 0;
            }
        } catch (Throwable ignored) {}

        Vec3d destino = new Vec3d(loc.x(), loc.y(), loc.z());
        Espera.pedir(p, "regresar a tu posición anterior", () -> {
            Traslado.ir(p, targetWorld, destino, loc.yaw(), loc.pitch());
            p.sendMessage(Text.literal("§aHas regresado a tu posición anterior."), false);
        });

        return 1;
    }

    private static int ejecutarStonecutter(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.LEYENDA)) {
            p.sendMessage(Text.literal("§cEl comando /stonecutter es exclusivo para el rango Leyenda."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> new StonecutterScreenHandler(syncId, inv, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                @Override
                public boolean canUse(PlayerEntity entity) {
                    return true;
                }
            },
            Text.literal("Cortapiedras Portátil")
        ));
        return 1;
    }

    private static int ejecutarSmithing(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.LEYENDA)) {
            p.sendMessage(Text.literal("§cEl comando /smithing es exclusivo para el rango Leyenda."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> new SmithingScreenHandler(syncId, inv, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                @Override
                public boolean canUse(PlayerEntity entity) {
                    return true;
                }
            },
            Text.literal("Mesa de Herrería Portátil")
        ));
        return 1;
    }

    private static int ejecutarGrindstone(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.LEYENDA)) {
            p.sendMessage(Text.literal("§cEl comando /grindstone es exclusivo para el rango Leyenda."), false);
            return 0;
        }

        p.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, player) -> new GrindstoneScreenHandler(syncId, inv, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                @Override
                public boolean canUse(PlayerEntity entity) {
                    return true;
                }
            },
            Text.literal("Afiladora Portátil")
        ));
        return 1;
    }

    private static int ejecutarRename(CommandContext<ServerCommandSource> ctx, String nuevoNombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.LEYENDA)) {
            p.sendMessage(Text.literal("§cEl comando /rename es exclusivo para el rango Leyenda."), false);
            return 0;
        }

        ItemStack stack = p.getMainHandStack();
        if (stack.isEmpty()) {
            p.sendMessage(Text.literal("§cDebes sostener un objeto en tu mano principal para renombrarlo."), false);
            return 0;
        }

        if (nuevoNombre.length() > 50) {
            p.sendMessage(Text.literal("§cEl nombre es demasiado largo (máximo 50 caracteres)."), false);
            return 0;
        }

        // Filtro de caracteres peligrosos o formato
        String filtrado = nuevoNombre.replace("§", "&");
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(filtrado));
        p.sendMessage(Text.literal("§aObjeto renombrado exitosamente a: §f" + filtrado), false);
        return 1;
    }

    // -------------------------------------------------------------
    // HELPERS FORMATO
    // -------------------------------------------------------------

    private static Pokemon obtenerPokemon(ServerPlayerEntity p, int slot) {
        try {
            var party = Cobblemon.INSTANCE.getStorage().getParty(p);
            return party.get(slot - 1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatearIV(int val) {
        if (val == 31) return "§a§l" + val + "/31 (Perfecto)§r";
        if (val == 0) return "§c" + val + "/31 (Mínimo)";
        if (val >= 25) return "§e" + val + "/31";
        return "§f" + val + "/31";
    }

    private static String formatearEV(int val) {
        if (val >= 252) return "§a§l" + val + "/252 (Al máximo)§r";
        if (val == 0) return "§70/252";
        return "§b" + val + "/252";
    }

    // -------------------------------------------------------------
    // TPA (Campeón, Maestro, Leyenda)
    // -------------------------------------------------------------

    private static int ejecutarTpa(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (!cumpleRango(p, Rank.CAMPEON)) {
            p.sendMessage(Text.literal("§cEl comando /tpa requiere rango Campeón o superior."), false);
            return 0;
        }

        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(ctx, "jugador");
        } catch (Exception e) {
            p.sendMessage(Text.literal("§cJugador no encontrado o no está conectado."), false);
            return 0;
        }

        if (target.getUuid().equals(p.getUuid())) {
            p.sendMessage(Text.literal("§cNo puedes enviarte una solicitud de teletransporte a ti mismo."), false);
            return 0;
        }

        // Combate Pokémon
        try {
            if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(p) != null
                || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(target) != null) {
                p.sendMessage(Text.literal("§cNo se pueden enviar solicitudes de teletransporte durante un combate."), false);
                return 0;
            }
        } catch (Throwable ignored) {}

        TpaRequest req = new TpaRequest(p.getUuid(), p.getName().getString(), target.getUuid(), System.currentTimeMillis());
        TPA_RECIBIDAS.put(target.getUuid(), req);
        TPA_ENVIADAS.put(p.getUuid(), target.getUuid());

        p.sendMessage(Text.literal("§a[TPA] Has enviado una solicitud de teletransporte a §e" + target.getName().getString() + "§a. Tienen 60s para responder."), false);

        target.sendMessage(Text.literal("§6§m--------------------------------------------------"), false);
        target.sendMessage(Text.literal("§e§l" + p.getName().getString() + " §7quiere teletransportarse hacia ti."), false);
        target.sendMessage(Text.literal("§7Tienes 60 segundos para responder:"), false);

        MutableText botones = Text.empty()
            .append(Text.literal("  §a§l[ACEPTAR] ")
                .styled(s -> s.withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClic para aceptar la solicitud")))))
            .append(Text.literal("  §c§l[RECHAZAR]")
                .styled(s -> s.withColor(Formatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§cClic para rechazar la solicitud")))));

        target.sendMessage(botones, false);
        target.sendMessage(Text.literal("§6§m--------------------------------------------------"), false);

        return 1;
    }

    private static int ejecutarTpAccept(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        TpaRequest req = TPA_RECIBIDAS.get(p.getUuid());
        if (req == null || (System.currentTimeMillis() - req.timestamp() > 60_000L)) {
            TPA_RECIBIDAS.remove(p.getUuid());
            p.sendMessage(Text.literal("§c[TPA] No tienes ninguna solicitud de teletransporte pendiente o ya ha caducado."), false);
            return 0;
        }

        TPA_RECIBIDAS.remove(p.getUuid());
        TPA_ENVIADAS.remove(req.solicitanteUuid());

        ServerPlayerEntity sender = p.getServer().getPlayerManager().getPlayer(req.solicitanteUuid());
        if (sender == null || sender.isDisconnected() || sender.isRemoved()) {
            p.sendMessage(Text.literal("§c[TPA] El jugador que envió la solicitud ya no está en línea."), false);
            return 0;
        }

        // Combate
        try {
            if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(p) != null
                || Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(sender) != null) {
                p.sendMessage(Text.literal("§c[TPA] No se puede completar el viaje mientras alguno esté en combate."), false);
                sender.sendMessage(Text.literal("§c[TPA] Viaje cancelado porque uno de los jugadores entró en combate."), false);
                return 0;
            }
        } catch (Throwable ignored) {}

        p.sendMessage(Text.literal("§a[TPA] Has aceptado la solicitud de §e" + sender.getName().getString() + "§a. Llegará en 5 segundos."), false);
        sender.sendMessage(Text.literal("§a[TPA] §e" + p.getName().getString() + "§a aceptó tu solicitud. Teletransportándote en 5 segundos... ¡No te muevas!"), false);

        if (sender.getServerWorld().getRegistryKey().equals(LunaDimensions.HOGAR)) {
            guardarBack(sender);
        }

        Espera.pedir(sender, "teletransportarte hasta " + p.getName().getString(), () -> {
            if (p.isRemoved() || p.isDisconnected()) {
                sender.sendMessage(Text.literal("§c[TPA] El destino ya no está disponible."), false);
                return;
            }
            ServerWorld targetWorld = p.getServerWorld();
            Traslado.ir(sender, targetWorld, p.getPos(), p.getYaw(), p.getPitch());
            sender.sendMessage(Text.literal("§a[TPA] ¡Te has teletransportado hasta §e" + p.getName().getString() + "§a!"), false);
            p.sendMessage(Text.literal("§a[TPA] §e" + sender.getName().getString() + "§a ha llegado a tu lado."), false);
        });

        return 1;
    }

    private static int ejecutarTpDeny(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        TpaRequest req = TPA_RECIBIDAS.remove(p.getUuid());
        if (req == null || (System.currentTimeMillis() - req.timestamp() > 60_000L)) {
            p.sendMessage(Text.literal("§c[TPA] No tienes ninguna solicitud pendiente para rechazar."), false);
            return 0;
        }

        TPA_ENVIADAS.remove(req.solicitanteUuid());
        p.sendMessage(Text.literal("§c[TPA] Has rechazado la solicitud de teletransporte de §e" + req.solicitanteNombre() + "§c."), false);

        ServerPlayerEntity sender = p.getServer().getPlayerManager().getPlayer(req.solicitanteUuid());
        if (sender != null && !sender.isDisconnected()) {
            sender.sendMessage(Text.literal("§c[TPA] §e" + p.getName().getString() + "§c ha rechazado tu solicitud de teletransporte."), false);
        }

        return 1;
    }

    private static int ejecutarTpCancel(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        UUID targetUuid = TPA_ENVIADAS.remove(p.getUuid());
        if (targetUuid == null) {
            p.sendMessage(Text.literal("§c[TPA] No tienes ninguna solicitud saliente activa para cancelar."), false);
            return 0;
        }

        TPA_RECIBIDAS.remove(targetUuid);
        p.sendMessage(Text.literal("§e[TPA] Has cancelado tu solicitud de teletransporte."), false);

        ServerPlayerEntity target = p.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target != null && !target.isDisconnected()) {
            target.sendMessage(Text.literal("§7[TPA] §e" + p.getName().getString() + "§7 canceló su solicitud de teletransporte."), false);
        }

        return 1;
    }
}
