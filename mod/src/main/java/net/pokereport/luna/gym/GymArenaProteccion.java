package net.pokereport.luna.gym;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.ui.Toque;
import net.pokereport.luna.world.LunaDimensions;

/**
 * PROTECCIÓN TOTAL DE ARENAS DE GIMNASIO.
 *
 * <p>Los retadores en las salas de combate no pueden romper bloques, colocar
 * bloques ni alterar la estructura del gimnasio: solo combatir.
 *
 * <p>Los operadores en creativo (nivel >= 2) conservan acceso para construir
 * y mantener los esquemas originales.
 */
public final class GymArenaProteccion {

    private GymArenaProteccion() {}

    /**
     * Determina si la posición pertenece a las arenas de gimnasio
     * tanto en el maestro como en todas las ranuras clonadas,
     * o a dimensiones protegidas del servidor (torre, lobby).
     */
    public static boolean esZonaProtegida(World mundo, BlockPos pos) {
        var key = mundo.getRegistryKey();
        return LunaDimensions.GIMNASIOS.equals(key)
                || LunaDimensions.TORRE.equals(key)
                || LunaDimensions.LOBBY.equals(key)
                || LunaDimensions.ISLAS_NARANJA.equals(key);
    }

    public static void registrar() {
        // 0. Prohibir golpear / empezar a picar bloques en la arena
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((jugador, mundo, mano, pos, direccion) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            if (sp.isCreative() && sp.hasPermissionLevel(2)) {
                return ActionResult.PASS;
            }
            if (esZonaProtegida(mundo, pos)) {
                avisar(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 1. Prohibir romper bloques en la arena
        PlayerBlockBreakEvents.BEFORE.register((mundo, jugador, pos, estado, be) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)) {
                return true;
            }
            if (sp.isCreative() && sp.hasPermissionLevel(2)) {
                return true;
            }
            if (esZonaProtegida(mundo, pos)) {
                avisar(sp);
                return false;
            }
            return true;
        });

        // 2. Prohibir colocar bloques contra superficies
        UseBlockCallback.EVENT.register((jugador, mundo, mano, golpe) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            if (sp.isCreative() && sp.hasPermissionLevel(2)) {
                return ActionResult.PASS;
            }
            BlockPos pos = golpe.getBlockPos();
            if (esZonaProtegida(mundo, pos)) {
                var stack = sp.getStackInHand(mano);
                if (esItemPeligroso(stack.getItem())) {
                    avisar(sp);
                    return ActionResult.FAIL;
                }
                var block = mundo.getBlockState(pos).getBlock();
                if (block instanceof net.minecraft.block.DoorBlock
                        || block instanceof net.minecraft.block.TrapdoorBlock
                        || block instanceof net.minecraft.block.FenceGateBlock) {
                    return ActionResult.PASS;
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 3. Prohibir colocar bloques o usar items peligrosos en el aire dentro de la arena
        UseItemCallback.EVENT.register((jugador, mundo, mano) -> {
            if (mundo.isClient() || !(jugador instanceof ServerPlayerEntity sp)) {
                return TypedActionResult.pass(jugador.getStackInHand(mano));
            }
            if (sp.isCreative() && sp.hasPermissionLevel(2)) {
                return TypedActionResult.pass(jugador.getStackInHand(mano));
            }
            if (esZonaProtegida(mundo, sp.getBlockPos())) {
                var stack = sp.getStackInHand(mano);
                if (esItemPeligroso(stack.getItem())) {
                    avisar(sp);
                    return TypedActionResult.fail(stack);
                }
            }
            return TypedActionResult.pass(jugador.getStackInHand(mano));
        });

        LunaEternal.LOG.info("Gimnasios: protección de bloques activa en arenas y zonas reservadas");
    }

    private static boolean esItemPeligroso(net.minecraft.item.Item item) {
        return item instanceof BlockItem
                || item instanceof net.minecraft.item.BucketItem
                || item instanceof net.minecraft.item.BoatItem
                || item instanceof net.minecraft.item.MinecartItem
                || item instanceof net.minecraft.item.FlintAndSteelItem
                || item instanceof net.minecraft.item.SpawnEggItem
                || item instanceof net.minecraft.item.EndCrystalItem;
    }

    private static void avisar(ServerPlayerEntity sp) {
        if (!Toque.repetido(sp.getUuid(), "gym_protect")) {
            sp.sendMessage(Text.literal("§c§lGIMNASIO §r§7— No puedes modificar los bloques de esta zona."), true);
        }
    }
}
