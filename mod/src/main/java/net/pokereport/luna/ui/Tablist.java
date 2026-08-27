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
        // --- de equipo. NO se ganan jugando y no desbloquean nada del juego:
        //     un moderador no tiene mas mochila por ser moderador.
        ADMIN     ("00_admin",  "§4§lADMIN", "§4", -1, true),
        DEV       ("10_dev",    "§d§lDEV",   "§d", -1, true),
        MODERADOR ("20_mod",    "§2§lMOD",   "§2", -1, true),

        // --- de jugador, de mayor a menor (decision del usuario, 2026-08-26).
        //
        // ⚠ El `escalon` es lo que ORDENA, y es un numero explicito en vez del
        //   `ordinal()`: con `ordinal()`, meter un rango en medio le cambia el
        //   nivel a todos los de abajo -- que es exactamente lo que la leccion
        //   del ENUM de MariaDB dice que no hagamos.
        LEYENDA   ("30_leyenda", "§6§lLEYENDA", "§6", 5, false),
        MAESTRO   ("40_maestro", "§5§lMAESTRO", "§5", 4, false),
        CAMPEON   ("50_campeon", "§b§lCAMPEÓN", "§b", 3, false),
        ELITE     ("60_elite",   "§a§lÉLITE",   "§a", 2, false),
        NOVATO    ("90_novato",  "§7NOVATO",    "§f", 1, false);

        public final String teamName;
        /** Etiqueta visible antes del nombre. */
        public final String tag;
        /** Color del propio nombre. */
        public final String nameColor;
        /**
         * Nivel de progresion, 1 el mas bajo. <b>-1 en los de equipo</b>: un
         * rango de staff no es «mas alto» que LEYENDA, es de otra clase.
         */
        public final int escalon;
        public final boolean equipo;

        Rank(String teamName, String tag, String nameColor, int escalon,
             boolean equipo) {
            this.teamName = teamName;
            this.tag = tag;
            this.nameColor = nameColor;
            this.escalon = escalon;
            this.equipo = equipo;
        }

        /** El rango por defecto. Todo el mundo empieza aqui. */
        public static Rank porDefecto() {
            return NOVATO;
        }

        /**
         * El rango con ese nombre, o {@link #NOVATO}.
         *
         * <p>⚠ Un nombre que no exista devuelve NOVATO y <b>lo dice en el
         * log</b>. Devolverlo en silencio convertiria un error de tecleo en
         * «este jugador perdio su rango», que es la clase de fallo que nadie
         * relaciona con su causa.
         */
        public static Rank de(String nombre) {
            if (nombre != null) {
                for (Rank r : values()) {
                    if (r.name().equalsIgnoreCase(nombre.trim())) {
                        return r;
                    }
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

    /**
     * Pone el prefijo del jugador: <b>rango + clan, juntos</b>.
     *
     * <h2>⚠⚠ Antes eran DOS funciones peleándose por el mismo hueco</h2>
     *
     * Un jugador solo puede estar en <b>un</b> equipo de marcador, y había dos
     * sitios metiéndole en uno: {@code applyRank} en el equipo de su rango y
     * {@code applyClanTag} en el suyo propio. Ganaba el último que corriera —
     * que es una forma elegante de decir que dependía del orden de dos llamadas
     * asíncronas. Si {@code applyRank} llegaba después, <b>la etiqueta del clan
     * desaparecía sin que nada fallara</b>.
     *
     * <p>Hoy hay una sola función y un solo equipo <b>por jugador</b>, y el
     * prefijo lleva las dos cosas. Un equipo por combinación de rango y clan
     * multiplicaría los equipos por los clanes que haya, y habría que crearlos y
     * borrarlos a mano cada vez que se funda o se disuelve uno.
     *
     * <p>Un equipo por jugador suena a mucho y no lo es: son diez jugadores como
     * máximo en este servidor, y un equipo de marcador es una fila en memoria.
     *
     * <p>⚠ Se hace con marcador y no con un paquete nuestro porque el prefijo lo
     * pinta <b>vanilla</b> en los tres sitios a la vez —chat, tablist y sobre la
     * cabeza—. Un paquete propio solo lo verían los que tengan el mod.
     *
     * @param etiqueta la del clan, o cadena vacía si no tiene. <b>Vacía es un
     *                 valor, no una ausencia</b>: es como se le quita a quien
     *                 acaban de echar.
     */
    public static void aplicarEtiqueta(MinecraftServer server, ServerPlayerEntity player,
                                       String etiqueta, char color) {
        var scoreboard = server.getScoreboard();
        String nombre = player.getGameProfile().getName();

        // ⚠ El nombre del equipo va acotado a 16: es el máximo del marcador y
        //   pasarse lanza. Un nombre de Minecraft ya son 16, así que no cabe
        //   entero con un prefijo -- se usa un hash corto y estable.
        String equipo = "luna" + Integer.toHexString(nombre.hashCode() & 0xFFFFFF);

        Team team = scoreboard.getTeam(equipo);
        if (team == null) {
            team = scoreboard.addTeam(equipo);
        }
        Rank rank = rankOf(server, player);
        String prefijo = rank.tag + " ";
        if (etiqueta != null && !etiqueta.isEmpty()) {
            prefijo += "\u00a7" + color + "[" + etiqueta + "] ";
        }
        team.setPrefix(plain(prefijo + rank.nameColor));
        team.setSuffix(plain(""));
        scoreboard.addScoreHolderToTeam(nombre, team);
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
    public static Rank rankOf(MinecraftServer server, ServerPlayerEntity player) {
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
    public static int escalonDe(ServerPlayerEntity player) {
        return net.pokereport.luna.rank.RankService.enCache(player.getUuid()).escalon;
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
        // ⚠ Se pone el prefijo SIN clan de entrada, y `refrescarClan` lo
        //   completa cuando la base conteste. Así, entre que entra y que llega
        //   la respuesta se ve su rango en vez de no verse nada -- y sobre todo
        //   no se ve el prefijo que dejó otro jugador con el mismo hash.
        aplicarEtiqueta(server, player, "", 'b');
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
