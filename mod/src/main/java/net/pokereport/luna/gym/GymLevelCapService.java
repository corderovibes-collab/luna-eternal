package net.pokereport.luna.gym;

import java.util.UUID;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Toque;

/**
 * CONTROL DE TOPE DE NIVEL Y OBEDIENCIA POR GIMNASIOS.
 *
 * <p>Reglas definidas:
 * <ul>
 *   <li>Atrapar Pokémon de cualquier nivel está <b>permitido</b>.</li>
 *   <li>Utilizarlos en combate o sacarlos de la Pokéball al mundo si superan el
 *       tope de nivel del jugador está <b>bloqueado</b>.</li>
 *   <li>Subir de nivel (ganar experiencia o usar caramelos) por encima del tope
 *       está <b>bloqueado</b>.</li>
 * </ul>
 *
 * <h2>Escala de progresión según {@link Gimnasio#TODOS}</h2>
 *
 * <ul>
 *   <li>0 medallas: tope <b>15</b> (antes de Brock / Nv. 15)</li>
 *   <li>Tras Brock: tope <b>19</b> (antes de Misty / Nv. 19)</li>
 *   <li>Tras Misty: tope <b>24</b> (antes de Surge / Nv. 24)</li>
 *   <li>Tras Surge: tope <b>28</b> (antes de Erika / Nv. 28)</li>
 *   <li>Fórmula: nivel del primer líder pendiente. Si se vencieron todos: 100.</li>
 * </ul>
 */
public final class GymLevelCapService {

    private GymLevelCapService() {}

    /**
     * Calcula el nivel máximo permitido para un jugador según sus medallas.
     */
    public static int nivelMaximo(UUID uuid) {
        if (uuid == null) {
            return 100;
        }
        for (Gimnasio.Gimnasio_ g : Gimnasio.TODOS) {
            if (!MedallaService.tiene(uuid, g)) {
                return g.nivel();
            }
        }
        return 100;
    }

    /**
     * Registra los eventos de Cobblemon y Fabric para forzar el tope de nivel.
     */
    public static void registrar() {
        // 1. Bloquear sacar el Pokémon al mundo si supera el nivel permitido
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(event -> {
            var pokemon = event.getPokemon();
            if (pokemon == null) return;
            UUID uuid = pokemon.getOwnerUUID();
            if (uuid == null) return;

            ServerPlayerEntity player = pokemon.getOwnerPlayer();
            if (player == null && event.getLevel() != null && event.getLevel().getServer() != null) {
                player = event.getLevel().getServer().getPlayerManager().getPlayer(uuid);
            }
            if (player != null && player.isCreative()) return;

            int cap = nivelMaximo(uuid);
            if (pokemon.getLevel() > cap) {
                event.cancel();
                if (player != null && !Toque.repetido(uuid, "cap_sent")) {
                    player.sendMessage(Text.literal(
                            "§c§l¡NO TE OBEDECE! §r§7Tu nivel máximo permitido es §eNv. " + cap + "§7.\n"
                            + "§e" + pokemon.getDisplayName(false).getString() + " §7(Nv. " + pokemon.getLevel() + ") "
                            + "requiere que derrotes a más líderes de gimnasio."), false);
                }
            }
        });

        // 2. Bloquear combates si el jugador incluye Pokémon por encima del nivel permitido
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            var battle = event.getBattle();
            if (battle == null) return;

            for (var actor : battle.getActors()) {
                for (UUID uuid : actor.getPlayerUUIDs()) {
                    ServerPlayerEntity player = null;
                    for (var p : battle.getPlayers()) {
                        if (p != null && p.getUuid().equals(uuid)) {
                            player = p;
                            break;
                        }
                    }
                    if (player != null && player.isCreative()) continue;

                    int cap = nivelMaximo(uuid);
                    for (var bp : actor.getPokemonList()) {
                        var orig = bp.getOriginalPokemon();
                        if (orig != null && orig.getLevel() > cap) {
                            event.cancel();
                            if (player != null && !Toque.repetido(uuid, "cap_battle")) {
                                player.sendMessage(Text.literal(
                                        "§c§l¡COMBATE BLOQUEADO! §r§7Tu equipo contiene a §e"
                                        + orig.getDisplayName(false).getString() + " §7(Nv. " + orig.getLevel() + ").\n"
                                        + "§7Tu límite actual es §cNv. " + cap + "§7. ¡Vence a más líderes de gimnasio!"), false);
                            }
                            return;
                        }
                    }
                }
            }
        });

        // 3. Limitar ganancia de experiencia al nivel tope
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(event -> {
            var pokemon = event.getPokemon();
            if (pokemon == null) return;
            UUID uuid = pokemon.getOwnerUUID();
            if (uuid == null) return;

            int cap = nivelMaximo(uuid);
            if (pokemon.getLevel() >= cap) {
                event.setExperience(0);
                event.cancel();
                return;
            }

            // Clampar la EXP para no sobrepasar el tope
            int maxExp = pokemon.getExperienceToLevel(cap);
            if (maxExp <= 0) {
                event.setExperience(0);
                event.cancel();
            } else if (event.getExperience() > maxExp) {
                event.setExperience(maxExp);
            }
        });

        // 4. Bloquear uso de caramelos raros y de EXP si está en el tope
        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.subscribe(event -> {
            var pokemon = event.getPokemon();
            var player = event.getPlayer();
            if (pokemon == null || player == null) return;
            if (player.isCreative()) return;

            int cap = nivelMaximo(player.getUuid());
            if (pokemon.getLevel() >= cap) {
                event.cancel();
                if (!Toque.repetido(player.getUuid(), "cap_candy")) {
                    player.sendMessage(Text.literal(
                            "§c§l¡LÍMITE DE NIVEL! §r§7" + pokemon.getDisplayName(false).getString()
                            + " ya alcanzó tu límite actual (§eNv. " + cap + "§7).\n"
                            + "§7Derrota al siguiente líder de gimnasio para seguir subiéndolo."), false);
                }
            }
        });

        // 5. Bloquear interacción / montura con Pokémon desobediente que esté en el mundo
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, golpe) -> {
            if (entidad instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
                var p = pe.getPokemon();
                if (p != null && jugador.getUuid().equals(p.getOwnerUUID())) {
                    if (jugador.isCreative()) return ActionResult.PASS;
                    int cap = nivelMaximo(jugador.getUuid());
                    if (p.getLevel() > cap) {
                        if (jugador instanceof ServerPlayerEntity sp
                                && !Toque.repetido(sp.getUuid(), "cap_interact")) {
                            sp.sendMessage(Text.literal(
                                    "§c§l¡NO TE OBEDECE! §r§7Tu límite actual es §eNv. " + cap + "§7. "
                                    + p.getDisplayName(false).getString() + " (Nv. " + p.getLevel() + ") no te obedece."), false);
                        }
                        return ActionResult.FAIL;
                    }
                }
            }
            return ActionResult.PASS;
        });

        LunaEternal.LOG.info("Gimnasios: servicio de límite de nivel y obediencia activo");
    }
}
