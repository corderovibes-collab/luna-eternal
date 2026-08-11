package net.pokereport.luna.ui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.util.ArrayList;
import java.util.List;

/**
 * Curar al equipo.
 *
 * <p><b>Gratis.</b> En Diosesmon curar es una función premium; aquí no, porque
 * es una necesidad básica y no un lujo: tras el primer combate perdido, un
 * jugador sin curación se queda bloqueado. Cobrar por curar es cobrar por
 * jugar ({@code docs/ui/interfaces-catalog.md} §0).
 *
 * <p>Lo que sí tiene es un <b>cooldown</b>. Sin él, la salud deja de ser un
 * recurso y las pociones de la tienda dejan de tener sentido — y con ellas,
 * un sink entero de la economía.
 */
public final class HealMenu extends Menu {

    /** Minutos entre curaciones gratuitas. */
    private static final int COOLDOWN_MIN = 10;

    private final PlayerSnapshot data;
    private final List<Pokemon> equipo;
    private final long segundosRestantes;

    private HealMenu(PlayerSnapshot data, List<Pokemon> equipo, long restantes) {
        super("§8✦ §aCentro Pokémon §8✦", 3);
        this.data = data;
        this.equipo = equipo;
        this.segundosRestantes = restantes;
    }

    public static void open(ServerPlayerEntity player, Menu parent) {
        MenuService.loadSnapshot(player, snap -> {
            // El equipo se lee en el hilo del servidor: es estado del mundo.
            List<Pokemon> equipo = new ArrayList<>();
            try {
                Cobblemon.INSTANCE.getStorage().getParty(player).forEach(equipo::add);
            } catch (Throwable t) {
                LunaEternal.LOG.error("No se pudo leer el equipo", t);
            }
            long restan = Cooldown.remaining(player);
            var menu = new HealMenu(snap, equipo, restan);
            if (parent != null) parent.openChild(player, menu);
            else menu.open(player);
        });
    }

    @Override
    protected void build(ServerPlayerEntity player) {
        boolean necesita = equipo.stream().anyMatch(HealMenu::herido);

        set(4, Icon.of(Items.RED_BED)
                .name("§aCentro Pokémon")
                .line("§7Descansa y recupera a tu equipo.")
                .blank()
                .line("§8Gratis, siempre. Solo tiene espera.")
                .build());

        // El equipo, uno por hueco, con su estado real.
        int[] huecos = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < equipo.size() && i < 6; i++) {
            Pokemon p = equipo.get(i);
            boolean malherido = herido(p);
            set(huecos[i], Icon.of(malherido ? Items.RED_DYE : Items.LIME_DYE)
                    .name((malherido ? "§c" : "§a") + p.getDisplayName(true).getString())
                    .line("§7Nivel: §f" + p.getLevel())
                    .line("§7Salud: " + (malherido ? "§c" : "§a")
                          + p.getCurrentHealth() + "§7/" + p.getMaxHealth())
                    .build(), null);
        }

        if (equipo.isEmpty()) {
            set(13, Icon.of(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name("§7No tienes ningún Pokémon")
                    .line("§8Elige tu primer compañero en el Almanaque.")
                    .build(), null);

        } else if (!necesita) {
            set(22, Icon.of(Items.LIME_CONCRETE)
                    .name("§aTu equipo está perfecto")
                    .line("§8No hace falta curar.")
                    .build(), null);

        } else if (segundosRestantes > 0) {
            set(22, Icon.of(Items.ORANGE_CONCRETE)
                    .name("§6Todavía no")
                    .line("§7Disponible en §f" + (segundosRestantes / 60 + 1) + " min")
                    .state(LockState.COOLDOWN)
                    .blank()
                    .line("§8Mientras tanto, usa pociones.")
                    .build(), null);

        } else {
            set(22, Icon.of(Items.LIME_CONCRETE)
                    .name("§aCurar al equipo")
                    .line("§7Restaura salud, PP y estados.")
                    .action("Clic para descansar")
                    .build(),
                (p, b) -> curar(p));
        }

        set(18, Icon.of(Items.ARROW).name("§7← Atrás").build(), (p, b) -> back(p));
        set(26, Icon.of(Items.BARRIER).name("§cCerrar").build(),
            (p, b) -> p.closeHandledScreen());

        fill(Items.GRAY_STAINED_GLASS_PANE);
    }

    private void curar(ServerPlayerEntity player) {
        if (Cooldown.remaining(player) > 0) {
            player.sendMessage(Text.literal("§cTodavía no puedes curar."), false);
            return;
        }
        try {
            Cobblemon.INSTANCE.getStorage().getParty(player).heal();
            Cooldown.mark(player);
            player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.MASTER, 0.3f, 1.6f);
            player.sendMessage(Text.literal(
                "§aTu equipo está como nuevo."), false);
            player.closeHandledScreen();
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo curar el equipo", t);
            player.sendMessage(Text.literal("§cNo se pudo curar."), false);
        }
    }

    private static boolean herido(Pokemon p) {
        return p.getCurrentHealth() < p.getMaxHealth() || p.getStatus() != null;
    }

    /**
     * Cooldown en memoria, y aquí sí es correcto.
     *
     * <p>Al contrario que el de los kits, este no protege valor económico: si
     * un reinicio lo borra, lo peor que pasa es que alguien cure diez minutos
     * antes. No merece una tabla ni una consulta por uso.
     */
    private static final class Cooldown {
        private static final java.util.Map<java.util.UUID, Long> ULTIMA =
            new java.util.concurrent.ConcurrentHashMap<>();

        static long remaining(ServerPlayerEntity player) {
            Long t = ULTIMA.get(player.getUuid());
            if (t == null) return 0;
            long pasado = (System.currentTimeMillis() - t) / 1000;
            return Math.max(0, COOLDOWN_MIN * 60L - pasado);
        }

        static void mark(ServerPlayerEntity player) {
            ULTIMA.put(player.getUuid(), System.currentTimeMillis());
        }
    }
}
