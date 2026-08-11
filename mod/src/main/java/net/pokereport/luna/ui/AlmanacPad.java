package net.pokereport.luna.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

import java.util.List;

/**
 * El Almanaque, en el Pad (D-025).
 *
 * <p>Es la misma pantalla que ya existía como menú de cofre, pero sin sus
 * límites: rejilla de 5×3 en vez de 9×6 forzadas, sin inventario del jugador
 * debajo, e iconos propios en vez de cabezas y lana.
 *
 * <p>El menú de cofre <b>se conserva</b> como respaldo para quien entre sin el
 * mod de cliente. No es cortesía: es lo que permite desplegar el mod de
 * cliente sin dejar a nadie fuera mientras actualiza.
 */
public final class AlmanacPad {

    private AlmanacPad() {}

    public static void abrir(ServerPlayerEntity jugador) {
        var snap = MenuService.cached(jugador);

        var p = new PadService.Pantalla("almanaque", "El Almanaque", 5, 3);

        p.celda(0, 0, "pokedex", "§bPokédex",
                List.of("§7Lo que has registrado.", "", "§eClic para abrir"),
                false, (j, d) -> PokedexMenu.open(j, null, 0));

        p.celda(1, 0, "cartera", "§eCartera",
                List.of("§7Tus tres monedas.", "", "§eClic para abrir"),
                false, (j, d) -> MenuService.openStandalone(j, WalletMenu::new));

        p.celda(2, 0, "vias", "§dTus Vías",
                List.of("§7Las cinco formas de progresar.", "", "§eClic para abrir"),
                false, (j, d) -> MenuService.openStandalone(j, PathsMenu::new));

        p.celda(3, 0, "misiones", "§aMisiones",
                List.of("§7Qué hacer ahora.", "", "§eClic para abrir"),
                false, (j, d) -> QuestsMenu.open(j, null));

        p.celda(4, 0, "kits", "§aKits",
                List.of("§7Tu entrega diaria.", "", "§eClic para abrir"),
                false, (j, d) -> KitsMenu.open(j, null));

        p.celda(0, 1, "tienda", "§6Tienda",
                List.of("§7Comprar y vender.", "", "§eClic para abrir"),
                false, (j, d) -> MenuService.openStandalone(j, ShopMenu::new));

        p.celda(1, 1, "gts", "§6GTS",
                List.of("§7Mercado entre jugadores.", "", "§eClic para abrir"),
                false, (j, d) -> GtsMenu.open(j, null, 0));

        p.celda(2, 1, "centro", "§aCentro Pokémon",
                List.of("§7Curar tu equipo. Gratis.", "", "§eClic para abrir"),
                false, (j, d) -> HealMenu.open(j, null));

        p.celda(3, 1, "puerta", "§3Puerta del Mundo",
                List.of("§7Viajar entre mundos.", "", "§eClic para abrir"),
                false, (j, d) -> new WorldGateMenu(-1).open(j));

        // Bloqueadas: existen en la rejilla para que se vea que el juego
        // continúa. Una rejilla a medias parece un error; una con candados
        // parece una promesa.
        p.celda(4, 1, "gimnasios", "§8Gimnasios",
                List.of("§7Aún no disponibles.", "§8Necesitan mundo construido."),
                true, (j, d) -> {});

        p.celda(0, 2, "tesoros", "§8Tesoros",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(1, 2, "clan", "§8Clanes",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(2, 2, "cosmeticos", "§8Cosméticos",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(3, 2, "cazas", "§8Cazas",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(4, 2, "explorar", "§8Explorar",
                List.of("§7Aún no disponible."), true, (j, d) -> {});

        if (snap != null) {
            p.pie("§7" + jugador.getGameProfile().getName()
                  + " §8· §6" + snap.balance(Currency.POKEDOLLAR)
                  + " §8· §b" + snap.balance(Currency.MARK));
        }

        try {
            p.abrir(jugador);
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo abrir el Pad", e);
        }
    }
}
