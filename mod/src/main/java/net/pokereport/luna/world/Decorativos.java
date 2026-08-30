package net.pokereport.luna.world;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

/**
 * POKÉMON DE DECORACIÓN: quietos, sin etiqueta y sin nivel.
 *
 * <p>Petición del usuario para el laboratorio de Oak: un Mewtwo flotando dentro
 * de un recipiente y los tres iniciales de Kanto delante, <i>«que no vayan a
 * hacer ninguna acción, ahí de decoración»</i>.
 *
 * <h2>⚠⚠ ES UN POKÉMON DE VERDAD, NO UNA ESTATUA</h2>
 *
 * No hay forma de dibujar un modelo de Cobblemon sin su entidad: los modelos y
 * sus animaciones viven dentro del mod. Así que se usa su entidad y se le
 * apagan <b>todas</b> las cosas que la hacen un Pokémon vivo.
 *
 * <h2>⚠⚠⚠ Y SON SIETE COSAS, NO UNA</h2>
 *
 * Olvidar cualquiera deja algo que se nota:
 *
 * <table>
 *   <tr><td>{@code hideNameRendering}</td><td>sin la etiqueta ni el nivel encima</td></tr>
 *   <tr><td>{@code setAiDisabled}</td><td>no anda, no mira, no huye</td></tr>
 *   <tr><td>{@code uncatchable}</td><td><b>no se puede capturar</b>. Sin esto,
 *       la decoración del laboratorio dura hasta el primer jugador con una
 *       Poké Ball</td></tr>
 *   <tr><td>{@code setInvulnerable}</td><td>no se le puede pegar</td></tr>
 *   <tr><td>{@code setSilent}</td><td>callado. Cuatro Pokémon haciendo ruido en
 *       un cuarto pequeño es insoportable</td></tr>
 *   <tr><td>{@code setPersistent}</td><td>no desaparece al alejarse. Sin esto,
 *       la decoración se esfuma sola y nadie sabe por qué</td></tr>
 *   <tr><td>{@code enablePoseTypeRecalculation}</td><td>⚠ <b>la que no es
 *       obvia</b>: Cobblemon recalcula la postura en cada tick. Sin apagarlo,
 *       poner «dormido» dura exactamente un tick</td></tr>
 * </table>
 *
 * <p>⚠ Y llevan una etiqueta de marcador ({@link #MARCA}) para poder borrarlos
 * después. Sin una marca, quitar la decoración obliga a acertarle con el ratón
 * a cada uno — y a distinguirlos de un Pokémon de verdad.
 */
public final class Decorativos {

    private Decorativos() {}

    /** La etiqueta que los distingue de un Pokémon cualquiera. */
    public static final String MARCA = "luna_decorativo";

    /**
     * La SEGUNDA etiqueta: este decorativo es una parada del moto taxi.
     *
     * <h2>⚠⚠ DOS ETIQUETAS Y NO UNA, Y ES LA DIFERENCIA ENTRE DOS COSAS</h2>
     *
     * {@link #MARCA} dice «esto es decoración» y de ella cuelgan las diez
     * protecciones. Ésta dice «además, es un punto de viaje», y de ella cuelga
     * <b>abrir la pantalla al hacer clic derecho</b>.
     *
     * <p>Con una sola, el Kabutops del laboratorio y los tres iniciales
     * abrirían Viajes al tocarlos — y eso no lo ha pedido nadie.
     */
    public static final String MARCA_PARADA = "luna_parada";

    /**
     * Las posturas que se pueden pedir.
     *
     * <p>⚠ Tres y no las quince de Cobblemon: las demás son estados de combate
     * o de movimiento, y una postura de andar en algo que no anda se ve peor
     * que estar de pie.
     */
    public enum Postura {
        QUIETO(PoseType.STAND, false),
        DORMIDO(PoseType.SLEEP, false),
        // ⚠ Flotando va SIN GRAVEDAD, si no cae al suelo y se queda ahí con
        //   cara de estar volando. Es lo que hace falta para el recipiente del
        //   laboratorio.
        FLOTANDO(PoseType.HOVER, true);

        public final PoseType pose;
        public final boolean sinGravedad;

        Postura(PoseType pose, boolean sinGravedad) {
            this.pose = pose;
            this.sinGravedad = sinGravedad;
        }
    }

    /**
     * NADIE LES PEGA. Se registra una vez, al arrancar.
     *
     * <h2>⚠⚠⚠ `setInvulnerable` NO BASTA, Y ESTE ES EL MOTIVO EXACTO</h2>
     *
     * {@code Entity.isInvulnerableTo} dice, literalmente, que la invulnerabilidad
     * <b>no se aplica si quien pega es un jugador en creativo</b>:
     *
     * <pre>
     *   invulnerable &amp;&amp; !fuente.isIn(BYPASSES_INVULNERABILITY)
     *                &amp;&amp; !fuente.isSourceCreativePlayer()
     * </pre>
     *
     * <p>Y en este servidor <b>todos los que colocan decoración son operadores
     * en creativo</b>, o sea justo el único caso que la bandera no cubre. Por eso
     * el usuario vio que sí se les podía pegar: la protección estaba puesta y no
     * aplicaba a quien lo probó.
     *
     * <p>Este evento se dispara <b>antes</b> de decidir nada, así que corta
     * también el creativo.
     *
     * <p>⚠⚠⚠ Y por eso hay que dejar pasar {@code BYPASSES_INVULNERABILITY} a
     * mano: sin esa excepción cortaba también {@code /kill} y el vacío, y un
     * decorativo mal puesto se volvía <b>imposible de quitar</b>. Aquí ponía que
     * {@code /kill} seguía funcionando y era falso — la consola contesta
     * «Killed N entities» y no muere ninguno.
     */
    /** ¿Este Pokémon es de decoración? */
    public static boolean esDecorativo(com.cobblemon.mod.common.pokemon.Pokemon p) {
        try {
            return p != null && p.getPersistentData().getBoolean(MARCA);
        } catch (Throwable ignorado) {
            return false;
        }
    }

    /**
     * CLIC DERECHO EN UNA PARADA: abre Viajes.
     *
     * <h2>⚠⚠ SOLO LOS QUE LLEVAN LA SEGUNDA ETIQUETA</h2>
     *
     * {@link #MARCA} es «esto es decoración» y la llevan también el Kabutops y
     * los tres iniciales del laboratorio. Si esto respondiera a esa, tocar un
     * Squirtle abriría el moto taxi — que no lo ha pedido nadie. Responde a
     * {@link #MARCA_PARADA}, que solo ponen las paradas.
     *
     * <h2>⚠⚠ Y DEVUELVE `SUCCESS` PARA CORTAR LO DE DEBAJO</h2>
     *
     * Sin cortar, el clic sigue su camino hasta Cobblemon y llega a su menú de
     * interacción de Pokémon. Y como el evento se dispara en <b>los dos
     * lados</b>, hay que cortar en los dos: si solo se cortara en el servidor,
     * el cliente enseñaría un instante el menú de Cobblemon antes de que llegue
     * el nuestro.
     */
    public static void abrirViajesAlTocar() {
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (jugador, mundo, mano, entidad, golpe) -> {
                    if (!entidad.getCommandTags().contains(MARCA_PARADA)) {
                        return net.minecraft.util.ActionResult.PASS;
                    }
                    // ⚠ Una sola mano: sin esto el evento llega dos veces (mano
                    //   principal y secundaria) y la pantalla se abriría dos
                    //   veces por cada clic.
                    if (mano != net.minecraft.util.Hand.MAIN_HAND) {
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    if (jugador instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                        net.pokereport.luna.net.Red.enviarViajes(sp, true);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                });
        LunaEternal.LOG.info("Paradas: el clic derecho abre Viajes");
    }

    /**
     * NO ENTRAN EN LA POKÉDEX.
     *
     * <h2>⚠⚠ UN DECORATIVO REGISTRADO ES UNA POKÉDEX MENTIROSA</h2>
     *
     * La Pokédex de este servidor está limitada a Kanto y Johto a propósito
     * (D-017), y el Miraidon de una parada es de novena generación. Dejar que se
     * registre significa que <b>ver una estatua cuenta como haber encontrado un
     * Pokémon</b> — y encima uno que no existe en el juego.
     *
     * <p>⚠ Se cortan LOS DOS eventos y no solo uno. {@code POKEMON_SEEN} es el
     * de «lo he visto» y {@code POKEDEX_DATA_CHANGED_PRE} el de «se va a
     * escribir en la Pokédex»: cancelar solo el primero deja abierto el escaneo
     * con la Pokédex, que es justo lo que el usuario probó.
     */
    public static void fueraDeLaPokedex() {
        try {
            com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_SEEN
                    .subscribe(evento -> {
                        if (esDecorativo(evento.getPokemon())) {
                            evento.cancel();
                        }

                    });
            com.cobblemon.mod.common.api.events.CobblemonEvents
                    .POKEDEX_DATA_CHANGED_PRE.subscribe(evento -> {
                        var datos = evento.getDataSource();
                        if (datos != null && esDecorativo(datos.getPokemon())) {
                            evento.cancel();
                        }

                    });
            LunaEternal.LOG.info("Decorativos: fuera de la Pokédex");
        } catch (Throwable t) {
            // ⚠ Si Cobblemon cambia estos eventos, el resto del mod sigue vivo.
            //   Lo que se pierde es que un decorativo se pueda registrar, que es
            //   feo pero no rompe nada.
            LunaEternal.LOG.warn("No se pudo excluir los decorativos de la "
                    + "Pokédex: {}", t.toString());
        }
    }

    public static void protegerlos() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
                .ALLOW_DAMAGE.register((entidad, fuente, cantidad) -> {
                    // ⚠ Se mira LA ETIQUETA y no si es un PokemonEntity: así
                    //   protege a cualquier decorativo, sea de la especie que
                    //   sea, y no protege a los Pokémon de verdad.
                    if (!entidad.getCommandTags().contains(MARCA)) {
                        return true;
                    }
                    // ⚠⚠⚠ PERO `/kill` TIENE QUE SEGUIR FUNCIONANDO, Y NO LO
                    //    HACIA. Aquí se cortaba TODO el daño, y eso incluye el
                    //    de `/kill` y el del vacío: un decorativo mal puesto se
                    //    volvía IMPOSIBLE de quitar salvo con nuestros propios
                    //    comandos. Y el síntoma era el peor posible — la consola
                    //    contesta «Killed 3 entities» y no muere ninguno.
                    //
                    //    Se descubrió intentando limpiar tres Brocks apilados en
                    //    una arena, y la documentación decía desde el principio
                    //    que `/kill` seguía funcionando. No era verdad.
                    //
                    //    ⚠ Esta etiqueta es la que el propio Minecraft usa para
                    //      «esto atraviesa la invulnerabilidad»: el vacío,
                    //      `/kill` y poco más. Un jugador en creativo NO está
                    //      ahí, así que la protección que importa se mantiene.
                    return fuente.isIn(net.minecraft.registry.tag.DamageTypeTags
                            .BYPASSES_INVULNERABILITY);
                });
        LunaEternal.LOG.info("Decorativos: protegidos del daño, creativo incluido "
                + "(pero /kill sigue funcionando)");
    }

    /**
     * Coloca uno. Devuelve {@code null} si la especie no existe.
     *
     * <p>⚠ La postura se fija <b>después</b> de soltarlo en el mundo: antes de
     * existir, el rastreador de datos no se sincroniza con nadie y el valor se
     * pierde en el camino.
     */
    public static PokemonEntity colocar(ServerWorld mundo, String especie,
                                        Postura postura, Vec3d donde, float giro) {
        PokemonEntity e;
        try {
            // ⚠ `uncatchable` es una propiedad de Cobblemon, no un invento
            //   nuestro: se pone con su misma sintaxis y la respeta su propio
            //   código de captura. Comprobado en el jar (UncatchableProperty).
            var props = PokemonProperties.Companion.parse(
                    especie.toLowerCase(java.util.Locale.ROOT) + " uncatchable");
            e = props.createEntity(mundo);
        } catch (Exception ex) {
            LunaEternal.LOG.warn("No se pudo crear el decorativo {}: {}",
                    especie, ex.toString());
            return null;
        }

        e.refreshPositionAndAngles(donde.x, donde.y, donde.z, giro, 0f);
        e.setHeadYaw(giro);
        e.setBodyYaw(giro);

        e.hideNameRendering();
        e.setAiDisabled(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent();
        e.setNoGravity(postura.sinGravedad);
        e.addCommandTag(MARCA);
        // ⚠⚠ Y EL POKEMON TAMBIEN, no solo la entidad. Los eventos de Pokédex
        //    reciben el <b>Pokémon</b>, no la entidad que lo lleva: sin marcar
        //    el objeto no hay forma de saber, desde ahí, que esto era
        //    decoración. `getPersistentData` es un hueco de Cobblemon pensado
        //    justo para que otros mods guarden lo suyo.
        try {
            e.getPokemon().getPersistentData().putBoolean(MARCA, true);
        } catch (Throwable ignorado) {
            // Si algún día cambia, el decorativo sigue siendo decorativo: lo
            // único que se pierde es el bloqueo de la Pokédex.
        }
        // ⚠⚠ ESTA ES LA QUE NO ES OBVIA. Cobblemon recalcula la postura en cada
        //    tick a partir de si anda, vuela o nada. Sin apagarlo, «dormido»
        //    dura un tick y vuelve a ponerse de pie.
        e.setEnablePoseTypeRecalculation(false);

        if (!mundo.spawnEntity(e)) {
            return null;
        }
        e.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), postura.pose);
        // ⚠⚠ NO SE PUEDE RETAR. `uncatchable` impide la Poké Ball, pero NO el
        //    combate: sin esto, el Miraidon del punto de viaje se convierte en
        //    el jefe final del servidor y alguien se pasa la tarde peleándose
        //    con la decoración. Es un campo propio de Cobblemon
        //    (`UNBATTLEABLE`), comprobado en el jar.
        e.getDataTracker().set(PokemonEntity.getUNBATTLEABLE(), true);
        return e;
    }

    /** Cuántos decorativos hay cerca, y los borra. */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio) {
        var caja = net.minecraft.util.math.Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(PokemonEntity.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        return n;
    }
}
