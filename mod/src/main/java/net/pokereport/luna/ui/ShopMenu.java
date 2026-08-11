package net.pokereport.luna.ui;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.shop.ShopCatalog;
import net.pokereport.luna.shop.ShopService;

/**
 * Tienda: categorías → objetos → confirmación.
 *
 * <p>Ninguna compra se ejecuta al primer clic. El jugador ve siempre
 * <b>qué recibe, qué paga y con cuánto se queda</b> antes de confirmar
 * ({@code docs/ui/interfaces-catalog.md} §2.2). Un clic accidental de 50 000
 * es un ticket de soporte garantizado.
 */
public final class ShopMenu extends Menu {

    private final PlayerSnapshot data;
    private final ShopCatalog catalog;

    public ShopMenu(PlayerSnapshot data) {
        super("§8✦ §6Tienda §8✦", 4, "tienda");
        this.data = data;
        this.catalog = LunaEternal.shop();
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        set(4, Icon.of(Items.EMERALD)
                .name("§6Tienda")
                .line("§7Compra a precio fijo. Vende al banco.")
                .blank()
                .line("§8Lo que compras siempre vale menos al revenderlo:")
                .line("§8la tienda no es una forma de ganar dinero.")
                .build());

        int col = 1;
        for (ShopCatalog.Category c : catalog.categories()) {
            if (col > 7) break;
            set(1, col, Icon.of(c.icon())
                    .name(c.name())
                    .line("§7" + c.description())
                    .line("§8" + c.entries().size() + " objetos")
                    .action("Clic para abrir")
                    .build(),
                (p, b) -> openChild(p, new ShopCategoryMenu(data, c)));
            col++;
        }

        set(2, 4, Icon.of(Items.GOLD_NUGGET)
                .name("§eTu dinero")
                .line("§6" + fmt(data.balance(
                    net.pokereport.luna.economy.Currency.POKEDOLLAR)) + " §7PokéDólares")
                .line("§e" + fmt(data.balance(
                    net.pokereport.luna.economy.Currency.REPORTCOIN)) + " §7"
                    + net.pokereport.luna.economy.Currency.REPORTCOIN.displayName)
                .build(), null);

        set(30, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(35, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    // ================================================================ categoría

    /** Los objetos de una categoría. */
    public static final class ShopCategoryMenu extends Menu {

        private final PlayerSnapshot data;
        private final ShopCatalog.Category category;

        ShopCategoryMenu(PlayerSnapshot data, ShopCatalog.Category category) {
            super("§8✦ " + category.name() + " §8✦", 6);
            this.data = data;
            this.category = category;
        }

        @Override
        protected void build(ServerPlayerEntity player) {
            int slot = 9;
            for (ShopCatalog.Entry e : category.entries()) {
                if (slot >= 45) break;

                long balance = data.balance(e.currency());
                boolean affordable = balance >= e.buy();

                var icon = Icon.of(e.item())
                        .name(e.displayName())
                        .line("§7Comprar: §f" + fmt(e.buy()) + " " + e.currency().displayName);

                if (e.sellable()) {
                    icon.line("§7Vender:  §f" + fmt(e.sell()) + " " + e.currency().displayName);
                } else {
                    icon.line("§8No se puede vender");
                }

                if (affordable) {
                    icon.blank()
                        .line("§8Clic izquierdo · comprar 1")
                        .line("§8Clic derecho · comprar 8");
                    if (e.sellable()) icon.line("§8Shift + clic · vender 1");
                } else {
                    icon.state(LockState.LOCKED)
                        .line("§7Te faltan §f" + fmt(e.buy() - balance)
                              + " " + e.currency().displayName);
                }

                set(slot, icon.build(), (p, button) -> {
                    // 0 = izquierdo, 1 = derecho. Shift no llega aquí como
                    // botón, así que vender va por el clic derecho sobre
                    // objetos ya poseídos — se resuelve en la confirmación.
                    int amount = (button == 1) ? 8 : 1;
                    openChild(p, new ConfirmMenu(data, e, amount));
                });
                slot++;
            }

            set(48, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
            set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
                (p, b) -> p.closeHandledScreen());

            fill(Items.GRAY_STAINED_GLASS_PANE);
        }

        private static String fmt(long v) {
            return String.format("%,d", v);
        }
    }

    // ============================================================ confirmación

    /** Confirmación de compra o venta. Nada se ejecuta antes de esto. */
    public static final class ConfirmMenu extends Menu {

        private final PlayerSnapshot data;
        private final ShopCatalog.Entry entry;
        private final int amount;

        ConfirmMenu(PlayerSnapshot data, ShopCatalog.Entry entry, int amount) {
            super("§8✦ §eConfirmar §8✦", 3);
            this.data = data;
            this.entry = entry;
            this.amount = amount;
        }

        @Override
        protected void build(ServerPlayerEntity player) {
            long total = entry.buy() * amount;
            long balance = data.balance(entry.currency());
            long after = balance - total;

            set(13, Icon.of(entry.item(), Math.min(amount, 64))
                    .name(entry.displayName())
                    .line("§7Cantidad: §f" + amount)
                    .build(), null);

            set(11, Icon.of(Items.LIME_CONCRETE)
                    .name("§aComprar")
                    .line("§7Pagas: §f" + fmt(total) + " " + entry.currency().displayName)
                    .line("§7Te quedan: §f" + fmt(Math.max(after, 0)))
                    .blank()
                    .line(after < 0 ? "§cNo tienes suficiente" : "§8Clic para confirmar")
                    .build(),
                after < 0 ? null : (p, b) -> ShopService.buy(p, entry, amount,
                    r -> reply(p, r)));

            if (entry.sellable()) {
                set(15, Icon.of(Items.ORANGE_CONCRETE)
                        .name("§6Vender")
                        .line("§7Recibes: §f" + fmt(entry.sell() * amount)
                              + " " + entry.currency().displayName)
                        .blank()
                        .line("§8Necesitas tenerlo en el inventario")
                        .line("§8Clic para confirmar")
                        .build(),
                    (p, b) -> ShopService.sell(p, entry, amount, r -> reply(p, r)));
            }

            set(18, Icon.of(Items.ARROW).name("§7← Cancelar").build(), (p, b) -> back(p));
            set(26, Icon.of(Items.BARRIER).name("§cCerrar").build(),
                (p, b) -> p.closeHandledScreen());

            fill(Items.GRAY_STAINED_GLASS_PANE);
        }

        /** Avisa y recarga: tras comprar, el saldo mostrado ya no es válido. */
        private void reply(ServerPlayerEntity p, ShopService.Result r) {
            p.sendMessage(Text.literal(r.message()), false);
            if (r.ok()) MenuService.openAlmanac(p);
        }

        private static String fmt(long v) {
            return String.format("%,d", v);
        }
    }
}
