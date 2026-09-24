package net.pokereport.luna.client.mixin;

import name.modid.client.CustomBattleController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Evita que el temporizador de 50ms de CustomBattleController borre el tooltip
 * encolado de MoveTooltipOverlayState mientras el jugador interactúa con el menú
 * de combate con teclado/mouse.
 */
@Mixin(value = CustomBattleController.class, remap = false)
public abstract class CustomBattleControllerMixin {
    @Inject(method = "isMovesSelectionActive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void luna$keepMovesSelectionActive(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen != null && mc.currentScreen.getClass().getName().contains("BattleGUI")) {
            // Si hay un tooltip encolado para renderizar, nunca permitir que el caché caducado lo borre
            try {
                if (MoveTooltipOverlayStateAccessor.isQueued()) {
                    cir.setReturnValue(true);
                }
            } catch (Throwable ignored) {
                cir.setReturnValue(true);
            }
        }
    }
}
