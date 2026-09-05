package net.pokereport.luna.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos de verificación del vertical slice.
 *
 * <p>No son la interfaz final —esa será la del cliente (D-026)—. Existen para
 * comprobar que persistencia, idempotencia y atomicidad funcionan de verdad
 * contra la base.
 *
 * <p><b>Mientras la interfaz nueva no exista, esto es lo único que hay.</b> No
 * es una excepción a P9 («interfaz, nunca comando»): P9 habla de lo que se le
 * ofrece al jugador, y a un jugador no se le está ofreciendo nada todavía.
 * Estos comandos son de verificación y siguen siéndolo.
 */
public final class LunaCommand {

    private LunaCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {

        d.register(literal("luna")
            .executes(ctx -> balance(ctx.getSource()))
            .then(literal("saldo")
                .executes(ctx -> balance(ctx.getSource())))

            // Viaje entre dimensiones, para CONSTRUCTORES (nivel 2).
            //
            // Existe porque la Puerta del Mundo se fue con los menús (D-026) y
            // sin esto la única forma de llegar a la ciudadela es escribir
            // `/execute in lunaeternal:ciudadela run tp @s 0 64 0`, que nadie
            // va a teclear veinte veces al día.
            //
            // No contradice P9: P9 protege al JUGADOR de tener que escribir
            // comandos. Un constructor con OP nivel 2 no es un jugador, y
            // cuando exista la interfaz esto seguirá siendo un atajo, no el
            // camino.
            .then(literal("ir")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("destino", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (String s : DESTINOS.keySet()) b.suggest(s);
                        return b.buildFuture();
                    })
                    .executes(ctx -> viajar(ctx.getSource(),
                        StringArgumentType.getString(ctx, "destino")))))

            // Alta de constructor con clave. Ver LunaConfig.builderKey.
            .then(literal("constructor")
                .then(argument("clave", StringArgumentType.word())
                    .executes(ctx -> altaConstructor(
                        ctx.getSource(), StringArgumentType.getString(ctx, "clave")))))

            .then(literal("auditar")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> audit(ctx.getSource())))

            .then(literal("dar")
                .requires(s -> s.hasPermissionLevel(3))
                .then(argument("moneda", StringArgumentType.word())
                .then(argument("cantidad", LongArgumentType.longArg(1))
                    .executes(ctx -> grant(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "moneda"),
                        LongArgumentType.getLong(ctx, "cantidad"))))))

            .then(literal("estado")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> status(ctx.getSource())))

            .then(literal("economia")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> { EconomyReport.send(ctx.getSource(), 24); return 1; })
                .then(argument("horas", com.mojang.brigadier.arguments.IntegerArgumentType
                        .integer(1, 720))
                    .executes(ctx -> {
                        EconomyReport.send(ctx.getSource(),
                            com.mojang.brigadier.arguments.IntegerArgumentType
                                .getInteger(ctx, "horas"));
                        return 1;
                    })))

            .then(literal("gimnasio")
                // ⚠ Nivel 4: escribe bloques en el mundo y mueve gente entre
                //   dimensiones. No es para jugadores.
                .requires(x -> x.hasPermissionLevel(4))
                // `/luna gimnasio` a secas: donde esta cada uno
                .executes(ctx -> {
                    var s = ctx.getSource();
                    s.sendFeedback(() -> Text.literal(
                        "\u00a76Los gimnasios \u00a77(dimension "
                        + "lunaeternal:gimnasios)"), false);
                    for (var g : net.pokereport.luna.gym.Gimnasio.TODOS) {
                        var o = net.pokereport.luna.gym.Gimnasio.maestro(g);
                        int libres = net.pokereport.luna.gym.Ranuras.libres(g);
                        s.sendFeedback(() -> Text.literal(String.format(
                            "  \u00a7f%-9s \u00a77maestro en \u00a7b%d %d %d"
                            + "  \u00a78%d/%d ranuras libres",
                            g.id(), o.getX(), o.getY(), o.getZ(),
                            libres, net.pokereport.luna.gym.Gimnasio.RANURAS - 1)),
                            false);
                    }
                    return net.pokereport.luna.gym.Gimnasio.TODOS.size();
                })
                .then(argument("cual", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (var g : net.pokereport.luna.gym.Gimnasio.TODOS) {
                            b.suggest(g.id());
                        }
                        return b.buildFuture();
                    })
                    // `/luna gimnasio brock` -> te lleva a su MAESTRO
                    .executes(ctx -> irAlMaestro(ctx, false))
                    // `/luna gimnasio brock plataforma` -> ademas la pone
                    .then(literal("plataforma")
                        .executes(ctx -> irAlMaestro(ctx, true)))
                    // ⚠ Mide lo construido de verdad, para que el area a
                    //   clonar deje de ser una suposicion.
                    .then(literal("medir")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            var m = net.pokereport.luna.gym.Arenas.medir(
                                    s.getServer(), g);
                            if (m == null) {
                                s.sendFeedback(() -> Text.literal(
                                    "§eNo hay nada construido en el maestro "
                                    + "de §f" + g.id()), false);
                                return 0;
                            }
                            s.sendFeedback(() -> Text.literal(String.format(
                                "§6%s§7 mide §b%d x %d x %d"
                                + "§7, desde el desfase §f%d %d %d"
                                + "§7 del origen",
                                g.id(), m[3], m[4], m[5], m[0], m[1], m[2])),
                                false);
                            return 1;
                        }))
                    // ⚠⚠ PONE AL LIDER EN EL MAESTRO PARA PODER MIRARLO. La
                    //    tarima se mide de pie en el juego, y hasta que alguien
                    //    ve a Brock ahi de verdad, la coordenada es un numero en
                    //    un fichero. Un lider dentro de una pared NO DA NINGUN
                    //    ERROR: aparece, y no se le ve.
                    //    Va en la ranura 0 a proposito: es la unica en la que no
                    //    combate nadie.
                    .then(literal("lider")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            var mob = net.pokereport.luna.gym.Lideres.enArena(
                                    s.getServer(), g, 0);
                            if (mob == null) {
                                s.sendError(Text.literal(
                                    "§cNo se pudo poner a §f" + g.lider()
                                    + "§c. Mira el log: lo mas probable es que "
                                    + "el entrenador §f" + g.entrenador()
                                    + "§c no exista en el datapack."));
                                return 0;
                            }
                            var sitio = net.pokereport.luna.gym.Gimnasio.lider(g, 0);
                            s.sendFeedback(() -> Text.literal(String.format(
                                "§6%s§7 puesto en §b%.2f %.2f %.2f"
                                + "§7 (giro %.0f)%s",
                                g.lider(), sitio.x, sitio.y, sitio.z,
                                net.pokereport.luna.gym.Gimnasio.giroLider(g),
                                net.pokereport.luna.gym.Gimnasio.tieneTarima(g)
                                    ? "" : " §eSIN MEDIR: es una suposicion")),
                                false);
                            return 1;
                        }))
                    // ⚠⚠⚠ COMPRUEBA LOS BLOQUES DE «BATTLE POSITION».
                    //    Son de `cobblemonbattlepositions` y son los que colocan
                    //    a los Pokemon en la arena. Si falta alguno de los dos
                    //    obligatorios, el combate SE JUEGA IGUAL --con los
                    //    Pokemon donde caigan-- y no hay ni un aviso.
                    .then(literal("posiciones")
                        .executes(ctx -> comprobarPosiciones(ctx)))
                    // ⚠⚠ BORRA LAS COPIAS. `clonar` NO copia el aire, asi que un
                    //    bloque QUITADO del maestro se queda en la copia para
                    //    siempre y volver a clonar no lo arregla. Mientras se
                    //    construye hace falta; despues, casi nunca.
                    // ⚠ Una ranura mal clonada NO da ningun error: le toca a
                    //   una persona, entra en una sala con media pared o sin los
                    //   bloques de posicion, y desde dentro parece que el
                    //   gimnasio esta roto. Y las otras seis estan bien.
                    // ⚠⚠ MIRAR: guarda hacia donde mira ese lider EN DISCO.
                    //    El giro ya se podia pasar a `ciudadela <grados>` y se
                    //    perdia al reiniciar: el comando decia «hecho», el lider
                    //    giraba, y dias despues «Brock se ha girado solo».
                    .then(literal("mirar")
                        .then(argument("grados",
                                com.mojang.brigadier.arguments.FloatArgumentType
                                        .floatArg(-360f, 360f))
                            .executes(ctx -> {
                                var s = ctx.getSource();
                                var g = net.pokereport.luna.gym.Gimnasio.de(
                                        StringArgumentType.getString(ctx, "cual"));
                                if (g == null) {
                                    s.sendError(Text.literal("§cNo existe"));
                                    return 0;
                                }
                                float grados = com.mojang.brigadier.arguments
                                        .FloatArgumentType.getFloat(ctx, "grados");
                                if (!net.pokereport.luna.gym.Orientacion
                                        .poner(g.id(), grados)) {
                                    s.sendError(Text.literal(
                                        "§cNo se pudo guardar. Mira el log."));
                                    return 0;
                                }
                                // Y se aplica ya, para verlo sin reiniciar.
                                int n = net.pokereport.luna.gym.Lideres
                                        .colocarRecepciones(s.getServer(), null);
                                s.sendFeedback(() -> Text.literal(
                                    "§a" + g.lider() + " §7mira a §f" + grados
                                    + "°§7, guardado. (" + n + " recepciones"
                                    + " recolocadas)\n"
                                    + "§80 sur · 90 oeste · 180 norte · -90 este"),
                                    false);
                                return 1;
                            })))
                    // ⚠ Y poder QUITARLO: sin esto, un giro mal puesto no se
                    //   puede deshacer sin editar el fichero a mano.
                    .then(literal("nomirar")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            boolean habia = net.pokereport.luna.gym.Orientacion
                                    .quitar(g.id());
                            net.pokereport.luna.gym.Lideres
                                    .colocarRecepciones(s.getServer(), null);
                            s.sendFeedback(() -> Text.literal(habia
                                ? "§a" + g.lider() + " §7vuelve a su giro de origen"
                                : "§7" + g.lider() + " no tenia ninguno guardado"),
                                false);
                            return 1;
                        }))
                    // ⚠⚠ EQUIPO: enseña QUE SACARIA el lider contra el equipo de
                    //    quien lo pide. Es la unica forma de ver la adaptacion
                    //    sin pelear, y por tanto la unica forma de notar que
                    //    esta eligiendo mal -- que es un fallo que NO da error.
                    .then(literal("equipo")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            var jugador = s.getPlayer();
                            if (jugador == null) {
                                s.sendError(Text.literal(
                                    "§cDesde la consola no: hace falta tu equipo"));
                                return 0;
                            }
                            for (String linea :
                                    net.pokereport.luna.gym.Adaptador
                                        .ensayo(jugador, g)) {
                                s.sendFeedback(() -> Text.literal(linea), false);
                            }
                            return 1;
                        }))
                    .then(literal("comprobar")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            net.pokereport.luna.gym.Arenas.comprobarRanuras(
                                s.getServer(), g,
                                linea -> {
                                    // ⚠ El informe llega repartido en varios
                                    //   ticks, asi que quien lo pidio puede
                                    //   haberse ido. Al log siempre; al jugador
                                    //   solo si sigue ahi.
                                    LunaEternal.LOG.info(linea.replace("\u00a7", "&"));
                                    try {
                                        s.sendFeedback(() -> Text.literal(linea),
                                                false);
                                    } catch (Exception ignorado) {
                                        // se fue: el log ya lo tiene
                                    }
                                });
                            return 1;
                        }))
                    .then(literal("limpiarranuras")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            var g = net.pokereport.luna.gym.Gimnasio.de(
                                    StringArgumentType.getString(ctx, "cual"));
                            if (g == null) {
                                s.sendError(Text.literal("§cNo existe"));
                                return 0;
                            }
                            int n = net.pokereport.luna.gym.Arenas.limpiarRanuras(
                                    s.getServer(), g);
                            s.sendFeedback(() -> Text.literal(
                                "§a" + n + " §7bloques quitados de las ranuras de "
                                + "§f" + g.id() + "§7. Se volveran a clonar del "
                                + "maestro."), false);
                            return 1;
                        })))
                // Los lideres de la CIUDADELA: los que reciben y abren el
                // dialogo. Van aparte de `<cual>` porque no son de un gimnasio
                // sino de todos los que ya tengan sitio construido.
                .then(literal("ciudadela")
                    .executes(ctx -> {
                        var s = ctx.getSource();
                        int n = net.pokereport.luna.gym.Lideres
                                .colocarRecepciones(s.getServer(), null);
                        s.sendFeedback(() -> Text.literal(
                            "§a" + n + " §7lider(es) en la ciudadela, con "
                            + "su Pokemon al lado."), false);
                        return n;
                    })
                    // ⚠ El giro se puede forzar porque hacia donde mira Brock
                    //   depende de como quede la sala, y eso no se sabe desde
                    //   aqui. Un numero, y se vuelve a ejecutar.
                    .then(argument("grados", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-180, 360))
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            float giro = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "grados");
                            int n = net.pokereport.luna.gym.Lideres
                                    .colocarRecepciones(s.getServer(), giro);
                            s.sendFeedback(() -> Text.literal(
                                "§a" + n + " §7lider(es) mirando a §f"
                                + (int) giro + "°"), false);
                            return n;
                        }))
                    .then(literal("quitar")
                        .executes(ctx -> {
                            var s = ctx.getSource();
                            int n = net.pokereport.luna.gym.Lideres
                                    .quitarRecepciones(s.getServer());
                            s.sendFeedback(() -> Text.literal(
                                "§a" + n + " §7quitados."), false);
                            return n;
                        })))
                // ⚠⚠ VUELVE A CLONAR LAS RANURAS. Hace falta MIENTRAS SE
                //    CONSTRUYE: una ranura se clona UNA VEZ por arranque, asi que
                //    un cambio en el maestro --mover a Brock, poner los bloques
                //    de posicion-- no llega a las copias ya hechas.
                //    ⚠ Solo olvida la marca: los bloques viejos siguen ahi y el
                //      clonado no borra aire. Para una copia limpia, reiniciar.
                // ⚠⚠ TODAS DE GOLPE, para empezar a mapear los 22 que faltan.
                //    Salta los que YA tienen sala: `preparar` escribe piedra en
                //    el origen, y sobre una sala construida eso le mete un
                //    cuadrado de andesita en mitad del suelo SIN DAR ERROR.
                .then(literal("plataformas")
                    .requires(s -> s.hasPermissionLevel(2))
                    .executes(ctx -> {
                        var s = ctx.getSource();
                        var mundo = net.pokereport.luna.gym.Arenas.mundo(
                                s.getServer());
                        if (mundo == null) {
                            s.sendError(Text.literal(
                                "\u00a7cLa dimension de gimnasios no existe."));
                            return 0;
                        }
                        int puestas = 0, saltadas = 0;
                        var saltados = new StringBuilder();
                        for (var g : net.pokereport.luna.gym.Gimnasio.TODOS) {
                            if (net.pokereport.luna.gym.Arenas.hayObra(mundo, g)) {
                                saltadas++;
                                if (saltados.length() > 0) {
                                    saltados.append(", ");
                                }
                                saltados.append(g.id());
                                continue;
                            }
                            net.pokereport.luna.gym.Arenas.preparar(
                                    s.getServer(), g);
                            puestas++;
                        }
                        final int pp = puestas, ss = saltadas;
                        final String lista = saltados.toString();
                        s.sendFeedback(() -> Text.literal(
                            "\u00a7a" + pp + " plataformas de "
                            + net.pokereport.luna.gym.Arenas.LADO + "x"
                            + net.pokereport.luna.gym.Arenas.LADO
                            + " puestas\u00a77, el bloque de oro es el origen.\n"
                            + (ss == 0 ? ""
                               : "\u00a7e" + ss + " saltados por tener sala ya: "
                                 + "\u00a7f" + lista + "\n")
                            + "\u00a77Ve a cada uno con \u00a7f/luna gimnasio <cual>"),
                            false);
                        return 1;
                    }))
                .then(literal("reclonar")
                    .executes(ctx -> {
                        net.pokereport.luna.gym.Ranuras.olvidarConstruidas();
                        // ⚠ Y lo MEDIDO tambien: si el maestro ha cambiado de
                        //   tamaño, clonar con la medida vieja copiaria de menos
                        //   --y un gimnasio cortado no da ningun error.
                        net.pokereport.luna.gym.Arenas.olvidarMedidas();
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§7Las ranuras se volveran a clonar del maestro la "
                            + "proxima vez que alguien entre."), false);
                        return 1;
                    })))

            .then(literal("paradas")
                // ⚠ Nivel 4: coloca entidades permanentes en la ciudadela.
                .requires(x -> x.hasPermissionLevel(4))
                .executes(ctx -> {
                    int n = net.pokereport.luna.world.Paradas
                            .colocarTodas(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "\u00a7a" + n + " \u00a77Miraidon colocados en las paradas."),
                        false);
                    return n;
                })
                // ⚠ Existe porque `/luna decorar quitar` solo borra ALREDEDOR
                //   de quien lo ejecuta, y las paradas estan repartidas por toda
                //   la ciudadela: quitarlas obligaba a ir andando a las siete, y
                //   andando es facil dejarse una -- que ademas no se puede
                //   atacar ni capturar, asi que se quedaria ahi para siempre.
                .then(literal("quitar")
                    .executes(ctx -> {
                        int n = net.pokereport.luna.world.Paradas
                                .quitarTodas(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§e" + n + " §7decorativos quitados de las "
                            + "siete paradas."), false);
                        return n;
                    })))

            .then(literal("decorar")
                // ⚠ Nivel 4: coloca entidades permanentes en el mundo. Es
                //   decoracion, pero decoracion que no se despawnea sola.
                .requires(s -> s.hasPermissionLevel(4))
                .then(literal("quitar")
                    .executes(ctx -> quitarDecorativos(ctx.getSource(), 8))
                    .then(argument("radio", com.mojang.brigadier.arguments
                            .IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> quitarDecorativos(ctx.getSource(),
                            com.mojang.brigadier.arguments.IntegerArgumentType
                                .getInteger(ctx, "radio")))))
                .then(argument("especie", StringArgumentType.word())
                    .then(argument("postura", StringArgumentType.word())
                        .executes(ctx -> decorar(ctx.getSource(),
                            StringArgumentType.getString(ctx, "especie"),
                            StringArgumentType.getString(ctx, "postura"), null, null))
                        .then(argument("donde", net.minecraft.command.argument
                                .Vec3ArgumentType.vec3())
                            .executes(ctx -> decorar(ctx.getSource(),
                                StringArgumentType.getString(ctx, "especie"),
                                StringArgumentType.getString(ctx, "postura"),
                                net.minecraft.command.argument.Vec3ArgumentType
                                    .getVec3(ctx, "donde"), null))
                            // ⚠ Los grados son OPCIONALES y no obligatorios: sin
                            //   ellos coge hacia donde mira quien lo pone, que es
                            //   lo comodo cuando estas delante. Con ellos se pone
                            //   un angulo exacto sin tener que apuntar.
                            .then(argument("grados", com.mojang.brigadier.arguments
                                    .FloatArgumentType.floatArg(-180f, 360f))
                                .executes(ctx -> decorar(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "especie"),
                                    StringArgumentType.getString(ctx, "postura"),
                                    net.minecraft.command.argument.Vec3ArgumentType
                                        .getVec3(ctx, "donde"),
                                    com.mojang.brigadier.arguments.FloatArgumentType
                                        .getFloat(ctx, "grados"))))))))

            .then(literal("rango")
                // ⚠ Nivel 4 y no 3. Un rango desbloquea comodidad para siempre;
                //   los comandos de nivel 3 son de diagnostico y los tienen los
                //   moderadores. Dar rangos es de la administracion.
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> listarRangos(ctx.getSource()))
                .then(argument("jugador", StringArgumentType.word())
                    .then(argument("rango", StringArgumentType.word())
                        .executes(ctx -> ponerRango(ctx.getSource(),
                            StringArgumentType.getString(ctx, "jugador"),
                            StringArgumentType.getString(ctx, "rango"))))))

            .then(literal("traje")
                // ⚠ Nivel 4, como `rango`: esto es lo que Tebex ejecuta por
                //   consola cuando alguien paga. Un traje es una compra.
                .requires(s -> s.hasPermissionLevel(4))
                .then(argument("jugador", StringArgumentType.word())
                    .executes(ctx -> verTrajes(ctx.getSource(),
                        StringArgumentType.getString(ctx, "jugador"))))
                .then(literal("dar")
                    .then(argument("jugador", StringArgumentType.word())
                        .then(argument("traje", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (var t : net.pokereport.luna.traje.Traje.todos()) {
                                    b.suggest(t.id());
                                }
                                return b.buildFuture();
                            })
                            .executes(ctx -> darTraje(ctx.getSource(),
                                StringArgumentType.getString(ctx, "jugador"),
                                StringArgumentType.getString(ctx, "traje"), true)))))
                .then(literal("quitar")
                    .then(argument("jugador", StringArgumentType.word())
                        .then(argument("traje", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (var t : net.pokereport.luna.traje.Traje.todos()) {
                                    b.suggest(t.id());
                                }
                                return b.buildFuture();
                            })
                            .executes(ctx -> darTraje(ctx.getSource(),
                                StringArgumentType.getString(ctx, "jugador"),
                                StringArgumentType.getString(ctx, "traje"), false))))))

            .then(literal("rotarcazas")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> rotarCazas(ctx.getSource())))

            .then(literal("cosmeticos")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> cosmeticos(ctx.getSource()))
                .then(literal("huerfanos")
                    .executes(ctx -> huerfanos(ctx.getSource()))))

            .then(literal("via")
                .requires(s -> s.hasPermissionLevel(3))
                .then(argument("via", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (var v : net.pokereport.luna.progression.Path.values()) {
                            b.suggest(v.name());
                        }
                        return b.buildFuture();
                    })
                    .then(argument("xp", com.mojang.brigadier.arguments.LongArgumentType
                            .longArg(1, 1_000_000))
                        .executes(ctx -> darVia(ctx.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType
                                        .getString(ctx, "via"),
                                com.mojang.brigadier.arguments.LongArgumentType
                                        .getLong(ctx, "xp"))))))

            .then(literal("reiniciarmision")
                .requires(s -> s.hasPermissionLevel(4))
                .then(argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (var q : LunaEternal.quests().catalogo()) {
                            b.suggest(q.id());
                        }
                        return b.buildFuture();
                    })
                    .executes(ctx -> reiniciarMision(ctx.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "id")))))

            // ⚠ Una medalla se gana UNA VEZ por cuenta --lo dice la clave
            //   primaria de `gym_badge`-- y eso es lo correcto en el juego y lo
            //   que estorba al probarlo. Mismo motivo que `reiniciarinicial`.
            .then(literal("reiniciarmedalla")
                .requires(s -> s.hasPermissionLevel(4))
                .then(argument("cual", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (var g : net.pokereport.luna.gym.Gimnasio.TODOS) {
                            b.suggest(g.id());
                        }
                        return b.buildFuture();
                    })
                    .executes(ctx -> reiniciarMedalla(ctx, null))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> reiniciarMedalla(ctx,
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador"))))))

            .then(literal("reiniciarinicial")
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> reiniciarInicial(ctx.getSource())))

            // ⚠ LA MODERACION DE FOTOS DEL SANTUARIO va a nivel 3: es de staff,
            //   no de administrador del servidor. Aprobar una foto es lo mismo
            //   que moderar un mensaje de chat -- y el chat lo moderan los de
            //   nivel 3.
            .then(literal("santuario")
                .requires(s -> s.hasPermissionLevel(3))
                .then(literal("aprobar")
                    .then(argument("foto", com.mojang.brigadier.arguments.LongArgumentType
                            .longArg(1))
                        .executes(ctx -> fotoSantuario(ctx.getSource(),
                                com.mojang.brigadier.arguments.LongArgumentType
                                        .getLong(ctx, "foto"), true))))
                .then(literal("rechazar")
                    .then(argument("foto", com.mojang.brigadier.arguments.LongArgumentType
                            .longArg(1))
                        .executes(ctx -> fotoSantuario(ctx.getSource(),
                                com.mojang.brigadier.arguments.LongArgumentType
                                        .getLong(ctx, "foto"), false))))
                .then(literal("pendientes")
                    .executes(ctx -> pendientesSantuario(ctx.getSource()))))

            .then(literal("autotest")
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> autotest(ctx.getSource())))
        );
    }

    // ------------------------------------------------------------------

    /** Nombre corto → dimensión. El orden es el que sale al autocompletar. */
    private static final java.util.Map<String, net.minecraft.registry.RegistryKey<net.minecraft.world.World>> DESTINOS =
        new java.util.LinkedHashMap<>() {{
            put("ciudadela", net.pokereport.luna.world.LunaDimensions.CIUDADELA);
            put("lobby", net.pokereport.luna.world.LunaDimensions.LOBBY);
            put("hogar", net.pokereport.luna.world.LunaDimensions.HOGAR);
            put("salvaje", net.pokereport.luna.world.LunaDimensions.SALVAJE);
        }};

    private static int viajar(ServerCommandSource src, String destino) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        var clave = DESTINOS.get(destino.toLowerCase(java.util.Locale.ROOT));
        if (clave == null) {
            src.sendError(Text.literal(
                "No conozco ese sitio. Hay: " + String.join(", ", DESTINOS.keySet())));
            return 0;
        }
        return net.pokereport.luna.world.TravelService.travel(
            p, clave, net.pokereport.luna.world.TravelService.nameOf(clave)) ? 1 : 0;
    }

    /** Nivel de operador de un constructor. NO subirlo: ver docs/world/construccion.md. */
    private static final int NIVEL_CONSTRUCTOR = 2;

    /**
     * Se da de alta como constructor presentando la clave.
     *
     * <p>Concede <b>nivel 2</b>: creativo, {@code /tp}, WorldEdit y Axiom
     * completo. NO concede nivel 3 ni 4, así que un constructor no puede
     * banear, ni dar OP, ni apagar el servidor — ni por accidente.
     *
     * <p>La comparación es de tiempo constante. Es exagerado para un servidor
     * de amigos, y cuesta una línea: comparar con {@code equals} filtra el
     * tiempo de respuesta y deja adivinar la clave carácter a carácter.
     */
    private static int altaConstructor(ServerCommandSource src, String clave) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }

        String esperada = LunaEternal.builderKey();
        if (esperada == null || esperada.isBlank()) {
            src.sendError(Text.literal("Las altas de constructor están cerradas."));
            return 0;
        }

        if (!java.security.MessageDigest.isEqual(
                clave.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                esperada.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            // Se registra: si alguien está probando claves, quiero verlo.
            LunaEternal.LOG.warn("Clave de constructor incorrecta de {}",
                p.getGameProfile().getName());
            src.sendError(Text.literal("Esa clave no vale."));
            return 0;
        }

        var server = p.getServer();
        if (p.hasPermissionLevel(NIVEL_CONSTRUCTOR)) {
            src.sendFeedback(() -> Text.literal("§aYa eres constructor."), false);
            return 1;
        }

        // addToOperators() daría el nivel de `op-permission-level`, que es 4.
        // Aquí se fuerza el 2 escribiendo la entrada a mano.
        server.getPlayerManager().getOpList().add(
            new net.minecraft.server.OperatorEntry(
                p.getGameProfile(), NIVEL_CONSTRUCTOR, false));
        // Sin esto el jugador no ve los comandos nuevos hasta reconectar.
        server.getPlayerManager().sendCommandTree(p);

        LunaEternal.LOG.info("{} es ahora constructor (nivel {})",
            p.getGameProfile().getName(), NIVEL_CONSTRUCTOR);
        src.sendFeedback(() -> Text.literal(
            "§a¡Listo! Ya eres constructor.\n"
            + "§7Ve a la ciudadela con §f/luna ir ciudadela\n"
            + "§7Ponte en creativo con §f/gamemode creative\n"
            + "§7Abre Axiom con §fShift derecho"), false);
        return 1;
    }

    private static int balance(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                long dollars = LunaEternal.economy().balance(id, Currency.POKEDOLLAR);
                long marks   = LunaEternal.economy().balance(id, Currency.MARK);
                // Volver al hilo del servidor para hablar con el jugador.
                p.getServer().execute(() -> p.sendMessage(Text.literal(
                    // El nombre sale del enum, no escrito aqui: cambiarlo en
                    // dos sitios es como se acaba con una pantalla que dice
                    // "Plata" y un comando que sigue diciendo otra cosa.
                    Currency.POKEDOLLAR.color + Currency.POKEDOLLAR.displayName
                    + ": §f" + dollars + "  " + Currency.MARK.color
                    + Currency.MARK.displayName + ": §f" + marks), false));
            } catch (Exception e) {
                reply(p, "§cError al consultar el saldo: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int grant(ServerCommandSource src, String currencyName, long amount) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        final Currency currency;
        try {
            currency = Currency.valueOf(currencyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // ⚠ EL MENSAJE SE CONSTRUYE DEL ENUM, no se escribe a mano. Decia
            //   "Usa POKEDOLLAR o MARK" desde antes de que existiera REPORTCOIN
            //   (D-013): la moneda funcionaba y el error juraba que no existia,
            //   que es la peor combinacion -- quien lo leyera dejaria de
            //   intentarlo. Sacandolo del enum, una moneda nueva se lista sola.
            String monedas = java.util.Arrays.stream(Currency.values())
                    .map(c -> c.name() + " (" + c.displayName + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            src.sendError(Text.literal("Moneda desconocida. Usa: " + monedas));
            return 0;
        }

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                // Clave de idempotencia nueva en cada uso: es un comando de
                // admin, no un reintento. En operaciones reales la clave
                // viene de la operación de origen (R4).
                long after = LunaEternal.economy().credit(
                    id, currency, amount, "admin_grant", UUID.randomUUID().toString());
                reply(p, "§aConcedido. Saldo " + currency.displayName + ": §f" + after);
            } catch (EconomyException e) {
                reply(p, "§c" + e.getMessage());
            } catch (Exception e) {
                reply(p, "§cError: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int audit(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                    .resolve(p.getUuid(), p.getGameProfile().getName());
                long dd = LunaEternal.economy()
                    .auditDiscrepancy(id, Currency.POKEDOLLAR);
                long dm = LunaEternal.economy()
                    .auditDiscrepancy(id, Currency.MARK);
                String msg = (dd == 0 && dm == 0)
                    ? "§aSaldo y libro de asientos cuadran."
                    : "§c¡DESCUADRE! " + Currency.POKEDOLLAR.displayName + ": " + dd
                      + "  " + Currency.MARK.displayName + ": " + dm;
                reply(p, msg);
            } catch (Exception e) {
                reply(p, "§cError al auditar: " + e.getMessage());
            }
        });
        return 1;
    }

    /**
     * Ejecuta la batería de invariantes económicos. Funciona desde la consola,
     * así que no hace falta ningún jugador conectado.
     */
    /**
     * Que cosmeticos tiene QUIEN LO ESCRIBE, leidos de la base.
     *
     * <p>Existe porque el usuario dijo «no compre el snorlax chef y dice que ya
     * lo tengo», y la unica forma de contestar a eso sin suponer es MIRAR. Salio
     * que si lo tenia --de las cuatro compras de prueba, con el catalogo viejo,
     * que usaba los mismos identificadores-- pero eso no se sabia hasta mirarlo.
     *
     * <p>Se queda porque la pregunta va a volver: cada vez que alguien diga «yo
     * no compre esto», la respuesta tiene que salir de la tabla, no de la
     * memoria de nadie.
     */
    private static int cosmeticos(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                var tiene = LunaEternal.cosmetics().poseidos(id);
                var lineas = new java.util.ArrayList<String>();
                for (String c : new java.util.TreeSet<>(tiene)) {
                    // Se marca lo que YA NO ESTA en el catalogo. Es el caso que
                    // importa: un cosmetico comprado que despues se retiro --los
                    // 8 del pack que no traian arte, por ejemplo-- sigue en la
                    // tabla y no sale en la tienda. Sin esta marca, el recuento
                    // de la pantalla y el de aqui no cuadran y parece un fallo.
                    boolean vigente = net.pokereport.luna.cosmetics.Catalogo.de(c) != null;
                    lineas.add((vigente ? "§7  " : "§8  ") + c
                            + (vigente ? "" : " §8(ya no esta en el catalogo)"));
                }
                origen.getServer().execute(() -> {
                    origen.sendFeedback(() -> Text.literal(
                            "§7" + tiene.size() + " cosmeticos de "
                            + jugador.getName().getString()), false);
                    for (String l : lineas) {
                        origen.sendFeedback(() -> Text.literal(l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron leer los cosmeticos: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Cosmeticos que alguien TIENE y ya no estan en el catalogo.
     *
     * <p>⚠ EXISTE PORQUE EL CATALOGO PUEDE ENCOGER, y encoger es normal: se
     * retiraron los nueve que el pack declaraba sin arte, las cinco formas mega y
     * la categoria de capas entera. Cada vez que eso pasa, quien hubiera comprado
     * uno se queda con una fila en {@code player_cosmetics} que apunta a algo que
     * ya no existe.
     *
     * <p><b>No rompe nada</b> —el catalogo se recorre al reves, asi que la pieza
     * simplemente no sale— y ese es justo el problema: <b>es invisible</b>. El
     * jugador pago y no tiene nada, y nadie se entera hasta que pregunta.
     *
     * <p>Esto no devuelve el dinero: solo dice a quien hay que devolverselo.
     * Reembolsar automaticamente seria peor —una regeneracion del catalogo con un
     * fallo devolveria dinero a medio servidor— y {@code /luna dar} ya existe.
     */
    private static int huerfanos(ServerCommandSource origen) {
        LunaEternal.submit(() -> {
            try {
                var filas = LunaEternal.cosmetics().huerfanos();
                origen.getServer().execute(() -> {
                    if (filas.isEmpty()) {
                        origen.sendFeedback(() -> Text.literal(
                                "§aNadie tiene cosmeticos retirados."), false);
                        return;
                    }
                    origen.sendFeedback(() -> Text.literal(
                            "§e" + filas.size() + " compras de cosmeticos que ya "
                            + "no estan en el catalogo:"), false);
                    for (String l : filas) {
                        origen.sendFeedback(() -> Text.literal("§7  " + l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudieron buscar los huerfanos: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Dar XP de una Via. <b>Solo para probar la pantalla de Trabajos.</b>
     *
     * <p>⚠ La XP de Vias se gana JUGANDO --capturas, combates, ventas--, asi que
     * sin esto no habia forma de ver una barra a medias ni un nivel distinto de
     * cero sin echar horas. Con las barras siempre a cero no se puede juzgar si
     * la pantalla dibuja bien, que es justo lo que hay que comprobar.
     *
     * <p>⚠⚠ ESTO INYECTA PROGRESION, y la progresion NO SE VENDE NI SE REGALA
     * (P4, D-014). Va a nivel 3 --el mismo que `/luna dar`-- y queda anotado en
     * el libro de la Via como cualquier otra concesion, para que una prueba no se
     * confunda despues con progresion jugada.
     */
    private static int darVia(ServerCommandSource origen, String nombre, long xp) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        net.pokereport.luna.progression.Path via = null;
        for (var v : net.pokereport.luna.progression.Path.values()) {
            if (v.name().equalsIgnoreCase(nombre)) {
                via = v;
            }
        }
        if (via == null) {
            // Se dicen las que HAY, en vez de "via desconocida". Un error que no
            // ofrece la salida obliga a ir a buscarla al codigo.
            var sb = new StringBuilder("Vias: ");
            for (var v : net.pokereport.luna.progression.Path.values()) {
                sb.append(v.name()).append(' ');
            }
            origen.sendError(Text.literal(sb.toString().trim()));
            return 0;
        }
        final var elegida = via;
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                // ⚠⚠ POR `OficiosService`, NO POR `progression().grant()`.
                //
                //   Estaba llamando a `grant` directamente, que solo escribe la
                //   fila: SIN PAGO, SIN AVISO Y SIN SONIDO. Y yo le dije al
                //   usuario que probara con este comando, asi que probo justo el
                //   unico camino que no hace nada de lo que se acababa de
                //   construir. Su reporte fue exacto: "cuando subes de nivel no
                //   da plata ni nada".
                //
                //   Un comando de prueba que no recorre el mismo camino que el
                //   juego no prueba nada; solo da la falsa sensacion de haberlo
                //   probado.
                net.pokereport.luna.progression.OficiosService.ganar(jugador, id, elegida, xp);
                var estado = LunaEternal.progression().all(id).get(elegida);
                final int nivel = estado == null ? 0 : estado.level();
                final long tiene = estado == null ? 0 : estado.xp();
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "§a" + elegida.displayName + " -> nivel "
                        + net.pokereport.luna.progression.Path.roman(nivel)
                        + " (" + tiene + " XP)"), false));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo dar XP de via: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Borra la marca de «ya elegiste inicial». <b>Solo para probar.</b>
     *
     * <p>⚠ Nivel 4 --el mas alto-- y no 3 como los demas. Los otros comandos de
     * prueba dan cosas; este PERMITE VOLVER A COGER UN POKEMON GRATIS. No es lo
     * mismo, y la diferencia importa: un constructor con nivel 2 o un moderador
     * con nivel 3 no deberian poder repartir iniciales.
     *
     * <p>⚠ Y NO quita el Pokemon que ya se entrego. Borra solo la marca, asi que
     * quien lo use se queda con los dos. Es correcto para probar y seria un
     * agujero en produccion: por eso el nivel.
     */
    /**
     * Borra el progreso de una mision concreta. <b>Solo para probar.</b>
     *
     * <p>⚠ Nivel 4, como el del inicial, y por lo mismo: NO devuelve la
     * recompensa ya cobrada, asi que quien reinicie una mision pagada se queda
     * con el dinero y puede volver a cobrarla. Es un agujero deliberado y por eso
     * esta donde esta.
     */
    private static int reiniciarMision(ServerCommandSource origen, String questId) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        if (LunaEternal.quests().byId(questId) == null) {
            origen.sendError(Text.literal("No existe la mision '" + questId + "'."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                int n = LunaEternal.quests().reiniciar(id, questId);
                net.pokereport.luna.net.Red.refrescarMisiones(jugador);
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "§a" + questId + ": " + n + " fila(s) borradas."), false));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo reiniciar la mision: {}", e.toString());
            }
        });
        return 1;
    }

    private static int reiniciarInicial(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players()
                        .resolve(jugador.getUuid(), jugador.getName().getString());
                LunaEternal.kitService().undoOnce(id,
                        net.pokereport.luna.starter.StarterService.CLAVE);

                // ⚠⚠ Y LA MISION TAMBIEN. Son DOS TABLAS distintas --`kit_claim`
                //    y `quest_progress`-- y borrar una dejaba la otra puesta: el
                //    usuario se encontraba la pantalla otra vez pero la mision
                //    «Un compañero» seguia completa y cobrada. Ni volvia a
                //    empezar ni se quedaba como estaba: quedaba a medias, que es
                //    peor que cualquiera de las dos.
                //
                //    Se buscan las misiones POR OBJETIVO y no por identificador:
                //    escribir "t1_inicial" aqui ataria este comando al nombre que
                //    tiene hoy una fila de un JSON.
                int misiones = 0;
                for (var q : LunaEternal.quests().catalogo()) {
                    if (q.objective().type()
                            == net.pokereport.luna.quest.Quest.Objective.Type.STARTER) {
                        misiones += LunaEternal.quests().reiniciar(id, q.id());
                    }
                }
                final int borradas = misiones;
                // ⚠ SE REENVIA EL ESTADO, y sin esto el comando "no servia":
                //   borraba la fila y no pasaba nada visible, porque el cliente
                //   guarda la ultima respuesta y seguia creyendo que ya habia
                //   elegido. Borrar en la base no cambia lo que el cliente cree.
                net.pokereport.luna.net.Red.refrescarInicial(jugador);
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "§aReiniciado: marca del inicial y " + borradas
                        + " mision(es). La pantalla se abrira sola."), false));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo reiniciar el inicial: {}", e.toString());
            }
        });
        return 1;
    }

    private static int autotest(ServerCommandSource src) {
        var server = src.getServer();
        src.sendFeedback(() -> Text.literal("§7Ejecutando autotest…"), false);
        LunaEternal.submit(() -> {
            var test = new net.pokereport.luna.test.AutoTest(
                LunaEternal.database(),
                LunaEternal.players(),
                LunaEternal.economy(),
                server.getRegistryManager(),
                line -> server.execute(() ->
                    src.sendFeedback(() -> Text.literal(line), false)));
            test.run();
        });
        return 1;
    }

    /** Fuerza la rotación de cazas. Herramienta de administración. */
    /** Los rangos que existen y cuanta gente hay en cada uno. */
    /**
     * Coloca un Pokemon de decoracion.
     *
     * <p>⚠ Acepta coordenadas <b>opcionales</b> y por eso admite `~ ~ ~`: la
     * decoracion se coloca con decimales --«dentro del recipiente»-- y ponerse
     * exactamente en un punto con tres decimales es imposible a pie.
     */
    private static int decorar(ServerCommandSource src, String especie,
                               String postura, net.minecraft.util.math.Vec3d donde,
                               Float grados) {
        var mundo = src.getWorld();
        net.pokereport.luna.world.Decorativos.Postura p;
        try {
            p = net.pokereport.luna.world.Decorativos.Postura
                    .valueOf(postura.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFeedback(() -> Text.literal(
                "§cPosturas: §fquieto §c· §fdormido "
                + "§c· §fflotando"), false);
            return 0;
        }
        var pos = donde != null ? donde : src.getPosition();
        // ⚠ Sin grados, el giro sale de HACIA DONDE MIRA quien lo pone: es lo
        //   comodo estando delante. Con grados se fija exacto y no hay que
        //   apuntar -- que es lo que hace falta para alinear varios iguales.
        //
        //   Los grados son los de Minecraft (los que enseña F3):
        //       0 = sur   ·   90 = oeste   ·   180 = norte   ·   -90 = este
        float giro = grados != null ? grados : src.getRotation().y;
        var e = net.pokereport.luna.world.Decorativos
                .colocar(mundo, especie, p, pos, giro);
        if (e == null) {
            src.sendFeedback(() -> Text.literal(
                "§cNo existe §f" + especie + "§c."), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal(
            "§a" + especie + " §7colocado en §f"
            + String.format("%.2f %.2f %.2f", pos.x, pos.y, pos.z)
            + " §8· mirando a §7" + Math.round(giro) + "°"), false);
        return 1;
    }

    private static int quitarDecorativos(ServerCommandSource src, int radio) {
        int n = net.pokereport.luna.world.Decorativos
                .quitar(src.getWorld(), src.getPosition(), radio);
        src.sendFeedback(() -> Text.literal(
            "§e" + n + " §7decorativos quitados en " + radio + " bloques."),
            false);
        return n;
    }

    private static int listarRangos(ServerCommandSource src) {
        var svc = LunaEternal.ranks();
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("§cEl sistema de rangos no está listo."), false);
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                var reparto = svc.reparto();
                var lineas = new java.util.ArrayList<String>();
                lineas.add("§7— rangos de jugador, de mayor a menor —");
                for (var r : net.pokereport.luna.ui.Tablist.Rank.deJugador()) {
                    lineas.add("  " + r.tag + " §8" + r.name()
                        + " §7· nivel §f" + r.escalon
                        + " §7· §f" + reparto.getOrDefault(r, 0) + " §7jugadores");
                }
                lineas.add("§8/luna rango <jugador> <RANGO>");
                src.getServer().execute(() -> {
                    for (String l : lineas) {
                        src.sendFeedback(() -> Text.literal(l), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo listar los rangos", e);
            }
        });
        return 1;
    }

    /**
     * Cambia el rango de alguien.
     *
     * <p>⚠⚠ FUNCIONA AUNQUE NO ESTE CONECTADO, y es a proposito: los rangos se
     * conceden desde fuera del juego —una compra, un evento— y esperar a que la
     * persona entre para poder dárselo convierte una tarea de un minuto en una
     * que hay que recordar.
     *
     * <p>⚠ Si está conectado se le refresca la etiqueta <b>y se le avisa</b>. Es
     * la lección de los clanes: el estado no es de quien lo mira, y un rango que
     * cambia sin que se note es un rango que nadie agradece.
     */
    private static int ponerRango(ServerCommandSource src, String jugador,
                                  String rango) {
        var svc = LunaEternal.ranks();
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("§cEl sistema de rangos no está listo."), false);
            return 0;
        }
        var r = net.pokereport.luna.ui.Tablist.Rank.de(rango);
        if (r.equipo || !r.name().equalsIgnoreCase(rango.trim())) {
            // ⚠ `Rank.de` devuelve ENTRENADOR ante un nombre desconocido, asi que
            //   sin esta comprobacion un error de tecleo DEGRADARIA al jugador
            //   en silencio en vez de dar error.
            src.sendFeedback(() -> Text.literal(
                "§cRango desconocido. Usa §f/luna rango §7para ver los que hay."), false);
            return 0;
        }
        var server = src.getServer();
        LunaEternal.submit(() -> {
            try {
                var conectado = server.getPlayerManager().getPlayer(jugador);
                java.util.UUID uuid = conectado != null ? conectado.getUuid() : null;
                Long id = LunaEternal.players().resolveByName(jugador);
                if (id == null) {
                    server.execute(() -> src.sendFeedback(() -> Text.literal(
                        "§cNo conozco a §f" + jugador + "§c."), false));
                    return;
                }
                var puesto = svc.cambiar(id, uuid, r);
                server.execute(() -> {
                    if (puesto == null) {
                        src.sendFeedback(() -> Text.literal("§cNo se pudo cambiar."), false);
                        return;
                    }
                    src.sendFeedback(() -> Text.literal(
                        "§a" + jugador + " §7ahora es " + puesto.tag), false);
                    if (conectado != null && !conectado.isRemoved()) {
                        net.pokereport.luna.ui.Tablist.refrescarClan(server, conectado);
                        conectado.sendMessage(Text.literal(
                            "§7Tu rango ahora es " + puesto.tag), false);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo cambiar el rango de {}", jugador, e);
            }
        });
        return 1;
    }

    /**
     * Da o quita un traje. Es la puerta por la que entra una compra de Tebex.
     *
     * <h2>⚠⚠ FUNCIONA CON EL JUGADOR DESCONECTADO, Y TIENE QUE FUNCIONAR</h2>
     *
     * Una compra llega cuando llega. Por eso se resuelve el {@code player_id}
     * por nombre igual que {@code /luna rango}, y el {@code uuid} solo se usa
     * para refrescar la caché de quien esté dentro.
     *
     * <p>⚠ Si está conectado se le reenvía la pantalla: el estado no es de quien
     * lo mira. Sin eso, alguien que acaba de pagar abre KITS y ve su traje
     * bloqueado hasta reconectar — y eso parece que no le ha llegado la compra.
     */
    private static int darTraje(ServerCommandSource src, String jugador,
                                String traje, boolean dar) {
        var svc = LunaEternal.trajes();
        if (svc == null) {
            src.sendFeedback(() -> Text.literal("§cEl sistema de trajes no está listo."), false);
            return 0;
        }
        var t = net.pokereport.luna.traje.Traje.de(traje);
        if (t == null) {
            // ⚠ `Traje.de` devuelve null ante lo desconocido a proposito, asi
            //   que aqui se puede dar un error de verdad en vez de conceder otro.
            src.sendFeedback(() -> Text.literal(
                "§cNo existe el traje §f" + traje + "§c."), false);
            return 0;
        }
        if (t.gratis()) {
            src.sendFeedback(() -> Text.literal(
                "§7El traje §f" + t.id() + " §7es gratis para todo el mundo: "
                + "no hay nada que dar ni que quitar."), false);
            return 0;
        }
        var server = src.getServer();
        LunaEternal.submit(() -> {
            try {
                var conectado = server.getPlayerManager().getPlayer(jugador);
                Long id = LunaEternal.players().resolveByName(jugador);
                if (id == null) {
                    server.execute(() -> src.sendFeedback(() -> Text.literal(
                        "§cNo conozco a §f" + jugador + "§c."), false));
                    return;
                }
                // ⚠ Desconectado no hay uuid ni falta: la cache se rellena al
                //   entrar (`cargarPropiedad`), asi que lo unico que importa es
                //   que la fila quede escrita.
                java.util.UUID uuid = conectado != null ? conectado.getUuid() : null;
                boolean cambio = dar ? svc.conceder(id, uuid, t)
                                     : svc.retirar(id, uuid, t);
                server.execute(() -> {
                    src.sendFeedback(() -> Text.literal(
                        "§a" + jugador + "§7: " + (dar ? "tiene" : "ya no tiene")
                        + " el traje §f" + t.id()
                        + (cambio ? "" : " §8(ya estaba así)")), true);
                    if (conectado != null && !conectado.isRemoved()) {
                        if (!dar) {
                            svc.revisar(conectado, id);
                            net.pokereport.luna.net.Red.repartirTraje(conectado);
                        }
                        net.pokereport.luna.net.Red.enviarTrajes(conectado);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo cambiar el traje de {}", jugador, e);
            }
        });
        return 1;
    }

    /** Qué trajes tiene alguien. Para comprobar una compra sin abrir la base. */
    private static int verTrajes(ServerCommandSource src, String jugador) {
        var server = src.getServer();
        LunaEternal.submit(() -> {
            try {
                var conectado = server.getPlayerManager().getPlayer(jugador);
                if (conectado == null) {
                    server.execute(() -> src.sendFeedback(() -> Text.literal(
                        "§7§f" + jugador + " §7no está conectado: los trajes se "
                        + "leen al entrar, así que no puedo listárselos."), false));
                    return;
                }
                var suyos = new java.util.ArrayList<String>();
                for (var t : net.pokereport.luna.traje.Traje.todos()) {
                    if (net.pokereport.luna.traje.TrajeService.tiene(
                            conectado.getUuid(), t)) {
                        suyos.add(t.id() + (t.gratis() ? " §8(gratis)§7" : ""));
                    }
                }
                String puesto = net.pokereport.luna.traje.TrajeService
                        .enCache(conectado.getUuid());
                server.execute(() -> src.sendFeedback(() -> Text.literal(
                    "§7" + jugador + " puede ponerse: §f"
                    + (suyos.isEmpty() ? "nada" : String.join("§7, §f", suyos))
                    + "§7. Lleva puesto: §f"
                    + (puesto == null ? "ninguno" : puesto)), false));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudieron listar los trajes de {}", jugador, e);
            }
        });
        return 1;
    }

    private static int rotarCazas(ServerCommandSource src) {
        var server = src.getServer();
        LunaEternal.submit(() -> {
            try {
                int n = LunaEternal.hunts().rotarYa();
                server.execute(() -> src.sendFeedback(() -> Text.literal(
                    "§aCiclos caducados: " + n
                    + ". El próximo vistazo sorteará cazas nuevas."), true));
            } catch (Exception e) {
                server.execute(() -> src.sendError(
                    Text.literal("No se pudo rotar: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int status(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
            "§6" + LunaEternal.NOMBRE + " §7· jugadores en cache: §f"
                + LunaEternal.players().cachedCount()), false);
        return 1;
    }

    private static void reply(ServerPlayerEntity p, String msg) {
        p.getServer().execute(() -> p.sendMessage(Text.literal(msg), false));
    }

    /** Aprueba o rechaza una foto pendiente del santuario. */
    private static int fotoSantuario(ServerCommandSource src, long fotoId, boolean aprobar) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            String motivo = aprobar
                    ? LunaEternal.santuario().aprobar(fotoId)
                    : LunaEternal.santuario().rechazar(fotoId);
            if (motivo == null) {
                reply(p, aprobar
                        ? "§aFoto " + fotoId + " aprobada. Su dueno ya puede colocarla."
                        : "§aFoto " + fotoId + " rechazada.");
            } else if ("no_pendiente".equals(motivo)) {
                reply(p, "§cEsa foto ya no esta pendiente.");
            } else {
                reply(p, "§cNo se pudo: " + motivo);
            }
        });
        return 1;
    }

    /** Las fotos pendientes de moderar. */
    private static int pendientesSantuario(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try (var c = LunaEternal.database().connection();
                 var ps = c.prepareStatement(
                         "SELECT f.foto_id, p.mc_uuid, f.subida_ms FROM santuario_foto f "
                                 + "JOIN player p ON p.player_id = f.owner_id "
                                 + "WHERE f.estado = 'PENDIENTE' ORDER BY f.foto_id")) {
                var lineas = new java.util.ArrayList<String>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lineas.add("§f#" + rs.getLong("foto_id") + " §7de §f"
                                + rs.getString("mc_uuid").substring(0, 8));
                    }
                }
                if (lineas.isEmpty()) {
                    reply(p, "§7No hay fotos pendientes de moderar.");
                } else {
                    reply(p, "§7Fotos pendientes (" + lineas.size() + "): "
                            + String.join(" §8·§7 ", lineas)
                            + " §8-- /luna santuario aprobar <id>");
                }
            } catch (Exception e) {
                reply(p, "§cNo se pudieron leer las pendientes: " + e.getMessage());
            }
        });
        return 1;
    }

    /**
     * Lleva al maestro de un gimnasio, y opcionalmente pone su plataforma.
     *
     * <h2>⚠⚠ LA PLATAFORMA NO SE PONE SOLA AL VIAJAR</h2>
     *
     * Hace falta pedirla (`plataforma`). Puesta en cada viaje, <b>pisaria el
     * gimnasio ya construido</b> cada vez que alguien entrara a mirarlo -- y un
     * cuadrado de piedra en medio del suelo del gimnasio no se ve hasta que
     * alguien pasa por encima.
     */
    private static int irAlMaestro(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
            boolean poner) {
        var s = ctx.getSource();
        String cual = StringArgumentType.getString(ctx, "cual");
        var g = net.pokereport.luna.gym.Gimnasio.de(cual);
        if (g == null) {
            s.sendError(Text.literal("\u00a7cNo existe el gimnasio \u00a7f"
                    + cual + "\u00a7c. Usa \u00a7f/luna gimnasio"));
            return 0;
        }
        var servidor = s.getServer();
        var mundo = net.pokereport.luna.gym.Arenas.mundo(servidor);
        if (mundo == null) {
            s.sendError(Text.literal("\u00a7cLa dimension de gimnasios no existe. "
                    + "\u00a77Hace falta reiniciar tras instalar el mod."));
            return 0;
        }
        if (poner) {
            net.pokereport.luna.gym.Arenas.preparar(servidor, g);
        }
        var o = net.pokereport.luna.gym.Gimnasio.maestro(g);
        var jugador = s.getPlayer();
        if (jugador != null) {
            net.pokereport.luna.world.Regreso.apuntar(jugador);
            // ⚠⚠ EL CENTRO SE CALCULA. Estaba a `+4.5` a mano --el centro de
            //    una plataforma de 9-- y al bajarla a 3 habria dejado al
            //    jugador FUERA, cayendo al vacio de la dimension. Los dos
            //    numeros van juntos siempre.
            double c = net.pokereport.luna.gym.Arenas.centro();
            jugador.teleport(mundo, o.getX() + c, o.getY() + 1, o.getZ() + c,
                    java.util.Set.of(), 0f, 0f);
        }
        s.sendFeedback(() -> Text.literal(
            "\u00a76" + g.id().toUpperCase(java.util.Locale.ROOT)
            + " \u00a77-- pega el esquema con la esquina en \u00a7b"
            + o.getX() + " " + o.getY() + " " + o.getZ()
            + "\u00a77 (el bloque de oro)"), false);
        return 1;
    }

    /**
     * COMPRUEBA LOS BLOQUES DE POSICION DE COMBATE EN EL MAESTRO.
     *
     * <h2>⚠⚠⚠ SIN ELLOS EL COMBATE NO FALLA: SALE MAL Y SE CALLA</h2>
     *
     * Los cuatro bloques son de {@code cobblemonbattlepositions} y dicen donde
     * se pone cada Pokemon y cada entrenador. Si faltan los dos obligatorios
     * --el del Pokemon del jugador y el del entrenador-- el mod se desentiende y
     * Cobblemon los coloca donde caiga: encima de una grada, dentro de una
     * pared, o detras del jugador. <b>No hay error, no hay aviso, y desde dentro
     * parece que el gimnasio esta roto.</b>
     *
     * <p>⚠ Los dos «stand» si son opcionales de verdad: sin ellos, el jugador y
     * el lider se quedan donde esten. Se dice cual falta, sin fingir que da igual.
     *
     * <p>⚠⚠ Y SE MIRA EN EL MAESTRO, que es donde se construye: las ranuras se
     * clonan de el, asi que un bloque puesto ahi aparece en las ocho copias solo.
     */
    private static int comprobarPosiciones(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        var s = ctx.getSource();
        var g = net.pokereport.luna.gym.Gimnasio.de(
                StringArgumentType.getString(ctx, "cual"));
        if (g == null) {
            s.sendError(Text.literal("§cNo existe"));
            return 0;
        }
        var mundo = net.pokereport.luna.gym.Arenas.mundo(s.getServer());
        if (mundo == null) {
            s.sendError(Text.literal("§cLa dimension de gimnasios no existe"));
            return 0;
        }
        String[][] cuales = {
            {"player_pokemon_position",  "Pokemon del jugador", "OBLIGATORIO"},
            {"trainer_pokemon_position", "Pokemon del lider",   "OBLIGATORIO"},
            {"player_stand_position",    "donde se pone el jugador", "opcional"},
            {"trainer_stand_position",   "donde se pone el lider",   "opcional"},
        };
        var origen = net.pokereport.luna.gym.Gimnasio.maestro(g);
        int alcance = net.pokereport.luna.gym.Gimnasio.PASO_RANURA;
        int encontrados = 0;
        s.sendFeedback(() -> Text.literal(
            "§6Bloques de posicion en el maestro de §f" + g.id()), false);
        for (String[] c : cuales) {
            var id = net.minecraft.util.Identifier.of("cobblemonbattlepositions", c[0]);
            var bloque = net.minecraft.registry.Registries.BLOCK.get(id);
            if (bloque == net.minecraft.block.Blocks.AIR) {
                s.sendError(Text.literal(
                    "§cEl mod cobblemonbattlepositions NO esta en el servidor. "
                    + "Sin el, los bloques que coloques no hacen nada."));
                return 0;
            }
            var donde = buscarBloque(mundo, origen, alcance, bloque);
            if (donde == null) {
                boolean grave = "OBLIGATORIO".equals(c[2]);
                s.sendFeedback(() -> Text.literal(
                    (grave ? "  §cFALTA  " : "  §e falta ")
                    + "§f" + c[1] + " §8(" + c[2] + ")"), false);
            } else {
                encontrados++;
                s.sendFeedback(() -> Text.literal(String.format(
                    "  §aOK     §f%s §8en desfase %d %d %d",
                    c[1], donde.getX() - origen.getX(),
                    donde.getY() - origen.getY(), donde.getZ() - origen.getZ())),
                    false);
            }
        }
        final int n = encontrados;
        s.sendFeedback(() -> Text.literal(n >= 2
            ? "§aLos dos obligatorios estan: el combate se colocara bien."
            : "§cFaltan obligatorios. El combate se jugara igual, con los "
              + "Pokemon donde caigan, y NO avisara de nada."), false);

        // ⚠⚠⚠ Y AHORA LA COMPROBACION QUE NO SE VE VENIR: QUE LAS COPIAS NO SE
        //    ROBEN LOS BLOQUES UNAS A OTRAS.
        //
        //    `cobblemonbattlepositions` busca EL BLOQUE MAS CERCANO al jugador,
        //    con un radio que su configuracion pone en 48 (leido de
        //    config/cobblemonbattlepositions.json, no supuesto). Las ranuras van
        //    a PASO_RANURA una de otra, y son copias identicas.
        //
        //    Si desde el fondo de una sala el bloque de la ranura SIGUIENTE
        //    quedara mas cerca que el propio, el combate colocaria los Pokemon
        //    EN LA SALA DE OTRO JUGADOR. No hay error: los Pokemon salen en otra
        //    parte y el jugador ve una arena vacia.
        //
        //    ⚠ Y solo se puede comprobar AQUI, porque hacen falta dos numeros que
        //      viven en el mundo: cuanto mide la sala y donde estan los bloques.
        //      En el autotest seria comparar constantes contra constantes -- la
        //      confianza falsa que ya nos mordio una vez.
        var m = net.pokereport.luna.gym.Arenas.medir(s.getServer(), g);
        if (m != null && encontrados > 0) {
            int fondo = m[2] + m[5];
            var pp = buscarBloque(mundo, origen, alcance,
                    net.minecraft.registry.Registries.BLOCK.get(
                        net.minecraft.util.Identifier.of(
                            "cobblemonbattlepositions", "player_pokemon_position")));
            if (pp != null) {
                int zb = pp.getZ() - origen.getZ();
                // Lo peor posible: alguien de pie en el borde sur de su sala.
                int aLaSiguiente = net.pokereport.luna.gym.Gimnasio.PASO_RANURA
                        + zb - fondo;
                boolean seguro = aLaSiguiente > RADIO_POSICIONES;
                s.sendFeedback(() -> Text.literal(seguro
                    ? String.format("§aLas copias no se pisan: del fondo de una "
                        + "sala al bloque de la siguiente hay %d, y el mod busca "
                        + "en %d.", aLaSiguiente, RADIO_POSICIONES)
                    : String.format("§cPELIGRO: del fondo de una sala al bloque "
                        + "de la SIGUIENTE hay %d, y el mod busca en %d. Un "
                        + "combate podria colocar los Pokemon en la sala de otro. "
                        + "Sube PASO_RANURA o baja horizontalSearchRadius.",
                        aLaSiguiente, RADIO_POSICIONES)), false);
            }
        }
        return n;
    }

    /**
     * El radio en el que {@code cobblemonbattlepositions} busca sus bloques.
     *
     * <p>⚠ Es su valor por defecto y el que tiene el servidor ahora mismo, leido
     * de {@code config/cobblemonbattlepositions.json}. Está aquí para poder
     * comprobar contra él; si algún día se cambia allí, hay que cambiarlo aquí —
     * y si no, esta comprobación deja de decir la verdad.
     */
    private static final int RADIO_POSICIONES = 48;

    /** Busca un bloque dentro de la caja del maestro. Devuelve el primero. */
    private static net.minecraft.util.math.BlockPos buscarBloque(
            net.minecraft.server.world.ServerWorld mundo,
            net.minecraft.util.math.BlockPos origen, int alcance,
            net.minecraft.block.Block bloque) {
        var pos = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dy = -16; dy < 120; dy++) {
            for (int dz = 0; dz < alcance; dz++) {
                for (int dx = 0; dx < alcance; dx++) {
                    pos.set(origen.getX() + dx, origen.getY() + dy,
                            origen.getZ() + dz);
                    if (mundo.getBlockState(pos).isOf(bloque)) {
                        return pos.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Quita una medalla para poder volver a retar al gimnasio.
     *
     * <p>⚠ Sin jugador, se la quita a quien ejecuta. Desde consola hay que
     * decir a quién: ahí no hay «yo», y fallar con un mensaje claro es mejor
     * que no hacer nada.
     *
     * <p>⚠⚠ Y REENVIA LA FICHA. La medalla ya no está en la base ni en la
     * caché, pero el PokePad dibuja lo que le mandaron la última vez: sin el
     * reenvío seguiría enseñándola encendida hasta reabrirlo. Es la lección del
     * 23-ago, la misma que aplica al ganarla.
     */
    private static int reiniciarMedalla(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
            ServerPlayerEntity otro)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var s = ctx.getSource();
        var g = net.pokereport.luna.gym.Gimnasio.de(
                StringArgumentType.getString(ctx, "cual"));
        if (g == null) {
            s.sendError(Text.literal("\u00a7cNo existe ese gimnasio. "
                    + "Usa \u00a7f/luna gimnasio\u00a7c para verlos."));
            return 0;
        }
        ServerPlayerEntity quien = otro != null ? otro : s.getPlayer();
        if (quien == null) {
            s.sendError(Text.literal("\u00a7cDesde consola hay que decir a quien: "
                    + "\u00a7f/luna reiniciarmedalla " + g.id() + " <jugador>"));
            return 0;
        }
        var svc = LunaEternal.medallas();
        if (svc == null) {
            s.sendError(Text.literal("\u00a7cEl sistema de medallas no esta listo."));
            return 0;
        }
        svc.quitar(quien, g, habia -> {
            net.pokereport.luna.net.Red.enviarSaldo(quien);
            s.sendFeedback(() -> Text.literal(habia
                ? "\u00a7aMedalla de \u00a7f" + g.lider() + "\u00a7a retirada a "
                  + "\u00a7f" + quien.getName().getString()
                  + "\u00a77. Ya puede volver a retarle."
                : "\u00a7e" + quien.getName().getString() + " no tenia la medalla "
                  + "de \u00a7f" + g.lider()), false);
        });
        return 1;
    }
}
