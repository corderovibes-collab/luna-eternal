package net.pokereport.luna.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

/** Destinos web oficiales usados por todas las pantallas del cliente. */
public final class Enlaces {
    public static final String TIENDA = "https://pokereport.online/tienda/";
    public static final String WIKI = "https://pokereport.online/wiki/";

    private Enlaces() {}

    /** Abre la confirmacion segura de Minecraft y regresa a la pantalla origen. */
    public static void abrirTienda(MinecraftClient cliente, Screen origen) {
        abrir(cliente, origen, TIENDA);
    }

    public static void abrirWiki(MinecraftClient cliente, Screen origen) {
        abrir(cliente, origen, WIKI);
    }

    private static void abrir(MinecraftClient cliente, Screen origen, String destino) {
        if (cliente == null) return;
        cliente.setScreen(new ConfirmLinkScreen(confirmado -> {
            if (confirmado) Util.getOperatingSystem().open(destino);
            cliente.setScreen(origen);
        }, destino, true));
    }
}
