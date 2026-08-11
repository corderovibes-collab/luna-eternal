package net.pokereport.luna.ui;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.world.LunaDimensions;
import net.pokereport.luna.world.TravelService;

/**
 * Puerta del Mundo: elegir entre el Mundo Hogar y el Mundo Salvaje.
 *
 * <p>Dos tarjetas grandes con tres puntos cada una. Diosesmon usa este mismo
 * patrón y funciona: se compara de un vistazo, sin manual.
 *
 * <p>Lo que añadimos y a ellos les falta: <b>decir cuándo es el próximo
 * reinicio y qué se pierde</b>. Un jugador que construye en el Salvaje sin
 * saberlo tiene toda la razón en enfadarse, y ese enfado se evita con una
 * línea de texto ({@code docs/world/worlds.md} §9).
 */
public final class WorldGateMenu extends Menu {

    /** Días para el reinicio. -1 mientras no haya temporada configurada. */
    private final int daysToReset;

    public WorldGateMenu(int daysToReset) {
        super("§8✦ §3Puerta del Mundo §8✦", 5, "puerta");
        this.daysToReset = daysToReset;
    }

    @Override
    protected void build(ServerPlayerEntity player) {

        var current = player.getWorld().getRegistryKey();

        set(4, Icon.of(Items.COMPASS)
                .name("§3¿A dónde quieres ir?")
                .line("§7Ahora estás en §f" + TravelService.nameOf(current))
                .blank()
                .line("§8Puedes ir y volver cuando quieras.")
                .line("§8No es una elección permanente.")
                .build());

        // ---------------------------------------------------- Mundo Hogar
        card(2, Items.OAK_DOOR, "§a🏠 MUNDO HOGAR", new String[]{
                "§7Tu casa, tu almacén, tu base.",
                "",
                "§a✔ §7Puedes proteger tu terreno",
                "§a✔ §7No se borra §fnunca",
                "§8· Aparecen menos Pokémon",
                "§8· Sin legendarios"
            },
            current.equals(LunaDimensions.HOGAR),
            (p, b) -> TravelService.travel(p, LunaDimensions.HOGAR, "el Mundo Hogar"));

        // -------------------------------------------------- Mundo Salvaje
        String resetLine = daysToReset < 0
            ? "§8· Nueva temporada: sin fecha aún"
            : "§6· Nueva temporada en §f" + daysToReset + " días";

        card(6, Items.GRASS_BLOCK, "§2🌿 MUNDO SALVAJE", new String[]{
                "§7Explorar, cazar, expediciones.",
                "",
                "§a✔ §7Muchos más Pokémon",
                "§a✔ §7Aquí salen los legendarios",
                "§c✘ §7Sin protecciones",
                resetLine
            },
            current.equals(LunaDimensions.SALVAJE),
            (p, b) -> TravelService.travel(p, LunaDimensions.SALVAJE, "el Mundo Salvaje"));

        // El aviso, dicho ANTES de entrar y no después de perder la casa.
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

        // Volver a la ciudadela, disponible desde cualquier sitio.
        set(3, 2, Icon.of(Items.BELL)
                .name("§6Volver a la Ciudadela")
                .line("§7Servicios, mercado y comunidad.")
                .action("Clic para viajar")
                .build(),
            (p, b) -> TravelService.travel(p, LunaDimensions.CIUDADELA, "la Ciudadela"));

        set(40, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(44, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    /** Tarjeta de mundo. Si ya estás allí, se marca en vez de repetir el viaje. */
    private void card(int col, Item item, String title, String[] lines,
                      boolean isCurrent, ClickAction action) {
        var icon = Icon.of(item).name(title);
        for (String l : lines) {
            if (l.isEmpty()) icon.blank(); else icon.line(l);
        }

        if (isCurrent) {
            icon.blank().line("§a✦ Estás aquí ahora mismo");
            set(1, col, icon.build(), null);
        } else {
            icon.action("Clic para viajar");
            set(1, col, icon.build(), action);
        }
    }
}
