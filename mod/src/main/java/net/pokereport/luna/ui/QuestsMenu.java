package net.pokereport.luna.ui;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.quest.Quest;
import net.pokereport.luna.quest.QuestService;

import java.util.List;

/**
 * Misiones: qué hacer ahora.
 *
 * <p>Es la respuesta a la pregunta que se hace todo jugador nuevo a los cinco
 * minutos: <i>¿y ahora qué?</i>. Las del tutorial van en orden porque enseñan
 * el bucle tal y como se juega — salir, capturar, volver, gastar, comerciar.
 */
public final class QuestsMenu extends Menu {

    private final PlayerSnapshot data;
    private final List<QuestService.State> estados;

    private QuestsMenu(PlayerSnapshot data, List<QuestService.State> estados) {
        super("§8✦ §aMisiones §8✦", 6);
        this.data = data;
        this.estados = estados;
    }

    public static void open(ServerPlayerEntity player, Menu parent) {
        MenuService.loadSnapshot(player, snap -> LunaEternal.submit(() -> {
            try {
                var estados = LunaEternal.quests().allStates(snap.playerId);
                player.getServer().execute(() -> {
                    var menu = new QuestsMenu(snap, estados);
                    if (parent != null) parent.openChild(player, menu);
                    else menu.open(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error abriendo misiones", e);
                player.getServer().execute(() -> player.sendMessage(
                    Text.literal("§cNo se pudieron cargar las misiones."), false));
            }
        }));
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        long cobrables = estados.stream().filter(QuestService.State::claimable).count();

        set(4, Icon.of(Items.WRITTEN_BOOK)
                .name("§aMisiones")
                .line("§7Qué hacer ahora.")
                .blank()
                .line(cobrables > 0
                    ? "§6✦ Tienes " + cobrables + " recompensa(s) sin cobrar"
                    : "§8Completa objetivos para cobrar recompensas.")
                .build());

        dibujarCadena(1, "tutorial", "§a✦ PRIMEROS PASOS");
        dibujarCadena(3, "diaria",   "§e✦ DIARIAS");

        set(48, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void dibujarCadena(int fila, String cadena, String etiqueta) {
        set(fila, 0, Icon.of(Items.WHITE_STAINED_GLASS_PANE).name(etiqueta).build(), null);

        int col = 1;
        for (var st : estados) {
            if (!st.quest().chain().equals(cadena)) continue;
            if (col > 7) break;
            dibujar(fila, col, st);
            col++;
        }
    }

    private void dibujar(int fila, int col, QuestService.State st) {
        Quest q = st.quest();
        long meta = q.objective().amount();

        // Una misión con requisito sin cobrar se muestra bloqueada, no oculta:
        // el jugador ve lo que viene después.
        boolean bloqueada = false;
        if (q.requires() != null) {
            var previa = estados.stream()
                .filter(s -> s.quest().id().equals(q.requires()))
                .findFirst().orElse(null);
            bloqueada = previa != null && !previa.claimed();
        }

        var icon = Icon.of(iconoDe(st, bloqueada))
                .name(q.name())
                .line("§7" + q.description());

        if (bloqueada) {
            icon.state(LockState.LOCKED)
                .line("§7Antes: §f" + nombreDe(q.requires()));
            set(fila, col, icon.build(), null);
            return;
        }

        icon.blank()
            .line(barra(st.fraction()))
            .line("§7Progreso: §f" + Math.min(st.progress(), meta) + "§8/" + meta);

        var r = q.rewards();
        icon.blank().line("§7Recompensa:");
        if (r.pokedollar() > 0) icon.line("§8 · §6" + fmt(r.pokedollar()) + " PokéDólares");
        if (r.mark() > 0)       icon.line("§8 · §b" + r.mark() + " Marcas");
        if (r.path() != null)   icon.line("§8 · §d" + r.xp() + " XP de " + r.path().displayName);

        if (st.claimed()) {
            icon.blank().line(q.repeatable()
                ? "§8Ya cobrada hoy. Vuelve mañana."
                : "§8Completada.");
            set(fila, col, icon.build(), null);

        } else if (st.claimable()) {
            icon.action("Clic para cobrar");
            set(fila, col, icon.build(), (p, b) -> cobrar(p, q));

        } else {
            icon.blank().line("§8Sigue jugando para completarla.");
            set(fila, col, icon.build(), null);
        }
    }

    private void cobrar(ServerPlayerEntity player, Quest q) {
        var server = player.getServer();
        LunaEternal.submit(() -> {
            try {
                boolean ok = LunaEternal.quests().claim(data.playerId, q);
                server.execute(() -> {
                    if (!ok) {
                        player.sendMessage(Text.literal(
                            "§cEsa recompensa ya estaba cobrada."), false);
                        return;
                    }
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.MASTER, 0.4f, 1.5f);
                    player.sendMessage(Text.literal(
                        "§a✦ " + q.name() + " §a— recompensa cobrada."), false);
                    QuestsMenu.open(player, null);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error cobrando la mision {}", q.id(), e);
            }
        });
    }

    private String nombreDe(String questId) {
        var q = LunaEternal.quests().byId(questId);
        return q != null ? q.name() : questId;
    }

    private static net.minecraft.item.Item iconoDe(QuestService.State st, boolean bloqueada) {
        if (bloqueada) return Items.GRAY_DYE;
        if (st.claimed()) return Items.LIME_STAINED_GLASS_PANE;
        if (st.claimable()) return Items.GOLD_INGOT;
        return Items.PAPER;
    }

    private static String barra(double f) {
        int ancho = 20;
        int lleno = (int) Math.round(f * ancho);
        return "§a" + "▮".repeat(lleno) + "§8" + "▯".repeat(ancho - lleno);
    }

    private static String fmt(long v) { return String.format("%,d", v); }
}
