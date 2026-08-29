package net.pokereport.luna.gym;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.Decorativos;
import net.pokereport.luna.world.LunaDimensions;

/**
 * LOS LÍDERES DE GIMNASIO: el que recibe en la ciudadela y el que combate.
 *
 * <h2>Son dos Brocks y hacen cosas distintas</h2>
 *
 * <table>
 *   <tr><th>Dónde</th><th>Qué hace al clic derecho</th></tr>
 *   <tr><td>Ciudadela, sala de recepción</td>
 *       <td>abre el <b>diálogo</b>: «¿estás listo?»</td></tr>
 *   <tr><td>Dentro de su arena, una copia por retador</td>
 *       <td><b>empieza el combate</b></td></tr>
 * </table>
 *
 * <p>El de la ciudadela existe siempre y hay <b>uno</b>. El de la arena existe
 * mientras haya alguien retando y hay <b>uno por ranura</b>.
 *
 * <h2>⚠⚠ ES UN ENTRENADOR DE VERDAD, NO UNA ESTATUA</h2>
 *
 * Igual que con los Pokémon decorativos: el modelo y la textura de Brock viven
 * dentro de rctmod y no hay forma de dibujarlos sin su entidad. Así que se usa
 * su entidad y se le apaga <b>todo lo que la hace un entrenador salvaje</b>.
 *
 * <h2>⚠⚠⚠ Y LO PRIMERO QUE HAY QUE APAGAR ES QUE RETE SOLO</h2>
 *
 * La configuración del servidor trae {@code forceBattleOnSight = true} con
 * ocho bloques de alcance: un Brock puesto en la plaza <b>retaría a quien pase
 * por delante</b>, sin tocarlo, en mitad de la ciudadela.
 *
 * <p>Eso vive en {@code ForceIntoBattleGoal}, que es un <i>Goal</i> — y los
 * Goals no corren con {@code setAiDisabled(true)}. Comprobado en el jar, no
 * supuesto: la lista de objetivos de rctmod está en
 * {@code world/entities/goals/} y ahí es donde está la de forzar combate.
 *
 * <h2>⚠⚠ Y NO SE LE PUEDE PEGAR, NI EN CREATIVO</h2>
 *
 * Lleva la misma etiqueta que los decorativos ({@link Decorativos#MARCA}) para
 * heredar su protección, que es la única que cubre el creativo:
 * {@code setInvulnerable} no aplica {@code if (fuente.isSourceCreativePlayer())},
 * y aquí todos los que construyen son operadores en creativo. La lección ya está
 * pagada una vez; esto es reutilizarla, no repetirla.
 */
public final class Lideres {

    private Lideres() {}

    /** «Este es un líder nuestro». De aquí cuelga poder borrarlo. */
    public static final String MARCA = "luna_lider";

    /** Además: recibe en la ciudadela. Clic derecho = diálogo. */
    public static final String MARCA_RECEPCION = "luna_recepcion";

    /** Además: está en su arena. Clic derecho = combate. */
    public static final String MARCA_ARENA = "luna_arena";

    /**
     * Qué gimnasio es.
     *
     * <p>⚠ Va en una etiqueta y no se deduce del {@code trainerId}: el día que
     * dos gimnasios compartan entrenador —una revancha, un doble— el
     * identificador dejaría de distinguirlos y el clic derecho abriría el
     * diálogo del que no es.
     */
    public static String marcaDe(Gimnasio.Gimnasio_ g) {
        return "luna_gym_" + g.id();
    }

    /** El gimnasio de una entidad marcada, o {@code null}. */
    public static Gimnasio.Gimnasio_ gimnasioDe(Entity e) {
        for (Gimnasio.Gimnasio_ g : Gimnasio.TODOS) {
            if (e.getCommandTags().contains(marcaDe(g))) {
                return g;
            }
        }
        return null;
    }

    /**
     * Coloca un líder y le apaga todo lo que le sobra.
     *
     * <p>⚠ El identificador de entrenador se comprueba <b>antes</b> de crear
     * nada. Uno mal escrito no da error: rctmod avisa por el log, deja la
     * entidad con el nombre por defecto y el jugador se encuentra un muñeco
     * genérico que no puede combatir. Es exactamente el fallo de los 62
     * cosméticos que no existían.
     *
     * @return la entidad, o {@code null} si no se pudo
     */
    public static TrainerMob colocar(ServerWorld mundo, Gimnasio.Gimnasio_ g,
                                     Vec3d donde, float giro, String rol) {
        if (!idValido(g)) {
            LunaEternal.LOG.error("Gimnasio {}: el entrenador '{}' NO EXISTE en "
                    + "los datos. No se coloca nada: un muñeco sin equipo no "
                    + "puede combatir y parecería un fallo del gimnasio.",
                    g.id(), g.entrenador());
            return null;
        }
        Entity bruto = TrainerMob.getEntityType().create(mundo);
        if (!(bruto instanceof TrainerMob mob)) {
            LunaEternal.LOG.error("No se pudo crear la entidad de entrenador");
            return null;
        }
        mob.refreshPositionAndAngles(donde.x, donde.y, donde.z, giro, 0f);
        mob.setHeadYaw(giro);
        mob.setBodyYaw(giro);
        mob.setTrainerId(g.entrenador());

        // ⚠⚠⚠ EL ORDEN DE ESTAS CUATRO NO ES INDIFERENTE EN UNA:
        //    `setAiDisabled` es la que impide que rete solo, y tiene que estar
        //    puesta antes de que corra el primer tick. Como todo esto pasa antes
        //    de `spawnEntity`, no hay ningún tick de por medio.
        mob.setAiDisabled(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        // ⚠ `setPersistent(true)` es lo que impide que rctmod se lo lleve solo:
        //   `checkDespawnIfUnseen` pregunta por `isPersistent()` antes de nada.
        //   Comprobado en el bytecode, no supuesto.
        mob.setPersistent(true);
        mob.setInvulnerable(true);

        mob.addCommandTag(MARCA);
        mob.addCommandTag(marcaDe(g));
        mob.addCommandTag(rol);
        // ⚠ La etiqueta de los decorativos, y no por pereza: es de la que cuelga
        //   la protección que SÍ cubre el creativo (ServerLivingEntityEvents).
        //   Sin ella, cualquier operador le rompe la cara a Brock sin querer.
        mob.addCommandTag(Decorativos.MARCA);

        if (!mundo.spawnEntity(mob)) {
            return null;
        }
        return mob;
    }

    /** ¿Existe ese entrenador en los datos cargados? */
    public static boolean idValido(Gimnasio.Gimnasio_ g) {
        try {
            return com.gitlab.srcmc.rctmod.api.RCTMod.getInstance()
                    .getTrainerManager().isValidId(g.entrenador());
        } catch (Throwable t) {
            // Si rctmod cambia esta llamada, no se bloquea la colocación: lo
            // peor que pasa es lo de antes, un aviso en el log.
            LunaEternal.LOG.warn("No se pudo validar el entrenador {}: {}",
                    g.entrenador(), t.toString());
            return true;
        }
    }

    /**
     * Borra los líderes que haya cerca.
     *
     * <p>⚠ Se borra <b>antes</b> de poner, siempre. Sin eso, ejecutar el comando
     * dos veces deja dos Brocks superpuestos — y como no se les puede pegar,
     * quitarlos a mano sería imposible. Es la misma trampa que ya se resolvió
     * con las siete paradas del moto taxi.
     */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio) {
        Box caja = Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(TrainerMob.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        return n;
    }

    // ---- la ciudadela ------------------------------------------------------

    /**
     * Pone a los líderes que ya tienen sitio en la ciudadela, con su Pokémon.
     *
     * <p>⚠ El Pokémon es <b>solo estético</b> y eso son diez cosas apagadas, no
     * una: sin etiqueta, sin nivel, sin IA, no se captura, no se le pega —ni en
     * creativo—, no combate, no suena, no desaparece, no cambia de postura y
     * <b>no entra en la Pokédex</b>. Todas viven ya en {@link Decorativos}; aquí
     * solo se le pide uno.
     *
     * @param giro si no es {@code null}, sobrescribe hacia dónde mira el líder
     * @return cuántas recepciones se pusieron
     */
    public static int colocarRecepciones(MinecraftServer servidor, Float giro) {
        ServerWorld mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) {
            LunaEternal.LOG.error("No existe la ciudadela");
            return 0;
        }
        int n = 0;
        for (Gimnasio.Gimnasio_ g : Gimnasio.conRecepcion()) {
            var r = Gimnasio.recepcion(g);
            // Primero limpiar: el radio es el mismo con el que se pone.
            quitar(mundo, r.lider(), 4);
            Decorativos.quitar(mundo, r.pokemon(), 4);

            var mob = colocar(mundo, g, r.lider(),
                    giro == null ? r.giro() : giro, MARCA_RECEPCION);
            if (mob == null) {
                continue;
            }
            var poke = Decorativos.colocar(mundo, r.especie(),
                    Decorativos.Postura.QUIETO, r.pokemon(),
                    giro == null ? r.giro() : giro);
            if (poke == null) {
                LunaEternal.LOG.warn("Gimnasio {}: no se pudo poner su {}",
                        g.id(), r.especie());
            }
            n++;
            LunaEternal.LOG.info("Gimnasio {}: recepción en {} {} {} (giro {})",
                    g.id(), r.x(), r.y(), r.z(), giro == null ? r.giro() : giro);
        }
        return n;
    }

    /** Quita las recepciones de la ciudadela, líder y Pokémon. */
    public static int quitarRecepciones(MinecraftServer servidor) {
        ServerWorld mundo = servidor.getWorld(LunaDimensions.CIUDADELA);
        if (mundo == null) {
            return 0;
        }
        int n = 0;
        for (Gimnasio.Gimnasio_ g : Gimnasio.conRecepcion()) {
            var r = Gimnasio.recepcion(g);
            n += quitar(mundo, r.lider(), 4);
            Decorativos.quitar(mundo, r.pokemon(), 4);
        }
        return n;
    }

    // ---- la arena ----------------------------------------------------------

    /**
     * Deja al líder puesto en una ranura, y solo uno.
     *
     * <p>⚠⚠ Borra antes de poner, igual que en la ciudadela — y aquí importa
     * más: la misma ranura se reutiliza cada vez que alguien la reserva, así que
     * sin limpiar se acumularía un Brock por combate en el mismo metro cuadrado.
     *
     * @return el líder, o {@code null} si no se pudo
     */
    public static TrainerMob enArena(MinecraftServer servidor,
                                     Gimnasio.Gimnasio_ g, int ranura) {
        ServerWorld mundo = Arenas.mundo(servidor);
        if (mundo == null) {
            return null;
        }
        Vec3d donde = Gimnasio.lider(g, ranura);
        quitar(mundo, donde, 6);
        var mob = colocar(mundo, g, donde, Gimnasio.giroLider(g), MARCA_ARENA);
        if (mob != null && !Gimnasio.tieneTarima(g)) {
            // ⚠ Si la tarima no está medida, el sitio es una SUPOSICIÓN: 20
            //   bloques al fondo de la entrada. Puede caer dentro de una pared,
            //   y un líder dentro de una pared no da ningún error — aparece, y
            //   no se le ve. Se dice por el log en vez de fingir que va bien.
            LunaEternal.LOG.warn("Gimnasio {}: la tarima del líder NO está "
                    + "medida. Se usa el respaldo, que puede caer en una pared.",
                    g.id());
        }
        return mob;
    }

    /** Se lleva al líder de una ranura. */
    public static void quitarDeArena(MinecraftServer servidor,
                                     Gimnasio.Gimnasio_ g, int ranura) {
        ServerWorld mundo = Arenas.mundo(servidor);
        if (mundo != null) {
            quitar(mundo, Gimnasio.lider(g, ranura), 6);
        }
    }
}
