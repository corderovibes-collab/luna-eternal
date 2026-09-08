package net.pokereport.luna.pase;

import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.LunaEternal;

/**
 * LA PUERTA POR LA QUE ENTRA TODA LA XP DEL PASE.
 *
 * <p>Un solo metodo, {@link #ganar}, y lo llaman los embudos que ya existen
 * &mdash; {@code OficiosListener}, {@code CaptureListener}, la Torre y los
 * gimnasios&mdash;. Nada de esto se suscribe a eventos por su cuenta: ver el
 * aviso de {@link PaseXp}.
 *
 * <h2>&#9888;&#9888; NADA DE AQUI PUEDE TIRAR LA ACCION QUE LO DISPARA</h2>
 *
 * Es la misma regla que ya siguen {@code CaptureListener} y
 * {@code OficiosListener}: el jugador ya ha capturado su Pokemon o ha picado su
 * mena, y perderlo por un fallo de contabilidad del pase seria mucho peor que
 * no apuntarlo. Todo va en {@code try/catch} y todo va fuera del hilo del
 * servidor.
 *
 * <h2>&#9888; SOLO AVISA AL SUBIR DE NIVEL</h2>
 *
 * Ganar 12 XP por una captura pasa veinticinco veces por hora; subir de nivel,
 * una o dos veces al dia. Un toast por cada gota de XP seria una cortina, que
 * es exactamente lo que dice {@code CaptureListener} sobre no avisar de las
 * capturas repetidas.
 */
public final class Pase {

    private Pase() {
    }

    /**
     * Suma XP del pase a ese jugador. Nunca lanza.
     *
     * @param motivo aparece en el log si algo falla. No viaja al cliente
     */
    public static void ganar(ServerPlayerEntity jugador, long xp, String motivo) {
        if (jugador == null || xp <= 0 || LunaEternal.pase() == null) {
            return;
        }
        // Un constructor en creativo rompiendo bloques con Axiom no es un
        // jugador ganando XP: es la ciudadela en obras. Es el mismo filtro que
        // ya aplica el oficio de MINERO, subido aqui para que valga para TODAS
        // las fuentes de una vez.
        if (jugador.isCreative() || jugador.isSpectator()) {
            return;
        }
        var uuid = jugador.getUuid();
        var nombre = jugador.getName().getString();
        var servidor = jugador.getServer();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(uuid, nombre);
                var g = LunaEternal.pase().ganar(id, xp);
                if (!g.subio() || servidor == null) {
                    return;
                }
                servidor.execute(() -> {
                    if (jugador.isRemoved()) {
                        return;
                    }
                    net.pokereport.luna.ui.Aviso.logro(jugador,
                            "PASE DE BATALLA  NIVEL " + g.nivelDespues(),
                            hayPremio(g.nivelDespues())
                                    ? "Tienes premio esperando en el PokePad"
                                    : "Sigue asi",
                            "minecraft:nether_star",
                            net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                            1.0f);
                    // El estado que dibuja la pantalla ha cambiado, asi que el
                    // servidor lo reenvia. Es la leccion del 23-ago: si el
                    // servidor cambia un estado que el cliente dibuja, el
                    // servidor lo reenvia.
                    net.pokereport.luna.net.Red.enviarPase(jugador);
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo dar XP del pase ({}): {}",
                        motivo, e.toString());
            }
        });
    }

    private static boolean hayPremio(int nivel) {
        return PaseCatalogo.libre(nivel) != null || PaseCatalogo.luna(nivel) != null;
    }
}
