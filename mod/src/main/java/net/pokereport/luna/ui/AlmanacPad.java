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

    /** Nivel en romano: ocupa menos y se lee mejor que "Nivel 3". */
    private static String romano(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }

    /** Recorta sin romper: 9 caracteres es lo que cabe en un panel. */
    private static String corto(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * 1250 → «1,2k». Un panel de 47 px no admite «1.250.000», y cortarlo
     * daria «1.250» — que no es un numero grande mal escrito, es un numero
     * DISTINTO. Abreviar miente menos que truncar.
     */
    private static String abreviar(long v) {
        if (v < 10_000) return String.valueOf(v);
        if (v < 1_000_000) return String.format("%.1fk", v / 1_000.0)
                                        .replace('.', ',');
        if (v < 1_000_000_000L) return String.format("%.1fM", v / 1_000_000.0)
                                             .replace('.', ',');
        return String.format("%.1fG", v / 1_000_000_000.0).replace('.', ',');
    }

    public static void abrir(ServerPlayerEntity jugador) {
        var snap = MenuService.cached(jugador);

        var p = new PadService.Pantalla("almanaque", "El Almanaque", 5, 3);

        p.celda(0, 0, "pokedex", "§bPokédex",
                List.of("§7Lo que has registrado.", "", "§eClic para abrir"),
                false, (j, d) -> PokedexMenu.open(j, null, 0));

        p.celda(1, 0, "cartera", "§eCartera",
                List.of("§7Tus tres monedas.", "", "§eClic para abrir"),
                false, (j, d) -> PantallasPad.cartera(j));

        p.celda(2, 0, "vias", "§dVías",
                List.of("§7Las cinco formas de progresar.", "", "§eClic para abrir"),
                false, (j, d) -> PantallasPad.vias(j));

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

        p.celda(2, 1, "centro", "§aCentro",
                List.of("§fCentro Pokémon", "§7Curar tu equipo. Gratis.", "", "§eClic para abrir"),
                false, (j, d) -> HealMenu.open(j, null));

        p.celda(3, 1, "puerta", "§3Puerta",
                List.of("§fPuerta del Mundo", "§7Viajar entre mundos.", "", "§eClic para abrir"),
                false, (j, d) -> PantallasPad.mundos(j));

        // Bloqueadas: existen en la rejilla para que se vea que el juego
        // continúa. Una rejilla a medias parece un error; una con candados
        // parece una promesa.
        p.celda(4, 1, "gimnasios", "§8Gimnasio",
                List.of("§fGimnasios", "§7Aún no disponibles.", "§8Necesitan mundo construido."),
                true, (j, d) -> {});

        p.celda(0, 2, "tesoros", "§8Tesoros",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(1, 2, "clan", "§8Clanes",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(2, 2, "cosmeticos", "§8Cosmét.",
                List.of("§fCosméticos", "§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(3, 2, "cazas", "§8Cazas",
                List.of("§7Aún no disponibles."), true, (j, d) -> {});

        p.celda(4, 2, "explorar", "§8Explorar",
                List.of("§7Aún no disponible."), true, (j, d) -> {});

        // Paneles laterales del arte. Son estrechos —caben unos 9
        // caracteres— asi que las cifras van abreviadas y los rotulos
        // cortos. Mejor "1,2k" legible que "1250" cortado a "12".
        // Verde: identidad. La cabeza cabe donde no cabe una palabra.
        p.izquierda("@cabeza");
        p.izquierda("");
        p.izquierda("§f" + corto(jugador.getGameProfile().getName(), 9));
        if (snap != null && snap.dominantLevel > 0) {
            p.izquierda("§d" + romano(snap.dominantLevel));
        }

        // Morado: lo que TIENES PENDIENTE. El dinero ya no va aqui — se ve
        // entero en la Cartera, y repetirlo abreviado solo restaba sitio.
        // Esto en cambio es lo unico que hace que pulses algo.
        if (snap != null) {
            p.derecha("@icono:kits");
            p.derecha("§aKit");
            p.derecha("§7listo");
            p.derecha("");
            p.derecha("@icono:misiones");
            p.derecha("§e" + snap.badges);
            p.derecha("§7activas");
        }

        try {
            p.abrir(jugador);
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo abrir el Pad", e);
        }
    }
}
