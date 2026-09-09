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
                .executes(ctx -> reiniciarInicial(ctx.getSource(), null)))

            // ⚠⚠ TODO LO DEL INICIAL, EN UN SITIO. `reiniciarinicial` se queda
            //    como atajo porque lleva semanas escrito en la documentacion y
            //    en los dedos de quien prueba, pero lo nuevo cuelga de aqui.
            .then(literal("puerta")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> estadoPuerta(ctx.getSource()))
                .then(literal("npc")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> colocarGuardian(ctx.getSource(),
                            net.pokereport.luna.puerta.PuertaNpc.ESPECIE))
                    .then(literal("quitar")
                        .executes(ctx -> quitarGuardian(ctx.getSource())))
                    .then(argument("especie", StringArgumentType.word())
                        .executes(ctx -> colocarGuardian(ctx.getSource(),
                                StringArgumentType.getString(ctx, "especie")))))
                .then(literal("activar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> activarPuerta(ctx.getSource(), true)))
                .then(literal("desactivar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> activarPuerta(ctx.getSource(), false)))
                .then(literal("reiniciar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> reiniciarPuerta(ctx.getSource(),
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador"))))))

            .then(literal("inicial")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> estadoInicial(ctx.getSource()))
                .then(literal("oak")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> colocarOak(ctx.getSource()))
                    .then(literal("quitar")
                        .executes(ctx -> quitarOak(ctx.getSource()))))
                .then(literal("reiniciar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> reiniciarInicial(ctx.getSource(), null))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> reiniciarInicial(ctx.getSource(),
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador")))))
                .then(literal("abrir")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> abrirInicial(ctx.getSource(),
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador")))))
                .then(literal("dar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .then(argument("especie",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, sb) -> {
                                for (var i : net.pokereport.luna.starter
                                        .StarterService.todos()) {
                                    sb.suggest(i.especie());
                                }
                                return sb.buildFuture();
                            })
                            .executes(ctx -> darInicial(ctx.getSource(),
                                    net.minecraft.command.argument.EntityArgumentType
                                            .getPlayer(ctx, "jugador"),
                                    com.mojang.brigadier.arguments.StringArgumentType
                                            .getString(ctx, "especie")))))))

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
                    .executes(ctx -> pendientesSantuario(ctx.getSource())))
                .then(literal("eliminar").requires(s -> s.hasPermissionLevel(4)).then(argument("nicho", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> eliminarNicho(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nicho")))))
                .then(literal("info").requires(s -> s.hasPermissionLevel(4)).then(argument("nicho", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> infoNicho(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nicho")))))
                .then(literal("recargar").requires(s -> s.hasPermissionLevel(4)).executes(ctx -> recargarSantuario(ctx.getSource())))
                .then(literal("listar").requires(s -> s.hasPermissionLevel(4)).executes(ctx -> listarNichos(ctx.getSource())))
                // ⚠ Colocar la Mew es nivel 4: es decoracion del mundo,
                //   como /luna decorar -- un moderador no construye.
                .then(literal("npc")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> npcSantuario(ctx.getSource()))
                    .then(literal("quitar")
                        .executes(ctx -> quitarNpcSantuario(ctx.getSource()))))

                // ⚠⚠ DEFINIR LOS NICHOS DE PIE DENTRO DE ELLOS. Es lo que pidio
                //    el usuario --«es mejor con un comando y la posicion del
                //    jugador definir cada punto ya que son muchisimos»-- y es
                //    nivel 4 por lo mismo que `npc`: esto escribe la geometria
                //    del mundo, no modera contenido.
                .then(literal("nicho")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> listarNichos(ctx.getSource()))
                    .then(literal("aqui")
                        .executes(ctx -> capturarNicho(ctx.getSource(), null))
                        .then(argument("nombre", StringArgumentType.greedyString())
                            .executes(ctx -> capturarNicho(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "nombre")))))
                    .then(literal("proyector")
                        .then(argument("nicho", StringArgumentType.word())
                            .suggests(LunaCommand::sugerirNichos)
                            .executes(ctx -> proyectorNicho(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "nicho")))))
                    .then(literal("renombrar")
                        .then(argument("nicho", StringArgumentType.word())
                            .suggests(LunaCommand::sugerirNichos)
                            .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> renombrarNicho(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nicho"),
                                        StringArgumentType.getString(ctx, "nombre"))))))
                    .then(literal("borrar")
                        .then(argument("nicho", StringArgumentType.word())
                            .suggests(LunaCommand::sugerirNichos)
                            .executes(ctx -> borrarNicho(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "nicho")))))
                    .then(literal("ir")
                        .then(argument("nicho", StringArgumentType.word())
                            .suggests(LunaCommand::sugerirNichos)
                            .executes(ctx -> irANicho(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "nicho")))))
                    .then(literal("ver")
                        .executes(ctx -> verNichos(ctx.getSource())))
                    .then(literal("recolocar")
                        .executes(ctx -> recolocarNichos(ctx.getSource())))))

            .then(literal("enfermera")
                .requires(s -> s.hasPermissionLevel(4))
                .then(literal("npc")
                    .executes(ctx -> npcEnfermera(ctx.getSource()))))

            .then(literal("pase")
                .requires(s -> s.hasPermissionLevel(3))
                .executes(ctx -> pase(ctx.getSource()))
                .then(literal("nueva_temporada")
                    .requires(s -> s.hasPermissionLevel(4))
                    .executes(ctx -> nuevaTemporadaPase(ctx.getSource(), 60))
                    .then(argument("dias",
                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 365))
                        .executes(ctx -> nuevaTemporadaPase(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "dias")))))
                .then(literal("xp")
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .then(argument("cantidad",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(ctx -> xpPase(ctx.getSource(),
                                    net.minecraft.command.argument.EntityArgumentType
                                            .getPlayer(ctx, "jugador"),
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "cantidad"))))))
                .then(literal("nivel")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .then(argument("n",
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .integer(0, 100))
                            .executes(ctx -> nivelPase(ctx.getSource(),
                                    net.minecraft.command.argument.EntityArgumentType
                                            .getPlayer(ctx, "jugador"),
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "n"))))))
                .then(literal("reiniciar")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> reiniciarPase(ctx.getSource(),
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador")))))
                .then(literal("via_luna")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("jugador",
                            net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> viaLunaPase(ctx.getSource(),
                                net.minecraft.command.argument.EntityArgumentType
                                        .getPlayer(ctx, "jugador"))))))

            .then(literal("torre_batalla")
                .requires(s -> s.hasPermissionLevel(4))
                .then(literal("npc")
                    .executes(ctx -> npcTorreBatalla(ctx.getSource())))
                .then(literal("holograma")
                    .then(literal("1vs1").executes(ctx -> hologramaTorre(ctx.getSource(), "1vs1")))
                    .then(literal("2vs2").executes(ctx -> hologramaTorre(ctx.getSource(), "2vs2")))
                    .then(literal("aleatorio").executes(ctx -> hologramaTorre(ctx.getSource(), "aleatorio")))
                    .then(literal("quitar").executes(ctx -> quitarHologramaTorre(ctx.getSource())))
                    .then(literal("actualizar").executes(ctx -> actualizarHologramasTorre(ctx.getSource()))))
                .then(literal("arena")
                    .executes(ctx -> arenaTorre(ctx.getSource())))
                .then(literal("tp")
                    .executes(ctx -> tpTorre(ctx.getSource())))
                .then(literal("nueva_temporada")
                    .executes(ctx -> nuevaTemporadaTorre(ctx.getSource())))
                .then(literal("dar_ronda")
                    .then(argument("jugador", net.minecraft.command.argument.EntityArgumentType.player())
                        .then(argument("modo", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .then(argument("ronda", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(ctx -> darRondaTorre(ctx.getSource(),
                                        net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "jugador"),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "modo"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ronda"))))))))

            .then(literal("autotest")
                .requires(s -> s.hasPermissionLevel(4))
                .executes(ctx -> autotest(ctx.getSource())))
        );
    }

    // ------------------------------------------------------------------

    // ------------------------------------------------------- la puerta

    private static int estadoPuerta(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
                "\u00a76La puerta\u00a78 \u00b7 \u00a7fprotocolo exigido: \u00a7e"
                + net.pokereport.luna.puerta.Puerta.PROTOCOLO), false);
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) {
            var v = net.pokereport.luna.puerta.Puerta.veredicto(p);
            src.sendFeedback(() -> Text.literal("\u00a77tu cliente: "
                    + (v.pasa() ? "\u00a7aal dia" : "\u00a7c" + v.fallo())), false);
            src.sendFeedback(() -> Text.literal("\u00a77est\u00e1s en el lobby: "
                    + (net.pokereport.luna.puerta.Puerta.enElLobby(p)
                            ? "\u00a7es\u00ed" : "\u00a77no")), false);
        }
        var svc = LunaEternal.puerta();
        if (svc != null) {
            src.sendFeedback(() -> Text.literal("\u00a78" + svc.enCache()
                    + " jugadores en cache"), false);
        }
        src.sendFeedback(() -> Text.literal("\u00a77activa: "
                + (net.pokereport.luna.puerta.Puerta.activa()
                        ? "\u00a7as\u00ed" : "\u00a7cno")), false);

        // ⚠⚠ EL ESTADO DEL RECORTE DE JUGADORES SE MIRA AQUI, y hay que mirarlo
        //    CON GENTE DENTRO: el mixin se enciende la primera vez que corre, y
        //    con el servidor vacio no ha corrido nunca. Un «no» con cero
        //    jugadores no significa nada; un «no» con gente dando vueltas
        //    significa que el mixin NO se aplico y que el recorte no existe.
        src.sendFeedback(() -> Text.literal("\u00a76Visibilidad\u00a78 \u00b7 "
                + "\u00a7flobby \u00a7e"
                + net.pokereport.luna.world.VisibilidadJugadores.TOPE_LOBBY
                + "\u00a7f \u00b7 ciudadela \u00a7e"
                + net.pokereport.luna.world.VisibilidadJugadores.TOPE_CIUDADELA), false);
        src.sendFeedback(() -> Text.literal("\u00a77el recorte ha corrido: "
                + (net.pokereport.luna.world.VisibilidadJugadores.vivo()
                        ? "\u00a7as\u00ed" : "\u00a7etodav\u00eda no")), false);
        return 1;
    }

    /**
     * ⚠ Se exige estar EN EL LOBBY, y no es burocracia: el guardian colocado en
     *   otra dimension seria un adorno que no abre ninguna puerta, y el operador
     *   creeria que ya esta hecho. Un comando que acepta lo que no sirve es un
     *   comando que miente.
     */
    private static int colocarGuardian(ServerCommandSource src, String especie) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        if (!net.pokereport.luna.world.LunaDimensions.LOBBY.equals(
                p.getServerWorld().getRegistryKey())) {
            src.sendError(Text.literal("\u00a7cEl guardi\u00e1n va en el LOBBY. "
                    + "Ve con \u00a7f/luna ir lobby\u00a7c y vuelve a intentarlo."));
            return 0;
        }
        boolean ok = net.pokereport.luna.puerta.PuertaNpc.colocar(
                p.getServerWorld(), p.getPos(), p.getYaw(), especie);
        if (!ok) {
            src.sendError(Text.literal("\u00a7cNo se pudo colocar: \u00bfexiste la "
                    + "especie \u00ab" + especie + "\u00bb?"));
            return 0;
        }
        src.sendFeedback(() -> Text.literal(
                "\u00a7aGuardi\u00e1n colocado\u00a78 (" + especie + ")"), false);
        return 1;
    }

    /**
     * ⚠⚠⚠ ENCENDERLA SIN GUARDIAN ENCIERRA A TODO EL QUE ENTRE, asi que el
     *    comando lo COMPRUEBA antes en vez de fiarse: la dimension del lobby no
     *    se sale andando y el candado de {@code Traslado} es justo lo que
     *    impide salir. Un aviso en la documentacion no habria evitado nada.
     *    ⚠ El barrido solo ve entidades EN CHUNKS CARGADOS, asi que se exige
     *      estar en el lobby --y por tanto tenerlo cargado-- para encenderla.
     *      Es la leccion de los cuatro diagnosticos con {@code @e}.
     */
    private static int activarPuerta(ServerCommandSource src, boolean valor) {
        ServerPlayerEntity p = src.getPlayer();
        if (valor) {
            if (p == null || !net.pokereport.luna.world.LunaDimensions.LOBBY.equals(
                    p.getServerWorld().getRegistryKey())) {
                src.sendError(Text.literal("§cPara encenderla tienes que estar "
                        + "EN EL LOBBY: hay que comprobar que el guardián "
                        + "está puesto, y solo se ve si su chunk está cargado."));
                return 0;
            }
            int guardianes = net.pokereport.luna.puerta.PuertaNpc.contar(
                    p.getServerWorld(), p.getPos(), 64.0);
            if (guardianes == 0) {
                src.sendError(Text.literal("§cNO HAY GUARDIÁN cerca. "
                        + "Si enciendes la puerta ahora, cada jugador nuevo se "
                        + "queda ENCERRADO en el lobby. Colócalo primero con "
                        + "§f/luna puerta npc§c."));
                return 0;
            }
        }
        try {
            net.pokereport.luna.puerta.Puerta.activar(valor);
        } catch (java.io.IOException e) {
            src.sendError(Text.literal("§cNo se pudo guardar: " + e.getMessage()));
            return 0;
        }
        src.sendFeedback(() -> Text.literal(valor
                ? "§aPuerta ACTIVA§7: los jugadores nuevos empiezan en el lobby."
                : "§7Puerta apagada: nadie pasa por el lobby."), true);
        return 1;
    }

    /**
     * Devuelve a alguien a la casilla de salida.
     *
     * <p>&#9888;&#9888; HACE FALTA PARA PODER PROBAR NADA. La cuenta del
     * operador esta marcada como cruzada por el relleno de la V036 --y tiene que
     * estarlo: sin ese relleno, todos los que ya jugaban habrian aparecido en el
     * lobby-- asi que sin este comando la unica forma de recorrer la puerta como
     * un jugador nuevo es entrar con OTRA CUENTA.
     *
     * <p>&#9888; No hace falta echarle ni pedirle que reconecte: {@code vigilar}
     * corre cada segundo, ve que ya no consta como cruzado y lo lleva al lobby
     * solo. Si la puerta esta apagada no se movera nadie, y se dice.
     */
    private static int reiniciarPuerta(ServerCommandSource src, ServerPlayerEntity quien) {
        var svc = LunaEternal.puerta();
        if (svc == null) {
            src.sendError(Text.literal("\u00a7cLa puerta no esta arrancada."));
            return 0;
        }
        var perfil = quien.getGameProfile();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(perfil.getId(), perfil.getName());
                svc.reiniciar(perfil.getId(), id);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo reiniciar la puerta de {}",
                        perfil.getName(), e);
                return;
            }
            src.getServer().execute(() -> src.sendFeedback(() -> Text.literal(
                    "\u00a7a" + perfil.getName() + " vuelve a ser un jugador nuevo"
                    + (net.pokereport.luna.puerta.Puerta.activa()
                            ? "\u00a77: en un segundo estara en el lobby."
                            : "\u00a7e, pero la puerta esta APAGADA: no se movera.")),
                    true));
        });
        return 1;
    }

    private static int quitarGuardian(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        int n = net.pokereport.luna.puerta.PuertaNpc.quitar(
                p.getServerWorld(), p.getPos(), 16.0);
        src.sendFeedback(() -> Text.literal("\u00a77Quitadas \u00a7f" + n
                + "\u00a77 entidades."), false);
        return 1;
    }

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

    /**
     * COLOCA A OAK donde esta quien escribe.
     *
     * <p>&#9888; Igual que las paradas y los lideres: <b>borra antes de poner</b>,
     * porque a Oak no se le puede pegar ni matar y uno de mas se quedaria en la
     * plaza para siempre.
     */
    private static int colocarOak(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        if (!net.pokereport.luna.starter.OakNpc.colocar(jugador)) {
            origen.sendError(Text.literal(
                    "No se pudo colocar. Tiene que ser DENTRO de la ciudadela, y el "
                    + "entrenador "
                    + net.pokereport.luna.starter.OakNpc.ENTRENADOR
                    + " tiene que existir en los datos de rctmod."));
            return 0;
        }
        origen.sendFeedback(() -> Text.literal(
                "\u00a7aProfesor Oak colocado, mirando a donde tu mirabas. "
                + "\u00a77Clic derecho para probarlo."), true);
        return 1;
    }

    private static int quitarOak(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal("Este comando se escribe desde el juego."));
            return 0;
        }
        int n = net.pokereport.luna.starter.OakNpc.quitar(
                jugador.getServerWorld(), jugador.getPos(), 12.0);
        origen.sendFeedback(() -> Text.literal(
                "\u00a77Quitadas \u00a7f" + n + "\u00a77 entidades de Oak en 12 bloques."),
                true);
        return 1;
    }

    /**
     * QUE HAY MONTADO AHORA MISMO, sin tocar nada.
     *
     * <p>&#9888;&#9888; Contesta las dos preguntas que de verdad se hacen cuando
     * «el inicial no funciona»: <b>&#191;existe el entrenador de Oak?</b> --si no,
     * no hay puerta-- y <b>&#191;ya elegi?</b> --si si, el clic derecho contesta
     * con una frase y no abre nada, que es lo correcto y parece roto--.
     */
    private static int estadoInicial(ServerCommandSource origen) {
        var jugador = origen.getPlayer();
        boolean oak = net.pokereport.luna.starter.OakNpc.idValido();
        var iniciales = net.pokereport.luna.starter.StarterService.todos();
        origen.sendFeedback(() -> Text.literal(
                "\u00a76Inicial \u00a78\u00b7 \u00a7f" + iniciales.size()
                + " \u00a77opciones \u00a78\u00b7 \u00a77entrenador de Oak: "
                + (oak ? "\u00a7aexiste" : "\u00a7cNO EXISTE")
                + " \u00a78(" + net.pokereport.luna.starter.OakNpc.ENTRENADOR + ")"),
                false);
        if (jugador == null) {
            return 1;
        }
        LunaEternal.submit(() -> {
            String linea;
            try {
                long id = LunaEternal.players().resolve(
                        jugador.getUuid(), jugador.getName().getString());
                linea = net.pokereport.luna.starter.StarterService.yaEligio(id)
                        ? "\u00a77Tu ya elegiste: el clic derecho en Oak te dira que si."
                        : "\u00a77Tu NO has elegido: el clic derecho te abrira la pantalla.";
            } catch (Exception e) {
                linea = "\u00a7cNo se pudo consultar tu estado: " + e;
            }
            final String f = linea;
            origen.getServer().execute(() ->
                    origen.sendFeedback(() -> Text.literal(f), false));
        });
        return 1;
    }

    /** Le abre la pantalla a alguien sin que tenga que ir a ver a Oak. */
    private static int abrirInicial(ServerCommandSource origen,
                                    ServerPlayerEntity destino) {
        net.pokereport.luna.net.Red.enviarAbrirInicial(destino);
        origen.sendFeedback(() -> Text.literal(
                "\u00a7aAbierta la eleccion a " + destino.getName().getString()
                + ". \u00a77Si ya eligio, la pantalla lo dira."), true);
        return 1;
    }

    /**
     * Le da un inicial concreto, saltandose la pantalla.
     *
     * <p>&#9888; Pasa por {@code conceder}, o sea por la misma puerta que la
     * pantalla: marca primero, entrega despues y deshace si falla. Entregar el
     * Pokemon a mano por otro camino dejaria la marca sin poner, y el jugador
     * podria elegir otra vez.
     */
    private static int darInicial(ServerCommandSource origen,
                                  ServerPlayerEntity destino, String especie) {
        if (net.pokereport.luna.starter.StarterService.porEspecie(especie) == null) {
            origen.sendError(Text.literal(especie + " no es un inicial. "
                    + "Escribe /luna inicial para ver cuantos hay."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                        destino.getUuid(), destino.getName().getString());
                net.pokereport.luna.starter.StarterService.conceder(
                        destino, id, especie,
                        () -> net.pokereport.luna.net.Red.refrescarInicial(destino));
                origen.getServer().execute(() -> origen.sendFeedback(() -> Text.literal(
                        "\u00a7aEntregado \u00a7f" + especie + "\u00a7a a "
                        + destino.getName().getString()), true));
            } catch (Exception e) {
                LunaEternal.LOG.warn("No se pudo dar el inicial: {}", e.toString());
            }
        });
        return 1;
    }

    /**
     * Borra la marca del inicial y su mision.
     *
     * @param destino a quien, o {@code null} para quien escribe
     */
    private static int reiniciarInicial(ServerCommandSource origen,
                                        ServerPlayerEntity destino) {
        var jugador = destino != null ? destino : origen.getPlayer();
        if (jugador == null) {
            origen.sendError(Text.literal(
                    "Desde consola hace falta el nombre: "
                    + "/luna inicial reiniciar <jugador>"));
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
                        + " mision(es). \u00a77Vuelve a hablar con Oak."), false));
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

    /** Coloca la Mew del santuario donde esta quien lo ejecuta. */
    private static int npcSantuario(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        // ⚠ Es decoracion del MUNDO (crear la entidad es trabajo de tick), y el
        //   comando ya corre en el hilo del servidor: nada de executor aqui.
        if (!net.pokereport.luna.santuario.SantuarioNpc.colocar(p)) {
            src.sendError(Text.literal("Solo se puede colocar en la ciudadela."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal(
                "§aMew del santuario colocado. Tocarla abre la app."), false);
        return 1;
    }

    private static int npcEnfermera(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(net.minecraft.text.Text.literal("Solo desde el juego."));
            return 0;
        }
        
        net.pokereport.luna.heal.EnfermeraService.colocarEnfermera(p);
        p.sendMessage(net.minecraft.text.Text.literal("§aEnfermera Joy colocada en el centro Pokémon. ¡Quedó invulnerable y estática!"), false);
        return 1;
    }

    private static int npcTorreBatalla(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(net.minecraft.text.Text.literal("Solo desde el juego."));
            return 0;
        }
        
        net.pokereport.luna.torrebatalla.TorreNpc.colocarNpc(p);
        p.sendMessage(net.minecraft.text.Text.literal("¡NPC de la Torre de Batalla colocado. Quedó invulnerable y estático!"), false);
        return 1;
    }

    private static int hologramaTorre(ServerCommandSource src, String modo) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        net.pokereport.luna.torrebatalla.TorreRanking.colocarHolograma(p.getServerWorld(), p.getPos(), modo);
        p.sendMessage(Text.literal("§aHolograma de ranking para §e" + modo + " §acolocado exitosamente."), false);
        return 1;
    }

    private static int quitarHologramaTorre(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        int quitados = net.pokereport.luna.torrebatalla.TorreRanking.quitarCercano(p.getServerWorld(), p.getPos());
        p.sendMessage(Text.literal("§eSe han retirado §f" + quitados + " §eholograma(s) de la torre cercanos."), false);
        return 1;
    }

    private static int actualizarHologramasTorre(ServerCommandSource src) {
        net.pokereport.luna.torrebatalla.TorreRanking.actualizarHologramasEnMundos(src.getServer());
        src.sendFeedback(() -> Text.literal("§aTodos los hologramas de ranking de la torre han sido actualizados."), false);
        return 1;
    }

    private static int arenaTorre(ServerCommandSource src) {
        net.minecraft.server.world.ServerWorld mundoTorre = src.getServer().getWorld(net.pokereport.luna.world.LunaDimensions.TORRE);
        if (mundoTorre == null) {
            src.sendError(Text.literal("§cDimensión lunaeternal:torre no encontrada."));
            return 0;
        }
        net.pokereport.luna.torrebatalla.TorreBatallaService.asegurarBloquesArena(mundoTorre);
        src.sendFeedback(() -> Text.literal("§aBloques de posiciones de combate asegurados en la plataforma."), false);
        return 1;
    }

    private static int tpTorre(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        net.minecraft.server.world.ServerWorld mundoTorre = src.getServer().getWorld(net.pokereport.luna.world.LunaDimensions.TORRE);
        if (mundoTorre == null) {
            src.sendError(Text.literal("§cDimensión lunaeternal:torre no encontrada."));
            return 0;
        }
        var pos = net.pokereport.luna.torrebatalla.TorreBatallaService.POS_JUGADOR;
        p.teleport(mundoTorre, pos.x, pos.y, pos.z,
                net.pokereport.luna.torrebatalla.TorreBatallaService.YAW_JUGADOR, 0f);
        p.sendMessage(Text.literal("§aTeletransportado a la plataforma de la Torre de Batalla."), false);
        return 1;
    }

    // ======================= PASE DE BATALLA (D-045) =======================

    /** Que dice el pase ahora mismo: temporada, curva y tope. */
    private static int pase(ServerCommandSource src) {
        var svc = LunaEternal.pase();
        if (svc == null) {
            src.sendError(Text.literal("\u00a7cEl pase no esta cargado."));
            return 0;
        }
        // \u26a0 La base se lee en el hilo de E/S y la respuesta se manda por el del
        //   servidor: `sendFeedback` toca el estado del comando, y eso no se
        //   hace desde otro hilo.
        LunaEternal.submit(() -> {
            try {
                var t = svc.temporada();
                String linea1 = String.format(
                        "\u00a76Pase de Batalla \u00a78\u00b7 \u00a7fTemporada %d, quedan %d dias",
                        t.numero(), t.diasRestantes());
                String linea2 = String.format(
                        "\u00a77%d niveles \u00a78\u00b7 \u00a77%,d XP en total \u00a78\u00b7 "
                        + "\u00a77tope %,d/dia \u00a78\u00b7 \u00a77minimo %d dias",
                        net.pokereport.luna.pase.PaseNivel.MAX,
                        net.pokereport.luna.pase.PaseNivel.total(),
                        net.pokereport.luna.pase.PaseNivel.TOPE_DIARIO,
                        net.pokereport.luna.pase.PaseNivel.diasMinimos());
                String linea3 = String.format(
                        "\u00a77Precio %,d LunaCoins \u00a78\u00b7 \u00a77%d Pokemon "
                        + "\u00a78\u00b7 \u00a77%d legendarias, %d epicas",
                        net.pokereport.luna.pase.PaseCatalogo.PRECIO,
                        net.pokereport.luna.pase.PaseCatalogo.cuantosPokemon(),
                        net.pokereport.luna.pase.PaseCatalogo.cuantosDe(
                                net.pokereport.luna.pase.Recompensa.Rareza.LEGENDARIA),
                        net.pokereport.luna.pase.PaseCatalogo.cuantosDe(
                                net.pokereport.luna.pase.Recompensa.Rareza.EPICA));
                src.getServer().execute(() -> {
                    src.sendFeedback(() -> Text.literal(linea1), false);
                    src.sendFeedback(() -> Text.literal(linea2), false);
                    src.sendFeedback(() -> Text.literal(linea3), false);
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo leer el pase", e);
            }
        });
        return 1;
    }

    /**
     * Rota la temporada.
     *
     * <p>&#9888;&#9888; NO BORRA NADA: las filas viejas llevan el numero de
     * temporada en la clave. Y avisa por difusion, como la Torre &mdash; una
     * temporada que empieza sin que nadie se entere no la juega nadie.
     */
    private static int nuevaTemporadaPase(ServerCommandSource src, int dias) {
        var svc = LunaEternal.pase();
        if (svc == null) {
            src.sendError(Text.literal("\u00a7cEl pase no esta cargado."));
            return 0;
        }
        LunaEternal.submit(() -> {
            try {
                var t = svc.nuevaTemporada(dias);
                src.getServer().execute(() -> {
                    src.getServer().getPlayerManager().broadcast(Text.literal(
                            "\u00a76\u00a7l[PASE DE BATALLA] \u00a7eEmpieza la "
                            + "\u00a76\u00a7lTemporada #" + t.numero()
                            + "\u00a7e. " + dias + " dias para llegar al nivel "
                            + net.pokereport.luna.pase.PaseNivel.MAX + "."), false);
                    for (ServerPlayerEntity p
                            : src.getServer().getPlayerManager().getPlayerList()) {
                        net.pokereport.luna.net.Red.enviarPase(p);
                    }
                });
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo rotar la temporada del pase", e);
            }
        });
        return 1;
    }

    /** Da XP del pase a mano. Para probar sin jugar sesenta dias. */
    /**
     * Da XP del pase a mano. Para probar sin jugar cuarenta y cinco d\u00edas.
     *
     * <h2>\u26a0\u26a0\u26a0 ESTE COMANDO NO HAC\u00cdA NADA, Y LA CAUSA ERA CORRECTA</h2>
     *
     * Llamaba a {@code Pase.ganar}, que <b>descarta a quien est\u00e1 en creativo</b>
     * \u2014 un constructor con Axiom rompiendo bloques no es un jugador ganando XP.
     * El filtro est\u00e1 bien puesto, pero <b>quien prueba el pase es un operador, y
     * un operador est\u00e1 en creativo</b>: la \u00fanica forma de probarlo chocaba con
     * la \u00fanica protecci\u00f3n del sistema. El comando dec\u00eda \u00abhecho\u00bb y no pasaba nada.
     *
     * <p>\u26a0\u26a0 Y aunque el filtro no hubiera estado, <b>habr\u00eda seguido pareciendo
     * roto</b>: pedir 50.000 con un tope de 1.200 concede 1.200. Por eso ahora
     * se dice <b>lo que de verdad ha entrado</b> y, si se ha topado, por qu\u00e9 \u2014
     * y se remite a {@code /luna pase nivel}, que es lo que sirve para llegar
     * al final del carril.
     */
    private static int xpPase(ServerCommandSource src, ServerPlayerEntity p, int xp) {
        net.pokereport.luna.pase.Pase.ganar(p, xp, "comando", g -> {
            String base = String.format("\u00a7a+%,d XP de pase a %s \u00a78(nivel %d)",
                    g.concedida(), p.getName().getString(), g.nivelDespues());
            src.sendFeedback(() -> Text.literal(base), false);
            if (g.topado()) {
                src.sendFeedback(() -> Text.literal(String.format(
                        "\u00a7e  se pidieron %,d y el TOPE DIARIO dejo entrar %,d. "
                        + "\u00a77Para saltar al final: /luna pase nivel <jugador> <n>",
                        g.pedida(), g.concedida())), false);
            }
        });
        return 1;
    }

    /** Pone a alguien en un nivel exacto del pase. Nivel 4. */
    private static int nivelPase(ServerCommandSource src, ServerPlayerEntity p,
                                 int nivel) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                        p.getUuid(), p.getName().getString());
                LunaEternal.pase().fijarNivel(id, nivel);
                net.pokereport.luna.net.Red.enviarPase(p);
                src.getServer().execute(() -> src.sendFeedback(
                        () -> Text.literal("\u00a7a" + p.getName().getString()
                                + " \u00a77pasa al nivel \u00a7f" + nivel + "\u00a77 del pase"),
                        false));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo fijar el nivel del pase", e);
            }
        });
        return 1;
    }

    /** Devuelve el pase de alguien a cero, sin rotar la temporada. Nivel 4. */
    private static int reiniciarPase(ServerCommandSource src, ServerPlayerEntity p) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                        p.getUuid(), p.getName().getString());
                LunaEternal.pase().reiniciar(id);
                net.pokereport.luna.net.Red.enviarPase(p);
                src.getServer().execute(() -> src.sendFeedback(
                        () -> Text.literal("\u00a7aPase de " + p.getName().getString()
                                + " \u00a77reiniciado \u00a78(XP, pase y reclamos de esta "
                                + "temporada)"), false));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo reiniciar el pase", e);
            }
        });
        return 1;
    }

    /**
     * Da la via Luna sin cobrar.
     *
     * <p>&#9888; Existe para los DOS casos que van a pasar de verdad: un
     * reembolso y un premio de evento. Sin comando, la unica forma seria tocar
     * la base a mano.
     */
    private static int viaLunaPase(ServerCommandSource src, ServerPlayerEntity p) {
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(
                        p.getUuid(), p.getName().getString());
                LunaEternal.pase().darLuna(id);
                net.pokereport.luna.net.Red.enviarPase(p);
                // \u26a0 `sendFeedback` toca el estado del comando, y eso no se hace
                //   desde el hilo de E/S: se compone aqu\u00ed y se manda por el del
                //   servidor.
                src.getServer().execute(() -> src.sendFeedback(
                        () -> Text.literal("\u00a7aVia Luna concedida a "
                                + p.getName().getString() + " \u00a78(sin cobrar)"),
                        false));
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo dar la via Luna", e);
            }
        });
        return 1;
    }

    private static int nuevaTemporadaTorre(ServerCommandSource src) {
        int nueva = net.pokereport.luna.torrebatalla.TorreRecompensas.avanzarTemporada();
        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            net.pokereport.luna.net.Red.enviarEstadoRecompensasTorre(p);
        }
        src.getServer().getPlayerManager().broadcast(Text.literal(
                "§6§l[TORRE DE BATALLA] §e¡Ha comenzado la §6§lTemporada #" + nueva + "§e! Las recompensas han sido reiniciadas. ¡A luchar!"), false);
        return 1;
    }

    private static int darRondaTorre(ServerCommandSource src, ServerPlayerEntity p, String modo, int ronda) {
        String modoKey = switch (modo.toLowerCase()) {
            case "1vs1", "1v1" -> net.pokereport.luna.torrebatalla.TorreRanking.MODO_1VS1;
            case "2vs2", "2v2" -> net.pokereport.luna.torrebatalla.TorreRanking.MODO_2VS2;
            default -> net.pokereport.luna.torrebatalla.TorreRanking.MODO_ALEATORIO;
        };
        net.pokereport.luna.torrebatalla.TorreRanking.actualizarRonda(src.getServer(), modoKey, p.getName().getString(), ronda);
        net.pokereport.luna.torrebatalla.TorreRecompensas.registrarVictoria(p.getUuid(), ronda);
        net.pokereport.luna.net.Red.enviarEstadoRecompensasTorre(p);
        src.sendFeedback(() -> Text.literal("§aRonda " + ronda + " asignada a " + p.getName().getString() + " en modo " + modoKey), false);
        return 1;
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

    /** Elimina la reclamacion de un nicho (lo deja libre). */
    private static int eliminarNicho(ServerCommandSource src, String nichoId) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            String motivo = LunaEternal.santuario().eliminar(nichoId);
            if (motivo == null) {
                net.pokereport.luna.santuario.SantuarioProteccion.recargar();
                reply(p, "§aNicho '" + nichoId + "' liberado.");
            } else {
                reply(p, "§cNo se pudo eliminar: " + motivo);
            }
        });
        return 1;
    }

    /** Muestra la informacion de un nicho. */
    private static int infoNicho(ServerCommandSource src, String nichoId) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            var info = LunaEternal.santuario().info(nichoId);
            if (info == null) {
                reply(p, "§cNicho '" + nichoId + "' no existe.");
            } else {
                reply(p, info);
            }
        });
        return 1;
    }

    /** Recarga la config de nichos sin reiniciar. */
    private static int recargarSantuario(ServerCommandSource src) {
        // ⚠ La config es un fichero local pequeno, se lee en el hilo del
        //   servidor. Lo que NO puede ir ahi es la parte de base de datos: de
        //   eso se encarga `aplicarSantuario`.
        int n;
        try {
            n = net.pokereport.luna.santuario.SantuarioProteccion.catalogo().recargar();
        } catch (Exception e) {
            src.sendError(Text.literal("§cError al recargar: " + e.getMessage()));
            return 0;
        }
        aplicarSantuario(src.getServer());
        final int total = n;
        src.sendFeedback(() -> Text.literal(
                "§aSantuario recargado: " + total + " nichos."), false);
        return 1;
    }

    /**
     * LO QUE HAY QUE HACER DESPUES DE QUE CAMBIE LA GEOMETRIA DE LOS NICHOS.
     *
     * <h2>⚠⚠⚠ LA MITAD DE ESTO ESTABA EN EL HILO DEL SERVIDOR, Y ES BASE DE DATOS</h2>
     *
     * {@code recargarSantuario} llamaba a {@code garantizarNichos} y a
     * {@code SantuarioProteccion.recargar()} directamente desde el comando, o
     * sea <b>dos consultas a MariaDB en el hilo del tick</b> -- la regla numero
     * uno de este proyecto. No daba error porque el fichero es pequeño y la
     * consulta rapida: lo que da es un servidor que se para el dia que la base
     * tarde, y eso se lee como «lag», no como este fallo.
     *
     * <p>⚠⚠ <b>Y HAY QUE REENVIAR EL ESTADO.</b> Es la leccion de los clanes:
     * <i>el estado no es de quien lo mira</i>. Quien tuviera la pantalla del
     * Santuario abierta mientras se captura un nicho seguiria viendo la lista
     * vieja --sin el nicho nuevo, o con el borrado-- hasta reabrir, y eso se
     * comporta como debe, que es lo que despista.
     */
    private static void aplicarSantuario(net.minecraft.server.MinecraftServer servidor) {
        var ids = net.pokereport.luna.santuario.SantuarioProteccion.catalogo().todos()
                .stream()
                .map(net.pokereport.luna.santuario.NichoCatalogo.Nicho::id)
                .toList();
        LunaEternal.submit(() -> {
            try {
                LunaEternal.santuario().garantizarNichos(ids);
            } catch (Exception e) {
                LunaEternal.LOG.error("Santuario: no se pudieron crear las filas", e);
            }
            net.pokereport.luna.santuario.SantuarioProteccion.recargar();
            net.pokereport.luna.net.Red.enviarSantuarioATodos(servidor);
        });
    }

    /** Lista todos los nichos y su estado. */
    private static int listarNichos(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        LunaEternal.submit(() -> {
            var catalogo = net.pokereport.luna.santuario.SantuarioProteccion.catalogo();
            if (!catalogo.hay()) {
                reply(p, "§7No hay nichos configurados.");
                return;
            }
            try {
                var nichos = LunaEternal.santuario().nichos();
                var sb = new StringBuilder("§7Nichos (" + catalogo.todos().size() + "):\n");
                for (var cn : catalogo.todos()) {
                    net.pokereport.luna.santuario.SantuarioService.Nicho sn = null;
                    for (var n : nichos) {
                        if (n.id().equals(cn.id())) {
                            sn = n;
                            break;
                        }
                    }
                    sb.append("§f ").append(cn.id());
                    if (sn != null && sn.ownerId() != null && !sn.libre(System.currentTimeMillis())) {
                        sb.append(" §a[ocupado]");
                        if (sn.permanente()) sb.append(" §6permanente");
                    } else {
                        sb.append(" §7[libre]");
                    }
                    // ⚠ LAS COORDENADAS SALEN AQUI porque esta lista es la que
                    //   usa quien esta construyendo: sin ellas, un nicho que
                    //   quedo dos bloques corrido no se distingue del bueno --
                    //   y saber CUAL revisar es la mitad del trabajo.
                    sb.append(" §8").append(cn.nombre())
                      .append("\n §7  caja ").append(caja(cn))
                      .append(" §7· holo §b").append(punto(cn.proyector()));
                    sb.append("\n");
                }
                reply(p, sb.toString().trim());
            } catch (Exception e) {
                reply(p, "§cError al leer los nichos: " + e.getMessage());
            }
        });
        return 1;
    }

    // =====================================================================
    // DEFINIR LOS NICHOS ANDANDO POR ELLOS
    //
    // ⚠⚠ Peticion del usuario, con su motivo dentro: «falta colocar el lugar de
    //    cada holografica y todo eso, asi que es mejor con un comando y la
    //    posicion del jugador definir cada punto ya que son muchisimos».
    //
    // ⚠⚠⚠ Y LA HOLOGRAFICA NO TIENE COORDENADA PROPIA: el cliente dibuja la foto
    //    a `proyector.y + 1,55`, asi que colocar el proyector ES colocar el
    //    holograma. Un cuarto punto que declarar seria un cuarto punto que
    //    puede dejar de cuadrar con los otros tres.
    // =====================================================================

    /** Autocompleta con los nichos que hay en la config. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            sugerirNichos(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                          com.mojang.brigadier.suggestion.SuggestionsBuilder sb) {
        for (var n : net.pokereport.luna.santuario.SantuarioProteccion.catalogo().todos()) {
            sb.suggest(n.id());
        }
        return sb.buildFuture();
    }

    /**
     * CAPTURA UN NICHO DONDE ESTA EL JUGADOR.
     *
     * <p>⚠ El centro es <b>el bloque sobre el que estan los pies</b>, no el que
     * pisa: {@code getBlockPos()} de un jugador de pie ya devuelve el bloque de
     * aire donde esta el cuerpo, que es el suelo del nicho. Es lo que hace que
     * ponerse en medio del nicho y teclear el comando baste.
     *
     * <p>⚠⚠ Y NO SE ESCRIBE NADA SI NO VALIDA. La caja nueva puede solapar con
     * una que ya estaba --dos nichos pegados a dos bloques-- y eso, escrito en
     * la config, <b>deja el servidor sin arrancar</b>. Aqui se dice y no se
     * toca el disco.
     */
    private static int capturarNicho(ServerCommandSource src, String nombreDado) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego: hace falta tu posicion."));
            return 0;
        }
        if (!net.pokereport.luna.world.LunaDimensions.CIUDADELA.equals(
                p.getServerWorld().getRegistryKey())) {
            src.sendError(Text.literal("\u00a7cLos nichos van en la CIUDADELA. "
                    + "\u00a77Estas en otra dimension."));
            return 0;
        }
        var catalogo = net.pokereport.luna.santuario.SantuarioProteccion.catalogo();
        var lista = net.pokereport.luna.santuario.NichoEditor.copia(catalogo);
        String id = net.pokereport.luna.santuario.NichoEditor.siguienteId(lista);
        if (id == null) {
            src.sendError(Text.literal("\u00a7cNo quedan identificadores libres."));
            return 0;
        }
        String nombre = nombreDado == null || nombreDado.isBlank()
                ? net.pokereport.luna.santuario.NichoEditor.nombrePara(id)
                : nombreDado.trim();

        var nicho = net.pokereport.luna.santuario.NichoEditor.capturar(
                p.getBlockPos(), id, nombre);
        lista.add(nicho);
        String malo = net.pokereport.luna.santuario.NichoEditor.guardar(lista);
        if (malo != null) {
            src.sendError(Text.literal("\u00a7cNo se guardo nada: \u00a7f" + malo));
            return 0;
        }
        recargarCatalogo(src);
        pintarNicho(p.getServerWorld(), nicho, 3);
        src.sendFeedback(() -> Text.literal(
                "\u00a7aNicho \u00a7f" + id + "\u00a7a capturado \u00a77(" + nombre + ")\n"
                + "\u00a77  caja " + caja(nicho) + "\n"
                + "\u00a77  holografica sobre \u00a7b" + punto(nicho.proyector())
                + "\u00a77 -- pon ahi el proyector"), true);
        return 1;
    }

    /**
     * MUEVE LA HOLOGRAFICA DE UN NICHO AL BLOQUE QUE MIRAS.
     *
     * <p>⚠⚠ Existe porque la forma capturada es <b>una suposicion razonable</b>,
     * no una verdad: si un nicho se construyo con el pedestal en otro sitio, el
     * proyector va donde lo pusiera el constructor. Se apunta al bloque y se
     * teclea, que es la unica forma de no volver a escribir coordenadas.
     *
     * <p>⚠ Y tiene que caer <b>dentro de su caja</b> -- lo exige
     * {@code NichoCatalogo.validar}, porque si no se protegeria un 3x3 que no es
     * el que tiene el proyector y el memorial se abriria desde un bloque que no
     * se ve. Aqui se avisa antes de escribir.
     */
    private static int proyectorNicho(ServerCommandSource src, String id) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        var lista = net.pokereport.luna.santuario.NichoEditor.copia(
                net.pokereport.luna.santuario.SantuarioProteccion.catalogo());
        int i = indiceDe(lista, id);
        if (i < 0) {
            src.sendError(Text.literal("\u00a7cNo hay ningun nicho \u00a7f" + id));
            return 0;
        }
        var golpe = p.raycast(8.0, 0f, false);
        if (!(golpe instanceof net.minecraft.util.hit.BlockHitResult bloque)
                || golpe.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            src.sendError(Text.literal("\u00a7cApunta al bloque del proyector "
                    + "\u00a77(a menos de 8 bloques)\u00a7c y vuelve a intentarlo."));
            return 0;
        }
        var viejo = lista.get(i);
        var nuevo = new net.pokereport.luna.santuario.NichoCatalogo.Nicho(
                viejo.id(), viejo.nombre(), viejo.min(), viejo.max(),
                bloque.getBlockPos());
        lista.set(i, nuevo);
        String malo = net.pokereport.luna.santuario.NichoEditor.guardar(lista);
        if (malo != null) {
            src.sendError(Text.literal("\u00a7cNo se guardo nada: \u00a7f" + malo));
            return 0;
        }
        recargarCatalogo(src);
        pintarNicho(p.getServerWorld(), nuevo, 3);
        src.sendFeedback(() -> Text.literal(
                "\u00a7aHolografica de \u00a7f" + id + "\u00a7a en \u00a7b"
                + punto(nuevo.proyector())), true);
        return 1;
    }

    /** Le cambia el nombre visible a un nicho. */
    private static int renombrarNicho(ServerCommandSource src, String id, String nombre) {
        var lista = net.pokereport.luna.santuario.NichoEditor.copia(
                net.pokereport.luna.santuario.SantuarioProteccion.catalogo());
        int i = indiceDe(lista, id);
        if (i < 0) {
            src.sendError(Text.literal("\u00a7cNo hay ningun nicho \u00a7f" + id));
            return 0;
        }
        var viejo = lista.get(i);
        lista.set(i, new net.pokereport.luna.santuario.NichoCatalogo.Nicho(
                viejo.id(), nombre.trim(), viejo.min(), viejo.max(), viejo.proyector()));
        String malo = net.pokereport.luna.santuario.NichoEditor.guardar(lista);
        if (malo != null) {
            src.sendError(Text.literal("\u00a7cNo se guardo nada: \u00a7f" + malo));
            return 0;
        }
        recargarCatalogo(src);
        src.sendFeedback(() -> Text.literal(
                "\u00a7a" + id + " pasa a llamarse \u00a7f" + nombre.trim()), true);
        return 1;
    }

    /**
     * BORRA UN NICHO DE LA CONFIG.
     *
     * <p>⚠⚠ <b>Esto NO borra su fila de la base</b>, y es a proposito: la fila
     * guarda quien lo tiene, hasta cuando y su memorial. Si el nicho se quito
     * por error, volver a capturarlo con el mismo id lo devuelve entero. Para
     * soltar la reclamacion esta {@code /luna santuario eliminar}, que es otra
     * decision y se toma aparte.
     *
     * <p>⚠ Lo que si desaparece de inmediato es <b>su proteccion</b>: sin caja
     * en la config, ese 3x3 vuelve a ser suelo publico.
     */
    private static int borrarNicho(ServerCommandSource src, String id) {
        var lista = net.pokereport.luna.santuario.NichoEditor.copia(
                net.pokereport.luna.santuario.SantuarioProteccion.catalogo());
        if (!net.pokereport.luna.santuario.NichoEditor.quitar(lista, id)) {
            src.sendError(Text.literal("\u00a7cNo hay ningun nicho \u00a7f" + id));
            return 0;
        }
        String malo = net.pokereport.luna.santuario.NichoEditor.guardar(lista);
        if (malo != null) {
            src.sendError(Text.literal("\u00a7cNo se guardo nada: \u00a7f" + malo));
            return 0;
        }
        recargarCatalogo(src);
        src.sendFeedback(() -> Text.literal(
                "\u00a7aNicho \u00a7f" + id + "\u00a7a fuera de la config. "
                + "\u00a77Su reclamacion sigue guardada."), true);
        return 1;
    }

    /** Teletransporta al centro de un nicho, para mirarlo. */
    private static int irANicho(ServerCommandSource src, String id) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        var nicho = net.pokereport.luna.santuario.SantuarioProteccion.catalogo().de(id);
        if (nicho == null) {
            src.sendError(Text.literal("\u00a7cNo hay ningun nicho \u00a7f" + id));
            return 0;
        }
        var mundo = p.getServer().getWorld(net.pokereport.luna.world.LunaDimensions.CIUDADELA);
        if (mundo == null) {
            src.sendError(Text.literal("\u00a7cLa ciudadela no existe."));
            return 0;
        }
        // ⚠ Por `Traslado` y no a pelo: carga el chunk de destino antes de
        //   mover. La ciudadela es un vacio con una isla, y llegar a un chunk
        //   frio es caerse.
        net.pokereport.luna.world.Traslado.ir(p, mundo,
                new net.minecraft.util.math.Vec3d(
                        nicho.min().getX() + net.pokereport.luna.santuario
                                .NichoEditor.RADIO + 0.5,
                        nicho.min().getY(),
                        nicho.min().getZ() + net.pokereport.luna.santuario
                                .NichoEditor.RADIO + 0.5));
        pintarNicho(mundo, nicho, 5);
        src.sendFeedback(() -> Text.literal("\u00a7aEn \u00a7f" + id
                + " \u00a77" + caja(nicho)), false);
        return 1;
    }

    /**
     * DIBUJA TODOS LOS NICHOS CON PARTICULAS DURANTE UNOS SEGUNDOS.
     *
     * <h2>⚠⚠ ES LA MITAD QUE HACE QUE «MUCHISIMOS» SE PUEDA COMPROBAR</h2>
     *
     * Un comando que captura coordenadas y no las enseña obliga a fiarse: con
     * cuarenta nichos, el que quedo dos bloques corrido <b>no se ve</b> --su
     * caja protege un sitio que no es el construido, y eso no da error, da un
     * hueco--. Aqui se ven las ocho aristas y el punto de la holografica, en el
     * mundo y encima de lo que hay construido.
     */
    private static int verNichos(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        var catalogo = net.pokereport.luna.santuario.SantuarioProteccion.catalogo();
        if (!catalogo.hay()) {
            src.sendFeedback(() -> Text.literal("\u00a77No hay ningun nicho todavia. "
                    + "\u00a7fPonte dentro de uno y usa /luna santuario nicho aqui"), false);
            return 1;
        }
        var mundo = p.getServerWorld();
        for (var n : catalogo.todos()) {
            pintarNicho(mundo, n, 10);
        }
        src.sendFeedback(() -> Text.literal("\u00a7a" + catalogo.todos().size()
                + " nichos marcados durante 10 s. \u00a77Blanco la caja, "
                + "\u00a7bazul\u00a77 la holografica."), false);
        return 1;
    }

    /**
     * RECOLOCA EL PROYECTOR DE TODOS LOS NICHOS A LA ALTURA DE HOY.
     *
     * <p>&#9888;&#9888; Es la migracion de que {@code ALTURA_PROYECTOR} bajara de
     * 3 a 1: los nichos capturados antes tienen la foto saliendose por el techo.
     * Recapturarlos a mano seria volver a andar hasta cada uno <b>y perder su
     * nombre y su reclamacion</b>; esto solo mueve el pedestal.
     */
    private static int recolocarNichos(ServerCommandSource src) {
        var lista = net.pokereport.luna.santuario.NichoEditor.copia(
                net.pokereport.luna.santuario.SantuarioProteccion.catalogo());
        if (lista.isEmpty()) {
            src.sendError(Text.literal("\u00a7cNo hay ningun nicho en la config."));
            return 0;
        }
        int movidos = 0;
        for (int i = 0; i < lista.size(); i++) {
            var nuevo = net.pokereport.luna.santuario.NichoEditor.recolocar(lista.get(i));
            if (!nuevo.proyector().equals(lista.get(i).proyector())) {
                movidos++;
            }
            lista.set(i, nuevo);
        }
        String malo = net.pokereport.luna.santuario.NichoEditor.guardar(lista);
        if (malo != null) {
            src.sendError(Text.literal("\u00a7cNo se guardo nada: \u00a7f" + malo));
            return 0;
        }
        recargarCatalogo(src);
        final int n = movidos;
        src.sendFeedback(() -> Text.literal("\u00a7a" + n + " de " + lista.size()
                + " proyectores recolocados. \u00a77Mira con "
                + "\u00a7f/luna santuario nicho ver"), true);
        return 1;
    }

    /** Quita la Mew del santuario y su cartel. */
    private static int quitarNpcSantuario(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Solo desde el juego."));
            return 0;
        }
        int n = net.pokereport.luna.santuario.SantuarioNpc.quitar(p);
        src.sendFeedback(() -> Text.literal(
                "\u00a7a" + n + " entidades del santuario quitadas."), true);
        return 1;
    }

    // ------------------------------------------------------------ ayudantes

    private static int indiceDe(
            java.util.List<net.pokereport.luna.santuario.NichoCatalogo.Nicho> lista,
            String id) {
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String punto(net.minecraft.util.math.BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String caja(net.pokereport.luna.santuario.NichoCatalogo.Nicho n) {
        return punto(n.min()) + " \u2192 " + punto(n.max());
    }

    /** Relee la config del disco y pone al dia base y clientes. */
    private static void recargarCatalogo(ServerCommandSource src) {
        try {
            net.pokereport.luna.santuario.SantuarioProteccion.catalogo().recargar();
        } catch (Exception e) {
            // No deberia pasar: se acaba de escribir un fichero que ya valido.
            LunaEternal.LOG.error("Santuario: la config recien escrita no se relee", e);
            src.sendError(Text.literal("\u00a7cEscrito, pero no se pudo releer: "
                    + e.getMessage()));
            return;
        }
        aplicarSantuario(src.getServer());
    }

    /**
     * Marca un nicho con particulas: las aristas en blanco y la holografica en
     * azul, repetido una vez por segundo.
     *
     * <p>⚠ Va por {@code Programador}, que corre en el tick del servidor -- que
     * es donde se pueden mandar particulas.
     */
    private static void pintarNicho(net.minecraft.server.world.ServerWorld mundo,
                                    net.pokereport.luna.santuario.NichoCatalogo.Nicho n,
                                    int segundos) {
        for (int s = 0; s < segundos; s++) {
            net.pokereport.luna.gym.Programador.en(1 + s * 20, () -> {
                var min = n.min();
                var max = n.max();
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        for (int z = min.getZ(); z <= max.getZ(); z++) {
                            // Solo las ARISTAS: pintar la caja maciza son 45
                            // particulas por nicho y no se ve la forma.
                            int bordes = 0;
                            if (x == min.getX() || x == max.getX()) bordes++;
                            if (y == min.getY() || y == max.getY()) bordes++;
                            if (z == min.getZ() || z == max.getZ()) bordes++;
                            if (bordes < 2) {
                                continue;
                            }
                            mundo.spawnParticles(
                                    net.minecraft.particle.ParticleTypes.END_ROD,
                                    x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                        }
                    }
                }
                var pr = n.proyector();
                mundo.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        pr.getX() + 0.5, pr.getY() + 0.5, pr.getZ() + 0.5,
                        6, 0.15, 0.15, 0.15, 0.0);
                // Donde va a flotar la foto: proyector + 1,55 (lo dice
                // HologramaSantuario, y este numero se lee de ahi al escribirlo).
                mundo.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        pr.getX() + 0.5, pr.getY() + 1.55, pr.getZ() + 0.5,
                        4, 0.35, 0.35, 0.35, 0.0);
            });
        }
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
