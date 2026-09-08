package net.pokereport.luna.ui;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;

/**
 * EL CARTEL FLOTANTE DE UN NPC: nombre, para que sirve, y como se usa.
 *
 * <h2>&#9888;&#9888; ESTO ESTABA ESCRITO DOS VECES, Y LA SEGUNDA IBA A SER LA TERCERA</h2>
 *
 * El cartel de Oak se escribio entero dentro de {@code OakNpc}, y al pedir el
 * mismo para la Mew del Santuario lo facil era copiarlo. Este proyecto ya sabe
 * como acaba eso: {@code recalcular()} estaba <b>copiado en once pantallas</b> y
 * cuando se midio ya habia <b>seis variantes distintas</b> --y la version buena
 * estaba en una sola--. Un cartel copiado empieza igual y termina con dos
 * fondos, dos alturas y dos ideas de donde va el icono.
 *
 * <p>Aqui viven las cuatro decisiones que ya costaron una captura del usuario:
 * el fondo casi opaco, el icono con su palabra, la raiz vacia y el borrado.
 *
 * <h2>&#9888;&#9888;&#9888; EL FONDO ES CASI OPACO, Y ESE NUMERO SE PAGO MIRANDO</h2>
 *
 * El primero heredo el de los carteles de gimnasio ({@code 0x40000000}, negro al
 * 25 %) y <b>alli esta bien</b>: las salas de gimnasio son de piedra gris. El
 * laboratorio de Oak es <b>blanco entero</b>, y sobre blanco un velo del 25 % no
 * oscurece nada: el texto blanco desaparecia.
 *
 * <p><b>La regla que queda: un cartel del mundo NO SABE contra que pared lo van
 * a leer, asi que se trae su propio fondo.</b> Es la misma decision que ya
 * tomaron los hologramas de la Torre --«fondo solido para que el texto sea
 * legible contra cualquier iluminacion o shaders»--.
 *
 * <h2>&#9888;&#9888; EL TEXTO VA COMPUESTO Y EN ESPAÑOL, al reves de la regla del idioma</h2>
 *
 * Un {@code TextDisplay} guarda el {@code Text} <b>en el mundo</b>, asi que una
 * clave de traduccion se resolveria al colocarlo y quedaria congelada para
 * todos, en el idioma de quien puso el cartel.
 */
public final class Cartel {

    /** Fondo: negro azulado al 90 %. Ver el javadoc de la clase. */
    private static final int FONDO = 0xE6060B14;

    /** Ancho de linea antes de partir, en pixeles de fuente. */
    private static final int ANCHO = 220;

    /**
     * Alcance, como multiplicador de 64: 48 bloques.
     *
     * <p>&#9888; Es mas que el lado de la plaza (56), o sea que el cartel se lee
     * desde el otro extremo de donde este puesto.
     */
    private static final float ALCANCE = 0.75f;

    private Cartel() {
    }

    /**
     * EL TEXTO DE UN CARTEL DE NPC: titulo, para que sirve, y el clic derecho.
     *
     * <p>&#9888;&#9888;&#9888; <b>LA RAIZ VA VACIA, Y NO ES UNA MANIA DE ESTILO:
     * ES LO QUE PROTEGE AL ICONO.</b> Colgando los trozos de un
     * {@code Text.literal(titulo).formatted(GOLD, BOLD)}, todo lo que se le
     * añade <b>hereda</b> dorado y negrita -- y un glifo de mapa de bits se
     * dibuja <b>multiplicando por el color del estilo</b>, asi que el raton
     * saldria dorado y, con la negrita, <b>dibujado dos veces desplazado</b>.
     * Con {@code Text.empty()} cada trozo lleva el suyo.
     *
     * <p>&#9888;&#9888; <b>Y el icono va SIEMPRE con su palabra al lado.</b> Si
     * algun dia el glifo faltara, lo que quedaria plantado en la ciudadela para
     * siempre seria un <b>cuadrado blanco</b> -- y solo se arregla volviendo a
     * colocar el cartel, porque el texto vive en el mundo.
     */
    public static Text texto(String titulo, String parodeQue) {
        return Text.empty()
                .append(Text.literal(titulo + "\n")
                        .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(parodeQue + "\n")
                        .formatted(Formatting.WHITE))
                .append(Iconos.clicDerecho())
                .append(Text.literal(" Clic derecho")
                        .formatted(Formatting.AQUA));
    }

    /**
     * Pone el cartel flotando sobre un punto.
     *
     * @param pies    la posicion del NPC
     * @param altura  cuanto flota por encima de ella
     * @param marca   la etiqueta con la que se le podra volver a encontrar
     * @return {@code true} si quedo puesto
     */
    public static boolean poner(ServerWorld mundo, Vec3d pies, double altura,
                                String marca, Text texto) {
        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) {
            LunaEternal.LOG.warn("No se pudo crear el cartel '{}'", marca);
            return false;
        }
        cartel.setPosition(pies.x, pies.y + altura, pies.z);
        cartel.setText(texto);
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        cartel.setBackground(FONDO);
        cartel.setLineWidth(ANCHO);
        cartel.setViewRange(ALCANCE);
        cartel.setNoGravity(true);
        cartel.addCommandTag(marca);
        return mundo.spawnEntity(cartel);
    }

    /**
     * Borra los carteles con esa marca que haya cerca.
     *
     * <p>&#9888;&#9888;&#9888; {@code discard()} y no {@code kill}: a un
     * TextDisplay <b>el daño no le llega</b>, asi que la consola diria «Killed 1
     * entity» y no se iria ninguno. Es la leccion de los tres Brocks apilados.
     *
     * <p>&#9888; Y hay que llamarlo <b>antes</b> de poner, siempre: los comandos
     * que colocan NPC se repiten, y un cartel de mas se queda flotando para
     * siempre encima del bueno.
     *
     * @return cuantos se quitaron
     */
    public static int quitar(ServerWorld mundo, Vec3d centro, double radio,
                             String marca) {
        Box caja = Box.of(centro, radio * 2, radio * 2, radio * 2);
        int n = 0;
        for (var e : mundo.getEntitiesByClass(
                DisplayEntity.TextDisplayEntity.class, caja,
                x -> x.getCommandTags().contains(marca))) {
            e.discard();
            n++;
        }
        return n;
    }
}
