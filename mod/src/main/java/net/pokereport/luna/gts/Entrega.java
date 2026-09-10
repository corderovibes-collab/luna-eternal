package net.pokereport.luna.gts;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * EL UNICO SITIO QUE SABE LEER LO QUE EL GTS GUARDA EN CUSTODIA.
 *
 * <h2>&#9888;&#9888;&#9888; EXISTE PORQUE LA ENTREGA LLEVABA MESES SIN FUNCIONAR
 * Y NADIE PODIA VERLO</h2>
 *
 * {@code GtsDelivery} decodificaba <b>todas</b> las reclamaciones con
 * {@link ItemCodec}, y ese no es el formato de <b>ninguno</b> de los dos que el
 * servidor escribe hoy. Peor: {@code pendingClaims} <b>ni siquiera leia la
 * columna {@code kind}</b>, asi que la entrega no tenia forma de saber que
 * estaba mirando.
 *
 * <p>Los dos errores del log durante meses se explican enteros con eso, y se
 * comprobaron contra las filas de verdad antes de tocar una linea:
 *
 * <pre>
 *   POKEMON  payload 1F8B0800...  gzip: readCompressed PASA, y root.get("item")
 *                                 sale null -> «Not a map: null»
 *   ITEM     payload 6D696E65...  "mine..." en texto plano: no es gzip
 *                                 -> «ZipException: Not in GZIP format»
 * </pre>
 *
 * <h2>&#9888;&#9888;&#9888; Y ESO SIGNIFICA QUE COMPRAR UN POKEMON NUNCA
 * ENTREGO NADA</h2>
 *
 * No eran «siete reclamaciones raras»: la compra de un Pokemon delega la
 * entrega en {@code GtsDelivery.claimAll} --con un comentario que decia «el
 * camino de siempre, que ya sabe que hacer si el equipo esta lleno»-- y ese
 * camino no sabia leer un Pokemon. <b>El dinero cambiaba de manos, el listado
 * se marcaba SOLD, y el comprador no recibia nada.</b> Como la reclamacion
 * <b>no</b> se marca entregada --que es lo correcto-- no se perdio nada: en
 * cuanto esto funciona, las siete se entregan solas al entrar.
 *
 * <h2>&#9888;&#9888; LO QUE LO DEJO PASAR FUE UNA PRUEBA QUE MIRABA AL LADO</h2>
 *
 * El autotest comprobaba que el payload de un objeto <i>«lleva el separador que
 * espera la entrega»</i> -- o sea la FORMA de la cadena-- y <b>no llamaba a la
 * entrega jamas</b>. Una prueba que valida un formato contra su propia
 * descripcion, y no contra quien tiene que leerlo, pasa siempre. Es la leccion
 * de {@code testProtocoloNulos}: lo que caza estas cosas es <b>ejercitar el
 * camino</b>.
 *
 * <h2>&#9888;&#9888; POR QUE UNA CLASE Y NO UN {@code if} EN LA ENTREGA</h2>
 *
 * Porque el formato de los objetos ya tenia <b>dos lectores</b> --este y el de
 * la compra en vivo, dentro de {@code Red}-- y CLAUDE.md lleva escrito desde el
 * 25-ago que ese es justo el fallo que se come mercancia en silencio: <i>«dos
 * sitios con su propia idea del formato»</i>. Hoy los dos entran por aqui.
 */
public final class Entrega {

    private Entrega() {
    }

    /** El separador del payload de un objeto: identificador NUL cantidad. */
    private static final char SEPARADOR = (char) 0;

    /**
     * Entrega lo que haya en custodia. Devuelve el nombre de lo entregado, o
     * {@code null} si no se pudo leer.
     *
     * <p>&#9888; <b>Se llama EN EL HILO DEL SERVIDOR</b>: toca el inventario y
     * el almacen de Cobblemon, y ninguno de los dos se puede tocar desde otro.
     *
     * <p>&#9888; Un {@code kind} desconocido devuelve {@code null} en vez de
     * intentar adivinar. Adivinar es lo que hacia la version anterior.
     */
    public static Text entregar(ServerPlayerEntity jugador, String kind, byte[] datos) {
        if (jugador == null || datos == null || datos.length == 0) {
            return null;
        }
        if ("POKEMON".equals(kind)) {
            return pokemon(jugador, datos);
        }
        if ("ITEM".equals(kind)) {
            return objeto(jugador, datos);
        }
        LunaEternal.LOG.error("GTS: tipo de custodia desconocido «{}». "
                + "No se entrega nada: adivinar el formato es como se rompio esto.", kind);
        return null;
    }

    // ------------------------------------------------------------- objetos

    /**
     * Un objeto: {@code identificador + NUL + cantidad}, en UTF-8.
     *
     * <p>&#9888; Es texto plano <b>a proposito</b>: al escaparate solo entran
     * objetos <b>sin datos propios</b> (D-042), asi que el identificador y la
     * cantidad los describen por completo. Lo que no puede pasar es que quien
     * lo escribe y quien lo lee no estén de acuerdo -- por eso hay un solo
     * lector, y el autotest lo cruza con el escritor de verdad.
     */
    public static Text objeto(ServerPlayerEntity jugador, byte[] datos) {
        var leido = leerObjeto(datos);
        if (leido == null) {
            return null;
        }
        net.pokereport.luna.market.Inventarios.meter(
                jugador, leido.item(), leido.cantidad());
        // ⚠ Text SIN RESOLVER: `.getString()` aqui lo congelaria en `en_us`,
        //   que es el unico idioma que existe en un servidor.
        return new ItemStack(leido.item()).getName().copy()
                .append(Text.literal(" x" + leido.cantidad()));
    }

    /** Lo que un payload de objeto dice que es. */
    public record Objeto(Item item, int cantidad) {
    }

    /**
     * Lee el payload de un objeto <b>sin entregar nada</b>.
     *
     * <p>&#9888; Separado de {@link #objeto} para que el autotest pueda cruzar
     * lo que escribe {@code publicarObjeto} con lo que lee la entrega
     * <b>sin necesitar un jugador dentro</b>. Sin esta mitad, el invariante que
     * habria cazado este fallo no se puede escribir.
     */
    public static Objeto leerObjeto(byte[] datos) {
        if (datos == null || datos.length == 0) {
            return null;
        }
        try {
            String s = new String(datos, StandardCharsets.UTF_8);
            int corte = s.indexOf(SEPARADOR);
            if (corte <= 0) {
                return null;
            }
            Item item = net.pokereport.luna.market.Inventarios
                    .objeto(s.substring(0, corte));
            int cantidad = Integer.parseInt(s.substring(corte + 1).trim());
            if (item == null || cantidad <= 0) {
                return null;
            }
            return new Objeto(item, cantidad);
        } catch (Exception e) {
            LunaEternal.LOG.warn("GTS: payload de objeto ilegible: {}", e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------- Pokemon

    /**
     * Un Pokemon: NBT de Cobblemon <b>comprimido</b>, tal cual lo escribe
     * {@code pokemon.saveToNBT(...)} al publicarlo.
     *
     * <p>&#9888;&#9888; <b>La raiz es el Pokemon</b>, no {@code {"item": ...}}.
     * Ahi estaba el «Not a map: null»: {@link ItemCodec} descomprimia bien y
     * despues buscaba una clave que ese NBT no tiene.
     *
     * <p>&#9888; Si el equipo esta lleno va al PC y <b>se dice</b>. Es lo mismo
     * que hace el Pase, y por el mismo motivo: sin avisar, el jugador ve
     * «recibido» y no encuentra nada.
     */
    public static Text pokemon(ServerPlayerEntity jugador, byte[] datos) {
        try {
            NbtCompound nbt = NbtIo.readCompressed(
                    new ByteArrayInputStream(datos), NbtSizeTracker.ofUnlimitedBytes());
            var bicho = leerPokemon(nbt, jugador.getRegistryManager());
            if (bicho == null) {
                return null;
            }
            var almacen = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage();
            if (!almacen.getParty(jugador).add(bicho)) {
                almacen.getPC(jugador).add(bicho);
                jugador.sendMessage(Text.literal(
                        LunaEternal.PREFIJO + "§eTu equipo estaba lleno: "
                        + "§flo recibido ha ido al PC."), false);
            }
            // ⚠ El nombre de la especie es un Text traducible: viaja sin
            //   resolver, como el del objeto.
            return bicho.getSpecies().getTranslatedName();
        } catch (Throwable t) {
            LunaEternal.LOG.warn("GTS: payload de Pokemon ilegible: {}", t.toString());
            return null;
        }
    }

    /**
     * Reconstruye el Pokemon del NBT ya descomprimido.
     *
     * <p>&#9888; {@code loadFromNBT} es un metodo <b>de instancia</b> que
     * devuelve el Pokemon cargado --se verifico con {@code javap} contra el jar
     * de Cobblemon 1.8.0 que corre este servidor, no contra {@code vendor/}, que
     * es HEAD--. De ahi el {@code new Pokemon()} vacio de la linea de arriba.
     */
    public static com.cobblemon.mod.common.pokemon.Pokemon leerPokemon(
            NbtCompound nbt, net.minecraft.registry.DynamicRegistryManager registros) {
        if (nbt == null) {
            return null;
        }
        try {
            return new com.cobblemon.mod.common.pokemon.Pokemon()
                    .loadFromNBT(registros, nbt);
        } catch (Throwable t) {
            LunaEternal.LOG.warn("GTS: no se pudo reconstruir el Pokemon: {}",
                    t.toString());
            return null;
        }
    }
}
