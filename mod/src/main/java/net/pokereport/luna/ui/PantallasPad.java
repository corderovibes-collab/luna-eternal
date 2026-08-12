package net.pokereport.luna.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.progression.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Las pantallas del Pad que ya no son menús de cofre (P9-bis).
 *
 * <p>Todas siguen el mismo patrón: el servidor arma la pantalla con datos ya
 * resueltos y la manda. Añadir una pantalla nueva <b>no toca el mod de
 * cliente</b> — por eso el protocolo se diseñó genérico.
 */
public final class PantallasPad {

    private PantallasPad() {}

    // ------------------------------------------------------------ Cartera

    /**
     * Tus tres monedas, grandes y con su icono.
     *
     * <p>Sustituye al menú de cofre. Aquí la cifra se lee entera, sin
     * abreviar: hay sitio, al contrario que en el panel lateral.
     */
    public static void cartera(ServerPlayerEntity jugador) {
        MenuService.loadSnapshot(jugador, snap -> {
            var p = new PadService.Pantalla("cartera", "Cartera", 3, 1);

            celdaMoneda(p, 0, "moneda_dolar", Currency.POKEDOLLAR, snap,
                "Se gana jugando.", "Se gasta en la tienda y el GTS.");
            celdaMoneda(p, 1, "moneda_marca", Currency.MARK, snap,
                "Se gana capturando y explorando.",
                "No se puede comerciar (D-014).");
            celdaMoneda(p, 2, "moneda_premium", Currency.REPORTCOIN, snap,
                "Moneda premium.",
                "Nunca se convierte en las otras.");

            p.izquierda("@cabeza");
            p.izquierda("");
            p.izquierda("§f" + recorta(jugador.getGameProfile().getName()));

            p.pie("§7Ninguna moneda se convierte en otra");
            p.abrirDesde(jugador, () -> AlmanacPad.abrir(jugador));
        });
    }

    private static void celdaMoneda(PadService.Pantalla p, int col,
                                    String icono, Currency c,
                                    PlayerSnapshot snap, String... notas) {
        List<String> desc = new ArrayList<>();
        desc.add("§f" + String.format("%,d", snap.balance(c)));
        desc.add("");
        for (String n : notas) desc.add("§7" + n);
        p.celda(col, 0, icono, c.color + c.displayName, desc, false,
                (j, d) -> {});
    }

    // ------------------------------------------------------------ Vías

    /**
     * Las cinco vías, una por columna.
     *
     * <p>En vertical y en paralelo a propósito: son <b>cinco caminos
     * simultáneos</b>, no una escalera. Ponerlas en lista sugeriría un orden
     * que no existe.
     */
    public static void vias(ServerPlayerEntity jugador) {
        MenuService.loadSnapshot(jugador, snap -> {
            var p = new PadService.Pantalla("vias", "Tus Vías", 5, 1).tarjetas();

            int col = 0;
            for (Path via : Path.values()) {
                var estado = snap.paths.get(via);
                int nivel = estado == null ? 0 : estado.level();
                long xp = estado == null ? 0 : estado.xp();

                p.celda(col++, 0, iconoVia(via), via.displayName,
                        List.of("§eNivel " + romano(nivel),
                                "§7" + xp + " XP"),
                        false, (j, d) -> {});
            }

            p.izquierda("@cabeza");
            p.izquierda("");
            p.izquierda("§f" + recorta(jugador.getGameProfile().getName()));

            p.pie("§7Las cinco avanzan a la vez. No hay que elegir");
            p.abrirDesde(jugador, () -> AlmanacPad.abrir(jugador));
        });
    }

    private static String iconoVia(Path via) {
        return switch (via) {
            case ENTRENADOR -> "gimnasios";
            case COLECCIONISTA -> "pokedex";
            case EXPLORADOR -> "explorar";
            case CRIADOR -> "centro";
            case COMERCIANTE -> "gts";
        };
    }

    // ------------------------------------------------------------ Mundos

    /**
     * Los dos mundos, en tarjetas grandes (D-016).
     *
     * <p>Es la pantalla que más se parece a la referencia: dos opciones con
     * consecuencias distintas y permanentes. Una rejilla de iconos pequeños
     * no comunicaría que <b>elegir mal aquí te cuesta lo construido</b>.
     */
    public static void mundos(ServerPlayerEntity jugador) {
        var p = new PadService.Pantalla("mundos", "Puerta del Mundo", 2, 1)
            .tarjetas();

        p.celda(0, 0, "tienda", "§aMUNDO HOGAR",
                List.of("§aCon protecciones",
                        "§7Pocos Pokémon salvajes",
                        "§aPermanente"),
                false, (j, d) -> new WorldGateMenu(-1).open(j));

        p.celda(1, 0, "explorar", "§cMUNDO SALVAJE",
                List.of("§cSin protecciones",
                        "§aMuchos Pokémon",
                        "§cSe reinicia"),
                false, (j, d) -> new WorldGateMenu(-1).open(j));

        p.izquierda("@cabeza");
        p.pie("§7Nada de lo importante vive en el terreno");
        p.abrirDesde(jugador, () -> AlmanacPad.abrir(jugador));
    }

    // ------------------------------------------------------------ utilidades

    private static String recorta(String s) {
        return s.length() <= 9 ? s : s.substring(0, 8) + "…";
    }

    private static String romano(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }
}
