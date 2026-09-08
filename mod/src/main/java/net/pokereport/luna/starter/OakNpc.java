package net.pokereport.luna.starter;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.Decorativos;
import net.pokereport.luna.world.LunaDimensions;

/**
 * EL PROFESOR OAK: LA UNICA PUERTA AL POKEMON INICIAL.
 *
 * <h2>&#9888;&#9888;&#9888; ESTO NO AÑADE LA PANTALLA: LE PONE UNA PUERTA</h2>
 *
 * La eleccion de inicial existe desde el 2026-08-23 y funciona: {@code
 * StarterService} entrega, {@code InicialScreen} dibuja los seis en 3D y el
 * protocolo esta escrito. Lo que hace este fichero es cambiar <b>por donde se
 * entra</b>: antes la pantalla <b>se abria sola</b> al entrar al servidor
 * --decision de entonces, y buena: <i>«quien acaba de entrar no sabe que el
 * PokePad existe»</i>-- y hoy hay que <b>ir a ver a Oak</b>.
 *
 * <p>&#9888;&#9888; <b>Y esa decision vieja no era tonta, asi que se sustituye
 * por otra cosa, no se borra sin mas.</b> Un jugador nuevo que aparece sin
 * Pokemon y sin que nadie le diga nada esta igual de perdido que antes. Lo que
 * lo arregla es que Oak <b>esta en la llegada</b>, tiene un cartel encima y el
 * servidor le dice por el chat a donde ir la primera vez que entra.
 *
 * <h2>&#9888;&#9888;&#9888; OAK NO SE DIBUJA: YA VIENE DENTRO DE rctmod</h2>
 *
 * Se busco en el jar antes de plantearse dibujar nada, y estan las tres pieles
 * ({@code professor_oak_00c8}, {@code professor_oak_00d2},
 * {@code prof_prof_oak_01ff}) <b>y sus tres entrenadores completos</b> en
 * {@code data/rctmod/trainers/}. O sea: cero arte, cero datapack, y el mod ya
 * esta instalado en los dos lados desde los gimnasios.
 *
 * <p>&#9888;&#9888; <b>Y trae un Tauros de NIVEL 99.</b> Eso no es un adorno: un
 * {@code TrainerMob} con {@code forceBattleOnSight} a ocho bloques seria <b>el
 * jefe final del servidor plantado en la plaza</b>. Lo corta {@code
 * setAiDisabled(true)} <b>antes del primer tick</b> --como Brock-- y el clic
 * derecho, que se ataja aqui antes de que llegue a {@code interactMob}.
 *
 * @see StarterService el que entrega, y el que decide si ya eligio
 */
public final class OakNpc {

    /**
     * El entrenador de rctmod que se usa.
     *
     * <p>&#9888; De las tres pieles de Oak que trae el mod, esta es la del
     * Oak clasico. Si algun dia no existiera, {@link #colocar} <b>no coloca
     * nada</b> y lo dice: un muñeco generico plantado en el laboratorio
     * pareceria un fallo del sistema y no un identificador mal escrito.
     */
    public static final String ENTRENADOR = "professor_oak_00c8";

    /** La etiqueta que convierte a un entrenador en la puerta del inicial. */
    public static final String MARCA = "luna_oak";

    /** La del cartel que flota encima. */
    private static final String MARCA_CARTEL = "luna_oak_cartel";

    /** Cuanto flota el cartel sobre sus pies. */
    private static final double ALTURA_CARTEL = 2.45;

    /** Radio que se barre al limpiar antes de colocar. */
    private static final double RADIO = 6.0;

    private OakNpc() {
    }

    /**
     * CLIC DERECHO EN OAK.
     *
     * <p>&#9888;&#9888;&#9888; <b>{@code SUCCESS} no es cosmetico aqui: es lo que
     * impide un combate.</b> Si el evento devolviera {@code PASS}, el clic
     * seguiria su camino hasta {@code TrainerMob.interactMob} y Oak sacaria su
     * Tauros de nivel 99. Se corta en los dos lados porque el evento corre en
     * los dos.
     *
     * <p>&#9888;&#9888; <b>Y la respuesta se decide en el servidor, no en la
     * pantalla</b> (P6): si ya eligio, no se le manda la pantalla siquiera. Un
     * cliente modificado que la abriera igual se encontraria con que
     * {@code claimOnce} le dice que no, porque quien de verdad corta el doble
     * inicial es la clave de la base -- pero no hay ninguna razon para dejarle
     * llegar hasta ahi.
     */
    public static void registrarClic() {
        UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, golpe) -> {
            if (!entidad.getCommandTags().contains(MARCA)) {
                return ActionResult.PASS;
            }
            // Sneak en creativo: para poder moverlo o mirarlo sin abrir nada.
            if (jugador.isCreative() && jugador.isSneaking()) {
                return ActionResult.PASS;
            }
            if (mano != Hand.MAIN_HAND) {
                return ActionResult.SUCCESS;
            }
            if (jugador instanceof ServerPlayerEntity sp) {
                atender(sp);
            }
            return ActionResult.SUCCESS;
        });
    }

    /**
     * Le abre la pantalla, o le dice por que no.
     *
     * <p>&#9888; Va por el executor de E/S porque pregunta a la base si ya
     * eligio, y consultar desde el hilo del servidor esta prohibido.
     */
    private static void atender(ServerPlayerEntity jugador) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                        jugador.getUuid(), jugador.getGameProfile().getName());
                if (StarterService.yaEligio(id)) {
                    hablar(jugador, "§7«Ya te llevaste el tuyo, "
                            + jugador.getGameProfile().getName()
                            + ". Cuidalo bien.»");
                    return;
                }
                net.pokereport.luna.net.Red.enviarAbrirInicial(jugador);
            } catch (Exception e) {
                LunaEternal.LOG.warn("Oak no pudo atender a {}: {}",
                        jugador.getGameProfile().getName(), e.toString());
                hablar(jugador, "§cOak esta ocupado ahora mismo. Vuelve en un momento.");
            }
        });
    }

    /** Un mensaje de Oak por el chat, siempre desde el hilo del servidor. */
    private static void hablar(ServerPlayerEntity jugador, String texto) {
        var servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        servidor.execute(() -> {
            if (!jugador.isRemoved()) {
                jugador.sendMessage(Text.literal("§6§lPROF. OAK §8» §r" + texto), false);
            }
        });
    }

    /**
     * COLOCA A OAK DONDE ESTA EL JUGADOR.
     *
     * <p>&#9888; Se barre antes de poner, siempre. El comando se puede repetir y
     * a Oak <b>no se le puede pegar ni matar</b> --lleva la etiqueta de los
     * decorativos-- asi que un Oak de mas se quedaria en la plaza para siempre.
     * Es la trampa que ya costo tres Brocks apilados.
     *
     * @return {@code true} si quedo colocado
     */
    public static boolean colocar(ServerPlayerEntity jugador) {
        var mundo = jugador.getServerWorld();
        if (!LunaDimensions.CIUDADELA.equals(mundo.getRegistryKey())) {
            return false;
        }
        return colocar(mundo, jugador.getPos(), jugador.getYaw());
    }

    /** Lo mismo, con la posicion dada. */
    public static boolean colocar(ServerWorld mundo, Vec3d donde, float giro) {
        if (!idValido()) {
            LunaEternal.LOG.error("El entrenador '{}' NO EXISTE en los datos: no "
                    + "se coloca a Oak. Un muñeco generico en el laboratorio "
                    + "pareceria un fallo del inicial y no un id mal escrito.",
                    ENTRENADOR);
            return false;
        }
        quitar(mundo, donde, RADIO);

        Entity bruto = TrainerMob.getEntityType().create(mundo);
        if (!(bruto instanceof TrainerMob mob)) {
            LunaEternal.LOG.error("No se pudo crear la entidad de Oak");
            return false;
        }
        mob.refreshPositionAndAngles(donde.x, donde.y, donde.z, giro, 0f);
        mob.setHeadYaw(giro);
        mob.setBodyYaw(giro);
        mob.setTrainerId(ENTRENADOR);

        // ⚠⚠⚠ Y SE LE QUITA EL NOMBRE, QUE SI NO SALE DOS VECES.
        //    `setTrainerId` llama por dentro a `udpateCustomName` --el typo es
        //    suyo-- y le pone al mob un CUSTOM NAME. Vanilla dibuja la etiqueta
        //    de un mob con nombre EN CUANTO LE APUNTAS
        //    (`MobEntityRenderer.hasLabel`: `hasCustomName() && targetedEntity`),
        //    asi que encima del cartel bueno aparecia un «Professor Oak» blanco
        //    y pequeño -- y en blanco sobre las paredes blancas del laboratorio
        //    lo unico que hacia era emborronar el nuestro.
        //    ⚠ `setCustomNameVisible(false)` NO BASTA: esa via es la de
        //      `shouldRenderName()`, y la de apuntar pregunta por
        //      `hasCustomName()`. Hay que dejarlo en null.
        mob.setCustomName(null);
        mob.setCustomNameVisible(false);

        // ⚠⚠⚠ ESTAS CUATRO VAN ANTES DE `spawnEntity`, Y NO ES INDIFERENTE:
        //    `setAiDisabled` es lo unico que impide que rete solo, y
        //    `ForceIntoBattleGoal` es un GOAL --corre en el primer tick--. Aqui
        //    no hay ningun tick de por medio porque todavia no esta en el mundo.
        mob.setAiDisabled(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        // ⚠ Sin esto rctmod se lo lleva solo: `checkDespawnIfUnseen` pregunta
        //   por `isPersistent()` antes de nada.
        mob.setPersistent(true);

        mob.addCommandTag(MARCA);
        // ⚠⚠ La etiqueta de los decorativos, y no por pereza: es de la que
        //    cuelga la proteccion que SI cubre el creativo
        //    (ServerLivingEntityEvents.ALLOW_DAMAGE). Sin ella, cualquier
        //    operador le rompe la cara a Oak sin querer -- y `setInvulnerable`
        //    NO cubre el creativo, lo dice el propio Minecraft.
        mob.addCommandTag(Decorativos.MARCA);

        if (!mundo.spawnEntity(mob)) {
            return false;
        }
        cartel(mundo, donde);
        LunaEternal.LOG.info("Inicial: Profesor Oak colocado en {}",
                mob.getBlockPos());
        return true;
    }

    /**
     * EL CARTEL QUE FLOTA ENCIMA.
     *
     * <p>&#9888;&#9888; <b>Es la mitad de que la puerta se entienda.</b> Un
     * jugador nuevo no sabe que ese señor de bata da Pokemon: sin cartel, Oak es
     * un adorno mas de la plaza. Con el, se lee desde lejos.
     *
     * <p>&#9888; Va en español y COMPUESTO, al reves de la regla del idioma: un
     * TextDisplay guarda el {@code Text} <b>en el mundo</b>, asi que una clave de
     * traduccion se resolveria al colocarlo y quedaria congelada para todos.
     */
    private static void cartel(ServerWorld mundo, Vec3d pies) {
        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) {
            LunaEternal.LOG.warn("No se pudo crear el cartel de Oak");
            return;
        }
        cartel.setPosition(pies.x, pies.y + ALTURA_CARTEL, pies.z);
        cartel.setText(Text.literal("Profesor Oak\n")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Elige tu primer Pokemon\n")
                        .formatted(Formatting.WHITE))
                .append(Text.literal("Clic derecho")
                        .formatted(Formatting.AQUA)));
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        // Fondo oscuro a media transparencia: el texto claro sobre la piedra
        // clara de la ciudadela desaparece sin el.
        // ⚠⚠⚠ CASI OPACO, Y ANTES ESTABA AL 25 %. El valor venia del cartel
        //    de los gimnasios (`0x40000000`, negro al 25 %) y ALLI ESTA BIEN: las
        //    salas de gimnasio son de piedra gris. EL LABORATORIO DE OAK ES BLANCO
        //    ENTERO, y sobre blanco un velo del 25 % no oscurece nada: el texto
        //    blanco desaparecia y el gris de «Clic derecho» todavia mas.
        //    ⚠⚠ LA REGLA QUE QUEDA: un cartel del mundo NO SABE contra que pared
        //       lo van a leer, asi que SE TRAE SU PROPIO FONDO. Es la misma
        //       decision que ya tomaron los hologramas de la Torre --«fondo azul
        //       pizarra SOLIDO para que el texto sea legible contra cualquier
        //       iluminacion o shaders»-- y estaba escrita desde el 7 de septiembre.
        cartel.setBackground(0xE6060B14);
        cartel.setLineWidth(220);
        // Multiplicador de 64, no una distancia: 48 bloques, que es mas que el
        // lado de la plaza.
        cartel.setViewRange(0.75f);
        cartel.setNoGravity(true);
        cartel.addCommandTag(MARCA_CARTEL);
        mundo.spawnEntity(cartel);
    }

    /**
     * Borra los Oak y sus carteles que haya cerca.
     *
     * <p>&#9888;&#9888;&#9888; {@code discard()} y no {@code kill}: a Oak el daño
     * NO le llega --lleva la marca de los decorativos-- asi que la consola diria
     * «Killed 1 entity» y no moriria ninguno. Es la leccion de los tres Brocks.
     *
     * @return cuantas entidades se quitaron
     */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio) {
        Box caja = Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(TrainerMob.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        for (var e : mundo.getEntitiesByClass(
                DisplayEntity.TextDisplayEntity.class, caja,
                x -> x.getCommandTags().contains(MARCA_CARTEL))) {
            e.discard();
            n++;
        }
        return n;
    }

    /** ¿Existe ese entrenador en los datos cargados? */
    public static boolean idValido() {
        try {
            return com.gitlab.srcmc.rctmod.api.RCTMod.getInstance()
                    .getTrainerManager().isValidId(ENTRENADOR);
        } catch (Throwable t) {
            // Si rctmod cambia esta llamada no se bloquea la colocacion: lo peor
            // que pasa es lo de antes, un aviso en el log.
            LunaEternal.LOG.warn("No se pudo validar el entrenador {}: {}",
                    ENTRENADOR, t.toString());
            return true;
        }
    }
}
