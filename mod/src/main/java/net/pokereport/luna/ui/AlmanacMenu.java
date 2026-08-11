package net.pokereport.luna.ui;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.economy.Currency;

/**
 * El Almanaque — punto de entrada único al juego.
 *
 * <p>Ver {@code docs/ui/navigation.md}. Está agrupado en cuatro bloques
 * temáticos a propósito: una rejilla plana de quince iconos indistintos obliga
 * al jugador a memorizar posiciones en vez de leer, y empeora con cada sección
 * nueva. Cuatro grupos de cuatro o cinco se leen de un vistazo, y una sección
 * futura entra en su grupo sin rediseñar nada.
 *
 * <p>Lo que aún no está construido aparece bloqueado <b>con su motivo</b>. No
 * se oculta: el Almanaque enseña también lo que queda por escribir.
 */
public final class AlmanacMenu extends Menu {

    private final PlayerSnapshot data;

    public AlmanacMenu(PlayerSnapshot data) {
        super("§8✦ §6El Almanaque §8✦", 6);
        this.data = data;
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        header();
        pokemonRow();
        adventureRow();
        economyRow();
        selfRow();
        footer();
        // Al final: fill() solo rellena lo que quedó vacío, así que nunca
        // pisa un icono ya colocado.
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------ fila 0

    private void header() {
        set(0, Icon.of(moonItem())
                .name("§f" + data.moonName())
                .line(data.night ? "§7Es de noche" : "§7Es de día")
                .blank()
                .line(data.moonHint(false))
                .build());

        set(4, Icon.of(Items.WRITABLE_BOOK)
                .name("§6" + data.username)
                .line("§7Vía: §f" + data.dominantPath
                      + (data.dominantLevel > 0 ? " " + roman(data.dominantLevel) : ""))
                .line("§7Clan: §f" + orNone(data.clan))
                .line("§7Oficio: §f" + orNone(data.job))
                .blank()
                .line("§7Medallas: §f" + data.badges + "§8/8")
                .build());

        set(8, Icon.of(Items.GOLD_NUGGET)
                .name("§eCartera")
                .line("§6" + fmt(data.balance(Currency.POKEDOLLAR)) + " §7PokéDólares")
                .line("§b" + fmt(data.balance(Currency.MARK)) + " §7Marcas")
                .line("§e" + fmt(data.balance(Currency.REPORTCOIN)) + " §7"
                      + Currency.REPORTCOIN.displayName)
                .blank()
                .line("§8Las tres monedas no se convierten entre sí.")
                .action("Clic para ver la Cartera")
                .build(),
            (p, b) -> MenuService.openChild(p, this, WalletMenu::new));
    }

    // ------------------------------------------------------------ grupos

    private void pokemonRow() {
        group(1, "§b✦ TUS POKÉMON");
        set(1, 2, Icon.of(Items.ENCHANTED_BOOK)
                .name("§bPokédex")
                .line("§7Lo que has visto y capturado.")
                .action("Clic para abrir")
                .build(),
            (p, b) -> PokedexMenu.open(p, this, 0));
        open(1, 3, Items.ENDER_CHEST,    "§bCaja",    "Tus Pokémon guardados.",        "Caja");
        open(1, 4, Items.BUNDLE,         "§bMochila", "Tus objetos.",                  "Mochila");
        open(1, 5, Items.GOLDEN_APPLE,   "§bCurar",   "Restaura a tu equipo.",         "Curar");
        locked(1, 6, Items.TURTLE_EGG,   "Criadero", "Cría y linajes.",
               "Criador I", "cría tu primer Pokémon en la guardería");
    }

    private void adventureRow() {
        group(2, "§a✦ AVENTURA");
        open(2, 2, Items.WRITTEN_BOOK, "§aMisiones", "Objetivos activos e historia.", "Misiones");
        open(2, 3, Items.TARGET,       "§aCazas",    "Objetivos rotativos con recompensa.", "Cazas");
        open(2, 4, Items.GOLD_INGOT,   "§aMedallas", "Tu progreso y dónde está el siguiente gimnasio.", "Medallas");
        locked(2, 5, Items.CHEST,      "Tesoros",    "Recompensas por descubrir.",
               "Explorador I", "descubre tu primera zona");
        open(2, 6, Items.FILLED_MAP,   "§aExplorar", "El mapa y las zonas.", "Explorar");
    }

    private void economyRow() {
        group(3, "§6✦ ECONOMÍA");
        set(3, 2, Icon.of(Items.COMPARATOR)
                .name("§6GTS")
                .line("§7Mercado entre jugadores.")
                .action("Clic para abrir")
                .build(),
            (p, b) -> GtsMenu.open(p, this, 0));
        set(3, 3, Icon.of(Items.EMERALD)
                .name("§6Tienda")
                .line("§7Compra objetos y consumibles.")
                .action("Clic para abrir")
                .build(),
            (p, b) -> MenuService.openChild(p, this, ShopMenu::new));
        locked(3, 4, Items.IRON_PICKAXE, "Oficios", "Profesiones con progresión propia.",
               "Tutorial completo", "termina el tutorial");
        locked(3, 5, Items.PAPER,        "Historial", "Movimientos y precios de mercado.",
               "Comerciante IV", "sube la vía Comerciante");
    }

    private void selfRow() {
        group(4, "§d✦ TÚ");
        set(4, 2, Icon.of(Items.NETHER_STAR)
                .name("§dVías")
                .line("§7Tus cinco reputaciones y desbloqueos.")
                .line("§8Actual: §7" + data.dominantPath)
                .action("Clic para abrir")
                .build(),
            (p, b) -> MenuService.openChild(p, this, PathsMenu::new));
        open(4, 3, Items.LEATHER_CHESTPLATE, "§dCosméticos", "Aspecto, títulos y efectos.", "Cosméticos");
        open(4, 4, Items.SHULKER_BOX,        "§dKits",       "Inicial, periódicos y de rango.", "Kits");
        locked(4, 5, Items.SHIELD,           "Clan",         "Crea o únete a un clan.",
               "Nivel de progresión", "avanza en cualquier vía");
        open(4, 6, Items.NETHERITE_INGOT,    "§dRangos",     "Qué incluye cada rango.", "Rangos");
    }

    private void footer() {
        set(45, Icon.of(Items.MINECART)
                .name("§8Viajes")
                .line("§8Puntos de la ciudadela.")
                .state(LockState.DISABLED)
                .line("§8La ciudadela aún no está construida.")
                .build());

        set(46, Icon.of(Items.OAK_DOOR)
                .name("§3Puerta del Mundo")
                .line("§7Ir al Mundo Hogar o al Mundo Salvaje.")
                .action("Clic para elegir")
                .build(),
            (p, b) -> openChild(p, new WorldGateMenu(-1)));

        if (hasParent()) {
            set(48, Icon.of(Items.ARROW).name("§7← Atrás").build(),
                (p, b) -> back(p));
        }

        set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());
    }

    // ------------------------------------------------------------ auxiliares

    /** Etiqueta de grupo, en la columna 0 de su fila. */
    private void group(int row, String label) {
        set(row, 0, Icon.of(Items.WHITE_STAINED_GLASS_PANE).name(label).build(), null);
    }

    /** Entrada disponible. */
    private void open(int row, int col, Item item, String name, String desc, String section) {
        set(row, col, Icon.of(item)
                .name(name)
                .line("§7" + desc)
                .action("Clic para abrir")
                .build(),
            (p, b) -> notImplemented(p, section));
    }

    /**
     * Entrada bloqueada: muestra siempre <b>qué es, qué falta y qué hacer</b>.
     * El tercer punto es el que casi siempre se olvida y el único que convierte
     * un muro en un objetivo.
     */
    private void locked(int row, int col, Item item, String name, String desc,
                        String requirement, String how) {
        set(row, col, Icon.of(item)
                .name("§8" + name)
                .line("§8" + desc)
                .state(LockState.LOCKED)
                .line("§7Necesitas: §f" + requirement)
                .line("§7Cómo: §f" + how)
                .build(),
            (p, b) -> p.sendMessage(Text.literal(
                "§7Aún no has escrito esa página. §fNecesitas: " + requirement), true));
    }

    private Item moonItem() {
        return switch (data.moonPhase) {
            case 0 -> Items.GLOWSTONE;
            case 4 -> Items.COAL_BLOCK;
            default -> Items.QUARTZ_BLOCK;
        };
    }

    /** Aviso honesto: la sección está diseñada pero todavía no construida. */
    private void notImplemented(ServerPlayerEntity p, String section) {
        p.sendMessage(Text.literal("§8[§6Almanaque§8] §f" + section
            + " §7está diseñada pero aún no implementada."), false);
    }

    private static String orNone(String s) {
        return (s == null || s.isBlank()) ? "§8ninguno" : s;
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }
}
