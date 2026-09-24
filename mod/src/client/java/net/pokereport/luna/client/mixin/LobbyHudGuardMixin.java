package net.pokereport.luna.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.pokereport.luna.client.EstadoCliente;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En la antesala solo se ve el mundo: sin party de Cobblemon, hotbar, chat,
 * minimapa integrado en HUD ni accesos visuales. La pantalla de ChatScreen se
 * dibuja aparte y sigue permitiendo escribir las credenciales de EasyAuth.
 */
@Mixin(InGameHud.class)
public abstract class LobbyHudGuardMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void pokereport$ocultarHudLobby(DrawContext context,
                                            RenderTickCounter tickCounter,
                                            CallbackInfo ci) {
        if (EstadoCliente.enLobby()) {
            ci.cancel();
        }
    }
}
