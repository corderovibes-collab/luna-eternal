package net.pokereport.luna.ui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.gts.GtsService;
import net.pokereport.luna.gts.ItemCodec;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GTS: mercado entre jugadores.
 *
 * <p>Tres vistas: explorar, mis listados y publicar. El acceso por niveles
 * (G0-G4) de {@code docs/trading/gts.md} §2 se aplicará cuando exista la
 * progresión de la vía Comerciante; de momento todo está abierto para poder
 * probarlo.
 */
public final class GtsMenu extends Menu {

    private final PlayerSnapshot data;
    private final List<GtsService.Listing> listings;
    private final int page;

    public GtsMenu(PlayerSnapshot data, List<GtsService.Listing> listings, int page) {
        super("§8✦ §6GTS §8· §7página " + (page + 1) + " §8✦", 6);
        this.data = data;
        this.listings = listings;
        this.page = page;
    }

    @Override
    protected void build(ServerPlayerEntity player) {

        set(4, Icon.of(Items.COMPARATOR)
                .name("§6Mercado global")
                .line("§7Compra y vende con otros jugadores.")
                .blank()
                .line("§8Publicar cuesta §71 % §8del precio, por adelantado.")
                .line("§8Al vender se aplica un impuesto progresivo.")
                .build());

        if (listings.isEmpty()) {
            set(22, Icon.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name("§7No hay nada a la venta")
                    .line("§8Sé el primero: pon algo en tu mano")
                    .line("§8y usa §7Vender§8.")
                    .build(), null);
        } else {
            int slot = 9;
            for (GtsService.Listing l : listings) {
                if (slot >= 45) break;

                boolean affordable = data.balance(
                    net.pokereport.luna.economy.Currency.POKEDOLLAR) >= l.price();
                boolean mine = l.sellerId() == data.playerId;

                var icon = Icon.of(itemOf(l.itemId()), Math.max(1, Math.min(l.quantity(), 64)))
                        .name("§f" + l.displayName())
                        .line("§7Cantidad: §f" + l.quantity())
                        .line("§7Precio: §6" + fmt(l.price()))
                        .line("§7Vendedor: §f" + l.sellerName())
                        .line("§8Caduca en " + remaining(l.expiresAt()));

                if (mine) {
                    icon.blank().line("§eEs tuyo").line("§8Clic para retirarlo");
                    set(slot, icon.build(), (p, b) -> cancel(p, l));
                } else if (affordable) {
                    icon.action("Clic para comprar");
                    set(slot, icon.build(), (p, b) -> confirmBuy(p, l));
                } else {
                    icon.state(LockState.LOCKED).line("§7No te llega el dinero");
                    set(slot, icon.build(), null);
                }
                slot++;
            }
        }

        // ---- barra inferior ------------------------------------------------
        if (page > 0) {
            set(45, Icon.of(Items.ARROW).name("§7← Página anterior").build(),
                (p, b) -> reopen(p, page - 1));
        }
        if (listings.size() >= 36) {
            set(53 - 1, Icon.of(Items.ARROW).name("§7Página siguiente →").build(),
                (p, b) -> reopen(p, page + 1));
        }

        set(47, Icon.of(Items.CHEST)
                .name("§eMis listados")
                .line("§7Lo que tienes a la venta.")
                .build(),
            (p, b) -> openMine(p));

        set(49, Icon.of(Items.GOLD_INGOT)
                .name("§6Vender lo que tengo en la mano")
                .line("§7Publica el objeto de tu mano principal.")
                .blank()
                .line("§8Sale de tu inventario y queda en custodia")
                .line("§8del mercado hasta que se venda o lo retires.")
                .action("Clic para publicar")
                .build(),
            (p, b) -> openSell(p));

        set(48, Icon.of(Items.BARRIER).name("§7← Atrás").build(), (p, b) -> back(p));
        set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------ acciones

    /** Abre el mercado cargando la página pedida. */
    public static void open(ServerPlayerEntity player, Menu parent, int page) {
        MenuService.loadSnapshot(player, snap -> LunaEternal.submit(() -> {
            try {
                var list = LunaEternal.gts().browse(page, 36);
                player.getServer().execute(() -> {
                    var menu = new GtsMenu(snap, list, page);
                    if (parent != null) parent.openChild(player, menu);
                    else menu.open(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error abriendo el GTS", e);
                player.getServer().execute(() -> player.sendMessage(
                    Text.literal("§cNo se pudo abrir el mercado."), false));
            }
        }));
    }

    private void reopen(ServerPlayerEntity p, int newPage) {
        open(p, this, newPage);
    }

    private void openMine(ServerPlayerEntity player) {
        LunaEternal.submit(() -> {
            try {
                var list = LunaEternal.gts().mine(data.playerId);
                player.getServer().execute(() ->
                    openChild(player, new GtsMenu(data, list, 0)));
            } catch (Exception e) {
                LunaEternal.LOG.error("Error listando lo propio", e);
            }
        });
    }

    private void confirmBuy(ServerPlayerEntity player, GtsService.Listing l) {
        openChild(player, new ConfirmBuyMenu(data, l));
    }

    private void cancel(ServerPlayerEntity player, GtsService.Listing l) {
        var server = player.getServer();
        LunaEternal.submit(() -> {
            try {
                var r = LunaEternal.gts().cancel(data.playerId, l.id());
                server.execute(() -> {
                    player.sendMessage(Text.literal(r.message()), false);
                    if (r.ok() && r.payload() != null) {
                        give(player, ItemCodec.decode(r.payload(),
                            player.getRegistryManager()));
                    }
                    MenuService.openAlmanac(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error cancelando listado", e);
            }
        });
    }

    /** Publica el objeto de la mano principal. */
    private void openSell(ServerPlayerEntity player) {
        ItemStack inHand = player.getMainHandStack();
        if (inHand.isEmpty()) {
            player.sendMessage(Text.literal(
                "§cPon en tu mano principal lo que quieras vender."), false);
            return;
        }
        if (AlmanacItem.is(inHand)) {
            player.sendMessage(Text.literal("§cEl Almanaque no se vende."), false);
            return;
        }
        openChild(player, new SellMenu(data, inHand.copy()));
    }

    static void give(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            player.sendMessage(Text.literal(
                "§cEl objeto no se pudo recuperar. Avisa a un administrador."), false);
            return;
        }
        // offerOrDrop: si no cabe, cae al suelo. Nunca se pierde.
        player.getInventory().offerOrDrop(stack);
    }

    private static net.minecraft.item.Item itemOf(String id) {
        if (id == null) return Items.PAPER;
        var identifier = net.minecraft.util.Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) return Items.PAPER;
        return Registries.ITEM.get(identifier);
    }

    private static String remaining(LocalDateTime expires) {
        Duration d = Duration.between(LocalDateTime.now(), expires);
        if (d.isNegative()) return "caducado";
        long h = d.toHours();
        return h >= 1 ? h + " h" : d.toMinutes() + " min";
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    // ========================================================= confirmar compra

    static final class ConfirmBuyMenu extends Menu {
        private final PlayerSnapshot data;
        private final GtsService.Listing listing;

        ConfirmBuyMenu(PlayerSnapshot data, GtsService.Listing listing) {
            super("§8✦ §eConfirmar compra §8✦", 3);
            this.data = data;
            this.listing = listing;
        }

        @Override
        protected void build(ServerPlayerEntity player) {
            long balance = data.balance(net.pokereport.luna.economy.Currency.POKEDOLLAR);

            set(13, Icon.of(itemOf(listing.itemId()),
                            Math.max(1, Math.min(listing.quantity(), 64)))
                    .name("§f" + listing.displayName())
                    .line("§7Cantidad: §f" + listing.quantity())
                    .line("§7Vendedor: §f" + listing.sellerName())
                    .build(), null);

            set(11, Icon.of(Items.LIME_CONCRETE)
                    .name("§aComprar")
                    .line("§7Pagas: §6" + fmt(listing.price()))
                    .line("§7Te quedan: §f" + fmt(balance - listing.price()))
                    .action("Clic para confirmar")
                    .build(),
                (p, b) -> execute(p));

            set(15, Icon.of(Items.RED_CONCRETE).name("§cCancelar").build(),
                (p, b) -> back(p));

            fill(Items.GRAY_STAINED_GLASS_PANE);
        }

        private void execute(ServerPlayerEntity player) {
            var server = player.getServer();
            player.closeHandledScreen();
            LunaEternal.submit(() -> {
                try {
                    var r = LunaEternal.gts().buy(data.playerId, listing.id());
                    server.execute(() -> {
                        player.sendMessage(Text.literal(r.message()), false);
                        if (r.ok() && r.payload() != null) {
                            give(player, ItemCodec.decode(r.payload(),
                                player.getRegistryManager()));
                        }
                    });
                } catch (Exception e) {
                    LunaEternal.LOG.error("Error comprando en el GTS", e);
                    server.execute(() -> player.sendMessage(
                        Text.literal("§cError en la compra."), false));
                }
            });
        }
    }

    // =============================================================== publicar

    static final class SellMenu extends Menu {
        private static final long[] PRICES = {100, 500, 1_000, 5_000, 25_000, 100_000};

        private final PlayerSnapshot data;
        private final ItemStack stack;

        SellMenu(PlayerSnapshot data, ItemStack stack) {
            super("§8✦ §6Publicar §8✦", 4);
            this.data = data;
            this.stack = stack;
        }

        @Override
        protected void build(ServerPlayerEntity player) {
            set(4, stack.copy());

            set(13, Icon.of(Items.PAPER)
                    .name("§7Elige un precio")
                    .line("§8La tasa del 1 % se cobra ahora")
                    .line("§8y no se devuelve aunque no se venda.")
                    .build(), null);

            int col = 1;
            for (long price : PRICES) {
                long fee = GtsService.listingFee(price);
                long tax = GtsService.taxFor(price);
                set(2, col, Icon.of(Items.GOLD_NUGGET)
                        .name("§6" + fmt(price))
                        .line("§7Tasa ahora: §c-" + fmt(fee))
                        .line("§7Impuesto si se vende: §c-" + fmt(tax))
                        .blank()
                        .line("§7Recibirías: §a" + fmt(price - tax))
                        .action("Clic para publicar")
                        .build(),
                    (p, b) -> publish(p, price));
                col++;
            }

            set(27, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
            set(35, Icon.of(Items.BARRIER).name("§cCerrar").build(),
                (p, b) -> p.closeHandledScreen());

            fill(Items.GRAY_STAINED_GLASS_PANE);
        }

        private void publish(ServerPlayerEntity player, long price) {
            var server = player.getServer();

            // Se retira de la mano ANTES de publicar: la custodia empieza aquí.
            ItemStack current = player.getMainHandStack();
            if (!ItemStack.areItemsAndComponentsEqual(current, stack)
                    || current.getCount() < stack.getCount()) {
                player.sendMessage(Text.literal(
                    "§cYa no tienes ese objeto en la mano."), false);
                return;
            }

            byte[] payload = ItemCodec.encode(stack, player.getRegistryManager());
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            String name = stack.getName().getString();
            int qty = stack.getCount();

            current.setCount(0);
            player.closeHandledScreen();

            LunaEternal.submit(() -> {
                try {
                    var r = LunaEternal.gts().publish(
                        data.playerId, payload, name, itemId, qty, price);
                    server.execute(() -> {
                        player.sendMessage(Text.literal(r.message()), false);
                        // Si no se pudo publicar, se devuelve. Nunca se pierde.
                        if (!r.ok()) give(player,
                            ItemCodec.decode(payload, player.getRegistryManager()));
                    });
                } catch (Exception e) {
                    LunaEternal.LOG.error("Error publicando en el GTS", e);
                    server.execute(() -> {
                        player.sendMessage(Text.literal(
                            "§cNo se pudo publicar. Se te ha devuelto el objeto."), false);
                        give(player, ItemCodec.decode(payload, player.getRegistryManager()));
                    });
                }
            });
        }
    }
}
