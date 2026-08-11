package net.pokereport.luna.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.EconomyException;

import java.util.UUID;

/**
 * Comprar y vender en la tienda NPC.
 *
 * <p>El orden de las operaciones no es casual y resuelve el problema de fondo:
 * <b>el dinero vive en la base de datos y los objetos en el inventario, así que
 * no puede haber una transacción realmente atómica entre los dos.</b> Lo que sí
 * se puede es ordenar los pasos para que ningún fallo deje al jugador peor que
 * al empezar:
 *
 * <ol>
 *   <li>comprobar que hay hueco <b>antes</b> de cobrar,</li>
 *   <li>cobrar,</li>
 *   <li>entregar,</li>
 *   <li>si la entrega falla pese a todo, <b>devolver el dinero</b>.</li>
 * </ol>
 *
 * <p>Al revés —cobrar y luego descubrir que no cabe— el jugador pierde dinero
 * por un fallo nuestro, que es la peor clase de error posible en un servidor
 * con economía.
 */
public final class ShopService {

    private ShopService() {}

    /** Resultado de una operación, ya listo para enseñar al jugador. */
    public record Result(boolean ok, String message) {}

    /**
     * Compra {@code amount} unidades. Se ejecuta en el hilo de E/S, pero toca
     * el inventario en el hilo del servidor.
     */
    public static void buy(ServerPlayerEntity player, ShopCatalog.Entry entry,
                           int amount, java.util.function.Consumer<Result> then) {
        var server = player.getServer();
        if (server == null) return;

        long total = entry.buy() * amount;
        ItemStack stack = new ItemStack(entry.item(), amount);

        // 1. ¿Cabe? Se comprueba antes de tocar el dinero.
        if (!hasRoom(player, stack)) {
            then.accept(new Result(false, "§cNo te cabe en el inventario."));
            return;
        }

        var profile = player.getGameProfile();
        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(profile.getId(), profile.getName());
                String key = UUID.randomUUID().toString();

                // 2. Cobrar.
                LunaEternal.economy().debit(id, entry.currency(), total, "shop_buy", key);
                LunaEternal.quests().advance(id,
                    net.pokereport.luna.quest.Quest.Objective.Type.SHOP_BUY, 1);

                // 3. Entregar, ya en el hilo del servidor.
                server.execute(() -> {
                    if (player.isRemoved()) {
                        refund(id, entry, total, key, player, server);
                        return;
                    }
                    // offerOrDrop, NO insertStack. insertStack inserta de forma
                    // PARCIAL y devuelve false: el jugador se quedaría con la
                    // mitad de los objetos Y con el reembolso completo, o sea
                    // duplicando valor. offerOrDrop no puede fallar — lo que no
                    // cabe cae al suelo — así que la rama de reembolso por falta
                    // de hueco deja de existir.
                    player.getInventory().offerOrDrop(stack.copy());
                    then.accept(new Result(true,
                        "§aComprado §f" + amount + "x " + entry.displayName()
                        + " §7por §f" + fmt(total) + " " + entry.currency().displayName));
                });

            } catch (EconomyException e) {
                String msg = e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS
                    ? "§cNo tienes suficiente " + entry.currency().displayName + "."
                    : "§c" + e.getMessage();
                server.execute(() -> then.accept(new Result(false, msg)));
            } catch (Exception e) {
                LunaEternal.LOG.error("Fallo comprando en la tienda", e);
                server.execute(() -> then.accept(
                    new Result(false, "§cError en la compra. No se te ha cobrado.")));
            }
        });
    }

    /** Vende al banco lo que el jugador tenga en la mano. */
    public static void sell(ServerPlayerEntity player, ShopCatalog.Entry entry,
                            int amount, java.util.function.Consumer<Result> then) {
        var server = player.getServer();
        if (server == null) return;

        if (!entry.sellable()) {
            then.accept(new Result(false, "§cEsto no se puede vender."));
            return;
        }
        if (countOf(player, entry) < amount) {
            then.accept(new Result(false, "§cNo tienes tantos."));
            return;
        }

        // Aquí el orden se invierte: primero se quita el objeto y luego se
        // paga. Si fallara el pago, el jugador se queda sin objeto — así que
        // el pago se hace inmediatamente después y con reintento en el log.
        int removed = removeItems(player, entry, amount);
        if (removed <= 0) {
            then.accept(new Result(false, "§cNo se pudo retirar el objeto."));
            return;
        }

        long total = entry.sell() * removed;
        int finalRemoved = removed;
        var profile = player.getGameProfile();

        LunaEternal.submit(() -> {
            try {
                long id = LunaEternal.players().resolve(profile.getId(), profile.getName());
                LunaEternal.economy().credit(id, entry.currency(), total,
                    "shop_sell", UUID.randomUUID().toString());
                server.execute(() -> then.accept(new Result(true,
                    "§aVendido §f" + finalRemoved + "x " + entry.displayName()
                    + " §7por §f" + fmt(total) + " " + entry.currency().displayName)));
            } catch (Exception e) {
                // El jugador ya no tiene el objeto: hay que devolvérselo.
                LunaEternal.LOG.error("Fallo pagando una venta; devolviendo objetos", e);
                server.execute(() -> {
                    player.getInventory().offerOrDrop(
                        new ItemStack(entry.item(), finalRemoved));
                    then.accept(new Result(false,
                        "§cNo se pudo completar la venta. Se te ha devuelto todo."));
                });
            }
        });
    }

    // ------------------------------------------------------------ auxiliares

    private static void refund(long playerId, ShopCatalog.Entry entry, long total,
                               String originalKey, ServerPlayerEntity player,
                               net.minecraft.server.MinecraftServer server) {
        LunaEternal.submit(() -> {
            try {
                LunaEternal.economy().credit(playerId, entry.currency(), total,
                    "shop_refund", originalKey + ":refund");
                server.execute(() -> player.sendMessage(Text.literal(
                    "§eNo cabía en el inventario. Se te ha devuelto el dinero."), false));
            } catch (Exception e) {
                // Si ni el reembolso funciona, queda registrado para revisarlo
                // a mano. El libro de asientos permite reconstruir qué pasó.
                LunaEternal.LOG.error(
                    "REEMBOLSO FALLIDO player_id={} importe={} {}",
                    playerId, total, entry.currency(), e);
            }
        });
    }

    private static boolean hasRoom(ServerPlayerEntity player, ItemStack stack) {
        var inv = player.getInventory();
        int room = 0;
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack slot = inv.main.get(i);
            if (slot.isEmpty()) room += stack.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                room += slot.getMaxCount() - slot.getCount();
            }
            if (room >= stack.getCount()) return true;
        }
        return false;
    }

    /**
     * ¿Es un objeto "corriente" de este tipo?
     *
     * <p>Comparar solo por tipo (<code>isOf</code>) sería un fallo grave: al
     * vender un "pico de hierro" el jugador podría perder <b>su pico encantado
     * con nombre propio</b>, porque para el código serían el mismo objeto. Se
     * exige que los componentes coincidan con los de un objeto recién creado,
     * así lo personalizado nunca se toca.
     */
    private static boolean isPlain(ItemStack stack, ShopCatalog.Entry entry) {
        if (!stack.isOf(entry.item())) return false;
        return ItemStack.areItemsAndComponentsEqual(stack, new ItemStack(entry.item()));
    }

    private static int countOf(ServerPlayerEntity player, ShopCatalog.Entry entry) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (isPlain(s, entry)) total += s.getCount();
        }
        return total;
    }

    private static int removeItems(ServerPlayerEntity player, ShopCatalog.Entry entry,
                                   int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.main.size() && remaining > 0; i++) {
            ItemStack s = inv.main.get(i);
            if (!isPlain(s, entry)) continue;
            int take = Math.min(remaining, s.getCount());
            s.decrement(take);
            remaining -= take;
        }
        return amount - remaining;
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }
}
