package net.pokereport.luna.ui;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyService;

import java.time.format.DateTimeFormatter;

/**
 * Cartera: los tres saldos y los últimos movimientos.
 *
 * <p>Enseña de forma explícita que las monedas <b>no se convierten entre
 * sí</b>. Es la regla que sostiene todo el modelo económico (D-014) y el
 * jugador debe entenderla antes de preguntarse por qué no puede cambiar unas
 * por otras.
 */
public final class WalletMenu extends Menu {

    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final PlayerSnapshot data;

    public WalletMenu(PlayerSnapshot data) {
        super("§8✦ §eCartera §8✦", 6, "cartera");
        this.data = data;
    }

    @Override
    protected void build(ServerPlayerEntity player) {

        // ---- las tres monedas, cada una explicando lo que es -------------
        set(1, 2, Icon.of(Items.GOLD_INGOT)
                .name("§6" + Currency.POKEDOLLAR.displayName)
                .line("§f" + fmt(data.balance(Currency.POKEDOLLAR)))
                .blank()
                .line("§7Se gana jugando.")
                .line("§7Se comercia con otros jugadores.")
                .build(), null);

        set(1, 4, Icon.of(Items.AMETHYST_SHARD)
                .name("§b" + Currency.MARK.displayName)
                .line("§f" + fmt(data.balance(Currency.MARK)))
                .blank()
                .line("§7Se gana con logros y descubrimientos.")
                .line("§8No se comercia: la progresión no se compra.")
                .build(), null);

        set(1, 6, Icon.of(Items.SUNFLOWER)
                .name("§e" + Currency.REPORTCOIN.displayName)
                .line("§f" + fmt(data.balance(Currency.REPORTCOIN)))
                .blank()
                .line("§7Compra identidad y comodidad.")
                .line("§8No se comercia entre jugadores.")
                .build(), null);

        // ---- la regla, dicha una vez y claramente ------------------------
        set(2, 4, Icon.of(Items.BARRIER)
                .name("§7Las monedas no se cambian entre sí")
                .line("§8Ninguna se convierte en otra,")
                .line("§8en ninguna dirección.")
                .blank()
                .line("§8Es lo que evita que el dinero real")
                .line("§8se convierta en ventaja de juego.")
                .build(), null);

        // ---- movimientos --------------------------------------------------
        set(3, 0, Icon.of(Items.WHITE_STAINED_GLASS_PANE)
                .name("§f✦ ÚLTIMOS MOVIMIENTOS").build(), null);

        if (data.recent.isEmpty()) {
            set(4, 4, Icon.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name("§7Todavía no hay movimientos")
                    .line("§8Aquí aparecerá todo lo que ganes y gastes.")
                    .build(), null);
        } else {
            int slot = 0;
            for (EconomyService.Entry e : data.recent) {
                if (slot >= 14) break;                 // dos filas de siete
                int row = 4 + (slot / 7);
                int col = 1 + (slot % 7);
                boolean income = e.delta() > 0;
                set(row, col, Icon.of(income ? Items.LIME_DYE : Items.RED_DYE)
                        .name((income ? "§a+" : "§c") + fmt(e.delta())
                              + " §7" + e.currency().displayName)
                        .line("§8" + reasonLabel(e.reason()))
                        .line("§8" + e.when().format(WHEN))
                        .blank()
                        .line("§7Saldo después: §f" + fmt(e.balanceAfter()))
                        .build(), null);
                slot++;
            }
        }

        footer();
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void footer() {
        set(48, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());
    }

    /** Traduce el motivo técnico a algo que el jugador entienda. */
    private static String reasonLabel(String reason) {
        return switch (reason) {
            case "admin_grant"  -> "Concedido por un administrador";
            case "gts_sale"     -> "Venta en el GTS";
            case "gts_buy"      -> "Compra en el GTS";
            case "gts_tax"      -> "Impuesto del GTS";
            case "gts_listing"  -> "Tasa de publicación";
            case "shop_buy"     -> "Compra en la tienda";
            case "shop_sell"    -> "Venta al banco";
            case "wild_catch"   -> "Pokémon salvaje";
            case "quest_reward" -> "Recompensa de misión";
            case "transfer"     -> "Transferencia";
            default             -> reason;
        };
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
