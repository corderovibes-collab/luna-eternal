package net.pokereport.luna.ui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.kit.KitCatalog;
import net.pokereport.luna.kit.KitService;

import java.util.HashMap;
import java.util.Map;

/**
 * Kits: qué puedes reclamar y cuándo.
 *
 * <p>Muestra siempre el contenido exacto <b>antes</b> de reclamar, y lo que
 * falta para el siguiente. Un kit con cooldown que no dice cuánto queda
 * convierte al jugador en alguien que pincha cada dos minutos a ver si ya.
 */
public final class KitsMenu extends Menu {

    private final PlayerSnapshot data;
    private final Map<String, KitService.Status> estados;

    private KitsMenu(PlayerSnapshot data, Map<String, KitService.Status> estados) {
        super("§8✦ §aKits §8✦", 4);
        this.data = data;
        this.estados = estados;
    }

    /** Carga los estados y abre. */
    public static void open(ServerPlayerEntity player, Menu parent) {
        MenuService.loadSnapshot(player, snap -> LunaEternal.submit(() -> {
            try {
                Map<String, KitService.Status> estados = new HashMap<>();
                for (var kit : LunaEternal.kits().kits()) {
                    estados.put(kit.id(),
                        LunaEternal.kitService().status(snap.playerId, kit));
                }
                player.getServer().execute(() -> {
                    var menu = new KitsMenu(snap, estados);
                    if (parent != null) parent.openChild(player, menu);
                    else menu.open(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error abriendo los kits", e);
                player.getServer().execute(() -> player.sendMessage(
                    Text.literal("§cNo se pudieron cargar los kits."), false));
            }
        }));
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        set(4, Icon.of(Items.CHEST)
                .name("§aKits")
                .line("§7Un empujón, no un sueldo.")
                .blank()
                .line("§8Los kits nunca son la fuente principal de recursos:")
                .line("§8si lo fueran, entrarías a reclamar y te irías.")
                .build());

        int col = 1;
        for (KitCatalog.Kit kit : LunaEternal.kits().kits()) {
            if (col > 7) break;
            dibujar(col, kit, estados.get(kit.id()));
            col++;
        }

        set(27, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(35, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void dibujar(int col, KitCatalog.Kit kit, KitService.Status estado) {
        var icon = Icon.of(kit.icon()).name(kit.name())
                .line("§7" + kit.description())
                .blank()
                .line("§7Contiene:");

        for (var item : kit.items()) {
            icon.line("§8 · §f" + item.count() + "x §7"
                      + item.item().getName().getString());
        }

        // Un rango que no existe todavía no se anuncia como comprable.
        if (kit.requiredRank() != null) {
            icon.state(LockState.LOCKED)
                .line("§7Necesitas el rango: §f" + kit.requiredRank())
                .line("§8Los rangos aún no están implementados.");
            set(1, col, icon.build(), null);
            return;
        }

        if (estado == null || estado.claimable()) {
            icon.blank();
            if (kit.once()) icon.line("§8Solo una vez");
            else icon.line("§8Cada " + kit.cooldownHours() + " h");
            icon.action("Clic para reclamar");
            set(1, col, icon.build(), (p, b) -> reclamar(p, kit));

        } else if (estado.reason() != null) {
            icon.state(LockState.DISABLED).line("§8" + estado.reason());
            set(1, col, icon.build(), null);

        } else {
            icon.state(LockState.COOLDOWN)
                .line("§7Disponible en §f" + estado.remaining())
                .line("§8Reclamado " + estado.timesClaimed() + " veces");
            set(1, col, icon.build(), null);
        }
    }

    /**
     * Reclama. Se marca primero y se entrega después.
     *
     * <p>El orden es el contrario al de la tienda, y a propósito: aquí lo malo
     * es reclamar de más (un exploit), mientras que en una compra lo malo es
     * cobrar de más. Si la entrega fallara, se deshace la marca.
     */
    private void reclamar(ServerPlayerEntity player, KitCatalog.Kit kit) {
        var server = player.getServer();
        player.closeHandledScreen();

        LunaEternal.submit(() -> {
            try {
                if (!LunaEternal.kitService().claim(data.playerId, kit)) {
                    server.execute(() -> player.sendMessage(
                        Text.literal("§cTodavía no puedes reclamar ese kit."), false));
                    return;
                }
                server.execute(() -> {
                    if (player.isRemoved()) {
                        deshacer(kit);
                        return;
                    }
                    for (var item : kit.items()) {
                        // offerOrDrop: lo que no cabe cae al suelo, así que la
                        // entrega no puede fallar a medias.
                        player.getInventory().offerOrDrop(
                            new ItemStack(item.item(), item.count()));
                    }
                    player.sendMessage(Text.literal(
                        "§aHas reclamado " + kit.name() + "§a."), false);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error reclamando el kit {}", kit.id(), e);
                deshacer(kit);
                server.execute(() -> player.sendMessage(
                    Text.literal("§cNo se pudo entregar el kit. Inténtalo otra vez."),
                    false));
            }
        });
    }

    private void deshacer(KitCatalog.Kit kit) {
        try {
            LunaEternal.kitService().undo(data.playerId, kit);
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo deshacer la reclamación de {}", kit.id(), e);
        }
    }
}
