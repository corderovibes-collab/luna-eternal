package net.pokereport.luna.gym;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.gitlab.srcmc.rctmod.api.RCTMod;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pokereport.luna.LunaEternal;

/**
 * EL RETO: de la sala de recepción a la arena, y de la arena a la medalla.
 *
 * <h2>El recorrido entero, en orden</h2>
 *
 * <ol>
 *   <li>clic derecho en el Brock de la ciudadela → el servidor manda el estado
 *       y el cliente abre el diálogo;</li>
 *   <li>«sí» → {@link #retar}: se comprueba, se reserva una <b>ranura</b>, se
 *       clona la sala si hacía falta, aparece el líder y viaja el jugador;</li>
 *   <li>clic derecho en el Brock de la arena → {@link #empezar}: el combate;</li>
 *   <li>victoria o derrota → {@link #escuchar}: la medalla y la vuelta.</li>
 * </ol>
 *
 * <h2>⚠⚠⚠ EL COMBATE NO PASA POR {@code startBattleWith}, Y ESO ES DELIBERADO</h2>
 *
 * {@code TrainerMob.startBattleWith} parece el método obvio y <b>habría hecho
 * que el gimnasio no funcionara</b>: antes de nada llama a
 * {@code canBattleAgainst}, que comprueba <b>la progresión de rctmod</b> — su
 * tope de nivel, su serie y sus entrenadores requeridos. Y la configuración real
 * de este servidor, leída del panel, dice:
 *
 * <pre>
 *   initialLevelCap  = 15      y el Onix de Brock es nivel 20
 *   initialSeries    = "empty" y Brock es de la serie "kanto"
 *   allowOverLeveling = false
 * </pre>
 *
 * <p>O sea que a un jugador normal le habría dicho que no. Y no con un error:
 * con <b>un diálogo por el chat</b> — que es justo lo que el usuario pidió que
 * no hubiera (P9). El clic derecho habría parecido no hacer nada.
 *
 * <p>Se usa {@code RCTMod.makeBattle}, que es público, es <b>lo mismo que llama
 * rctmod por debajo</b> y no comprueba nada de eso. <b>El gimnasio tiene su
 * propia puerta</b>: las medallas que hacen falta, que es nuestro diseño, está
 * en nuestra base y se enseña en nuestra pantalla.
 *
 * <h2>⚠⚠ Y LA VICTORIA SE ESCUCHA EN COBBLEMON, NO EN RCTMOD</h2>
 *
 * {@code BATTLE_VICTORY} da los actores que ganaron y los que perdieron, y de
 * cada uno se sacan los UUID de jugador. Es la fuente más cercana al hecho —
 * «este combate lo ganó este jugador»— y no depende de la contabilidad de
 * rctmod, que es justo la que hemos rodeado.
 */
public final class Combate {

    private Combate() {}

    /** Cuánto se espera antes de devolver a alguien a la ciudadela. */
    private static final int TICKS_VUELTA = 100;   // 5 s

    /** Cuánto se espera, tras el viaje, para poner al líder en su tarima. */
    private static final int TICKS_LIDER = 20;     // 1 s

    /**
     * DÓNDE ESTABA CADA UNO ANTES DE ENTRAR.
     *
     * <h2>⚠⚠ EN MEMORIA, COMO LAS RANURAS, Y POR EL MISMO MOTIVO</h2>
     *
     * Es estado <b>de esta sesión</b>: significa «hay alguien dentro que tiene
     * que volver a algún sitio». Guardarlo en la base no arreglaría el caso que
     * importa —reiniciar el servidor con alguien dentro— porque tras el reinicio
     * la ranura ya no existe. Lo que sí lo arregla es {@link #alEntrar}, que
     * saca de la arena a quien vuelva a conectarse dentro de una.
     *
     * <p>⚠ {@code Regreso} no vale para esto: solo recuerda el Mundo Hogar, y a
     * propósito.
     */
    private record Vuelta(RegistryKey<World> mundo, Vec3d donde, float giro) {}

    private static final Map<UUID, Vuelta> VUELTAS = new ConcurrentHashMap<>();

    /** Quién está retando a quién, para saber a qué gimnasio darle la medalla. */
    private static final Map<UUID, String> RETANDO = new ConcurrentHashMap<>();

    // ------------------------------------------------------------- clic derecho

    /**
     * EL CLIC DERECHO SOBRE UN LÍDER.
     *
     * <h2>⚠⚠ EL MISMO GESTO HACE DOS COSAS, Y LO DECIDE LA ETIQUETA</h2>
     *
     * En la ciudadela abre el diálogo; dentro de la arena empieza el combate. No
     * se distingue por dimensión —que sería adivinar— sino por qué etiqueta lleva
     * la entidad, que es lo que se puso al colocarla.
     *
     * <h2>⚠⚠ Y DEVUELVE {@code SUCCESS} PARA CORTAR LO DE DEBAJO</h2>
     *
     * Sin cortar, el clic sigue su camino hasta {@code TrainerMob.interactMob},
     * que es el de rctmod: enseñaría su tarjeta de entrenador o soltaría un
     * diálogo por el chat — las dos cosas que el usuario pidió que no pasaran.
     *
     * <p>⚠ Y solo la mano principal: el evento llega dos veces, una por mano, y
     * sin filtrar se abriría el diálogo dos veces por cada clic. Es la misma
     * lección que las paradas del moto taxi.
     */
    public static void registrarClic() {
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (jugador, mundo, mano, entidad, golpe) -> {
                    var tags = entidad.getCommandTags();
                    if (!tags.contains(Lideres.MARCA)) {
                        return net.minecraft.util.ActionResult.PASS;
                    }
                    if (mano != net.minecraft.util.Hand.MAIN_HAND) {
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    if (!(jugador instanceof ServerPlayerEntity sp)) {
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    var g = Lideres.gimnasioDe(entidad);
                    if (g == null) {
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                    if (tags.contains(Lideres.MARCA_ARENA)
                            && entidad instanceof TrainerMob mob) {
                        empezar(sp, mob, g);
                    } else if (tags.contains(Lideres.MARCA_RECEPCION)) {
                        net.pokereport.luna.net.Red.enviarGimnasio(sp, g);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                });
        LunaEternal.LOG.info("Gimnasios: el clic derecho en un líder responde");
    }

    // ------------------------------------------------------------------ retar

    /**
     * POR QUÉ NO SE PUEDE RETAR.
     *
     * <h2>⚠⚠ VIAJA LA CLAVE, NO LA FRASE</h2>
     *
     * Un servidor no tiene idioma: {@code .getString()} congelaría el motivo en
     * inglés, que es el único que existe ahí. Se manda <b>la clave y su número</b>
     * y la frase la pone el cliente, en el suyo. Es la regla del protocolo que
     * costó una pantalla entera el 25-ago.
     *
     * @param clave sufijo de {@code gimnasio.lunaeternal.no.*}
     * @param dato  el número que hace falta para la frase, o 0
     */
    public record Motivo(String clave, int dato) {}

    /** Por qué no se puede retar. {@code null} = sí se puede. */
    public static Motivo porQueNo(ServerPlayerEntity jugador,
                                  Gimnasio.Gimnasio_ g) {
        UUID uuid = jugador.getUuid();
        if (MedallaService.tiene(uuid, g)) {
            return new Motivo("ya_ganada", 0);
        }
        int tengo = MedallaService.cuantas(uuid);
        if (tengo < g.medallas()) {
            return new Motivo("faltan", g.medallas() - tengo);
        }
        // ⚠ Sin Pokémon no hay combate, y el error que da Cobblemon por dentro
        //   no se entiende. Mejor decirlo antes de teletransportar a nadie.
        if (equipoVacio(jugador)) {
            return new Motivo("sin_pokemon", 0);
        }
        if (Ranuras.libres(g) <= 0) {
            return new Motivo("lleno", 0);
        }
        return null;
    }

    private static boolean equipoVacio(ServerPlayerEntity jugador) {
        try {
            for (var p : Cobblemon.INSTANCE.getStorage().getParty(jugador)) {
                if (p != null) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            // Si no se puede leer el equipo, se deja pasar: peor que un combate
            // raro es no poder retar a nadie por un fallo de lectura.
            return false;
        }
    }

    /**
     * Acepta el reto: reserva ranura, prepara la sala y lleva al jugador.
     *
     * <h2>⚠⚠⚠ SE RESERVA LA RANURA ANTES DE TOCAR NADA</h2>
     *
     * Y si algo falla después, <b>se suelta</b>. Sin eso, un clonado que se
     * negara —porque la sala no cabe en su ranura— dejaría la ranura ocupada por
     * alguien que se quedó en la ciudadela: no da ningún error, y al octavo
     * nadie puede retar a Brock nunca más.
     *
     * @return {@code null} si salió bien, o el motivo por el que no
     */
    public static Motivo retar(ServerPlayerEntity jugador, Gimnasio.Gimnasio_ g) {
        // ⚠⚠ SE VUELVE A COMPROBAR AQUÍ, aunque el diálogo ya lo hiciera. La
        //    pantalla se abrió hace un rato: entre medias pudo perder su equipo,
        //    llenarse el gimnasio o ganar la medalla en otra sesión. Y sobre
        //    todo, el «sí» llega DEL CLIENTE (P6).
        Motivo no = porQueNo(jugador, g);
        if (no != null) {
            return no;
        }
        var servidor = jugador.getServer();
        if (servidor == null || Arenas.mundo(servidor) == null) {
            return new Motivo("sin_dimension", 0);
        }
        int ranura = Ranuras.reservar(g, jugador.getUuid());
        if (ranura < 0) {
            return new Motivo("lleno", 0);
        }
        // ⚠ Que el entrenador EXISTA se comprueba antes de mover a nadie: es lo
        //   único que se puede saber sin estar dentro, y es el fallo que dejaría
        //   al jugador en una sala vacía.
        if (!Lideres.idValido(g)) {
            Ranuras.soltar(jugador.getUuid());
            return new Motivo("sin_lider", 0);
        }
        // ⚠⚠ CINCO SEGUNDOS QUIETO ANTES DE ENTRAR (peticion del usuario). La
        //    ranura ya esta apartada, asi que si se mueve HAY QUE SOLTARLA: si
        //    no, esa copia queda reservada a alguien que no va a ir, y al octavo
        //    nadie puede retar al lider. No da ningun error: deja de funcionar.
        RETANDO.put(jugador.getUuid(), g.id());
        net.pokereport.luna.world.Espera.pedir(jugador, "gimnasio",
                () -> entrar(jugador, g, ranura),
                () -> {
                    Ranuras.soltar(jugador.getUuid());
                    RETANDO.remove(jugador.getUuid());
                });
        return null;
    }

    /** Lo que pasa cuando la cuenta atras termina sin moverse. */
    private static void entrar(ServerPlayerEntity jugador, Gimnasio.Gimnasio_ g,
                               int ranura) {
        var servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        try {
            // Una vez por ranura y por arranque: copiar ocho mil bloques en cada
            // combate sí se notaría en el hilo del servidor.
            Arenas.clonar(servidor, g, ranura);
            // ⚠ Se apunta la vuelta ANTES de mover. Después ya está en la arena
            //   y se guardaría la posición de destino: volver le devolvería a la
            //   arena. Es la misma trampa que ya estaba resuelta en el viaje al
            //   Mundo Hogar, y aquí habría vuelto a caer.
            VUELTAS.put(jugador.getUuid(), new Vuelta(
                    jugador.getServerWorld().getRegistryKey(),
                    jugador.getPos(), jugador.getYaw()));
            RETANDO.put(jugador.getUuid(), g.id());
            if (!Arenas.llevar(jugador, g, ranura)) {
                soltar(jugador, g.id());
                // ⚠ Ya no se puede devolver un motivo: la pantalla se cerro
                //   hace cinco segundos. Se dice por el chat, que es lo unico
                //   que le queda al jugador aqui.
                jugador.sendMessage(Text.translatable(
                        "gimnasio.lunaeternal.no.sin_dimension"), false);
                return;
            }
            // ⚠⚠⚠ EL LIDER SE PONE DESPUES DE QUE LLEGUE EL JUGADOR, Y ESTO NO
            //    ES ESTETICA: ES LO QUE HACE QUE LA LIMPIEZA FUNCIONE.
            //
            //    Ponerlo antes parecía lo natural --que esté esperando cuando
            //    llegues-- y tenía un fallo mudo: los chunks de la ranura pueden
            //    estar FRIOS, y aunque se carguen a mano, <b>las entidades no
            //    llegan en el mismo tick</b>: el gestor de entidades las trae
            //    después. Así que la limpieza miraba una sala que para ella
            //    estaba vacía, no borraba al de la vez anterior, y ponía uno al
            //    lado. Un Brock más por combate.
            //
            //    Con el jugador ya dentro, la sala está viva y sus entidades
            //    cargadas: limpiar encuentra lo que hay de verdad.
            //
            //    ⚠ Un segundo de retraso no se nota --el jugador está mirando
            //      dónde ha caído-- y evita un fallo que solo aparece cuando la
            //      sala llevaba un rato sin nadie, que es justo cuando nadie
            //      está mirando.
            Programador.en(TICKS_LIDER, () -> {
                var vivo = servidor.getPlayerManager().getPlayer(jugador.getUuid());
                if (vivo == null) {
                    return;   // se fue; `alSalir` ya soltó la ranura
                }
                if (Lideres.enArena(servidor, g, ranura) == null) {
                    // ⚠⚠ Y SI NO APARECE, SE LE SACA. De esta dimensión no se
                    //    sale andando: dejarlo dentro con una sala vacía sería
                    //    dejarlo encerrado.
                    vivo.sendMessage(Text.translatable(
                            "gimnasio.lunaeternal.no.sin_lider"), false);
                    devolver(vivo, g.id());
                }
            });
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo llevar a {} al gimnasio {}",
                    jugador.getName().getString(), g.id(), e);
            soltar(jugador, g.id());
            jugador.sendMessage(Text.translatable(
                    "gimnasio.lunaeternal.no.sin_dimension"), false);
            return;
        }
        LunaEternal.LOG.info("Gimnasio {}: {} entra en la ranura {}",
                g.id(), jugador.getName().getString(), ranura);
    }

    // ---------------------------------------------------------------- combatir

    /**
     * Clic derecho en el líder de la arena: empieza el combate.
     *
     * <p>⚠ Se comprueba que sea <b>su</b> ranura. Sin eso, un operador que entre
     * a mirar podría empezar el combate de otro — y la medalla se la llevaría
     * quien no lo peleó.
     */
    public static void empezar(ServerPlayerEntity jugador, TrainerMob mob,
                               Gimnasio.Gimnasio_ g) {
        int mia = Ranuras.ranuraDe(g, jugador.getUuid());
        if (mia < 0) {
            jugador.sendMessage(
                    Text.translatable("gimnasio.lunaeternal.no.no_es_tuya"), true);
            return;
        }
        if (mob.isInBattle() || RCTMod.getInstance().isInBattle(jugador)) {
            return;
        }
        try {
            // ⚠⚠⚠ `makeBattle` Y NO `startBattleWith`. El motivo está entero en
            //    la cabecera de esta clase, y se resume así: `startBattleWith`
            //    pregunta primero a la progresión de rctmod --tope de nivel,
            //    serie, entrenadores requeridos-- y con la configuración real de
            //    este servidor la respuesta sería NO, dicha por el chat.
            if (!RCTMod.getInstance().makeBattle(mob, jugador)) {
                jugador.sendMessage(
                        Text.translatable("gimnasio.lunaeternal.no.combate"), true);
                return;
            }
            // Para que rctmod sepa que este combate existe: es lo que hace que
            // el líder no intente empezar otro y que se le repartan sus cosas al
            // acabar. `setOpponent` es suyo y no se puede llamar desde fuera,
            // así que esto es lo que hay — y basta, porque la IA está apagada.
            RCTMod.getInstance().getTrainerManager().addBattle(jugador, mob);
        } catch (Throwable t) {
            LunaEternal.LOG.error("No se pudo empezar el combate de {} contra {}",
                    jugador.getName().getString(), g.id(), t);
            jugador.sendMessage(
                    Text.translatable("gimnasio.lunaeternal.no.combate"), true);
        }
    }

    // ----------------------------------------------------------------- ganar

    /**
     * Escucha el final del combate.
     *
     * <p>⚠⚠ Se mira <b>quién estaba retando</b>, no quién ganó a secas: en el
     * servidor hay combates a todas horas —salvajes, entrenadores del mundo,
     * duelos— y ninguno de ellos da una medalla. Sin ese filtro, ganarle a un
     * Rattata te haría campeón de Ciudad Plateada.
     */
    public static void escuchar() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(evento -> {
            try {
                // ⚠ Los jugadores salen de la BATALLA y no del actor: el actor
                //   solo da UUID, y para avisar a alguien hace falta su entidad.
                //   Buscarla en el servidor obligaría a tener el servidor a mano
                //   desde un evento que no lo trae.
                var ganadores = new java.util.HashSet<UUID>();
                for (var actor : evento.getWinners()) {
                    for (UUID u : actor.getPlayerUUIDs()) {
                        ganadores.add(u);
                    }
                }
                for (ServerPlayerEntity p : evento.getBattle().getPlayers()) {
                    terminar(p, ganadores.contains(p.getUuid()));
                }
            } catch (Throwable t) {
                // Un fallo aquí no puede romper el final de un combate: el
                // jugador ya ha peleado, y perder la partida por un error de
                // contabilidad sería mucho peor que no anotar la medalla.
                LunaEternal.LOG.error("Error cerrando un combate de gimnasio", t);
            }
        });
        LunaEternal.LOG.info("Gimnasios: escuchando el final de los combates");
    }

    private static void terminar(ServerPlayerEntity jugador, boolean gano) {
        UUID uuid = jugador.getUuid();
        String cual = RETANDO.remove(uuid);
        if (cual == null) {
            return;   // no estaba retando a nadie: es un combate cualquiera
        }
        var g = Gimnasio.de(cual);
        if (g == null) {
            return;
        }
        if (!gano) {
            // ⚠ `Text.translatable` y no una cadena ya resuelta: el servidor no
            //   tiene idioma, y `.getString()` la congelaría en inglés. La
            //   lección del 25-ago, que costó una pantalla entera.
            jugador.sendMessage(Text.translatable(
                    "gimnasio.lunaeternal.derrota", g.lider()), false);
            devolverEn(jugador, cual, TICKS_VUELTA);
            return;
        }
        var medallas = LunaEternal.medallas();
        if (medallas == null) {
            devolverEn(jugador, cual, TICKS_VUELTA);
            return;
        }
        medallas.conceder(jugador, g, nueva -> {
            if (nueva) {
                // ⚠ El toast viaja como TEXTO YA COMPUESTO: así lo pide `Aviso`,
                //   y por un motivo escrito ahí — si viajaran las piezas, el
                //   formato viviría en el servidor y en el cliente a la vez.
                net.pokereport.luna.ui.Aviso.logro(jugador,
                        "MEDALLA " + g.medalla().toUpperCase(java.util.Locale.ROOT),
                        "Has vencido a " + g.lider(),
                        "cobblemon:poke_ball",
                        net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        1.0f);
            }
            // ⚠⚠ EL ESTADO NO ES DE QUIEN LO MIRA, y aquí es literal: la ficha
            //    del PokePad la dibuja el cliente con lo que le mandaron la
            //    última vez. Sin reenviarla, el jugador acaba de ganar la
            //    medalla y su PokePad sigue enseñándola apagada hasta que
            //    reabra. Es la lección del 23-ago, aplicada.
            net.pokereport.luna.net.Red.enviarSaldo(jugador);
            devolverEn(jugador, cual, TICKS_VUELTA);
        });
    }

    // ---------------------------------------------------------------- volver

    /**
     * Devuelve al jugador dentro de N ticks.
     *
     * <p>⚠ No inmediatamente: el combate acaba de terminar y Cobblemon todavía
     * está recogiendo —animaciones, curación, mensajes—. Sacarlo del mundo en
     * ese instante deja Pokémon sueltos en una sala vacía.
     */
    private static void devolverEn(ServerPlayerEntity jugador, String cual,
                                   int ticks) {
        var servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        UUID uuid = jugador.getUuid();
        Programador.en(ticks, () -> {
            // ⚠ Se vuelve a buscar al jugador: en cinco segundos le da tiempo a
            //   desconectarse, y teletransportar a una entidad que ya no está
            //   revienta el tick del servidor.
            var vivo = servidor.getPlayerManager().getPlayer(uuid);
            if (vivo != null) {
                devolver(vivo, cual);
            }
        });
    }

    /**
     * Saca a alguien de la arena y suelta su ranura.
     *
     * <p>⚠ Si no se sabe de dónde vino —porque el servidor se reinició— va al
     * punto de llegada de la ciudadela, que existe siempre. Dejarlo donde está
     * sería dejarlo encerrado en una copia sin líder.
     */
    public static void devolver(ServerPlayerEntity jugador, String cual) {
        var v = VUELTAS.remove(jugador.getUuid());
        soltar(jugador, cual);
        var servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        if (v != null) {
            var mundo = servidor.getWorld(v.mundo());
            if (mundo != null) {
                net.pokereport.luna.world.Traslado.ir(
                        jugador, mundo, v.donde(), v.giro(), 0f);
                return;
            }
        }
        net.pokereport.luna.world.TravelService.travel(jugador,
                net.pokereport.luna.world.LunaDimensions.CIUDADELA, "la Ciudadela");
    }

    /**
     * Suelta la ranura y se lleva al líder que había en ella.
     *
     * <h2>⚠⚠⚠ SE LE PASA EL GIMNASIO, NO SE BUSCA AQUÍ, Y ESO ARREGLA UN FALLO</h2>
     *
     * Antes lo sacaba de {@code RETANDO} — y para cuando llegaba aquí, ya lo
     * había sacado {@link #terminar}. Así que la ranura se soltaba pero
     * <b>el líder se quedaba de pie en la arena vacía</b> hasta el siguiente
     * retador. No daba ningún error, y desde fuera se veía como «hay un Brock en
     * una sala en la que no hay nadie».
     *
     * <p>⚠ Y con {@code cual} nulo sigue haciendo lo único que puede: soltar la
     * ranura. Perder al líder es feo; dejar la ranura pillada es lo que rompe el
     * gimnasio para todos.
     */
    private static void soltar(ServerPlayerEntity jugador, String cual) {
        var servidor = jugador.getServer();
        RETANDO.remove(jugador.getUuid());
        if (servidor != null && cual != null) {
            var g = Gimnasio.de(cual);
            if (g != null) {
                int r = Ranuras.ranuraDe(g, jugador.getUuid());
                if (r > 0) {
                    Lideres.quitarDeArena(servidor, g, r);
                }
            }
        }
        Ranuras.soltar(jugador.getUuid());
    }

    // ------------------------------------------------------------ ciclo de vida

    /**
     * Al desconectar.
     *
     * <h2>⚠⚠ ESTE ES EL CAMINO QUE SE OLVIDA</h2>
     *
     * Ganar y perder son los dos obvios; desconectarse a mitad no. Sin esto la
     * ranura queda reservada a alguien que ya no está, y al octavo <b>nadie
     * puede retar al líder nunca más</b> — sin un solo error en el log.
     */
    public static void alSalir(ServerPlayerEntity jugador) {
        soltar(jugador, RETANDO.get(jugador.getUuid()));
        VUELTAS.remove(jugador.getUuid());
    }

    /** Cuánto se espera antes de sacar a alguien que entró dentro de una arena. */
    private static final int TICKS_RESCATE = 40;   // 2 s

    /**
     * Al entrar: si se quedó dentro de una arena, fuera.
     *
     * <p>⚠⚠ Pasa de verdad y no es raro: alguien se desconecta a mitad de
     * combate, o el servidor se reinicia con gente dentro. Al volver aparece en
     * una copia que ya no tiene reservada, sin líder y sin salida — porque de la
     * dimensión de gimnasios no se sale andando.
     *
     * <h2>⚠⚠⚠ PERO NO SE LE SACA EN EL ACTO, Y ESTO COSTO UNA SESION ENTERA</h2>
     *
     * <b>Teletransportar a un jugador dentro del evento de conexión rompe su
     * contabilidad de chunks.</b> Acaba de ser añadido al mundo y el gestor de
     * tickets todavía no lo tiene apuntado en su sección; sacarlo de la
     * dimensión en ese instante deja el apunte a medias.
     *
     * <p>Y lo peor es <b>cuándo se nota</b>: no ahí. El jugador juega
     * normalmente, y <b>siete minutos después</b>, en el siguiente cambio de
     * dimensión, revienta con
     *
     * <pre>
     *   NullPointerException: Cannot invoke "ObjectSet.remove(Object)"
     *     at ChunkTicketManager.handleChunkLeave
     *     at ServerWorld.removePlayer
     *     at ServerPlayerEntity.teleport
     * </pre>
     *
     * <p>El viaje se queda a medias: el jugador sale de un mundo y no llega al
     * otro. Visto de verdad — el usuario acabó en el Mundo Hogar con las
     * coordenadas de la ciudadela, y su cliente dibujando <b>al Brock de la
     * arena flotando sobre la plaza</b>, porque recibió las entidades del mundo
     * nuevo sin haber cambiado de mundo.
     *
     * <p>⚠ La traza <b>no nombra el evento de conexión por ningún lado</b>: es
     * un fallo a distancia. Por eso queda escrito aquí y no en un comentario de
     * una línea.
     *
     * <p>Dos segundos de espera bastan, y de propina el chunk de destino ya está
     * cargado cuando llega.
     */
    public static void alEntrar(ServerPlayerEntity jugador) {
        if (!Ranuras.estabaEnArena(jugador)) {
            return;
        }
        var servidor = jugador.getServer();
        if (servidor == null) {
            return;
        }
        UUID uuid = jugador.getUuid();
        LunaEternal.LOG.info("{} volvió dentro de una arena: se le saca en {} ticks",
                jugador.getName().getString(), TICKS_RESCATE);
        Programador.en(TICKS_RESCATE, () -> {
            // ⚠ Se vuelve a buscar: en dos segundos le da tiempo a
            //   desconectarse otra vez, y mover a alguien que ya no está
            //   revienta el tick del servidor.
            var vivo = servidor.getPlayerManager().getPlayer(uuid);
            if (vivo == null || !Ranuras.estabaEnArena(vivo)) {
                return;
            }
            net.pokereport.luna.world.TravelService.travel(vivo,
                    net.pokereport.luna.world.LunaDimensions.CIUDADELA,
                    "la Ciudadela");
        });
    }
}
