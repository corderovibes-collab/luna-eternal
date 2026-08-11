package net.pokereport.luna.ui;

import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.pokereport.luna.economy.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Barra lateral: el progreso visible sin abrir nada
 * ({@code docs/ui/navigation.md} §3).
 *
 * <p>Se envía por paquetes directos al jugador, no con el marcador del
 * servidor. El motivo es que <b>los huecos de visualización del marcador de
 * Minecraft son globales</b>: con la API normal, todos los jugadores verían la
 * misma barra. Enviando paquetes, cada cliente construye su propia vista y
 * nunca se registra nada en el marcador real del mundo — así tampoco
 * interferimos con datapacks o comandos que lo usen.
 *
 * <p>Solo se reenvía cuando el contenido cambia de verdad. Mandar quince
 * paquetes por segundo a cada jugador para repintar lo mismo es tráfico
 * desperdiciado y se nota con gente conectada.
 */
public final class Sidebar {

    /** Mismo nombre para todos: cada cliente solo conoce el suyo. */
    private static final String OBJECTIVE = "luna_sidebar";

    /** Última barra enviada a cada jugador, para no repetir paquetes. */
    private static final Map<UUID, List<String>> lastSent = new ConcurrentHashMap<>();

    private Sidebar() {}

    /** Crea la barra en el cliente. Se llama una vez al conectar. */
    public static void install(ServerPlayerEntity player) {
        ScoreboardObjective objective = objective(player);
        player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(
            objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
        player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(
            ScoreboardDisplaySlot.SIDEBAR, objective));
        lastSent.remove(player.getUuid());
    }

    public static void remove(ServerPlayerEntity player) {
        lastSent.remove(player.getUuid());
    }

    /** Redibuja si algo cambió. Barato de llamar a menudo. */
    public static void update(ServerPlayerEntity player, PlayerSnapshot data) {
        List<String> lines = render(data);
        List<String> previous = lastSent.get(player.getUuid());
        if (Objects.equals(previous, lines)) return;

        // Las líneas sobrantes de un dibujado anterior hay que borrarlas, o se
        // quedan colgando debajo cuando la barra se acorta.
        if (previous != null && previous.size() > lines.size()) {
            for (int i = lines.size(); i < previous.size(); i++) {
                player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket(
                        holder(i), OBJECTIVE));
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            // La puntuación ordena: la primera línea debe quedar arriba.
            int score = lines.size() - i;
            player.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                holder(i),
                OBJECTIVE,
                score,
                Optional.of(plain(lines.get(i))),
                Optional.empty()));
        }
        lastSent.put(player.getUuid(), lines);
    }

    // ------------------------------------------------------------ contenido

    private static List<String> render(PlayerSnapshot d) {
        List<String> l = new ArrayList<>();

        // La fase lunar va arriba del todo: es el pilar del juego, y así el
        // jugador ve el estado del mundo cada vez que mira la pantalla.
        l.add("§8" + "─".repeat(15));
        l.add(" " + moonIcon(d.moonPhase) + " §f" + d.moonName());
        l.add("§8" + "─".repeat(15));

        l.add(" §6" + fmt(d.balance(Currency.POKEDOLLAR)) + " §7PokéDólares");
        l.add(" §b" + fmt(d.balance(Currency.MARK)) + " §7Marcas");
        l.add(" §e" + fmt(d.balance(Currency.REPORTCOIN)) + " §7ReportCoins");
        l.add("§8" + "─".repeat(15));

        l.add(" §7Vía: §f" + d.dominantPath);
        l.add(" §7Clan: §f" + orNone(d.clan));
        l.add(" §7Oficio: §f" + orNone(d.job));
        l.add("§8" + "─".repeat(15));

        l.add(" §7Medallas: §f" + d.badges + "§8/8");

        // Sin "¡Cómpralo!" ni "¡MUY PRONTO!": un hueco vacío informa,
        // no vende ni anuncia lo que no existe (diosesmon-analysis.md §0).
        return l;
    }

    private static String moonIcon(int phase) {
        return switch (phase) {
            case 0 -> "§e●";
            case 4 -> "§8○";
            default -> "§7◐";
        };
    }

    // ------------------------------------------------------------ interno

    private static ScoreboardObjective objective(ServerPlayerEntity player) {
        return new ScoreboardObjective(
            player.getScoreboard(),
            OBJECTIVE,
            ScoreboardCriterion.DUMMY,
            plain("§8✦ §6LUNA ETERNAL §8✦"),
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            null);
    }

    /**
     * Portador único e invisible por línea. El texto real va en el campo de
     * visualización del paquete, así que el nombre nunca se ve.
     */
    private static String holder(int index) {
        return "luna." + index;
    }

    private static Text plain(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withItalic(false));
    }

    private static String orNone(String s) {
        return (s == null || s.isBlank()) ? "§8ninguno" : s;
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
