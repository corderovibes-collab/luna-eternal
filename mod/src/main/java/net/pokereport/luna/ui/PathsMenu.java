package net.pokereport.luna.ui;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.progression.Path;
import net.pokereport.luna.progression.ProgressionService.PathState;

/**
 * Vías: el perfil de progreso del jugador.
 *
 * <p>Es la respuesta a <i>"¿quién soy en este servidor?"</i>, y por eso va la
 * segunda en El Almanaque. Cinco barras en vez de un número dejan claro de un
 * vistazo que <b>el progreso es un perfil, no una cifra</b>
 * ({@code docs/progression/progression-model.md} §1).
 */
public final class PathsMenu extends Menu {

    private static final int BAR_WIDTH = 20;

    private final PlayerSnapshot data;

    public PathsMenu(PlayerSnapshot data) {
        super("§8✦ §dTus Vías §8✦", 6, "vias");
        this.data = data;
    }

    @Override
    protected void build(ServerPlayerEntity player) {

        set(4, Icon.of(Items.NETHER_STAR)
                .name("§d" + data.username)
                .line("§7Tu progreso no es un número: es un perfil.")
                .blank()
                .line("§8Hay más caminos que tiempo.")
                .line("§8Elegir uno es renunciar a otro, de momento.")
                .build());

        // Una vía por fila, de la 1 a la 5.
        int row = 1;
        for (Path path : Path.values()) {
            PathState state = data.paths.get(path);
            if (state == null) state = new PathState(path, 0, 0);
            drawPath(row, path, state);
            row++;
        }

        set(45, Icon.of(Items.AMETHYST_SHARD)
                .name("§bMarcas: §f" + fmt(
                    data.balance(net.pokereport.luna.economy.Currency.MARK)))
                .line("§7Se gastan en desbloqueos.")
                .blank()
                .line("§8Todo desbloqueo pide dos cosas:")
                .line("§8haber hecho el requisito §7y §8pagar en Marcas.")
                .build());

        set(48, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(53, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void drawPath(int row, Path path, PathState state) {
        String level = state.level() == 0
            ? "§8sin empezar"
            : path.color + Path.roman(state.level());

        var icon = Icon.of(path.icon)
                .name(path.color + path.displayName + " §8· " + level)
                .line("§7" + path.howToRaise);

        if (state.maxed()) {
            icon.blank().line("§6✦ Nivel máximo alcanzado");
        } else {
            icon.blank()
                .line(bar(state.fraction(), path.color))
                .line("§7" + fmt(state.xp()) + " §8/ §7" + fmt(state.xpForNext()));
        }

        icon.blank().line("§7Abre: §f" + path.unlocks);

        // Solo el icono, en la columna 1. Las demás quedan libres para los
        // desbloqueos de cada vía cuando se implementen (PROG-002).
        set(row, 1, icon.build(), null);

        // Barra repetida en huecos contiguos para que se lea sin pasar el ratón.
        int filled = (int) Math.round(state.fraction() * 5);
        for (int i = 0; i < 5; i++) {
            boolean on = i < filled;
            set(row, 3 + i, Icon.of(on
                        ? Items.LIME_STAINED_GLASS_PANE
                        : Items.GRAY_STAINED_GLASS_PANE)
                    .name(on ? path.color + "▮" : "§8▯")
                    .build(), null);
        }
    }

    /** Barra de texto, que es lo que se ve al pasar el ratón. */
    private static String bar(double fraction, String color) {
        int filled = (int) Math.round(fraction * BAR_WIDTH);
        return color + "▮".repeat(filled) + "§8" + "▯".repeat(BAR_WIDTH - filled);
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
