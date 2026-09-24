package net.pokereport.luna.ui;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * La lista de jugadores: cabecera, pie y etiqueta de rango junto a cada nombre.
 *
 * <p>Los rangos se aplican con <b>equipos del marcador</b>, no con paquetes.
 * Es la vía correcta aquí porque un equipo lleva prefijo y, además, el nombre
 * del equipo <b>ordena la lista</b> — por eso van numerados (<code>00_</code>,
 * <code>10_</code>…): así los rangos altos salen arriba sin escribir ninguna
 * lógica de ordenación.
 *
 * <p>Ventaja añadida: el prefijo aparece también sobre la cabeza del jugador y
 * en el chat, sin trabajo extra.
 *
 * <p>Al contrario que la barra lateral, aquí los equipos <b>sí</b> son globales
 * y debe ser así: el rango de un jugador lo ve todo el mundo, que es justo lo
 * que lo convierte en estatus ({@code docs/analysis/diosesmon-analysis.md} §4).
 */
public final class Tablist {

    /**
     * Rangos. El prefijo numérico <b>solo</b> sirve para ordenar la lista y
     * nunca se muestra.
     */
    public enum Rank {
        // --- de equipo. NO se ganan jugando y no desbloquean nada del juego:
        //     un moderador no tiene mas mochila por ser moderador.
        ADMIN     ("00_admin",     "\uE015", Formatting.DARK_RED,     "ADMIN",      -1, true),
        DEV       ("10_dev",       "\uE016", Formatting.LIGHT_PURPLE, "DEV",        -1, true),
        MODERADOR ("20_mod",       "\uE017", Formatting.DARK_GREEN,   "MOD",        -1, true),

        // --- de jugador, de mayor a menor (decision del usuario, 2026-08-26).
        LEYENDA   ("30_leyenda",   "\uE014", Formatting.GOLD,         "LEYENDA",    5, false),
        MAESTRO   ("40_maestro",   "\uE013", Formatting.DARK_PURPLE,  "MAESTRO",    4, false),
        CAMPEON   ("50_campeon",   "\uE012", Formatting.AQUA,         "CAMPEÓN",    3, false),
        ELITE     ("60_elite",     "\uE011", Formatting.GREEN,        "ÉLITE",      2, false),
        ENTRENADOR("90_entrenador", "\uE010", Formatting.WHITE,        "ENTRENADOR", 1, false);

        public final String teamName;
        /** Glifo visible de la insignia antes del nombre. */
        public final String tag;
        /** Color del propio nombre y del equipo. */
        public final Formatting color;
        /** Nombre legible del rango. */
        public final String titulo;
        /**
         * Nivel de progresion, 1 el mas bajo. <b>-1 en los de equipo</b>: un
         * rango de staff no es «mas alto» que LEYENDA, es de otra clase.
         */
        public final int escalon;
        public final boolean equipo;

        Rank(String teamName, String tag, Formatting color, String titulo, int escalon,
             boolean equipo) {
            this.teamName = teamName;
            this.tag = tag;
            this.color = color;
            this.titulo = titulo;
            this.escalon = escalon;
            this.equipo = equipo;
        }

        public MutableText badge() {
            return Iconos.glifo(this.tag);
        }

        public MutableText conNombre() {
            return Text.empty()
                    .append(badge())
                    .append(Text.literal(" " + this.titulo).formatted(this.color));
        }

        /** El rango por defecto. Todo el mundo empieza aqui. */
        public static Rank porDefecto() {
            return ENTRENADOR;
        }

        /**
         * El rango con ese nombre, o {@link #ENTRENADOR}.
         */
        public static Rank de(String nombre) {
            if (nombre != null) {
                String n = nombre.trim();
                for (Rank r : values()) {
                    if (r.name().equalsIgnoreCase(n)) {
                        return r;
                    }
                }
                if (n.equalsIgnoreCase("MOD")) {
                    return MODERADOR;
                }
            }
            if (nombre != null && !nombre.isBlank()) {
                net.pokereport.luna.LunaEternal.LOG.warn(
                    "Rango desconocido «{}»: se usa {}", nombre, porDefecto());
            }
            return porDefecto();
        }

        /** Los que un jugador puede tener, de mayor a menor. */
        public static java.util.List<Rank> deJugador() {
            var salida = new java.util.ArrayList<Rank>();
            for (Rank r : values()) {
                if (!r.equipo) {
                    salida.add(r);
                }
            }
            return salida;
        }

        /**
         * Nivel comercial estricto para Tebex:
         * NONE (ENTRENADOR) = 0, ELITE = 1, CAMPEON = 2, MAESTRO = 3, LEYENDA = 4.
         * Devuelve -1 para rangos administrativos/staff (equipo = true).
         */
        public int nivelComercial() {
            return switch (this) {
                case ENTRENADOR -> 0;
                case ELITE -> 1;
                case CAMPEON -> 2;
                case MAESTRO -> 3;
                case LEYENDA -> 4;
                default -> -1;
            };
        }

        /** ¿Es un rango comercial de pago (Elite, Campeón, Maestro, Leyenda)? */
        public boolean esComercial() {
            return !this.equipo && this != ENTRENADOR;
        }

        /** ¿Es un rango de staff u operativo (Admin, Dev, Mod)? */
        public boolean esStaff() {
            return this.equipo;
        }
    }

    public record ClanInfo(String etiqueta, char color) {}
    private static final java.util.Map<java.util.UUID, ClanInfo> CLAN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static ClanInfo clanDe(ServerPlayerEntity player) {
        if (player == null) return null;
        return CLAN_CACHE.get(player.getUuid());
    }

    private Tablist() {}

    /** Prefijo de equipo con el glifo de insignia en fuente Iconos.FUENTE y espacio. */
    public static MutableText prefixOf(Rank rank) {
        return Text.empty()
                .append(Iconos.glifo(rank.tag))
                .append(Text.literal(" "));
    }

    /** Registra el manejador de chat para que el clan solo aparezca en el chat. */
    public static void initChat() {
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            ClanInfo clan = clanDe(sender);
            net.minecraft.text.MutableText chatMsg = net.minecraft.text.Text.empty();
            if (clan != null && !clan.etiqueta().isEmpty()) {
                chatMsg.append(net.minecraft.text.Text.literal("§" + clan.color() + "[" + clan.etiqueta() + "] "));
            }
            chatMsg.append(sender.getDisplayName())
                   .append(net.minecraft.text.Text.literal("§7: §f"))
                   .append(message.getContent());
            sender.server.getPlayerManager().broadcast(chatMsg, false);
            return false;
        });
    }

    /** Crea los equipos ordenados una sola vez, al arrancar el servidor. */
    public static void setup(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        // Limpiar antiguos equipos temporales luna<hash> si existieran
        for (Team t : new java.util.ArrayList<>(scoreboard.getTeams())) {
            if (t.getName().startsWith("luna")) {
                scoreboard.removeTeam(t);
            }
        }
        for (Rank rank : Rank.values()) {
            Team team = scoreboard.getTeam(rank.teamName);
            if (team == null) team = scoreboard.addTeam(rank.teamName);
            team.setPrefix(prefixOf(rank));
            team.setColor(rank.color);
            team.setSuffix(Text.empty());
        }
    }

    /**
     * Aplica el rango en el scoreboard (ordena la lista por rango y muestra solo la insignia y el nombre).
     * El clan se guarda en cache para mostrarse exclusivamente en el chat.
     */
    public static void aplicarEtiqueta(MinecraftServer server, ServerPlayerEntity player,
                                       String etiqueta, char color) {
        var scoreboard = server.getScoreboard();
        String nombre = player.getGameProfile().getName();

        Rank rank = rankOf(server, player);
        Team team = scoreboard.getTeam(rank.teamName);
        if (team == null) {
            team = scoreboard.addTeam(rank.teamName);
        }
        team.setPrefix(prefixOf(rank));
        team.setColor(rank.color);
        team.setSuffix(Text.empty());
        scoreboard.addScoreHolderToTeam(nombre, team);

        if (etiqueta != null && !etiqueta.isEmpty()) {
            CLAN_CACHE.put(player.getUuid(), new ClanInfo(etiqueta, color));
        } else {
            CLAN_CACHE.remove(player.getUuid());
        }
    }

    /**
     * Lee el clan del jugador y le pone la etiqueta. <b>Va por el hilo de E/S.</b>
     *
     * <p>Se usa al entrar. Para los cambios en vivo —fundar, entrar, salir, que
     * te echen— quien refresca es {@code Red.refrescarA}, que ya sabe el clan y
     * no necesita volver a consultarlo.
     *
     * <p>⚠ Si la consulta falla, se pone la etiqueta <b>vacía</b> en vez de no
     * poner nada. No poner nada deja el prefijo anterior, y el prefijo anterior
     * de un jugador que acaba de entrar es el que dejó otro con el mismo hash.
     */
    public static void refrescarClan(MinecraftServer server, ServerPlayerEntity player) {
        var uuid = player.getUuid();
        var nombre = player.getName().getString();
        net.pokereport.luna.LunaEternal.submit(() -> {
            String etiqueta = "";
            char color = 'b';
            try {
                long id = net.pokereport.luna.LunaEternal.players().resolve(uuid, nombre);
                var clan = net.pokereport.luna.LunaEternal.clans().clanDe(id);
                if (clan != null) {
                    etiqueta = clan.etiqueta();
                    color = clan.color();
                }
            } catch (Exception e) {
                // Sin etiqueta se ve el nombre a secas. Que falle esto no puede
                // costarle a nadie la entrada al servidor.
                net.pokereport.luna.LunaEternal.LOG.debug(
                        "Sin etiqueta de clan para {}: {}", nombre, e.toString());
            }
            final String et = etiqueta;
            final char co = color;
            server.execute(() -> {
                if (!player.isRemoved()) {
                    aplicarEtiqueta(server, player, et, co);
                }
            });
        });
    }

    /**
     * Rango del jugador.
     *
     * <h2>⚠⚠ SALE DE LA CACHE, NO DE LA BASE</h2>
     *
     * Esto lo llama el dibujado del tablist y el prefijo del chat, o sea
     * <b>muchas veces y en el hilo del servidor</b>. Consultar la base aqui
     * seria tocar la base desde el hilo del servidor, que es justo lo que la
     * primera regla del proyecto prohibe.
     *
     * <p>La cache la rellena {@code RankService} al entrar, y se actualiza
     * cuando alguien cambia de rango.
     *
     * <p>⚠ Un operador se ve como ADMIN <b>ademas</b> de su rango guardado: es
     * informacion operativa y tiene que verse. Pero para lo que DESBLOQUEA
     * cuenta el guardado ({@link #escalonDe}) -- si no, darle OP a alguien para
     * mirar una cosa le regalaria la mochila entera.
     */
    private static final java.util.Map<java.util.UUID, Rank> TEST_RANKS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setTestRank(ServerPlayerEntity player, Rank rank) {
        if (rank == null) {
            TEST_RANKS.remove(player.getUuid());
        } else {
            TEST_RANKS.put(player.getUuid(), rank);
        }
        refrescarClan(player.server, player);
    }

    public static Rank rankOf(MinecraftServer server, ServerPlayerEntity player) {
        Rank test = TEST_RANKS.get(player.getUuid());
        if (test != null) {
            return test;
        }
        if (server.getPlayerManager().isOperator(player.getGameProfile())) {
            return Rank.ADMIN;
        }
        return net.pokereport.luna.rank.RankService.enCache(player.getUuid());
    }

    /**
     * El escalon que de verdad tiene, para decidir que desbloquea.
     *
     * <p>⚠ NO usa {@link #rankOf}, y esa es la diferencia importante: alli un
     * operador sale como ADMIN, que tiene escalon -1. Aqui manda lo guardado.
     */
    public static Rank rangoDe(ServerPlayerEntity player) {
        return net.pokereport.luna.rank.RankService.enCache(player.getUuid());
    }

    public static int escalonDe(ServerPlayerEntity player) {
        return rangoDe(player).escalon;
    }

    /** Cabecera y pie. Se reenvía cuando cambia el número de conectados. */
    public static void updateHeaderFooter(MinecraftServer server) {
        int online = server.getCurrentPlayerCount();

        MutableText header = Text.literal("¡Hay ").setStyle(Style.EMPTY.withColor(Formatting.WHITE).withItalic(false))
                .append(Text.literal(String.valueOf(online)).setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withItalic(false)))
                .append(Text.literal(" usuario(s) jugando!\n").setStyle(Style.EMPTY.withColor(Formatting.WHITE).withItalic(false)));

        MutableText footer = Text.literal("\n")
                .append(Text.literal("Accede a nuestra ").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withItalic(false)))
                .append(Text.literal("TIENDA").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true).withItalic(false)))
                .append(Text.literal(" desde\n").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withItalic(false)))
                .append(Text.literal("TIENDA OFICIAL\n").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true).withItalic(false)))
                .append(Text.literal("pokereport.online").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true).withItalic(false)));

        var packet = new PlayerListHeaderS2CPacket(header, footer);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(packet);
        }
    }

    /** Todo lo del tablist para un jugador que acaba de entrar. */
    public static void onJoin(MinecraftServer server, ServerPlayerEntity player) {
        aplicarEtiqueta(server, player, "", 'b');
        updateHeaderFooter(server);
    }

    public static void onLeave(MinecraftServer server, ServerPlayerEntity player) {
        TEST_RANKS.remove(player.getUuid());
        CLAN_CACHE.remove(player.getUuid());
        server.getScoreboard().clearTeam(player.getGameProfile().getName());
        server.execute(() -> updateHeaderFooter(server));
    }
}
