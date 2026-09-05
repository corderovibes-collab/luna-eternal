package net.pokereport.luna.proteccion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.shop.Modulos;

/**
 * LAS PARCELAS DE UN JUGADOR, LEÍDAS DEL MOD QUE LAS GUARDA.
 *
 * <h2>⚠⚠⚠ POR REFLEXIÓN, Y NO PORQUE APETEZCA</h2>
 *
 * ClaimBlocks es <b>ARR y no publica maven</b>: no se puede compilar contra él.
 * Lo que sí expone —leído del jar con {@code javap}, no supuesto— basta:
 *
 * <pre>
 *   CBManager.INSTANCE.getRegions()   Map de nombre a region, MUTABLE
 *   CBManager.INSTANCE.save()         persiste a config/ClaimBlocks/claims.json
 *   CBRegion                          getOwner, getStoneType, getCenterBlock,
 *                                     getPos1/2, getMembers, getWorld
 * </pre>
 *
 * <p>⚠⚠ Y ASÍ NUESTRO MOD FUNCIONA CON ÉL Y SIN ÉL. Si algún día se retira, esto
 * devuelve una lista vacía y la pantalla lo dice, en vez de que el servidor no
 * arranque con un {@code NoClassDefFoundError} que ni siquiera nombra al mod que
 * falta. Es la misma decisión que se tomó con CobblemonCards.
 *
 * <h2>⚠⚠ TODO ESTO CORRE EN EL HILO DEL SERVIDOR</h2>
 *
 * No toca la base de datos: son estructuras en memoria del otro mod. Consultarlo
 * desde el executor de E/S sería leerlas mientras el hilo del servidor las
 * cambia.
 */
public final class Protecciones {

    /**
     * Una parcela, ya traducida a lo nuestro.
     *
     * @param lado cuánto mide de lado, CALCULADO de las dos esquinas y no del
     *             radio de la configuración: si alguien cambia el radio de un
     *             escalón, las parcelas que ya existen <b>siguen midiendo lo
     *             que medían</b>, y enseñar el número nuevo sería mentir
     */
    public record Parcela(String nombre, String tipo, BlockPos centro,
                          String mundo, int lado, int miembros) {}

    private static boolean buscado;
    private static Object gestor;
    private static Method getRegions;
    private static Method save;
    private static Method rOwner;
    private static Method rTipo;
    private static Method rCentro;
    private static Method rPos1;
    private static Method rPos2;
    private static Method rMiembros;
    private static Method rMundo;

    private Protecciones() {}

    private static synchronized void localizar() {
        if (buscado) {
            return;
        }
        buscado = true;
        try {
            Class<?> m = Class.forName("com.f0cus.protectionstones.CBManager");
            gestor = m.getField("INSTANCE").get(null);
            getRegions = m.getMethod("getRegions");
            save = m.getMethod("save");

            Class<?> r = Class.forName("com.f0cus.protectionstones.CBRegion");
            rOwner = r.getMethod("getOwner");
            rTipo = r.getMethod("getStoneType");
            rCentro = r.getMethod("getCenterBlock");
            rPos1 = r.getMethod("getPos1");
            rPos2 = r.getMethod("getPos2");
            rMiembros = r.getMethod("getMembers");
            rMundo = r.getMethod("getWorld");
            LunaEternal.LOG.info("Protecciones: ClaimBlocks localizado");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.warn("Protecciones: no encuentro ClaimBlocks ({}). "
                    + "La pantalla saldra vacia y lo dira.", e.toString());
        }
    }

    /** ¿Está el mod? La pantalla lo dice en vez de enseñar una lista vacía. */
    public static boolean hay() {
        localizar();
        return getRegions != null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> regiones() {
        try {
            return (Map<String, Object>) getRegions.invoke(gestor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido leer las parcelas: {}", e.toString());
            return Map.of();
        }
    }

    /** Las parcelas de ese jugador, de la más grande a la más pequeña. */
    public static List<Parcela> de(UUID jugador) {
        if (!hay()) {
            return List.of();
        }
        var salida = new ArrayList<Parcela>();
        for (var e : regiones().entrySet()) {
            try {
                if (!jugador.equals(rOwner.invoke(e.getValue()))) {
                    continue;
                }
                BlockPos p1 = (BlockPos) rPos1.invoke(e.getValue());
                BlockPos p2 = (BlockPos) rPos2.invoke(e.getValue());
                salida.add(new Parcela(
                        e.getKey(),
                        String.valueOf(rTipo.invoke(e.getValue())),
                        (BlockPos) rCentro.invoke(e.getValue()),
                        String.valueOf(rMundo.invoke(e.getValue())),
                        Math.abs(p2.getX() - p1.getX()) + 1,
                        ((Set<?>) rMiembros.invoke(e.getValue())).size()));
            } catch (ReflectiveOperationException | RuntimeException ex) {
                LunaEternal.LOG.error("Parcela {} ilegible: {}", e.getKey(), ex.toString());
            }
        }
        salida.sort((a, b) -> Integer.compare(b.lado(), a.lado()));
        return salida;
    }

    /**
     * Borra una parcela y devuelve su módulo.
     *
     * <h2>⚠⚠⚠ SE COMPRUEBA QUE ES SUYA AQUÍ, NO EN LA PANTALLA</h2>
     *
     * El nombre de la parcela llega <b>del cliente</b>. Sin esta comprobación,
     * un cliente modificado borraría la de cualquiera escribiendo su nombre
     * (P6). Que la pantalla solo enseñe las tuyas es dibujo, no una regla.
     *
     * <h2>⚠⚠ Y HACE LAS TRES COSAS, no solo quitar la fila</h2>
     *
     * Quitar la parcela dejando el bloque puesto deja <b>un módulo que ya no
     * protege nada</b>, que el jugador intentaría romper — que es justo el lío
     * del que venimos. Se quita la fila, se quita el bloque y se devuelve el
     * módulo, que es lo que hace el menú del propio mod.
     *
     * @return {@code null} si ha ido bien, o la clave del motivo si no
     */
    public static String borrar(ServerPlayerEntity jugador, String nombre) {
        if (!hay()) {
            return "sin_mod";
        }
        var mapa = regiones();
        Object region = mapa.get(nombre);
        if (region == null) {
            return "no_existe";
        }
        String tipo;
        BlockPos centro;
        try {
            if (!jugador.getUuid().equals(rOwner.invoke(region))) {
                LunaEternal.LOG.warn("{} ha intentado borrar la parcela {}, que no "
                        + "es suya", jugador.getGameProfile().getName(), nombre);
                return "no_es_tuya";
            }
            tipo = String.valueOf(rTipo.invoke(region));
            centro = (BlockPos) rCentro.invoke(region);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido mirar la parcela {}: {}", nombre, e.toString());
            return "error";
        }

        // ⚠ EL MODULO SE FABRICA ANTES DE BORRAR NADA. Si no se pudiera, el
        //   jugador se quedaria sin parcela Y sin modulo, y eso no se deshace.
        ItemStack modulo = Modulos.fabricar(Modulos.PROVEEDOR + tipo, 1);
        if (modulo == null) {
            return "error";
        }

        mapa.remove(nombre);
        try {
            save.invoke(gestor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("Parcela {} borrada de memoria y NO guardada: {}",
                    nombre, e.toString());
        }

        // ⚠ El bloque puede no estar: el mod deja «esconder el modulo», y
        //   entonces la parcela sigue viva sin nada en el mundo. Se quita solo
        //   si de verdad hay una cabeza ahi -- romper a ciegas se llevaria por
        //   delante lo que el jugador hubiera construido en esa coordenada.
        var mundo = jugador.getServerWorld();
        var estado = mundo.getBlockState(centro);
        if (estado.getBlock() instanceof net.minecraft.block.AbstractSkullBlock) {
            mundo.removeBlock(centro, false);
        }
        jugador.getInventory().offerOrDrop(modulo);
        LunaEternal.LOG.info("{} borro su parcela {} ({})",
                jugador.getGameProfile().getName(), nombre, tipo);
        return null;
    }

    /** El módulo de esa parcela, para dibujarlo en la pantalla. */
    public static ItemStack pilaDe(String tipo) {
        ItemStack p = Modulos.fabricar(Modulos.PROVEEDOR + tipo, 1);
        return p == null ? new ItemStack(net.minecraft.item.Items.PLAYER_HEAD) : p;
    }
}
