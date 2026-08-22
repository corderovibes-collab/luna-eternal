package net.pokereport.luna.client.pokepad;

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Qué mascota lleva puesta cada jugador, en el cliente.
 *
 * <p>Lo manda el servidor ({@code Red.CosmeticoPuesto}) y aquí solo se guarda
 * para dibujarlo. El cliente <b>no decide</b> quién lleva qué: con
 * {@code D-039}, donde comprar es la única vía, eso sería la forma de saltárselo.
 *
 * <h2>Por qué hay un estado de animación por jugador</h2>
 *
 * Es la misma lección que costó cuatro intentos en la tienda: dos sitios que
 * dibujan el mismo modelo con un {@code FloatingState} compartido se pisan la
 * pose y el reloj de animación. Aquí el riesgo es mayor —dos jugadores pueden
 * llevar el mismo cosmético a la vez— así que la clave es el <b>jugador</b>, no
 * el cosmético.
 */
public final class MascotasPuestas {

    private MascotasPuestas() {
    }

    /** Especie y aspecto de la mascota de alguien. Especie vacía = no lleva. */
    public record Puesta(String especie, String aspecto) {
        public boolean vacia() {
            return especie.isEmpty();
        }
    }

    private static final Map<UUID, Puesta> PUESTAS = new HashMap<>();
    private static final Map<UUID, FloatingState> ESTADOS = new HashMap<>();

    /**
     * Guarda lo que manda el servidor.
     *
     * <p>Especie vacía significa <b>quitarse la mascota</b>, y por eso se borra
     * la entrada en vez de guardar un registro vacío: si se dejara, el mapa
     * crecería con cada jugador que alguna vez llevó algo.
     *
     * <p>⚠ El estado de animación se tira <b>solo al cambiar de cosmético</b>.
     * Tirarlo en cada paquete reiniciaría la animación cada vez que el servidor
     * reenvía lo mismo — y lo reenvía a todos cada vez que alguien se equipa
     * algo.
     */
    public static void guardar(UUID jugador, String especie, String aspecto) {
        Puesta nueva = new Puesta(especie, aspecto);
        Puesta anterior = PUESTAS.get(jugador);
        if (anterior != null && !anterior.equals(nueva)) {
            ESTADOS.remove(jugador);
        }
        if (nueva.vacia()) {
            PUESTAS.remove(jugador);
            ESTADOS.remove(jugador);
        } else {
            PUESTAS.put(jugador, nueva);
        }
    }

    /** {@code null} si no lleva nada, o si aún no ha llegado su paquete. */
    public static Puesta de(UUID jugador) {
        return PUESTAS.get(jugador);
    }

    public static FloatingState estado(UUID jugador) {
        return ESTADOS.computeIfAbsent(jugador, k -> new FloatingState());
    }

    /**
     * Se olvida todo al salir del servidor.
     *
     * <p>Sin esto, entrar en otro servidor enseñaría a la gente con las mascotas
     * del anterior — y peor: los UUID coinciden entre servidores, así que no
     * serían mascotas ajenas al azar, serían <b>las de las mismas personas</b>,
     * que es mucho más creíble y por tanto más difícil de detectar como fallo.
     */
    public static void olvidar() {
        PUESTAS.clear();
        ESTADOS.clear();
    }
}
