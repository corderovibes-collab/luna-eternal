package net.pokereport.luna.market;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Contar, sacar y meter objetos del inventario de un jugador.
 *
 * <h2>⚠⚠ Todo esto gira alrededor de UNA pregunta: ¿qué es «el mismo objeto»?</h2>
 *
 * Y la respuesta no es «tienen el mismo tipo». Comparar solo por tipo es un
 * fallo grave: al vender un «pico de hierro», el jugador perdería <b>su pico
 * encantado con nombre propio</b>, porque para el código serían el mismo objeto.
 *
 * <p>Se exige que los <b>componentes coincidan con los de un objeto recién
 * creado</b>. Lo personalizado no se toca nunca.
 *
 * <h2>Y eso mismo es la definición de FUNGIBLE del mercado</h2>
 *
 * Al libro de órdenes solo entran objetos «corrientes», y no por prudencia: es
 * lo que hace que {@code item_id} más una cantidad describa la mercancía por
 * completo. Si dos órdenes «iguales» pudieran llevar dentro cosas distintas, el
 * libro estaría mintiendo — comprarías la más barata y recibirías la peor.
 *
 * <p>Es el mismo motivo por el que los Pokémon no pueden ir en un libro de
 * órdenes (D-041), aplicado a los objetos con encantamientos.
 *
 * <p>La lógica venía de {@code ShopService}, donde ya estaba escrita y probada.
 * Se extrae aquí para que la tienda y el mercado usen <b>la misma</b>: dos
 * copias de la regla de «qué es el mismo objeto» es como acaban diciendo cosas
 * distintas y alguien pierde su pico.
 */
public final class Inventarios {

    private Inventarios() {}

    /** El objeto de un identificador, o {@code null} si no existe. */
    public static Item objeto(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) {
            return null;
        }
        return Registries.ITEM.get(id);
    }

    /**
     * ¿Es un ejemplar corriente de este objeto?
     *
     * <p>Ver el comentario de la clase: esto es lo que protege lo personalizado
     * <b>y</b> lo que define qué puede entrar en el libro.
     */
    public static boolean corriente(ItemStack pila, Item item) {
        if (pila.isEmpty() || !pila.isOf(item)) {
            return false;
        }
        return ItemStack.areItemsAndComponentsEqual(pila, new ItemStack(item));
    }

    /** Cuántos corrientes tiene el jugador. */
    public static int cuantos(ServerPlayerEntity jugador, Item item) {
        int total = 0;
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            if (corriente(inv.main.get(i), item)) {
                total += inv.main.get(i).getCount();
            }
        }
        return total;
    }

    /**
     * Saca objetos del inventario. Devuelve cuántos sacó de verdad.
     *
     * <p>⚠⚠ <b>Quien llama tiene que mirar el número que devuelve.</b> Si saca
     * menos de los que pedía —porque otro camino se los quitó entre la
     * comprobación y ahora— y se crea la orden igual, el mercado estaría
     * vendiendo objetos que no tiene: la custodia se rompe justo por donde
     * decía el GTS que no debía romperse.
     */
    public static int sacar(ServerPlayerEntity jugador, Item item, int cantidad) {
        int quedan = cantidad;
        var inv = jugador.getInventory();
        for (int i = 0; i < inv.main.size() && quedan > 0; i++) {
            ItemStack s = inv.main.get(i);
            if (!corriente(s, item)) {
                continue;
            }
            int coge = Math.min(quedan, s.getCount());
            s.decrement(coge);
            quedan -= coge;
        }
        return cantidad - quedan;
    }

    /**
     * Mete objetos, y los que no quepan caen al suelo.
     *
     * <p>⚠ {@code offerOrDrop} y NO {@code insertStack}: el segundo inserta de
     * forma <b>parcial</b> y devuelve {@code false}, así que el jugador se
     * quedaría con la mitad de los objetos y con la deuda entera. Con
     * {@code offerOrDrop} no hay fallo posible —lo que no cabe cae al suelo— y
     * la rama de «no cupo» deja de existir. Es la misma decisión que ya tomó la
     * tienda.
     *
     * <p>⚠ Se entrega en pilas del tamaño máximo del objeto. Una sola pila de
     * 10.000 Poké Balls no es un objeto válido: al guardarse y volver a leerse,
     * Minecraft la recorta a 64 y las otras 9.936 desaparecen sin un aviso.
     */
    public static void meter(ServerPlayerEntity jugador, Item item, int cantidad) {
        int quedan = Math.max(0, cantidad);
        int porPila = Math.max(1, new ItemStack(item).getMaxCount());
        while (quedan > 0) {
            int trozo = Math.min(quedan, porPila);
            jugador.getInventory().offerOrDrop(new ItemStack(item, trozo));
            quedan -= trozo;
        }
    }
}
