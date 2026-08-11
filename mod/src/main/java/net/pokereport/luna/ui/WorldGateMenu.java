package net.pokereport.luna.ui;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Puerta del mundo: elegir entre el Mundo Hogar y el Mundo Salvaje.
 *
 * <p>Dos tarjetas grandes y tres puntos cada una. Diosesmon usa este mismo
 * patrón y funciona: se compara de un vistazo y no hay que leer un manual.
 *
 * <p>Lo que añadimos y a ellos les falta: <b>decir cuándo es el próximo
 * reinicio</b>. Un jugador que construye en el Salvaje sin saberlo y lo pierde
 * tiene toda la razón en enfadarse — y ese enfado se evita con una línea de
 * texto ({@code docs/world/worlds.md} §9).
 */
public final class WorldGateMenu extends Menu {

    /** Días para el reinicio. -1 mientras no haya temporada configurada. */
    private final int daysToReset;

    public WorldGateMenu(int daysToReset) {
        super("§8✦ §3Puerta del Mundo §8✦", 5);
        this.daysToReset = daysToReset;
    }

    @Override
    protected void build(ServerPlayerEntity player) {

        set(4, Icon.of(Items.COMPASS)
                .name("§3¿A dónde quieres ir?")
                .line("§7Puedes ir y volver cuando quieras.")
                .line("§8No es una elección permanente.")
                .build());

        // ---------------------------------------------------- Mundo Hogar
        card(2, Items.OAK_DOOR, "§a🏠 MUNDO HOGAR",
             new String[]{
                 "§7Tu casa, tu almacén, tu base.",
                 "",
                 "§a✔ §7Puedes proteger tu terreno",
                 "§a✔ §7No se borra §fnunca",
                 "§7· Aparecen menos Pokémon",
                 "§7· Sin legendarios"
             },
             "§8La ciudadela aún no está construida.");

        // -------------------------------------------------- Mundo Salvaje
        String resetLine = daysToReset < 0
            ? "§8· Nueva temporada: sin fecha aún"
            : "§6· Nueva temporada en §f" + daysToReset + " días";

        card(6, Items.GRASS_BLOCK, "§2🌿 MUNDO SALVAJE",
             new String[]{
                 "§7Explorar, cazar, expediciones.",
                 "",
                 "§a✔ §7Muchos más Pokémon",
                 "§a✔ §7Aquí salen los legendarios",
                 "§c✘ §7Sin protecciones",
                 resetLine
             },
             "§8La ciudadela aún no está construida.");

        // El aviso que evita el enfado, dicho antes de entrar y no después.
        set(3, 4, Icon.of(Items.WRITABLE_BOOK)
                .name("§e¿Qué pasa al reiniciar el Salvaje?")
                .line("§aSe conserva todo lo que importa:")
                .line("§7 · Tus Pokémon")
                .line("§7 · Tu dinero y tu progreso")
                .line("§7 · Tu casa del Mundo Hogar")
                .blank()
                .line("§cSolo se pierde:")
                .line("§7 · Lo que hayas construido §fen el Salvaje")
                .build(), null);

        set(40, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(44, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    /** Tarjeta de mundo, centrada en su columna. */
    private void card(int col, net.minecraft.item.Item item, String title,
                      String[] lines, String disabledReason) {
        var icon = Icon.of(item).name(title);
        for (String l : lines) {
            if (l.isEmpty()) icon.blank(); else icon.line(l);
        }
        icon.state(LockState.DISABLED).line(disabledReason);
        set(1, col, icon.build(), null);
    }
}
