package net.pokereport.luna.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;

/**
 * Un botón dentro de la Pokédex de Cobblemon para volver a oír la descripción.
 *
 * <p><b>Por qué hace falta.</b> Cobblemon <b>no deja reescanear</b> un Pokémon
 * que ya tienes registrado, así que la voz del escaneo se oye una vez en la
 * vida y nunca más. Este botón es la única forma de repetirla.
 *
 * <p><b>Por qué no es un mixin.</b> {@code PokedexGUI} extiende {@code Screen},
 * así que basta con {@link ScreenEvents}, de la API de Fabric: no se parchea
 * ninguna clase suya y una actualización de Cobblemon no puede romper el
 * arranque del juego. Lo único que se toca de dentro es leer qué especie estás
 * mirando, y eso va con red de seguridad — si algún día cambia el nombre del
 * campo, <b>el botón desaparece y la Pokédex sigue funcionando</b>.
 *
 * <p>No pasa por el servidor: el cliente ya sabe qué especie tiene delante y si
 * hay voz grabada. Un viaje de ida y vuelta aquí solo añadiría latencia a algo
 * que es puramente local. P6 sigue intacto — esto no decide nada, solo suena.
 */
public final class BotonVoz {

    /** La pantalla de Cobblemon, buscada por nombre para no atarnos a su API. */
    private static final String CLASE_POKEDEX =
            "com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI";

    /** Su textura de botón de sonido, que nuestro pack ya repinta de azul luna. */
    private static final Identifier TEXTURA =
            Identifier.of("cobblemon", "textures/gui/pokedex/button_sound.png");

    // El chasis de su Pokédex mide 345x207 y va centrado, igual que lo calcula
    // ella misma: (width - 345) / 2. La posición es relativa a esa esquina.
    private static final int BASE_ANCHO = 345, BASE_ALTO = 207;
    private static final int BOTON_X = 296, BOTON_Y = 135;
    private static final int BOTON_W = 22, BOTON_H = 10;

    /** La textura trae dos estados apilados: normal arriba, pulsado abajo. */
    private static final int TEX_W = 44, TEX_H = 20;

    private static Field campoEntrada;
    private static boolean avisado;

    private BotonVoz() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((cliente, pantalla, ancho, alto) -> {
            if (!esPokedex(pantalla)) {
                return;
            }
            ScreenEvents.afterRender(pantalla).register(
                    (p, ctx, ratonX, ratonY, delta) -> dibujar(p, ctx, ratonX, ratonY));
            ScreenMouseEvents.allowMouseClick(pantalla).register(
                    (p, ratonX, ratonY, boton) -> !pulsado(p, ratonX, ratonY, boton));
        });
    }

    private static boolean esPokedex(Screen pantalla) {
        return pantalla != null && CLASE_POKEDEX.equals(pantalla.getClass().getName());
    }

    private static int origenX(Screen p) {
        return (p.width - BASE_ANCHO) / 2;
    }

    private static int origenY(Screen p) {
        return (p.height - BASE_ALTO) / 2;
    }

    private static void dibujar(Screen p, DrawContext ctx, int ratonX, int ratonY) {
        String especie = especieSeleccionada(p);
        if (especie == null) {
            return;       // sin especie legible: mejor ningún botón que uno roto
        }
        int x = origenX(p) + BOTON_X, y = origenY(p) + BOTON_Y;
        boolean encima = ratonX >= x && ratonX < x + BOTON_W
                && ratonY >= y && ratonY < y + BOTON_H;
        boolean hayVoz = net.pokereport.luna.pokedex.VozService.tieneVoz(especie);

        // ⚠ La mezcla alfa a mano, siempre. Ver docs/ui/dibujado.md §1.
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        // Apagado si esa especie no tiene voz: enseñar que el botón existe pero
        // que ahí no hay nada grabado es informacion; esconderlo, no.
        if (!hayVoz) {
            ctx.setShaderColor(1f, 1f, 1f, 0.35f);
        }
        // La mitad de abajo de la textura es el estado pulsado.
        int v = encima && hayVoz ? TEX_H / 2 : 0;
        ctx.drawTexture(TEXTURA, x, y, BOTON_W, BOTON_H,
                0f, v, TEX_W, TEX_H / 2, TEX_W, TEX_H);
        if (!hayVoz) {
            ctx.setShaderColor(1f, 1f, 1f, 1f);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        if (encima) {
            ctx.drawTooltip(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                    Text.translatable(hayVoz
                            ? "pokepad.lunaeternal.voz.oir"
                            : "pokepad.lunaeternal.voz.sin"),
                    ratonX, ratonY);
        }
    }

    /** @return true si el clic era nuestro y no debe seguir bajando. */
    private static boolean pulsado(Screen p, double ratonX, double ratonY, int boton) {
        if (boton != 0) {
            return false;
        }
        String especie = especieSeleccionada(p);
        if (especie == null || !net.pokereport.luna.pokedex.VozService.tieneVoz(especie)) {
            return false;
        }
        int x = origenX(p) + BOTON_X, y = origenY(p) + BOTON_Y;
        if (ratonX < x || ratonX >= x + BOTON_W || ratonY < y || ratonY >= y + BOTON_H) {
            return false;
        }
        if (VozPokedex.reproducir(especie)) {
            var cliente = net.minecraft.client.MinecraftClient.getInstance();
            if (cliente.player != null) {
                cliente.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
            }
        }
        return true;      // consumido: que no se cuele al widget de debajo
    }

    /**
     * Qué especie tiene el jugador seleccionada, leída de dentro de Cobblemon.
     *
     * <p><b>Es el único punto frágil</b> y por eso va aislado aquí: su campo
     * {@code selectedEntry} es privado. Si un día lo renombran, esto devuelve
     * {@code null}, el botón no se dibuja, y la Pokédex de Cobblemon sigue
     * funcionando igual. Se avisa una vez al log y no se insiste.
     */
    private static String especieSeleccionada(Screen pantalla) {
        try {
            if (campoEntrada == null) {
                campoEntrada = pantalla.getClass().getDeclaredField("selectedEntry");
                campoEntrada.setAccessible(true);
            }
            Object entrada = campoEntrada.get(pantalla);
            if (entrada == null) {
                return null;      // todavía no ha elegido ninguno
            }
            Object id = entrada.getClass().getMethod("getSpeciesId").invoke(entrada);
            String texto = String.valueOf(id);
            return net.pokereport.luna.pokedex.VozService.normalizar(texto);
        } catch (Throwable t) {
            if (!avisado) {
                avisado = true;
                net.pokereport.luna.LunaEternal.LOG.warn(
                        "Pokédex: no se puede leer la especie seleccionada, el botón de voz "
                                + "queda oculto ({})", t.toString());
            }
            return null;
        }
    }
}
