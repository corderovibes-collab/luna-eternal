package net.pokereport.luna.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;
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
            var p = new PadService.Pantalla("cartera", "Cartera", 3, 1)
                .tarjetas();

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
            // 3x2 y no 5x1: con cinco columnas la tarjeta mide 52 px utiles
            // y "Coleccionista" pide 65. Partir la palabra daria
            // "Coleccio / nista", que es peor que reordenar la rejilla.
            // Comprobado con tools/auditar_textos.py.
            var p = new PadService.Pantalla("vias", "Trabajos", 3, 2).tarjetas();

            int col = 0;
            for (Path via : Path.values()) {
                var estado = snap.paths.get(via);
                int nivel = estado == null ? 0 : estado.level();
                long xp = estado == null ? 0 : estado.xp();

                p.celda(col % 3, col / 3, iconoVia(via), via.displayName,
                        List.of("Nivel " + romano(nivel), xp + " XP"),
                        false, (j, d) -> {});
                col++;
            }

            p.izquierda("@cabeza");
            p.izquierda("");
            p.izquierda("§f" + recorta(jugador.getGameProfile().getName()));

            p.pie("§7Los cinco avanzan a la vez. No hay que elegir");
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

    // ------------------------------------------------------------ Pokédex

    /** Cuántas especies caben en una pantalla del Pad. */
    private static final int POR_PAGINA = 15;   // 5 x 3

    /**
     * La Pokédex, con los Pokémon en 3D.
     *
     * <p>Cada celda dibuja el <b>modelo real</b> de Cobblemon, no un icono.
     * Es la diferencia entre una lista y una coleccion: un numero 025 no dice
     * nada, un Pikachu girado en tres cuartos si.
     *
     * <p>Solo se dibujan los CAPTURADOS. Los que faltan salen bloqueados y sin
     * modelo — enseñar la silueta de lo que no tienes es de otro juego; aqui
     * el premio de capturar es justamente ver al Pokémon.
     */
    public static void pokedex(ServerPlayerEntity jugador, int pagina) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                    jugador.getUuid(), jugador.getGameProfile().getName());
                int desde = pagina * POR_PAGINA + 1;
                var entradas = LunaEternal.pokedex()
                    .range(id, desde, desde + POR_PAGINA - 1);
                var resumen = LunaEternal.pokedex().summary(id);

                jugador.getServer().execute(() ->
                    pintarPokedex(jugador, pagina, desde, entradas, resumen));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo abrir la Pokédex", e);
            }
        });
    }

    private static void pintarPokedex(
            ServerPlayerEntity jugador, int pagina, int desde,
            List<net.pokereport.luna.pokedex.PokedexService.Entry> entradas,
            net.pokereport.luna.pokedex.PokedexService.Summary resumen) {

        var p = new PadService.Pantalla("pokedex_" + pagina,
            "Pokédex  " + desde + "-" + (desde + POR_PAGINA - 1), 5, 3);
        // La Pokédex se queda en rejilla: son 15 a la vez y como tarjetas
        // no cabrian. Las tarjetas son para pantallas de 2 a 5 opciones.

        var porDex = new java.util.HashMap<Integer,
            net.pokereport.luna.pokedex.PokedexService.Entry>();
        for (var e : entradas) porDex.put(e.dexNumber(), e);

        for (int i = 0; i < POR_PAGINA; i++) {
            int dex = desde + i;
            if (dex > 251) break;              // Kanto + Johto (D-017)
            var e = porDex.get(dex);
            boolean tiene = e != null && e.caught();

            String num = String.format("%03d", dex);
            p.celda(i % 5, i / 5,
                    tiene ? "pokemon:" + e.species() : "pokedex",
                    tiene ? "§f" + num : "§8" + num,
                    tiene
                        ? List.of("§7" + e.species(),
                                  "§7Capturados: §f" + e.caughtCount(),
                                  e.shinyCaught() ? "§6¡Shiny!" : "")
                        : List.of("§8Sin capturar"),
                    !tiene, (j, d) -> {});
        }

        p.izquierda("@cabeza");
        p.izquierda("");
        p.izquierda("§f" + resumen.caught() + "§7/251");
        if (resumen.shiny() > 0) p.izquierda("§6" + resumen.shiny() + " shiny");

        // Paginación con las flechas del propio Pad seria confuso: esas son
        // navegacion. Aqui van como celdas del pie.
        p.pie("§7Página " + (pagina + 1) + " de "
              + ((251 + POR_PAGINA - 1) / POR_PAGINA));

        p.abrirDesde(jugador, () -> AlmanacPad.abrir(jugador));
    }

    // ------------------------------------------------------------ Cazas

    /**
     * Cazas y Crianza, con los Pokémon en 3D (HUNT-001).
     *
     * <p>Las mismas para todo el servidor y rotan cada 12 h. Arriba las de
     * captura, abajo las de crianza — se distinguen por posición y por color,
     * no solo por el texto.
     */
    public static void cazas(ServerPlayerEntity jugador) {
        abrirHunt(jugador, net.pokereport.luna.hunt.HuntService.Tipo.CAPTURA);
    }

    /** La otra mitad: criar. Pantalla aparte, no una fila mas. */
    public static void crianza(ServerPlayerEntity jugador) {
        abrirHunt(jugador, net.pokereport.luna.hunt.HuntService.Tipo.CRIANZA);
    }

    private static void abrirHunt(ServerPlayerEntity jugador,
                                  net.pokereport.luna.hunt.HuntService.Tipo tipo) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                    jugador.getUuid(), jugador.getGameProfile().getName());
                var ciclo = LunaEternal.hunts().cicloActual(id);
                jugador.getServer().execute(
                    () -> pintarCazas(jugador, ciclo, tipo));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudieron abrir las Cazas", e);
            }
        });
    }

    private static void pintarCazas(
            ServerPlayerEntity jugador,
            net.pokereport.luna.hunt.HuntService.Ciclo ciclo,
            net.pokereport.luna.hunt.HuntService.Tipo tipo) {

        boolean captura = tipo == net.pokereport.luna.hunt.HuntService.Tipo.CAPTURA;
        var objetivos = ciclo.objetivos().stream()
            .filter(o -> o.tipo() == tipo).toList();

        int cols = Math.max(1, objetivos.size());
        var p = new PadService.Pantalla(captura ? "cazas" : "crianza",
                                        captura ? "Cazas" : "Crianza",
                                        cols, 1).tarjetas();

        for (int i = 0; i < objetivos.size(); i++) {
            var o = objetivos.get(i);
            List<String> desc = new ArrayList<>();
            desc.add("@estrellas:" + o.rareza());       // lo unico visible
            desc.add("§7" + (captura ? "Captura" : "Cría") + ": §f"
                     + o.hechos() + " / " + o.necesarios());
            desc.add("§6Recompensa: §f" + o.premioDolar() + " PokéDólares");
            desc.add("§b" + o.premioMarca() + " Marcas");
            desc.add("");
            desc.add(o.cobrado() ? "§8Ya cobrada"
                   : o.completo() ? "§a¡Clic para cobrar!"
                   : "§8Aún no completada");

            long objetivoId = o.id();
            p.celda(i, 0, "pokemon:" + o.especie(),
                    (o.cobrado() ? "§8" : "§f") + mayus(o.especie()),
                    desc, false, (j, d) -> cobrar(j, objetivoId, tipo));
        }

        p.izquierda("@cabeza");
        p.izquierda("");
        p.izquierda("§7Rota en");
        p.izquierda("§f" + restante(ciclo.terminaEn()));

        // La otra mitad del sistema, para saber que existe. Se llega desde
        // el PokePad; poner aqui un boton propio exigiria otro indice
        // reservado y no compensa por un salto.
        p.derecha(captura ? "@icono:centro" : "@icono:cazas");
        p.derecha(captura ? "§dCrianza" : "§bCazas");
        p.derecha("§8en el");
        p.derecha("§8PokePad");

        p.pie(captura ? "§7Captúralo para completarla"
                      : "§7Cría con esa especie para completarla");
        p.abrirDesde(jugador, () -> AlmanacPad.abrir(jugador));
    }

    private static void cobrar(ServerPlayerEntity jugador, long objetivoId,
                               net.pokereport.luna.hunt.HuntService.Tipo tipo) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                    jugador.getUuid(), jugador.getGameProfile().getName());
                var r = LunaEternal.hunts().cobrar(id, objetivoId,
                                                   java.util.UUID.randomUUID());
                String msg = switch (r) {
                    case PAGADO -> "§a¡Recompensa cobrada!";
                    case NO_COMPLETO -> "§7Esa caza aún no está completa.";
                    case YA_COBRADO -> "§7Ya cobraste esa caza.";
                    case CADUCADO -> "§cEsa caza ya ha rotado.";
                };
                jugador.getServer().execute(() -> {
                    jugador.sendMessage(net.minecraft.text.Text.literal(msg), false);
                    abrirHunt(jugador, tipo);   // refrescar en su pantalla
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error cobrando una caza", e);
            }
        });
    }

    /** «7h 20m». En el panel lateral no caben más caracteres. */
    private static String restante(long finUnix) {
        long seg = finUnix - System.currentTimeMillis() / 1000L;
        if (seg <= 0) return "ya";
        long h = seg / 3600, m = (seg % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    // ------------------------------------------------------------ utilidades

    private static String recorta(String s) {
        return s.length() <= 9 ? s : s.substring(0, 8) + "…";
    }

    /** «graveler» → «Graveler». En un tooltip, en minúscula canta. */
    private static String mayus(String s) {
        return s.isEmpty() ? s
             : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String romano(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }
}
