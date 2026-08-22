package net.pokereport.luna.client.pokepad;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.List;

/**
 * El equipo del jugador, leído del propio Cobblemon.
 *
 * <p><b>No hace falta ningún paquete nuestro.</b> El cliente ya conoce su equipo
 * —lo necesita para la barra de Pokémon y para el PC—, así que pedírselo al
 * servidor sería mandar dos veces lo mismo y abrir una forma más de que llegue
 * desincronizado.
 *
 * <p>⚠ Esto es <b>solo para dibujar</b>. Al equipar viaja la RANURA, no el
 * Pokémon, y el servidor la resuelve contra el equipo de quien manda el paquete
 * (P6). Lo que se lea aquí no decide nada.
 */
public final class MiEquipo {

    private MiEquipo() {
    }

    /** Hasta 6, con huecos incluidos: la ranura importa y no se puede compactar. */
    public static List<Pokemon> equipo() {
        List<Pokemon> out = new ArrayList<>(6);
        try {
            var party = CobblemonClient.INSTANCE.getStorage().getParty();
            for (int i = 0; i < 6; i++) {
                out.add(party.get(i));
            }
        } catch (RuntimeException e) {
            // Antes de entrar del todo al mundo el almacen puede no existir.
            // Un equipo vacio dibuja bien; una excepcion tumbaria la pantalla.
            return List.of();
        }
        return out;
    }

    /**
     * ¿Tienes en el equipo la especie que pide este cosmético?
     *
     * <p>⚠ Se usa para <b>avisar antes de pagar</b>, no para bloquear la compra.
     * Un cosmético de Mewtwo cuesta 4.000 LunaCoins y solo sirve para Mewtwo:
     * enterarse de eso al pulsar EQUIPAR, con el dinero ya gastado, es la peor
     * forma de descubrirlo. Pero prohibir comprarlo tampoco vale — alguien puede
     * querer tenerlo para cuando capture uno.
     *
     * <p>Comprobar solo el EQUIPO y no el PC es deliberado: el selector elige de
     * los seis del equipo, así que «lo tengo guardado en el PC» seguiría sin
     * dejarte equiparlo, y avisar de otra cosa sería mentir de otra manera.
     */
    public static boolean tienesLaEspecie(Cosmetico c) {
        if (!c.esMascota() || c.esDeMinecraft()) {
            return true;                 // no depende de ninguna especie
        }
        String quiere = nombreEspecie(c.especie());
        for (Pokemon p : equipo()) {
            if (p != null && p.getSpecies().getName().equalsIgnoreCase(quiere)) {
                return true;
            }
        }
        return false;
    }

    /** `cobblemon:charizard` -> `charizard`. */
    public static String nombreEspecie(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    /** La ranura del primer Pokémon del equipo que sirve para este cosmético, o -1. */
    public static int primeraRanuraValida(Cosmetico c) {
        String quiere = nombreEspecie(c.especie());
        List<Pokemon> eq = equipo();
        for (int i = 0; i < eq.size(); i++) {
            Pokemon p = eq.get(i);
            if (p != null && p.getSpecies().getName().equalsIgnoreCase(quiere)) {
                return i;
            }
        }
        return -1;
    }
}
