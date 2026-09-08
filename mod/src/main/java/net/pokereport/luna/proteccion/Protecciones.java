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
    private static Method rNombre;
    private static Method rBanderas;
    private static Method rTitulo;
    private static Method rSubtitulo;
    private static Method rCopiar;
    private static Method actualizarBandera;
    private static Object catalogoBanderas;
    private static Method banderasRegistradas;
    private static Method banderaPorNombre;
    private static Method fNombre;
    private static Method fPorDefecto;

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
            rNombre = r.getMethod("getName");
            rBanderas = r.getMethod("getFlags");
            rTitulo = r.getMethod("getEnterTitle");
            rSubtitulo = r.getMethod("getEnterSubtitle");

            Class<?> flag = Class.forName("com.f0cus.protectionstones.flags.Flag");
            fNombre = flag.getMethod("getName");
            fPorDefecto = flag.getMethod("getDefaultValue");
            Class<?> flags = Class.forName("com.f0cus.protectionstones.flags.Flags");
            catalogoBanderas = flags.getField("INSTANCE").get(null);
            banderasRegistradas = flags.getMethod("getRegisteredFlags");
            banderaPorNombre = flags.getMethod("getFlagByName", String.class);
            actualizarBandera = m.getMethod("updateRegionFlag", r, flag, Object.class);

            // ⚠⚠ RENOMBRAR ES `copy`, NO UN `setName`: `CBRegion` es una data
            //    class de Kotlin y su nombre es FINAL. Se copia con el nombre
            //    nuevo y se cambia la clave del mapa, que es donde vive de
            //    verdad la identidad de una parcela.
            rCopiar = r.getMethod("copy", String.class, UUID.class, BlockPos.class,
                    String.class, BlockPos.class, BlockPos.class, Map.class,
                    Set.class, String.class, String.class, String.class);
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

    // ---- el detalle de una parcela ----------------------------------------

    public record Miembro(String uuid, String nombre) {}

    /** @param valor lo que vale ahora; {@code porDefecto} es lo que vale si nadie la toca */
    public record Permiso(String clave, boolean valor, boolean porDefecto) {}

    public record Detalle(String nombre, List<Miembro> miembros, List<Permiso> permisos,
                          String titulo, String subtitulo, boolean visible) {}

    /**
     * La región de ese nombre <b>si es suya</b>, o {@code null}.
     *
     * <h2>⚠⚠⚠ TODO LO QUE CAMBIA ALGO PASA POR AQUÍ</h2>
     *
     * El nombre llega del cliente en <b>todas</b> las operaciones —borrar,
     * renombrar, miembros, permisos— y en todas hay que volver a comprobar de
     * quién es. Un solo sitio: si la comprobación estuviera repetida en cinco
     * métodos, el día que se añada el sexto se olvidaría (P6).
     */
    private static Object mia(ServerPlayerEntity jugador, String nombre) {
        if (!hay()) {
            return null;
        }
        Object region = regiones().get(nombre);
        if (region == null) {
            return null;
        }
        try {
            if (!jugador.getUuid().equals(rOwner.invoke(region))) {
                LunaEternal.LOG.warn("{} ha tocado la parcela {}, que no es suya",
                        jugador.getGameProfile().getName(), nombre);
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return region;
    }

    private static void guardar() {
        try {
            save.invoke(gestor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("Cambios NO guardados: {}", e.toString());
        }
    }

    /** El nombre de un jugador por su uuid, aunque no esté conectado. */
    private static String nombreDe(ServerPlayerEntity quien, UUID id) {
        var servidor = quien.getServer();
        if (servidor != null && servidor.getUserCache() != null) {
            var p = servidor.getUserCache().getByUuid(id);
            if (p.isPresent()) {
                return p.get().getName();
            }
        }
        // ⚠ Se enseña el uuid recortado y no «desconocido»: con «desconocido»
        //   no se pueden distinguir dos, y quitar al que no era no se deshace.
        return id.toString().substring(0, 8);
    }

    /** Miembros y permisos de una parcela. {@code null} si no es suya. */
    public static Detalle detalle(ServerPlayerEntity jugador, String nombre) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return null;
        }
        var miembros = new ArrayList<Miembro>();
        var permisos = new ArrayList<Permiso>();
        try {
            for (Object id : (Set<?>) rMiembros.invoke(region)) {
                UUID u = (UUID) id;
                miembros.add(new Miembro(u.toString(), nombreDe(jugador, u)));
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> puestas = (Map<Object, Object>) rBanderas.invoke(region);
            // ⚠⚠ SE RECORREN LAS REGISTRADAS, NO LAS PUESTAS. El mapa de la
            //    región solo trae las que alguien ha tocado: enseñando solo
            //    esas, una parcela recién creada saldría SIN NINGÚN PERMISO y
            //    parecería que no se pueden configurar.
            for (Object f : (List<?>) banderasRegistradas.invoke(catalogoBanderas)) {
                boolean pordef = Boolean.TRUE.equals(fPorDefecto.invoke(f));
                Object v = puestas.get(f);
                permisos.add(new Permiso(String.valueOf(fNombre.invoke(f)),
                        v == null ? pordef : Boolean.TRUE.equals(v), pordef));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido leer el detalle de {}: {}", nombre, e.toString());
            return null;
        }
        miembros.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));
        try {
            return new Detalle(nombre, List.copyOf(miembros), List.copyOf(permisos),
                    texto(rTitulo.invoke(region)), texto(rSubtitulo.invoke(region)),
                    hayModulo(jugador, region));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("Detalle de {} a medias: {}", nombre, e.toString());
            return null;
        }
    }

    /** Cambia un permiso. Devuelve la clave del motivo, o {@code null} si fue bien. */
    public static String permiso(ServerPlayerEntity jugador, String nombre,
                                 String bandera, boolean valor) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return "no_es_tuya";
        }
        try {
            Object f = banderaPorNombre.invoke(catalogoBanderas, bandera);
            if (f == null) {
                // ⚠ La bandera llega del cliente. Una que no existe se rechaza
                //   en vez de guardarse: guardarla dejaría basura en el fichero
                //   del otro mod, que ni siquiera es nuestro.
                return "no_existe";
            }
            actualizarBandera.invoke(gestor, region, f, valor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido cambiar {} en {}: {}",
                    bandera, nombre, e.toString());
            return "error";
        }
        guardar();
        return null;
    }

    /**
     * Añade o quita a alguien de una parcela.
     *
     * <p>⚠⚠ SE COPIA LA REGIÓN CON EL CONJUNTO NUEVO en vez de mutar el suyo.
     * El {@code Set} que devuelve {@code getMembers} puede ser inmutable —es
     * Kotlin— y mutarlo reventaría; y aunque no lo fuera, cambiarle una
     * estructura por dentro a otro mod es contar con un detalle que él puede
     * cambiar sin avisar.
     */
    public static String miembro(ServerPlayerEntity jugador, String nombre,
                                 UUID quien, boolean anadir) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return "no_es_tuya";
        }
        if (jugador.getUuid().equals(quien)) {
            return "eres_tu";
        }
        try {
            @SuppressWarnings("unchecked")
            Set<UUID> actuales = (Set<UUID>) rMiembros.invoke(region);
            var nuevos = new java.util.LinkedHashSet<UUID>(actuales);
            if (anadir ? !nuevos.add(quien) : !nuevos.remove(quien)) {
                return anadir ? "ya_estaba" : "no_estaba";
            }
            regiones().put(nombre, conMiembros(region, nuevos));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido tocar los miembros de {}: {}",
                    nombre, e.toString());
            return "error";
        }
        guardar();
        return null;
    }

    private static Object conMiembros(Object region, Set<UUID> miembros)
            throws ReflectiveOperationException {
        return rCopiar.invoke(region,
                rNombre.invoke(region), rOwner.invoke(region), rCentro.invoke(region),
                rTipo.invoke(region), rPos1.invoke(region), rPos2.invoke(region),
                rBanderas.invoke(region), miembros,
                rTitulo.invoke(region), rSubtitulo.invoke(region), rMundo.invoke(region));
    }

    /**
     * Renombra una parcela.
     *
     * <p>⚠⚠⚠ EL NOMBRE ES LA CLAVE DEL MAPA, así que renombrar no es cambiar un
     * campo: es <b>quitar y volver a poner</b>. Y si ya existiera otra con ese
     * nombre, poner sin más se la <b>comería sin decir nada</b>.
     */
    public static String renombrar(ServerPlayerEntity jugador, String nombre,
                                   String nuevo) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return "no_es_tuya";
        }
        String limpio = nuevo == null ? "" : nuevo.trim();
        // ⚠ Se acota AQUÍ y no en la pantalla: el texto llega del cliente. Y se
        //   prohíbe el «§» por lo mismo que en los clanes -- un código de color
        //   dentro pintaría el resto de la línea de quien lo lea.
        if (limpio.isEmpty() || limpio.length() > 24
                || !limpio.matches("[\\p{L}\\p{N} _-]+")) {
            return "nombre_invalido";
        }
        if (limpio.equals(nombre)) {
            return null;
        }
        var mapa = regiones();
        if (mapa.containsKey(limpio)) {
            return "ya_existe";
        }
        try {
            Object copia = rCopiar.invoke(region,
                    limpio, rOwner.invoke(region), rCentro.invoke(region),
                    rTipo.invoke(region), rPos1.invoke(region), rPos2.invoke(region),
                    rBanderas.invoke(region), rMiembros.invoke(region),
                    rTitulo.invoke(region), rSubtitulo.invoke(region),
                    rMundo.invoke(region));
            mapa.remove(nombre);
            mapa.put(limpio, copia);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido renombrar {}: {}", nombre, e.toString());
            return "error";
        }
        guardar();
        return null;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** ¿Está puesto el módulo en el mundo, o se ha escondido? */
    private static boolean hayModulo(ServerPlayerEntity jugador, Object region)
            throws ReflectiveOperationException {
        BlockPos centro = (BlockPos) rCentro.invoke(region);
        return jugador.getServerWorld().getBlockState(centro).getBlock()
                instanceof net.minecraft.block.AbstractSkullBlock;
    }

    /**
     * Esconde o vuelve a poner el módulo. La protección sigue igual.
     *
     * <h2>⚠⚠⚠ VOLVER A PONERLO ES ALGO QUE EL MOD NO SABE HACER</h2>
     *
     * Su propio texto lo dice: <i>«If you hide the module, it will be
     * physically removed. You will NOT be able to see it again»</i>. Aquí sí,
     * porque la parcela guarda el centro y el tipo: con eso se reconstruye la
     * cabeza y se le devuelve su perfil ({@code SkullBlockEntity.setOwner}).
     *
     * <p>⚠⚠ Y NO SE COLOCA A CIEGAS. Si esa coordenada ya tiene algo que no es
     * aire, poner el módulo <b>se llevaría por delante</b> lo que el jugador
     * haya construido encima mientras estaba escondido. Se avisa y no se toca.
     */
    public static String visible(ServerPlayerEntity jugador, String nombre, boolean poner) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return "no_es_tuya";
        }
        try {
            BlockPos centro = (BlockPos) rCentro.invoke(region);
            var mundo = jugador.getServerWorld();
            boolean hay = hayModulo(jugador, region);
            if (poner == hay) {
                return poner ? "ya_visible" : "ya_escondido";
            }
            if (!poner) {
                mundo.removeBlock(centro, false);
                return null;
            }
            if (!mundo.getBlockState(centro).isReplaceable()) {
                return "ocupado";
            }
            ItemStack modulo = Modulos.fabricar(
                    Modulos.PROVEEDOR + String.valueOf(rTipo.invoke(region)), 1);
            if (modulo == null) {
                return "error";
            }
            mundo.setBlockState(centro,
                    net.minecraft.block.Blocks.PLAYER_HEAD.getDefaultState());
            var be = mundo.getBlockEntity(centro);
            var perfil = modulo.get(net.minecraft.component.DataComponentTypes.PROFILE);
            if (be instanceof net.minecraft.block.entity.SkullBlockEntity craneo
                    && perfil != null) {
                // ⚠ Sin esto la cabeza sale de Steve: la textura vive en el
                //   componente de perfil del objeto, no en el bloque.
                craneo.setOwner(perfil);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido cambiar la visibilidad de {}: {}",
                    nombre, e.toString());
            return "error";
        }
        return null;
    }

    /**
     * El mensaje que sale al entrar en la parcela.
     *
     * <p>⚠⚠ SE ACOTA AQUÍ, que es donde llega del cliente. Y se prohíbe el
     * «§» por lo mismo que en los clanes: un código de color dentro pintaría
     * el resto de la línea de quien lo lea. El propio mod deja escribirlo.
     */
    public static String mensaje(ServerPlayerEntity jugador, String nombre,
                                 String titulo, String subtitulo) {
        Object region = mia(jugador, nombre);
        if (region == null) {
            return "no_es_tuya";
        }
        String t = titulo == null ? "" : titulo.trim();
        String sub = subtitulo == null ? "" : subtitulo.trim();
        if (t.length() > 32 || sub.length() > 32
                || t.indexOf('§') >= 0 || sub.indexOf('§') >= 0) {
            return "mensaje_invalido";
        }
        try {
            regiones().put(nombre, rCopiar.invoke(region,
                    rNombre.invoke(region), rOwner.invoke(region), rCentro.invoke(region),
                    rTipo.invoke(region), rPos1.invoke(region), rPos2.invoke(region),
                    rBanderas.invoke(region), rMiembros.invoke(region),
                    t, sub, rMundo.invoke(region)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LunaEternal.LOG.error("No he podido cambiar el mensaje de {}: {}",
                    nombre, e.toString());
            return "error";
        }
        guardar();
        return null;
    }

    /** El uuid de un jugador por su nombre, aunque no esté conectado. */
    public static UUID uuidDe(ServerPlayerEntity quien, String nombre) {
        var servidor = quien.getServer();
        if (servidor == null || servidor.getUserCache() == null) {
            return null;
        }
        var p = servidor.getUserCache().findByName(nombre);
        return p.isPresent() ? p.get().getId() : null;
    }

    /** El módulo de esa parcela, para dibujarlo en la pantalla. */
    public static ItemStack pilaDe(String tipo) {
        ItemStack p = Modulos.fabricar(Modulos.PROVEEDOR + tipo, 1);
        return p == null ? new ItemStack(net.minecraft.item.Items.PLAYER_HEAD) : p;
    }
}
