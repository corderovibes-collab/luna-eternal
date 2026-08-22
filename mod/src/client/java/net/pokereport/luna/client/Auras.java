package net.pokereport.luna.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.cosmetics.CatalogoLuna;
import net.pokereport.luna.cosmetics.CatalogoLuna.Aura;
import org.joml.Vector3f;

/**
 * Dibuja las auras de los jugadores. <b>Solo cliente, y sin ninguna entidad.</b>
 *
 * <h2>Por qué no hay entidad</h2>
 *
 * Es la misma decisión que ya se tomó y se documentó para las mascotas de mundo,
 * y aquí vale igual: una entidad por jugador cuesta tick de servidor, ocupa
 * espacio en las colisiones, la ven los mobs, se cuenta para los límites de la
 * zona y puede meterse en un combate. <b>Un adorno no puede tener ninguna de esas
 * consecuencias.</b> Estas partículas nacen y mueren en el cliente: el servidor
 * solo dice <i>quién lleva qué</i>, una vez.
 *
 * <h2>⚠ El servidor manda, y aquí eso es literal</h2>
 *
 * Este fichero <b>no decide nunca</b> que alguien tenga un aura: solo dibuja lo
 * que le han dicho. Con D-039 —los cosméticos solo se consiguen comprándolos o
 * en eventos— el servidor es la única fuente, así que un cliente que se pintara
 * un aura a sí mismo se estaría engañando solo: los demás no la verían, porque
 * cada uno dibuja lo que <i>su</i> servidor le contó.
 */
public final class Auras {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("LunaEternal");

    private Auras() {
    }

    /**
     * Quién lleva qué. <b>Concurrente porque se escribe desde la red y se lee
     * desde el hilo de render</b>, que son hilos distintos: con un HashMap normal
     * esto es un fallo que aparece una vez cada mil arranques y nunca donde lo
     * buscas.
     */
    private static final Map<UUID, String> LLEVA = new ConcurrentHashMap<>();

    /** Un contador propio, y NO el tiempo del mundo. Ver `tick`. */
    private static int reloj;

    public static void recibir(UUID jugador, String aura) {
        if (aura == null || aura.isEmpty()) {
            LLEVA.remove(jugador);
        } else {
            LLEVA.put(jugador, aura);
        }
    }

    /** Al salir del servidor. Sin esto, entrar en otro mundo arrastra las de antes. */
    public static void olvidarTodo() {
        LLEVA.clear();
    }

    /**
     * Una tanda de partículas, si toca.
     *
     * <p>⚠ <b>Se usa un contador propio y no {@code world.getTime()}.</b> La
     * ciudadela tiene la hora CONGELADA ({@code fixed_time 18000}, noche
     * permanente), así que el tiempo del mundo <i>no avanza</i>: cualquier
     * cadencia calculada sobre él se quedaría clavada — o soltando siempre, o no
     * soltando nunca, según el resto. Es de los fallos que solo se dan en una
     * dimensión y por eso cuestan de encontrar.
     */
    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.isPaused() || LLEVA.isEmpty()) {
            return;
        }
        reloj++;
        for (PlayerEntity jugador : mc.world.getPlayers()) {
            String id = LLEVA.get(jugador.getUuid());
            if (id == null) {
                continue;
            }
            // ⚠ NO se dibuja el aura del jugador en primera persona: las
            //   partículas le nacerían dentro de la cámara y le taparían la
            //   pantalla. Se ve en tercera persona y en el previsualizador, que
            //   es donde uno se mira a sí mismo.
            if (jugador == mc.player && mc.options.getPerspective().isFirstPerson()) {
                continue;
            }
            if (jugador.isInvisible() || jugador.isSpectator()) {
                continue;
            }
            Aura aura = CatalogoLuna.auraDe(id);
            if (aura == null || reloj % Math.max(1, aura.cadencia()) != 0) {
                continue;
            }
            emitir(mc, jugador, aura);
        }
    }

    private static void emitir(MinecraftClient mc, PlayerEntity jugador, Aura aura) {
        ParticleEffect efecto = efecto(aura);
        if (efecto == null) {
            return;
        }
        var azar = jugador.getRandom();
        double alto = jugador.getHeight();
        for (int i = 0; i < aura.cuantas(); i++) {
            // El ángulo sale del reloj y no del azar: así la espiral y la órbita
            // giran en vez de parpadear en sitios sueltos.
            double giro = (reloj * 0.14) + (i * (Math.PI * 2 / Math.max(1, aura.cuantas())));
            Vec3d d = switch (aura.forma()) {
                case ANILLO -> new Vec3d(Math.cos(giro) * 0.55, 0.06, Math.sin(giro) * 0.55);
                case ESPIRAL -> {
                    double t = (reloj % 40) / 40.0;
                    yield new Vec3d(Math.cos(giro) * 0.45, t * alto, Math.sin(giro) * 0.45);
                }
                case NUBE -> new Vec3d(
                        (azar.nextDouble() - 0.5) * 0.9,
                        azar.nextDouble() * alto,
                        (azar.nextDouble() - 0.5) * 0.9);
                case LLUVIA -> new Vec3d(
                        (azar.nextDouble() - 0.5) * 0.8,
                        alto + 0.35,
                        (azar.nextDouble() - 0.5) * 0.8);
                case ORBITA -> {
                    double inclina = Math.sin(reloj * 0.06 + i) * 0.5;
                    yield new Vec3d(Math.cos(giro) * 0.6, alto * 0.55 + inclina,
                            Math.sin(giro) * 0.6);
                }
            };
            // Velocidad casi nula: un aura que sale disparada parece un efecto de
            // combate, y eso es justo lo que no puede pasar (ver el catálogo).
            double vy = aura.forma() == CatalogoLuna.Forma.LLUVIA ? -0.04 : 0.01;
            mc.world.addParticle(efecto,
                    jugador.getX() + d.x, jugador.getY() + d.y, jugador.getZ() + d.z,
                    0, vy, 0);
        }
    }

    /**
     * De la receta a la partícula de verdad.
     *
     * <p>⚠ <b>El color solo se aplica a {@code dust}</b>, que es la única que lo
     * admite. Teñir una llama no se puede, y hacer como que sí llevaría a diseñar
     * auras «rojas» que salen naranjas — el catálogo ya lo dice, y esto lo
     * cumple en vez de confiar en que nadie se equivoque.
     */
    private static ParticleEffect efecto(Aura aura) {
        if ("minecraft:dust".equals(aura.particula())) {
            return new DustParticleEffect(
                    new Vector3f(((aura.color() >> 16) & 0xFF) / 255f,
                            ((aura.color() >> 8) & 0xFF) / 255f,
                            (aura.color() & 0xFF) / 255f),
                    1.0f);
        }
        // ⚠ getOrEmpty y NO get: `get` de un identificador desconocido devuelve
        //   el tipo POR DEFECTO, no null. Con `get`, una errata en el catalogo
        //   saldria como partículas EQUIVOCADAS --que parecen un aura mal
        //   diseñada-- en vez de como un aviso en el log.
        var tipo = Registries.PARTICLE_TYPE
                .getOrEmpty(Identifier.of(aura.particula()))
                .orElse(null);
        if (tipo instanceof ParticleEffect simple) {
            return simple;
        }
        LOG.warn("El aura pide la particula {}, que no existe o no es simple",
                aura.particula());
        return ParticleTypes.END_ROD;
    }

}
