package net.pokereport.luna.shop;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.pokereport.luna.LunaEternal;

/**
 * LO QUE ENTREGA LA TIENDA CUANDO NO ES UN OBJETO A SECAS.
 *
 * <h2>⚠⚠⚠ UN MÓDULO DE PROTECCIÓN NO ES «UN OBJETO CON UN ID»</h2>
 *
 * Es un {@code minecraft:player_head} con una etiqueta
 * {@code protectionstones:stone_type} y su textura dentro — leído del jar, no
 * supuesto. Dar la cabeza pelada <b>no daría ningún error</b>: el jugador la
 * colocaría y no pasaría nada, porque el mod no la reconoce como suya.
 *
 * <h2>⚠⚠⚠ SE LO PEDIMOS A SU MOD, NO LO FABRICAMOS NOSOTROS</h2>
 *
 * {@code CBItemManager.getStone(tipo)} es <b>público</b> y devuelve el módulo
 * ya montado. Reimplementar su formato aquí sería tener <b>dos sitios con su
 * propia idea</b> de cómo es un módulo — y el día que él cambiara el suyo, los
 * nuestros dejarían de funcionar sin decir nada. Es la lección del payload del
 * escaparate.
 *
 * <h2>⚠⚠ POR REFLEXIÓN, Y NO COMPILANDO CONTRA SU JAR</h2>
 *
 * Es la misma decisión que con CobblemonCards: <b>no se compila contra el mod
 * ajeno</b>. Aquí además no hay elección — ClaimBlocks es ARR y no publica
 * maven. Lo que sí se gana es que nuestro mod <b>funciona con él y sin él</b>:
 * si no está, esto devuelve {@code null} y la tienda lo dice, en vez de
 * reventar al arrancar con un {@code NoClassDefFoundError} que ni siquiera
 * nombra al mod que falta.
 */
public final class Modulos {

    /** El prefijo que usa el catálogo: {@code claimblocks:template1}. */
    public static final String PROVEEDOR = "claimblocks:";

    private static boolean buscado;
    private static Object gestor;
    private static Method getStone;

    private Modulos() {}

    /**
     * Busca la clase una sola vez.
     *
     * <p>⚠ Se busca <b>tarde</b> (a la primera compra) y no al arrancar: el
     * orden de carga de mods no está garantizado, y preguntarlo demasiado
     * pronto daría «no está» para un mod que sí está.
     */
    private static synchronized void localizar() {
        if (buscado) {
            return;
        }
        buscado = true;
        try {
            Class<?> c = Class.forName("com.f0cus.protectionstones.CBItemManager");
            gestor = c.getField("INSTANCE").get(null);
            getStone = c.getMethod("getStone", String.class);
            LunaEternal.LOG.info("Protecciones: ClaimBlocks encontrado, la tienda "
                    + "puede entregar módulos");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.warn("Protecciones: no encuentro ClaimBlocks ({}). "
                    + "Los módulos NO se pueden vender.", e.toString());
        }
    }

    /** ¿Se puede entregar algo de ese proveedor? */
    public static boolean hay(String entrega) {
        if (entrega == null || !entrega.startsWith(PROVEEDOR)) {
            return false;
        }
        localizar();
        return getStone != null;
    }

    /**
     * El objeto que hay que entregar, o {@code null} si no se puede.
     *
     * <p>⚠⚠ Devolver {@code null} y no una cabeza pelada es deliberado: quien
     * llama tiene que <b>no cobrar</b>. Entregar algo que no funciona es peor
     * que no entregar nada, porque el jugador ya ha pagado.
     */
    public static ItemStack fabricar(String entrega, int cantidad) {
        if (!hay(entrega)) {
            return null;
        }
        String tipo = entrega.substring(PROVEEDOR.length());
        try {
            Object r = getStone.invoke(gestor, tipo);
            if (!(r instanceof ItemStack pila) || pila.isEmpty()) {
                LunaEternal.LOG.error("ClaimBlocks no conoce el modulo «{}»", tipo);
                return null;
            }
            ItemStack copia = pila.copy();
            copia.setCount(cantidad);
            return copia;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido pedir el modulo «{}»: {}", tipo, e.toString());
            return null;
        }
    }
}
