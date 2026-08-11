package net.pokereport.luna.ui;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

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
        ADMIN     ("00_admin",     "§4§lADMIN",      "§4"),
        DEV       ("10_dev",       "§d§lDEV",        "§d"),
        MODERADOR ("20_mod",       "§2§lMOD",        "§2"),
        MAESTRO   ("30_maestro",   "§5§lMAESTRO",    "§5"),
        LIDER     ("40_lider",     "§6§lLÍDER",      "§6"),
        AS        ("50_as",        "§b§lAS",         "§b"),
        ENTRENADOR("60_entrenador","§c§lENTRENADOR", "§c"),
        JUGADOR   ("90_jugador",   "§7JUGADOR",      "§f");

        public final String teamName;
        /** Etiqueta visible antes del nombre. */
        public final String tag;
        /** Color del propio nombre. */
        public final String nameColor;

        Rank(String teamName, String tag, String nameColor) {
            this.teamName = teamName;
            this.tag = tag;
            this.nameColor = nameColor;
        }
    }

    private Tablist() {}

    /** Crea los equipos una sola vez, al arrancar el servidor. */
    public static void setup(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        for (Rank rank : Rank.values()) {
            Team team = scoreboard.getTeam(rank.teamName);
            if (team == null) team = scoreboard.addTeam(rank.teamName);
            team.setPrefix(plain(rank.tag + " " + rank.nameColor));
            team.setSuffix(plain(""));
        }
    }

    /** Asigna el rango de un jugador. */
    public static void applyRank(MinecraftServer server, ServerPlayerEntity player, Rank rank) {
        var scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(rank.teamName);
        if (team == null) {
            setup(server);
            team = scoreboard.getTeam(rank.teamName);
            if (team == null) return;
        }
        scoreboard.addScoreHolderToTeam(player.getGameProfile().getName(), team);
    }

    /**
     * Rango del jugador. Provisional: hasta que exista el sistema de rangos
     * (PHASE 10) todo el mundo es JUGADOR salvo los operadores.
     */
    public static Rank rankOf(MinecraftServer server, ServerPlayerEntity player) {
        if (server.getPlayerManager().isOperator(player.getGameProfile())) return Rank.ADMIN;
        return Rank.JUGADOR;
    }

    /** Cabecera y pie. Se reenvía cuando cambia el número de conectados. */
    public static void updateHeaderFooter(MinecraftServer server) {
        int online = server.getCurrentPlayerCount();
        int max = server.getMaxPlayerCount();

        Text header = plain("""
            §8§m                                        §r
            §6§l✦ POKEREPORT §f§lLUNA ETERNAL §6§l✦
            §7Hay §f%d §7de §f%d §7entrenadores conectados
            §8§m                                        §r"""
            .formatted(online, max));

        // Sin "¡Cómpralo!" ni cuentas atrás de ofertas: el pie informa,
        // no vende (diosesmon-analysis.md §0).
        Text footer = plain("""
            §8§m                                        §r
            §7Abre §6El Almanaque §7con el libro de tu inventario
            §8pokereport.net§r""");

        var packet = new PlayerListHeaderS2CPacket(header, footer);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(packet);
        }
    }

    /** Todo lo del tablist para un jugador que acaba de entrar. */
    public static void onJoin(MinecraftServer server, ServerPlayerEntity player) {
        applyRank(server, player, rankOf(server, player));
        updateHeaderFooter(server);
    }

    public static void onLeave(MinecraftServer server, ServerPlayerEntity player) {
        server.getScoreboard().clearTeam(player.getGameProfile().getName());
        // El jugador que se va todavía cuenta en getCurrentPlayerCount() en
        // este instante, así que se recalcula en el tick siguiente.
    }

    private static Text plain(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withItalic(false));
    }
}
