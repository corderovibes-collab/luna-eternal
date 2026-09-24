package net.pokereport.luna.torrebatalla;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.gitlab.srcmc.rctapi.api.ai.RCTBattleAI;

import net.pokereport.luna.LunaEternal;

/**
 * IA de la Torre de Batalla (robusta para combates individuales y dobles).
 *
 * <p>Diseñada específicamente para eliminar de raíz los bloqueos (soft-locks)
 * durante cambios forzados y KO en combates 2vs2 y 1vs1:
 * <ul>
 *   <li>Coordina los cambios forzados entre ambos slots del actor para que
 *       jamás elijan el mismo Pokémon de banquillo ante un KO doble.</li>
 *   <li>Marca explícitamente {@code setWillBeSwitchedIn(true)} y reserva el
 *       UUID para evitar conflictos de validación en Showdown/Cobblemon.</li>
 *   <li>Devuelve estrictamente {@link PassActionResponse} cuando {@code movimientos == null}
 *       en fases de cambio forzado donde el slot actual sigue vivo, impidiendo que
 *       {@link StrongBattleAI} reciba movimientos nulos y lance excepciones.</li>
 *   <li>Protege la ejecución de turnos normales con un {@code try-catch} integral
 *       que delega en {@link RandomBattleAI} en caso de cualquier excepción interna
 *       del tracker de {@link StrongBattleAI}.</li>
 * </ul>
 * </p>
 */
final class TorreDoublesAI implements BattleAI {
    private final StrongBattleAI turnos;
    private final RCTBattleAI sustituciones = new RCTBattleAI();
    private final RandomBattleAI emergencia = new RandomBattleAI();

    private ShowdownActionRequest ultimoRequest = null;
    private final Set<UUID> elegidosEnPeticion = new HashSet<>();

    TorreDoublesAI(int skill) {
        this.turnos = new StrongBattleAI(skill);
    }

    @Override
    public ShowdownActionResponse choose(ActiveBattlePokemon activo, PokemonBattle batalla,
                                         BattleSide lado, ShowdownMoveset movimientos,
                                         boolean cambioForzado) {
        BattleActor actor = activo.getActor();
        if (actor != null) {
            ShowdownActionRequest req = actor.getRequest();
            if (req != null && req != ultimoRequest) {
                ultimoRequest = req;
                elegidosEnPeticion.clear();
            }
        }

        // 1. Detección de cambio forzado para este slot:
        boolean esForzado = cambioForzado || activo.isGone()
                || (activo.hasPokemon() && !activo.isAlive());

        if (esForzado) {
            return resolverCambioForzado(activo, batalla, lado, movimientos);
        }

        // 2. Si no es forzado pero movimientos == null, estamos en la fase de cambio forzado
        //    donde cayó el OTRO slot o el rival. Showdown requiere obligatoriamente "pass".
        //    Jamás delegar a StrongBattleAI con movimientos nulos porque colapsa.
        if (movimientos == null) {
            return PassActionResponse.INSTANCE;
        }

        // 3. Turno normal de combate con movimientos disponibles:
        try {
            ShowdownActionResponse respuesta = turnos.choose(activo, batalla, lado, movimientos, false);
            if (respuesta != null) {
                return respuesta;
            }
        } catch (Throwable error) {
            LunaEternal.LOG.warn("Torre: fallo StrongBattleAI en turno normal slot={}, usando emergencia: {}",
                    activo.getPNX(), error.getMessage());
        }

        try {
            return emergencia.choose(activo, batalla, lado, movimientos, false);
        } catch (Throwable t) {
            LunaEternal.LOG.error("Torre: fallo critico de emergencia en slot={}", activo.getPNX(), t);
            return PassActionResponse.INSTANCE;
        }
    }

    private ShowdownActionResponse resolverCambioForzado(
            ActiveBattlePokemon activo, PokemonBattle batalla,
            BattleSide lado, ShowdownMoveset movimientos) {

        BattleActor actor = activo.getActor();
        List<BattlePokemon> pokemonList = actor != null ? actor.getPokemonList() : List.of();

        // Candidatos válidos de banquillo:
        // Con vida > 0, no enviados al campo, no marcados como pendientes de cambio
        // y no elegidos en este mismo ciclo de peticion por el slot hermano.
        List<BattlePokemon> candidatos = new ArrayList<>();
        for (BattlePokemon bp : pokemonList) {
            if (bp == null) continue;
            UUID u = bp.getUuid();
            if (bp.getHealth() > 0 && !bp.isSentOut() && !bp.getWillBeSwitchedIn()
                    && !elegidosEnPeticion.contains(u)) {
                candidatos.add(bp);
            }
        }

        if (candidatos.isEmpty()) {
            LunaEternal.LOG.info("Torre: sin Pokemon disponibles para cambio en slot={}", activo.getPNX());
            return new DefaultActionResponse();
        }

        BattlePokemon elegido = null;

        // Intentar evaluación táctica de RCTBattleAI si está disponible:
        try {
            ShowdownActionResponse rctRes = sustituciones.choose(activo, batalla, lado, movimientos, true);
            if (rctRes instanceof SwitchActionResponse sar) {
                UUID deseado = sar.getNewPokemonId();
                if (deseado != null && !elegidosEnPeticion.contains(deseado)) {
                    for (BattlePokemon bp : candidatos) {
                        if (deseado.equals(bp.getUuid())) {
                            elegido = bp;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable error) {
            LunaEternal.LOG.warn("Torre: fallo al evaluar sustitucion RCT en slot={}: {}",
                    activo.getPNX(), error.getMessage());
        }

        // Si RCT no eligió o eligió un Pokémon no disponible / duplicado:
        if (elegido == null) {
            // Priorizar por mayor vida absoluta
            candidatos.sort((a, b) -> Integer.compare(b.getHealth(), a.getHealth()));
            elegido = candidatos.get(0);
        }

        // Coordinación de slots: marcar en Cobblemon y en nuestra memoria local
        elegido.setWillBeSwitchedIn(true);
        elegidosEnPeticion.add(elegido.getUuid());

        LunaEternal.LOG.info("Torre: cambio forzado exitoso slot={} Pokemon={}",
                activo.getPNX(), elegido.getName().getString());
        return new SwitchActionResponse(elegido.getUuid());
    }

    @Override
    public void onHealthChange(BattleHealthChangePacket packet) {
        try {
            turnos.onHealthChange(packet);
        } catch (Throwable ignored) {}
        try {
            sustituciones.onHealthChange(packet);
        } catch (Throwable ignored) {}
    }
}
