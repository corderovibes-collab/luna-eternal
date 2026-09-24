package net.pokereport.luna.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.pokereport.luna.client.EstadoCliente;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Impide inventario, creativo, Pokemon y cualquier interfaz de mods en Lobby. */
@Mixin(MinecraftClient.class)
public abstract class LobbyScreenGuardMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void pokereport$cerrarPantallasDelLobby(Screen screen, CallbackInfo ci) {
        MinecraftClient cliente = (MinecraftClient) (Object) this;
        if (!EstadoCliente.enLobby() || cliente.player == null || cliente.world == null || screen == null) {
            return;
        }

        // Chat queda solo como teclado para /login y /register; el servidor
        // bloquea mensajes y cualquier otro comando. Escape siempre debe abrir.
        if (screen instanceof ChatScreen
                || screen instanceof GameMenuScreen
                || screen instanceof DeathScreen) {
            return;
        }
        ci.cancel();
    }
}
