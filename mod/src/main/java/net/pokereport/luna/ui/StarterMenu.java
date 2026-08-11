package net.pokereport.luna.ui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.progression.Path;

/**
 * Elección del Pokémon inicial.
 *
 * <p>Es <b>la pantalla que desbloquea el juego</b>: sin un Pokémon, nada de lo
 * construido —economía, mercado, Pokédex, progresión— se puede usar, y capturar
 * el primero sin nada con lo que debilitar es cuestión de suerte.
 *
 * <p>Solo Kanto y Johto (D-017). Diosesmon pregunta primero la región y luego
 * enseña los tres; el flujo es correcto y lo adoptamos.
 */
public final class StarterMenu extends Menu {

    /** Clave de la reclamación única. */
    public static final String CLAVE = "__inicial";

    private static final int NIVEL = 5;

    /** Un inicial: especie, región y por qué elegirlo. */
    private record Inicial(String especie, String nombre, String region,
                           net.minecraft.item.Item icono, String color,
                           String tipo, String consejo) {}

    private static final Inicial[] KANTO = {
        new Inicial("bulbasaur",  "Bulbasaur",  "Kanto", Items.OXEYE_DAISY, "§a",
                    "Planta / Veneno",
                    "El más cómodo para empezar: aguanta bien los primeros combates."),
        new Inicial("charmander", "Charmander", "Kanto", Items.BLAZE_POWDER, "§c",
                    "Fuego",
                    "El más exigente al principio y el más fuerte después."),
        new Inicial("squirtle",   "Squirtle",   "Kanto", Items.HEART_OF_THE_SEA, "§b",
                    "Agua",
                    "El más equilibrado: encaja golpes y responde.")
    };

    private static final Inicial[] JOHTO = {
        new Inicial("chikorita", "Chikorita", "Johto", Items.FERN, "§a",
                    "Planta",
                    "Defensivo. Premia la paciencia."),
        new Inicial("cyndaquil", "Cyndaquil", "Johto", Items.FIRE_CHARGE, "§c",
                    "Fuego",
                    "Rápido y agresivo."),
        new Inicial("totodile",  "Totodile",  "Johto", Items.PRISMARINE_SHARD, "§b",
                    "Agua",
                    "El que más daño hace de los tres.")
    };

    private final PlayerSnapshot data;
    private final Inicial[] opciones;
    private final boolean eligiendoRegion;

    private StarterMenu(PlayerSnapshot data, Inicial[] opciones, boolean region) {
        super(region ? "§8✦ §6Tu primer compañero §8✦" : "§8✦ §6Elige §8✦", 3);
        this.data = data;
        this.opciones = opciones;
        this.eligiendoRegion = region;
    }

    /** Abre la elección, o avisa si ya se hizo. */
    public static void open(ServerPlayerEntity player, Menu parent) {
        MenuService.loadSnapshot(player, snap -> LunaEternal.submit(() -> {
            try {
                boolean yaTiene = LunaEternal.kitService()
                    .hasClaimed(snap.playerId, CLAVE);
                player.getServer().execute(() -> {
                    if (yaTiene) {
                        player.sendMessage(Text.literal(
                            "§7Ya elegiste tu primer compañero."), false);
                        return;
                    }
                    var menu = new StarterMenu(snap, null, true);
                    if (parent != null) parent.openChild(player, menu);
                    else menu.open(player);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("Error abriendo la eleccion de inicial", e);
            }
        }));
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        if (eligiendoRegion) {
            construirRegiones();
        } else {
            construirOpciones();
        }
        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void construirRegiones() {
        set(4, Icon.of(Items.WRITABLE_BOOK)
                .name("§6Tu primer compañero")
                .line("§7Tengo Pokémon de dos regiones distintas.")
                .line("§7¿De cuál quieres elegir?")
                .blank()
                .line("§8Esta elección no se puede deshacer.")
                .build());

        set(11, Icon.of(Items.RED_DYE)
                .name("§cPrimera generación §8· §7Kanto")
                .line("§7Bulbasaur · Charmander · Squirtle")
                .action("Clic para ver los tres")
                .build(),
            (p, b) -> openChild(p, new StarterMenu(data, KANTO, false)));

        set(15, Icon.of(Items.LIME_DYE)
                .name("§aSegunda generación §8· §7Johto")
                .line("§7Chikorita · Cyndaquil · Totodile")
                .action("Clic para ver los tres")
                .build(),
            (p, b) -> openChild(p, new StarterMenu(data, JOHTO, false)));

        set(26, Icon.of(Items.BARRIER).name("§cAhora no").build(),
            (p, b) -> p.closeHandledScreen());
    }

    private void construirOpciones() {
        set(4, Icon.of(Items.WRITABLE_BOOK)
                .name("§6Elige tu compañero")
                .line("§7" + opciones[0].region())
                .blank()
                .line("§8No hay una opción mala.")
                .line("§8Las tres llegan igual de lejos.")
                .build());

        int[] huecos = {11, 13, 15};
        for (int i = 0; i < opciones.length && i < 3; i++) {
            Inicial op = opciones[i];
            set(huecos[i], Icon.of(op.icono())
                    .name(op.color() + op.nombre())
                    .line("§7Tipo: §f" + op.tipo())
                    .line("§7Nivel: §f" + NIVEL)
                    .blank()
                    .line("§8" + op.consejo())
                    .action("Clic para elegirlo")
                    .build(),
                (p, b) -> entregar(p, op));
        }

        set(18, Icon.of(Items.ARROW).name("§7← Otra región").build(), (p, b) -> back(p));
        set(26, Icon.of(Items.BARRIER).name("§cAhora no").build(),
            (p, b) -> p.closeHandledScreen());
    }

    /**
     * Entrega el inicial.
     *
     * <p>Se marca primero y se entrega después: lo malo aquí es entregar dos
     * (un exploit), no dejar de entregar uno (recuperable). Si la entrega
     * falla, se deshace la marca.
     */
    private void entregar(ServerPlayerEntity player, Inicial op) {
        var server = player.getServer();
        player.closeHandledScreen();

        LunaEternal.submit(() -> {
            try {
                if (!LunaEternal.kitService().claimOnce(data.playerId, CLAVE)) {
                    server.execute(() -> player.sendMessage(Text.literal(
                        "§7Ya elegiste tu primer compañero."), false));
                    return;
                }

                server.execute(() -> {
                    boolean entregado = false;
                    try {
                        var props = PokemonProperties.Companion.parse(
                            op.especie() + " level=" + NIVEL);
                        var pokemon = props.create();
                        entregado = Cobblemon.INSTANCE.getStorage()
                            .getParty(player).add(pokemon);
                    } catch (Throwable t) {
                        LunaEternal.LOG.error("No se pudo crear el inicial {}",
                            op.especie(), t);
                    }

                    if (!entregado) {
                        deshacer();
                        player.sendMessage(Text.literal(
                            "§cNo se pudo entregar. Inténtalo otra vez."), false);
                        return;
                    }

                    player.sendMessage(Text.literal(
                        "§8[§6Luna Eternal§8] §f" + op.nombre()
                        + " §7se une a tu equipo."), false);
                    player.sendMessage(Text.literal(
                        "§8Abre §7El Almanaque §8con el libro de tu inventario."), false);

                    // Elegir compañero es el primer paso de la vía Entrenador.
                    LunaEternal.submit(() -> {
                        try {
                            LunaEternal.progression().grant(
                                data.playerId, Path.ENTRENADOR, 25);
                            LunaEternal.quests().advance(data.playerId,
                                net.pokereport.luna.quest.Quest.Objective.Type.STARTER, 1);
                        } catch (Exception e) {
                            LunaEternal.LOG.error("No se pudo dar XP del inicial", e);
                        }
                    });
                });

            } catch (Exception e) {
                LunaEternal.LOG.error("Error entregando el inicial", e);
                deshacer();
            }
        });
    }

    private void deshacer() {
        LunaEternal.submit(() -> {
            try {
                LunaEternal.kitService().undoOnce(data.playerId, CLAVE);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo deshacer la marca del inicial", e);
            }
        });
    }
}
