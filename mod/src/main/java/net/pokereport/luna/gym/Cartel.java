package net.pokereport.luna.gym;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.gym.Gimnasio.Gimnasio_;

/**
 * EL CARTEL QUE FLOTA SOBRE CADA LÍDER.
 *
 * <p>Petición del usuario: <i>«encima del NPC de cada entrenador debe ir un
 * texto flotante que diga la información del entrenador, nivel requerido…»</i>.
 *
 * <h2>⚠⚠ NO ES {@code setCustomName}, Y NO PODÍA SERLO</h2>
 *
 * El nombre de una entidad es <b>una línea</b>. Aquí hacen falta cuatro —
 * quién es, de qué tipo, a qué nivel se pelea y cuántas medallas pide — y
 * apretarlas en una sola daría un renglón ilegible de cuarenta caracteres
 * flotando sobre la cabeza.
 *
 * <p>Se usa un {@code TextDisplay}, que es de Minecraft desde 1.19.4: admite
 * saltos de línea, fondo propio, y <b>mira siempre al jugador</b>
 * ({@code BillboardMode.CENTER}), así que se lee igual desde cualquier lado de
 * la plaza.
 *
 * <h2>⚠⚠⚠ SE BORRA ANTES DE PONER, SIEMPRE</h2>
 *
 * Un {@code TextDisplay} es una entidad y se queda en el mundo. Sin borrar
 * primero, cada vez que se coloca un líder queda <b>un cartel más</b> en el
 * mismo sitio: dos, tres, ocho carteles superpuestos que se ven como un borrón.
 * Es exactamente lo que ya pasó con los tres Brocks apilados, y por el mismo
 * motivo — <b>colocar sin limpiar</b>.
 *
 * <p>⚠ Y se borran por MARCA, no por posición: si alguien mueve al líder un
 * bloque, el cartel viejo se queda donde estaba y ya nadie lo alcanza. Con la
 * marca se barre la zona entera y caen todos.
 */
public final class Cartel {

    private Cartel() {}

    /** La etiqueta que llevan todos los carteles nuestros. */
    public static final String MARCA = "luna_cartel";

    /** Y una por gimnasio, para poder rehacer solo el de uno. */
    public static String marcaDe(Gimnasio_ g) {
        return "luna_cartel_" + g.id();
    }

    /** Cuánto por encima de los pies del líder flota. */
    private static final double ALTURA = 2.45;

    /** El radio que se barre al limpiar. */
    private static final double RADIO = 6.0;

    /**
     * Pone el cartel de un líder, quitando antes el que hubiera.
     *
     * @param pies dónde está de pie el líder. El cartel va {@link #ALTURA}
     *             encima, que es justo por encima de una cabeza
     */
    public static void poner(ServerWorld mundo, Gimnasio_ g, Vec3d pies) {
        quitar(mundo, g, pies);

        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) {
            LunaEternal.LOG.warn("No se pudo crear el cartel de {}", g.id());
            return;
        }
        cartel.setPosition(pies.x, pies.y + ALTURA, pies.z);
        cartel.setText(texto(g));
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        // ⚠ El fondo es negro a media transparencia (0x40 de alfa). Sin fondo,
        //   el texto claro sobre una pared clara desaparece — y las salas de la
        //   ciudadela son de piedra clara.
        cartel.setBackground(0x40000000);
        cartel.setLineWidth(220);
        // ⚠ `setShadow(boolean)` NO EXISTE: la sombra del texto es un bit dentro
        //   de `setDisplayFlags(byte)`. Se deja el valor por defecto (sin
        //   sombra) en vez de tocar el byte entero, porque escribirlo a mano
        //   apagaría de paso la alineación y el fondo.
        // ⚠ 48 bloques de alcance: la plaza mide 56 de lado, así que el cartel
        //   se ve desde cualquier punto de ella y no desde el otro extremo del
        //   mundo. El valor es un MULTIPLICADOR de 64, no una distancia.
        cartel.setViewRange(0.75f);
        // ⚠⚠ SIN COLISIÓN Y SIN GRAVEDAD: un TextDisplay no tiene ninguna de las
        //    dos, pero se marca igual para que un cambio de vainilla no deje un
        //    cartel cayéndose al vacío de la ciudadela.
        cartel.setNoGravity(true);
        cartel.addCommandTag(MARCA);
        cartel.addCommandTag(marcaDe(g));
        mundo.spawnEntity(cartel);
    }

    /** Quita los carteles de ese líder que haya cerca de ese punto. */
    public static void quitar(ServerWorld mundo, Gimnasio_ g, Vec3d donde) {
        var caja = new net.minecraft.util.math.Box(
                donde.x - RADIO, donde.y - RADIO, donde.z - RADIO,
                donde.x + RADIO, donde.y + RADIO, donde.z + RADIO);
        for (var e : mundo.getEntitiesByClass(
                DisplayEntity.TextDisplayEntity.class, caja,
                x -> x.getCommandTags().contains(marcaDe(g)))) {
            // ⚠⚠⚠ `discard` y no `kill`: a estas entidades el daño no les llega,
            //    igual que a los decorativos. `/kill` diría «Killed 1 entity» y
            //    no moriría ninguna -- la lección que ya nos costó tres Brocks.
            e.discard();
        }
    }

    /**
     * QUÉ PONE.
     *
     * <pre>
     *   Brock
     *   Gimnasio de Roca
     *   Combate a nivel 15
     *   Medallas: ninguna
     * </pre>
     *
     * <p>⚠⚠ «Combate a nivel 15» y no «nivel máximo 15», que es lo que primero
     * escribí y era <b>mentira a medias</b>: {@code adjustLevel} no acota, iguala.
     * Un jugador con un inicial de nivel 5 también pelea a 15 — le <b>sube</b>.
     * Decir «máximo» le haría creer que su Pokémon de 5 va a entrar de 5.
     *
     * <p>⚠ El texto va ya compuesto y en español porque un {@code TextDisplay}
     * guarda un {@code Text} en el mundo y lo pinta el cliente sin volver a
     * preguntar: una clave de traducción aquí se resolvería al ponerlo, en el
     * idioma del servidor, y se quedaría congelada para todos. Es la regla del
     * idioma al revés — aquí el servidor <b>sí</b> tiene que decidir.
     */
    private static MutableText texto(Gimnasio_ g) {
        MutableText t = Text.literal(g.lider())
                .formatted(Formatting.GOLD, Formatting.BOLD);
        t.append(Text.literal("\n" + (g.campeon() ? "Campeón" : "Gimnasio de ")
                              + (g.campeon() ? "" : g.medalla()))
                     .formatted(Formatting.WHITE));
        t.append(Text.literal("\nCombate a nivel " + g.nivel())
                     .formatted(Formatting.AQUA));
        t.append(Text.literal("\nMedallas: "
                              + (g.medallas() == 0 ? "ninguna"
                                                   : String.valueOf(g.medallas())))
                     .formatted(g.medallas() == 0 ? Formatting.GREEN
                                                  : Formatting.YELLOW));
        if (!Gimnasio.construido(g)) {
            t.append(Text.literal("\nPróximamente").formatted(Formatting.GRAY));
        }
        return t;
    }
}
