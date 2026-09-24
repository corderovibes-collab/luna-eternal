package net.pokereport.luna.homes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
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
import net.pokereport.luna.command.RankCommands;
import net.pokereport.luna.rank.RankService;
import net.pokereport.luna.ui.Tablist.Rank;
import net.pokereport.luna.world.Espera;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.Traslado;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HomeCommands {

    private HomeCommands() {}

    public static void registrar(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /sethome [nombre]
        dispatcher.register(
            CommandManager.literal("sethome")
                .executes(ctx -> ejecutarSetHome(ctx, "home"))
                .then(CommandManager.argument("nombre", StringArgumentType.word())
                    .executes(ctx -> ejecutarSetHome(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );

        // /home [nombre]
        dispatcher.register(
            CommandManager.literal("home")
                .executes(ctx -> ejecutarHome(ctx, "home"))
                .then(CommandManager.argument("nombre", StringArgumentType.word())
                    .suggests(HomeCommands::sugerirHomesPropios)
                    .executes(ctx -> ejecutarHome(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );

        // /delhome <nombre>
        dispatcher.register(
            CommandManager.literal("delhome")
                .then(CommandManager.argument("nombre", StringArgumentType.word())
                    .suggests(HomeCommands::sugerirHomesPropios)
                    .executes(ctx -> ejecutarDelHome(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );

        // /homes
        dispatcher.register(
            CommandManager.literal("homes")
                .executes(HomeCommands::ejecutarListarHomes)
        );

        // /setpwarp <nombre>
        dispatcher.register(
            CommandManager.literal("setpwarp")
                .then(CommandManager.argument("nombre", StringArgumentType.word())
                    .suggests(HomeCommands::sugerirHomesPropios)
                    .executes(ctx -> ejecutarSetPwarp(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );

        // /delpwarp <nombre>
        dispatcher.register(
            CommandManager.literal("delpwarp")
                .then(CommandManager.argument("nombre", StringArgumentType.word())
                    .suggests(HomeCommands::sugerirHomesPropios)
                    .executes(ctx -> ejecutarDelPwarp(ctx, StringArgumentType.getString(ctx, "nombre"))))
        );

        // /pwarps
        dispatcher.register(
            CommandManager.literal("pwarps")
                .executes(HomeCommands::ejecutarListarPwarps)
        );

        // /pwarp <creador> <nombre>
        dispatcher.register(
            CommandManager.literal("pwarp")
                .executes(HomeCommands::ejecutarListarPwarps)
                .then(CommandManager.argument("creador", StringArgumentType.word())
                    .suggests(HomeCommands::sugerirCreadoresPwarp)
                    .then(CommandManager.argument("nombre", StringArgumentType.word())
                        .suggests(HomeCommands::sugerirNombresPwarp)
                        .executes(ctx -> ejecutarPwarp(ctx,
                            StringArgumentType.getString(ctx, "creador"),
                            StringArgumentType.getString(ctx, "nombre")))))
        );
    }

    private static int ejecutarSetHome(CommandContext<ServerCommandSource> ctx, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        // 1. REGLA ESTRICTA: Solo en dimensión Hogar
        if (!p.getServerWorld().getRegistryKey().equals(LunaDimensions.HOGAR)) {
            p.sendMessage(Text.literal("§c[Hogar] Solo puedes establecer hogares dentro del mundo Hogar."), false);
            return 0;
        }

        // 2. Comprobación de nombre
        if (nombre.length() > 16 || !nombre.matches("^[a-zA-Z0-9_-]+$")) {
            p.sendMessage(Text.literal("§c[Hogar] El nombre debe tener entre 1 y 16 caracteres alfanuméricos."), false);
            return 0;
        }

        long pid = resolverPlayerId(p);
        if (pid <= 0) {
            p.sendMessage(Text.literal("§c[Hogar] Tu perfil aún se está cargando, inténtalo de nuevo en unos segundos."), false);
            return 0;
        }

        Rank rank = RankService.enCache(p.getUuid());
        boolean isStaff = p.hasPermissionLevel(2) || rank.equipo;
        int maxHomes = HomeService.limiteHomes(rank, isStaff);

        if (maxHomes <= 0) {
            p.sendMessage(Text.literal("§c[Hogar] Tu rango actual (" + rank.titulo + ") no permite crear hogares. Mejora a rango Élite o superior."), false);
            return 0;
        }

        HomeService svc = LunaEternal.homes();
        int actuales = svc.contarHomes(pid);
        boolean yaExiste = svc.getHome(pid, nombre) != null;

        if (!yaExiste && actuales >= maxHomes) {
            p.sendMessage(Text.literal("§c[Hogar] Has alcanzado el límite de " + maxHomes + " hogares para tu rango (" + rank.titulo + ")."), false);
            return 0;
        }

        Vec3d pos = p.getPos();
        svc.guardarHome(pid, p.getName().getString(), p.getUuid(), nombre,
            LunaDimensions.HOGAR.getValue().toString(),
            pos.x, pos.y, pos.z, p.getYaw(), p.getPitch(),
            () -> p.sendMessage(Text.literal("§a[Hogar] Hogar '§e" + nombre + "§a' establecido correctamente (" + (yaExiste ? actuales : actuales + 1) + "/" + maxHomes + ")."), false),
            err -> p.sendMessage(Text.literal("§c[Hogar] Error al guardar el hogar en la base de datos."), false)
        );

        return 1;
    }

    private static int ejecutarHome(CommandContext<ServerCommandSource> ctx, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        // Comprobación de combate Pokémon
        if (enCombatePokemon(p)) {
            p.sendMessage(Text.literal("§c[Hogar] No puedes viajar a tu hogar mientras estás en combate Pokémon."), false);
            return 0;
        }

        long pid = resolverPlayerId(p);
        if (pid <= 0) {
            p.sendMessage(Text.literal("§c[Hogar] Tu perfil aún se está cargando."), false);
            return 0;
        }

        HomeService svc = LunaEternal.homes();
        HomeService.Home h = svc.getHome(pid, nombre);
        if (h == null) {
            p.sendMessage(Text.literal("§c[Hogar] No tienes ningún hogar llamado '§e" + nombre + "§c'."), false);
            return 0;
        }

        ServerWorld destWorld = p.getServer().getWorld(LunaDimensions.HOGAR);
        if (destWorld == null) {
            p.sendMessage(Text.literal("§c[Hogar] El mundo Hogar no está disponible."), false);
            return 0;
        }

        // Si está en Hogar, apuntamos para /back
        if (p.getServerWorld().getRegistryKey().equals(LunaDimensions.HOGAR)) {
            RankCommands.guardarBack(p);
        }

        Vec3d destino = new Vec3d(h.x(), h.y(), h.z());
        Espera.pedir(p, "viajar a tu hogar '" + nombre + "'", () -> {
            Traslado.ir(p, destWorld, destino, h.yaw(), h.pitch());
            p.sendMessage(Text.literal("§a[Hogar] Bienvenido a tu hogar '§e" + nombre + "§a'."), false);
        });

        return 1;
    }

    private static int ejecutarDelHome(CommandContext<ServerCommandSource> ctx, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        long pid = resolverPlayerId(p);
        if (pid <= 0) return 0;

        HomeService svc = LunaEternal.homes();
        if (svc.getHome(pid, nombre) == null) {
            p.sendMessage(Text.literal("§c[Hogar] No tienes ningún hogar llamado '§e" + nombre + "§c'."), false);
            return 0;
        }

        svc.borrarHome(pid, p.getName().getString(), nombre,
            () -> p.sendMessage(Text.literal("§a[Hogar] Hogar '§e" + nombre + "§a' eliminado correctamente."), false),
            err -> p.sendMessage(Text.literal("§c[Hogar] Error al eliminar el hogar."), false)
        );

        return 1;
    }

    private static int ejecutarListarHomes(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        long pid = resolverPlayerId(p);
        if (pid <= 0) return 0;

        HomeService svc = LunaEternal.homes();
        List<HomeService.Home> lista = svc.getHomes(pid);
        Rank rank = RankService.enCache(p.getUuid());
        boolean isStaff = p.hasPermissionLevel(2) || rank.equipo;
        int maxHomes = HomeService.limiteHomes(rank, isStaff);

        p.sendMessage(Text.literal("§6§m----------------§r §e§lTus Hogares (" + lista.size() + "/" + maxHomes + ") §6§m----------------"), false);
        if (lista.isEmpty()) {
            p.sendMessage(Text.literal("§7No tienes ningún hogar creado. Usa §e/sethome <nombre>§7 para guardar uno."), false);
            return 1;
        }

        for (HomeService.Home h : lista) {
            MutableText fila = Text.literal("§8▪ §e" + h.name() + (h.isPublic() ? " §a[Público]" : " §7[Privado]"));
            fila.append(Text.literal(" §a[IR] ")
                .styled(s -> s.withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + h.name()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClic para viajar a " + h.name())))));

            fila.append(Text.literal("§c[BORRAR]")
                .styled(s -> s.withColor(Formatting.RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/delhome " + h.name()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§cClic para borrar " + h.name())))));

            p.sendMessage(fila, false);
        }
        return 1;
    }

    private static int ejecutarSetPwarp(CommandContext<ServerCommandSource> ctx, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        long pid = resolverPlayerId(p);
        if (pid <= 0) return 0;

        Rank rank = RankService.enCache(p.getUuid());
        boolean isStaff = p.hasPermissionLevel(2) || rank.equipo;
        int maxPwarps = HomeService.limitePwarps(rank, isStaff);

        if (maxPwarps <= 0) {
            p.sendMessage(Text.literal("§c[Pwarp] Tu rango actual (" + rank.titulo + ") no permite publicar lugares públicos."), false);
            return 0;
        }

        HomeService svc = LunaEternal.homes();
        HomeService.Home h = svc.getHome(pid, nombre);
        if (h == null) {
            p.sendMessage(Text.literal("§c[Pwarp] No tienes ningún hogar llamado '§e" + nombre + "§c'. Primero créalo con /sethome."), false);
            return 0;
        }

        int pwarpsActuales = svc.contarPwarps(pid);
        if (!h.isPublic() && pwarpsActuales >= maxPwarps) {
            p.sendMessage(Text.literal("§c[Pwarp] Has alcanzado el límite de " + maxPwarps + " pwarps para tu rango (" + rank.titulo + ")."), false);
            return 0;
        }

        svc.setPublico(pid, p.getName().getString(), p.getUuid(), nombre, true,
            () -> p.sendMessage(Text.literal("§a[Pwarp] Tu hogar '§e" + nombre + "§a' ahora es público. Otros jugadores pueden visitarlo con §b/pwarp " + p.getName().getString() + " " + nombre), false),
            err -> p.sendMessage(Text.literal("§c[Pwarp] Error al publicar el lugar."), false)
        );

        return 1;
    }

    private static int ejecutarDelPwarp(CommandContext<ServerCommandSource> ctx, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        long pid = resolverPlayerId(p);
        if (pid <= 0) return 0;

        HomeService svc = LunaEternal.homes();
        HomeService.Home h = svc.getHome(pid, nombre);
        if (h == null || !h.isPublic()) {
            p.sendMessage(Text.literal("§c[Pwarp] No tienes ningún pwarp público llamado '§e" + nombre + "§c'."), false);
            return 0;
        }

        svc.setPublico(pid, p.getName().getString(), p.getUuid(), nombre, false,
            () -> p.sendMessage(Text.literal("§a[Pwarp] El hogar '§e" + nombre + "§a' ya no es público (sigue existiendo en tus homes privados)."), false),
            err -> p.sendMessage(Text.literal("§c[Pwarp] Error al despublicar el lugar."), false)
        );

        return 1;
    }

    private static int ejecutarListarPwarps(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        HomeService svc = LunaEternal.homes();
        List<HomeService.PwarpEntry> lista = svc.getTodosPwarps();

        p.sendMessage(Text.literal("§b§m----------------§r §3§lLugares Públicos (Pwarps: " + lista.size() + ") §b§m----------------"), false);
        if (lista.isEmpty()) {
            p.sendMessage(Text.literal("§7No hay lugares públicos disponibles en este momento."), false);
            return 1;
        }

        for (HomeService.PwarpEntry pw : lista) {
            MutableText fila = Text.literal("§8▪ §b" + pw.creadorNombre() + " §8/ §e" + pw.home().name() + " ");
            fila.append(Text.literal("§a[VISITAR]")
                .styled(s -> s.withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pwarp " + pw.creadorNombre() + " " + pw.home().name()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClic para viajar al lugar de " + pw.creadorNombre())))));

            p.sendMessage(fila, false);
        }
        return 1;
    }

    private static int ejecutarPwarp(CommandContext<ServerCommandSource> ctx, String creador, String nombre) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return 0;

        if (enCombatePokemon(p)) {
            p.sendMessage(Text.literal("§c[Pwarp] No puedes viajar mientras estás en combate Pokémon."), false);
            return 0;
        }

        HomeService svc = LunaEternal.homes();
        HomeService.PwarpEntry pw = svc.getPwarp(creador, nombre);
        if (pw == null) {
            p.sendMessage(Text.literal("§c[Pwarp] No se encontró ningún lugar público de §e" + creador + "§c con el nombre '§e" + nombre + "§c'."), false);
            return 0;
        }

        ServerWorld destWorld = p.getServer().getWorld(LunaDimensions.HOGAR);
        if (destWorld == null) {
            p.sendMessage(Text.literal("§c[Pwarp] El mundo Hogar no está disponible."), false);
            return 0;
        }

        if (p.getServerWorld().getRegistryKey().equals(LunaDimensions.HOGAR)) {
            RankCommands.guardarBack(p);
        }

        HomeService.Home h = pw.home();
        Vec3d destino = new Vec3d(h.x(), h.y(), h.z());
        Espera.pedir(p, "viajar al pwarp de " + pw.creadorNombre(), () -> {
            Traslado.ir(p, destWorld, destino, h.yaw(), h.pitch());
            p.sendMessage(Text.literal("§a[Pwarp] Has llegado al lugar público '§e" + h.name() + "§a' de §b" + pw.creadorNombre() + "§a."), false);
        });

        return 1;
    }

    private static CompletableFuture<Suggestions> sugerirHomesPropios(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) return builder.buildFuture();
        long pid = resolverPlayerId(p);
        if (pid > 0) {
            for (HomeService.Home h : LunaEternal.homes().getHomes(pid)) {
                builder.suggest(h.name());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> sugerirCreadoresPwarp(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (HomeService.PwarpEntry pw : LunaEternal.homes().getTodosPwarps()) {
            builder.suggest(pw.creadorNombre());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> sugerirNombresPwarp(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            String creador = StringArgumentType.getString(ctx, "creador");
            for (HomeService.PwarpEntry pw : LunaEternal.homes().getTodosPwarps()) {
                if (pw.creadorNombre().equalsIgnoreCase(creador)) {
                    builder.suggest(pw.home().name());
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static long resolverPlayerId(ServerPlayerEntity p) {
        try {
            return LunaEternal.players().resolve(p.getUuid(), p.getName().getString());
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean enCombatePokemon(ServerPlayerEntity p) {
        try {
            return com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(p) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
