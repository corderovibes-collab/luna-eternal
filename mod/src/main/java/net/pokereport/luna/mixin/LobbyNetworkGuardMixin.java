package net.pokereport.luna.mixin;

import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.puerta.Puerta;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.Set;

/** Candado de red del lobby: solo autenticacion, movimiento y guardian. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class LobbyNetworkGuardMixin {
    private static final Set<String> COMANDOS_AUTH = Set.of("login", "l", "register", "reg");

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void pokereport$bloquearChatLobby(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (Puerta.bloqueado(player)) {
            ci.cancel();
        }
    }

    /** Un cliente modificado tampoco puede sacar ni ordenar Pokemon en Lobby. */
    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void pokereport$bloquearCobblemonLobby(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        if (Puerta.bloqueado(player)
                && "cobblemon".equals(packet.payload().getId().id().getNamespace())) {
            ci.cancel();
        }
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void pokereport$bloquearComandoLobby(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (debeBloquear(packet.command())) {
            avisar();
            ci.cancel();
        }
    }

    @Inject(method = "onChatCommandSigned", at = @At("HEAD"), cancellable = true)
    private void pokereport$bloquearComandoFirmadoLobby(ChatCommandSignedC2SPacket packet,
                                                        CallbackInfo ci) {
        if (debeBloquear(packet.command())) {
            avisar();
            ci.cancel();
        }
    }

    private boolean debeBloquear(String comando) {
        if (!Puerta.bloqueado(player)) {
            return false;
        }
        String limpio = comando == null ? "" : comando.strip();
        int espacio = limpio.indexOf(' ');
        String raiz = (espacio < 0 ? limpio : limpio.substring(0, espacio))
                .toLowerCase(Locale.ROOT);
        return !COMANDOS_AUTH.contains(raiz);
    }

    private void avisar() {
        player.sendMessage(Text.literal(
                "§6§lPOKEREPORT §8» §7En el Lobby solo puedes moverte, autenticarte y usar el guardian."),
                false);
    }
}
