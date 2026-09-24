package net.pokereport.luna.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

/** Destinos web oficiales usados por todas las pantallas del cliente. */
public final class Enlaces {
    public static final String TIENDA = "https://pokereport.online/";

    private Enlaces() {}

    /** Abre la confirmacion segura de Minecraft y regresa a la pantalla origen. */
    public static void abrirTienda(MinecraftClient cliente, Screen origen) {
        if (cliente == null) return;
        cliente.setScreen(new ConfirmLinkScreen(confirmado -> {
            if (confirmado) Util.getOperatingSystem().open(TIENDA);
            cliente.setScreen(origen);
        }, TIENDA, true));
    }
}
