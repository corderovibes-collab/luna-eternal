package net.pokereport.luna.progression;

import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

/**
 * Los OFICIOS: minar, pescar y cosechar dan XP, y subir de nivel paga.
 *
 * <h2>El diseño, en una línea</h2>
 *
 * <b>Una Vía desbloquea contenido; un oficio da dinero.</b> Por eso solo los
 * oficios pagan ({@link Path#plataPorNivel}): si subir de Vía también pagara,
 * la progresión sería una fuente de ingresos, y P3 dice sumideros antes que
 * fuentes.
 *
 * <h2>⚠ El servidor es la única fuente, y aquí eso importa de verdad</h2>
 *
 * Esto <b>crea Plata de la nada</b> —es una fuente, no una transferencia— así que
 * cada camino que llega hasta aquí tiene que salir de un evento del servidor y
 * nunca de algo que diga el cliente. Los seis enganches actuales son eventos de
 * Minecraft o de Cobblemon; ninguno lleva datos del cliente.
 *
 * <h2>⚠ Todo va por el executor de E/S</h2>
 *
 * Se llama desde manejadores de evento, que corren en el hilo del servidor. Una
 * consulta ahí congela a todo el mundo. {@code LunaEternal.submit()}.
 */
public final class OficiosService {

    private OficiosService() {
    }

    /**
     * Lo que se paga por tener TODOS los oficios al máximo. <b>Una vez.</b>
     *
     * <p>⚠⚠ BAJO DE 250 A 100 POR DECISION DEL USUARIO, y hay que decir en voz
     * alta lo que eso significa: <b>con 100 no se compra ningún cosmético</b>. El
     * más barato del catálogo es un sombrero a 1.200.
     *
     * <p>Eso no lo convierte en un error —es una decisión de producto, y una
     * moneda premium escasa es defendible— pero sí <b>traslada todo el peso a los
     * eventos</b>. D-039 ya dice que los eventos son «la mitad que hace funcionar
     * la decisión»; con este número, dejan de ser la mitad y pasan a ser
     * prácticamente la única vía gratuita real.
     *
     * <p>Si algún día se quiere que completar los oficios baste para <i>algo</i>,
     * hay dos palancas: subir esto a ~1.200, o bajar el precio de algún cosmético
     * de entrada. Las dos son decisiones de producto, no de código.
     */
    private static final long PREMIO_LUNACOINS = 100;

    /**
     * Da XP de un oficio y paga si con eso ha subido de nivel.
     *
     * <p>⚠ <b>Se llama YA DESDE el hilo de E/S</b>, no lo cambia de hilo por su
     * cuenta: los enganches necesitan resolver el {@code player_id} antes, que ya
     * es una consulta, y encadenar dos saltos de hilo por cada mena picada sería
     * una cola de tareas por jugador.
     */
    public static void ganar(ServerPlayerEntity jugador, long playerId,
                             Path oficio, long xp) throws Exception {
        if (xp <= 0) {
            return;
        }
        var subida = LunaEternal.progression().grantDetallado(playerId, oficio, xp);
        if (!subida.subio()) {
            return;
        }

        // ⚠ SE PAGA CADA NIVEL QUE SE HA CRUZADO, no solo el último. Una sola
        //   concesión grande --un comando de prueba, o una recompensa de misión--
        //   puede saltar de I a IV, y pagar únicamente el IV regalaría dos
        //   niveles de trabajo. El bucle es lo que hace que el pago dependa del
        //   camino recorrido y no del sitio donde se acabó.
        long total = 0;
        for (int n = subida.nivelAnterior() + 1; n <= subida.estado().level(); n++) {
            total += oficio.plataPorNivel(n);
        }
        if (total > 0) {
            // ⚠ CLAVE DE IDEMPOTENCIA DERIVADA DEL NIVEL, y aquí sí es correcta.
            //   `oficio:<jugador>:<via>:<nivel>` sólo puede ocurrir una vez porque
            //   los niveles NO BAJAN: subir a Minero III es un hecho irrepetible.
            //   (Con los cosméticos una clave derivada estaba mal justo porque
            //   aquello sí se podía deshacer y volver a comprar.)
            //
            //   Y así un reintento tras un corte de red no paga dos veces.
            String clave = "oficio:" + playerId + ":" + oficio.name()
                    + ":" + subida.estado().level();
            LunaEternal.economy().credit(playerId, Currency.POKEDOLLAR, total,
                    "oficio_nivel", clave);
        }

        // ⚠ EL ICONO ES EL DE LA VIA, el mismo que sale en la pantalla de
        //   Trabajos. Que el aviso y la pantalla enseñen lo mismo es lo que hace
        //   que uno lleve al otro sin explicarlo.
        String objeto = net.minecraft.registry.Registries.ITEM
                .getId(oficio.icon).toString();
        String detalle = total > 0
                ? String.format("Nivel %s  ·  +%,d Plata",
                        Path.roman(subida.estado().level()), total)
                : "Nivel " + Path.roman(subida.estado().level());
        net.pokereport.luna.ui.Aviso.logro(
                jugador, oficio.displayName.toUpperCase(java.util.Locale.ROOT),
                detalle, objeto);

        comprobarCompletos(jugador, playerId);
    }

    /**
     * Si TODOS los oficios están al máximo, paga las LunaCoins una vez.
     *
     * <p>⚠⚠ <b>Es la única forma de conseguir LunaCoins sin pagar.</b> D-013 y
     * D-014 dicen que la moneda premium no se compra con moneda del juego <i>en
     * ninguna dirección</i>; un logro que la reparte a chorro sería esa conversión
     * por la puerta de atrás, solo que más lenta. Ver {@link #PREMIO_LUNACOINS}
     * para lo que implica la cifra actual.
     *
     * <p>⚠ La clave de idempotencia va derivada del jugador, sin nivel ni fecha, y
     * <b>es correcta aquí</b>: los niveles no bajan, así que «completar todos los
     * oficios» ocurre como mucho una vez en la vida de una cuenta. La economía
     * rechaza la segunda con {@code ALREADY_APPLIED} y no hace falta tabla.
     */
    private static void comprobarCompletos(ServerPlayerEntity jugador, long playerId)
            throws Exception {
        var todos = LunaEternal.progression().all(playerId);
        for (Path oficio : Path.oficios()) {
            var estado = todos.get(oficio);
            if (estado == null || estado.level() < Path.MAX_LEVEL) {
                return;
            }
        }
        try {
            LunaEternal.economy().credit(playerId, Currency.REPORTCOIN, PREMIO_LUNACOINS,
                    "oficios_completos", "oficios_completos:" + playerId);
        } catch (net.pokereport.luna.economy.EconomyException e) {
            // Ya se pagó. No es un fallo: es exactamente lo que la clave existe
            // para conseguir, y llegar aquí dos veces es normal --se comprueba
            // cada vez que se sube un nivel estando ya al tope--.
            return;
        }
        net.pokereport.luna.ui.Aviso.logro(jugador, "OFICIOS COMPLETOS",
                "+" + PREMIO_LUNACOINS + " LunaCoins", "minecraft:nether_star",
                net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
    }

    /** Atajo para los enganches: resuelve el jugador y concede, en el hilo de E/S. */
    public static void ganarAsync(ServerPlayerEntity jugador, Path oficio, long xp) {
        if (jugador == null || xp <= 0) {
            return;
        }
        UUID uuid = jugador.getUuid();
        String nombre = jugador.getName().getString();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                ganar(jugador, id, oficio, xp);
            } catch (Exception e) {
                // Que no se apunte una mena no puede tirar nada: se anota y sigue.
                LunaEternal.LOG.warn("No se pudo dar XP de {}: {}",
                        oficio.name(), e.toString());
            }
        });
    }
}
