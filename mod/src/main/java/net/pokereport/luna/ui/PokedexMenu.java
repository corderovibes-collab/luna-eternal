package net.pokereport.luna.ui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.pokedex.PokedexService;

import java.util.HashMap;
import java.util.Map;

/**
 * Pokédex: lo visto, lo capturado y lo que aún no ha llegado.
 *
 * <p>Muestra las especies <b>inactivas también</b>, marcadas como "aún no ha
 * llegado a este mundo". Ocultarlas se leería como carencia; enseñarlas
 * convierte la limitación en una promesa — el jugador ve que el mundo va a
 * crecer ({@code docs/pokemon/generations.md} §4).
 */
public final class PokedexMenu extends Menu {

    /** Última especie activa. Kanto + Johto (D-017). */
    private static final int ULTIMA_ACTIVA = 251;

    private static final int POR_PAGINA = 28;
    private static final int TOTAL = 1025;

    private final PlayerSnapshot data;
    private final PokedexService.Summary resumen;
    private final Map<Integer, PokedexService.Entry> registro;
    private final int pagina;

    private PokedexMenu(PlayerSnapshot data, PokedexService.Summary resumen,
                        Map<Integer, PokedexService.Entry> registro, int pagina) {
        super("§8✦ §bPokédex §8· §7" + (pagina * POR_PAGINA + 1)
              + "-" + Math.min((pagina + 1) * POR_PAGINA, TOTAL) + " §8✦", 6);
        this.data = data;
        this.resumen = resumen;
        this.registro = registro;
        this.pagina = pagina;
    }

    /** Carga la página y la abre. Consulta acotada al rango visible. */
    public static void open(ServerPlayerEntity player, Menu parent, int pagina) {
        MenuService.loadSnapshot(player, snap -> LunaEternal.submit(() -> {
            try {
                int desde = pagina * POR_PAGINA + 1;
                int hasta = Math.min(desde + POR_PAGINA - 1, TOTAL);

                var resumen = LunaEternal.pokedex().summary(snap.playerId);
                var filas = LunaEternal.pokedex().range(snap.playerId, desde, hasta);

                Map<Integer, PokedexService.Entry> porNumero = new HashMap<>();
                for (var e : filas) porNumero.put(e.dexNumber(), e);

                player.getServer().execute(() -> {
                    var menu = new PokedexMenu(snap, resumen, porNumero, pagina);
                    if (parent != null) parent.openChild(player, menu);
                    else menu.open(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error abriendo la Pokédex", e);
                player.getServer().execute(() -> player.sendMessage(
                    Text.literal("§cNo se pudo abrir la Pokédex."), false));
            }
        }));
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        cabecera();

        int desde = pagina * POR_PAGINA + 1;
        var porNumero = especiesPorNumero();

        for (int i = 0; i < POR_PAGINA; i++) {
            int dex = desde + i;
            if (dex > TOTAL) break;
            int fila = 1 + (i / 7);
            int col  = 1 + (i % 7);
            dibujarEntrada(fila, col, dex, porNumero.get(dex));
        }

        pie();
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    // ------------------------------------------------------------ dibujo

    private void cabecera() {
        int completadas = resumen.caught();
        int porcentaje = ULTIMA_ACTIVA == 0 ? 0
            : (int) Math.round(100.0 * completadas / ULTIMA_ACTIVA);

        set(4, Icon.of(Items.ENCHANTED_BOOK)
                .name("§bPokédex de " + data.username)
                .line("§7Capturadas: §f" + completadas + "§8/" + ULTIMA_ACTIVA
                      + " §8(" + porcentaje + "%)")
                .line("§7Vistas: §f" + resumen.seen())
                .line("§7Variocolor: §e" + resumen.shiny())
                .blank()
                .line(barra(completadas, ULTIMA_ACTIVA))
                .blank()
                .line("§8Ahora mismo el mundo tiene " + ULTIMA_ACTIVA + " especies.")
                .line("§8Las demás llegarán con el tiempo.")
                .build());
    }

    /**
     * Una entrada. Cuatro estados, y cada uno dice algo distinto:
     * capturada, vista, desconocida, y <b>aún no llegada</b> — que no es lo
     * mismo que desconocida y no debe parecerlo.
     */
    private void dibujarEntrada(int fila, int col, int dex, Species especie) {
        boolean activa = dex <= ULTIMA_ACTIVA;
        var entrada = registro.get(dex);
        String numero = "§8#" + String.format("%04d", dex);

        if (!activa) {
            set(fila, col, Icon.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(numero + " §8· §7Aún no ha llegado")
                    .line("§8Esta especie todavía no existe en este mundo.")
                    .blank()
                    .line("§8Llegará en una expansión futura.")
                    .build(), null);
            return;
        }

        String nombre = especie != null ? especie.getName() : "???";

        if (entrada != null && entrada.caught()) {
            var icon = Icon.of(entrada.shinyCaught() ? Items.NETHER_STAR : Items.LIME_DYE)
                    .name(numero + " §a" + nombre)
                    .line("§aCapturada")
                    .line("§7Veces: §f" + entrada.caughtCount());
            if (entrada.bestLevel() != null) {
                icon.line("§7Mejor nivel: §f" + entrada.bestLevel());
            }
            if (entrada.shinyCaught()) icon.line("§e✦ Variocolor conseguido");
            if (entrada.firstMoonPhase() != null) {
                icon.blank().line("§8La primera, con " + luna(entrada.firstMoonPhase()));
            }
            set(fila, col, icon.build(), null);

        } else if (entrada != null && entrada.seen()) {
            set(fila, col, Icon.of(Items.YELLOW_DYE)
                    .name(numero + " §e" + nombre)
                    .line("§eVista, sin capturar")
                    .blank()
                    .line("§8Te falta atraparla.")
                    .build(), null);

        } else {
            set(fila, col, Icon.of(Items.GRAY_DYE)
                    .name(numero + " §8· §7Desconocida")
                    .line("§8Aún no la has visto.")
                    .build(), null);
        }
    }

    private void pie() {
        int ultimaPagina = (TOTAL - 1) / POR_PAGINA;

        if (pagina > 0) {
            set(45, Icon.of(Items.ARROW).name("§7← Anterior").build(),
                (p, b) -> PokedexMenu.open(p, null, pagina - 1));
        }
        if (pagina < ultimaPagina) {
            set(52, Icon.of(Items.ARROW).name("§7Siguiente →").build(),
                (p, b) -> PokedexMenu.open(p, null, pagina + 1));
        }

        // Salto a lo que falta: con 37 páginas, llegar a la 9 a base de
        // clics es una tarea, no una interfaz.
        set(48, Icon.of(Items.COMPASS)
                .name("§bIr a lo que falta")
                .line("§7Salta a la primera especie sin capturar.")
                .build(),
            (p, b) -> irAloQueFalta(p));

        set(49, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());
    }

    // ------------------------------------------------------------ auxiliares

    /**
     * Especies por número de Pokédex.
     *
     * <p>Se pide a Cobblemon en cada apertura en vez de cachearse: la lista
     * puede cambiar si se instala un addon, y una caché desactualizada aquí
     * enseñaría nombres equivocados.
     */
    private static Map<Integer, Species> especiesPorNumero() {
        Map<Integer, Species> out = new HashMap<>();
        try {
            for (Species s : PokemonSpecies.getSpecies()) {
                out.putIfAbsent(s.getNationalPokedexNumber(), s);
            }
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo leer el registro de especies", t);
        }
        return out;
    }

    /**
     * Salta a la primera especie sin capturar. La consulta va a la base: en
     * memoria solo tenemos la ventana de 28 entradas de esta página.
     */
    private void irAloQueFalta(ServerPlayerEntity player) {
        var server = player.getServer();
        LunaEternal.submit(() -> {
            try {
                int dex = LunaEternal.pokedex()
                    .firstUncaughtDex(data.playerId, ULTIMA_ACTIVA);
                if (dex > ULTIMA_ACTIVA) {
                    server.execute(() -> player.sendMessage(Text.literal(
                        "§a¡Las tienes todas! Pokédex completa."), false));
                    return;
                }
                int pag = (dex - 1) / POR_PAGINA;
                server.execute(() -> PokedexMenu.open(player, null, pag));
            } catch (Exception e) {
                LunaEternal.LOG.error("Error buscando la siguiente sin capturar", e);
            }
        });
    }

    private static String barra(int hecho, int total) {
        int ancho = 20;
        int lleno = total == 0 ? 0 : (int) Math.round((double) hecho / total * ancho);
        return "§a" + "▮".repeat(lleno) + "§8" + "▯".repeat(ancho - lleno);
    }

    private static String luna(int fase) {
        return switch (fase) {
            case 0 -> "luna llena";
            case 4 -> "luna nueva";
            default -> "luna " + fase;
        };
    }
}
