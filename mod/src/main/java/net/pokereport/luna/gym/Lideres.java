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
     * Cuánto se barre en X al limpiar una ranura.
     *
     * <p>⚠ El gimnasio de Brock mide 96 de ancho y los gimnasios van a 1024 uno
     * de otro, así que 128 cubre la sala de sobra y no llega ni de lejos al
     * vecino. Se acota porque de este número salen los chunks que hay que
     * cargar, y barrer medio hueco serían más de cien.
     */
    private static final int ANCHO_MAX = 128;

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
            // ⚠⚠ EL GIRO SE GUARDA, no solo se aplica. Antes el número viajaba
            //    como parámetro, giraba al líder, y se perdía al reiniciar: el
            //    comando decía «hecho» y días después Brock «se había girado
            //    solo». Ahora, si viene uno, se escribe; y si no, manda el que
            //    haya guardado. Detalle en Orientacion.
            float suGiro;
            if (giro != null) {
                Orientacion.poner(g.id(), giro);
                suGiro = Orientacion.giro(g.id(), r.giro());
            } else {
                suGiro = Orientacion.giro(g.id(), r.giro());
            }

            // Primero limpiar: el radio es el mismo con el que se pone.
            quitar(mundo, r.lider(), 4);
            Decorativos.quitar(mundo, r.pokemon(), 4);
            Cartel.quitar(mundo, g, r.lider());

            var mob = colocar(mundo, g, r.lider(), suGiro, MARCA_RECEPCION);
            if (mob == null) {
                continue;
            }
            var poke = Decorativos.colocar(mundo, r.especie(),
                    Decorativos.Postura.QUIETO, r.pokemon(), suGiro);
            if (poke == null) {
                LunaEternal.LOG.warn("Gimnasio {}: no se pudo poner su {}",
                        g.id(), r.especie());
            }
            // ⚠ El cartel va DESPUÉS del líder: si la colocación falla no se
            //   queda un rótulo flotando sobre un sitio vacío, que es la clase
            //   de resto que luego nadie sabe de dónde salió.
            Cartel.poner(mundo, g, r.lider());
            n++;
            LunaEternal.LOG.info("Gimnasio {}: recepción en {} {} {} (giro {})",
                    g.id(), r.x(), r.y(), r.z(), suGiro);
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
            // ⚠ El cartel también. Es una entidad, se queda en el mundo, y a un
            //   TextDisplay el daño NO le llega: `/kill` diría que lo ha matado
            //   y seguiría ahí. La única forma es `discard`, que es lo que hace.
            Cartel.quitar(mundo, g, r.lider());
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
        // ⚠⚠ SE LIMPIA LA RANURA ENTERA, no un radio alrededor de la tarima.
        //    El motivo esta en `quitarDeRanura`, y se resume en que el lider NO
        //    se queda donde lo pusiste.
        quitarDeRanura(mundo, g, ranura);
        var mob = colocar(mundo, g, donde, Gimnasio.giroLider(g), MARCA_ARENA);
        if (mob != null) {
            // ⚠ El mismo cartel que en la ciudadela. Aquí sirve para otra cosa:
            //   el jugador ya aceptó el reto, así que lo que le recuerda es A
            //   QUÉ NIVEL se pelea -- que es lo que explica por qué su Pokémon
            //   de nivel 40 aparece de 15.
            Cartel.poner(mundo, g, donde);
        }
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
            quitarDeRanura(mundo, g, ranura);
        }
    }

    /**
     * BORRA TODOS LOS LÍDERES DE UNA RANURA, ESTÉN DONDE ESTÉN DENTRO DE ELLA.
     *
     * <h2>⚠⚠⚠ EL LÍDER NO SE QUEDA DONDE LO PONES, Y POR ESO ESTO NO ES UN RADIO</h2>
     *
     * Se limpiaba «en un radio de 6 alrededor de la tarima», que parecía de
     * sobra. Y no lo era: cuando empieza el combate, <b>el mod de posiciones
     * teletransporta al líder a su bloque {@code trainer_stand_position}</b> —
     * en el gimnasio de Brock, 26 bloques al fondo. Al acabar el combate se
     * queda ahí.
     *
     * <p>Así que la limpieza no lo encontraba, y el siguiente reto ponía otro:
     * <b>un Brock más por cada combate</b>. No daba ningún error; el usuario lo
     * vio en el juego, en la segunda pelea.
     *
     * <p>⚠⚠ La lección: <b>un radio solo vale si sabes que la cosa no se
     * mueve.</b> Y aquí no lo sabíamos — lo mueve un mod ajeno, con unas
     * coordenadas que pone quien construye la sala. La ranura entera sí es un
     * límite que conocemos, porque lo definimos nosotros.
     *
     * <p>⚠ La caja no llega a la ranura siguiente ni al gimnasio de al lado: las
     * ranuras van a {@code PASO_RANURA} en Z y los gimnasios a
     * {@code SEPARACION} en X, así que se para antes de las dos.
     *
     * <p>⚠ Solo alcanza a los que estén en chunks cargados. En el momento en que
     * se llama —al empezar un reto o al acabar un combate— el jugador está o
     * acaba de estar dentro, así que lo están.
     */
    public static int quitarDeRanura(ServerWorld mundo, Gimnasio.Gimnasio_ g,
                                     int ranura) {
        var o = Gimnasio.origen(g, ranura);
        // ⚠⚠⚠ LOS CHUNKS PRIMERO, Y ESTA ES LA MITAD QUE FALTABA.
        //    `getEntitiesByClass` solo ve lo que esta CARGADO. Si la ranura
        //    esta fria, la limpieza no encuentra nada, no borra nada, y el
        //    lider que iba a sustituir aparece AL LADO del anterior.
        //    Cuando ya estan cargados esto es una busqueda en una tabla, asi
        //    que no cuesta nada en el caso normal.
        for (int cx = o.getX() >> 4; cx <= (o.getX() + ANCHO_MAX) >> 4; cx++) {
            for (int cz = o.getZ() >> 4;
                 cz <= (o.getZ() + Gimnasio.PASO_RANURA) >> 4; cz++) {
                mundo.getChunk(cx, cz);
            }
        }
        // ⚠ Un pelo por debajo del paso: un líder justo en el borde es de la
        //   ranura siguiente, no de esta.
        Box caja = new Box(
                o.getX() - 8, o.getY() - 64, o.getZ() - 4,
                o.getX() + ANCHO_MAX, o.getY() + 256,
                o.getZ() + Gimnasio.PASO_RANURA - 0.01);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(TrainerMob.class, caja,
                x -> x.getCommandTags().contains(MARCA))) {
            e.discard();
            n++;
        }
        if (n > 1) {
            // Si alguna vez sale más de uno, es que algo los estaba dejando
            // atrás. Decirlo es más barato que descubrirlo en una captura.
            LunaEternal.LOG.warn("Gimnasio {}: habia {} lideres en la ranura {}",
                    g.id(), n, ranura);
        }
        return n;
    }
}
