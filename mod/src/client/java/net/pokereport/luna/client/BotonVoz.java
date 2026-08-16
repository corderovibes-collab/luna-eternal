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

    /**
     * La MISMA textura que usa su botón de grito, y a propósito.
     *
     * <p>El usuario lo pidió así: «igual a ese pero con la voz». Los dos hacen
     * lo mismo —soltar un sonido de ese Pokémon— y que se parezcan es lo que
     * hace que se entienda sin explicarlo. Nuestro pack además ya la repinta de
     * azul luna, así que encaja con el resto de la interfaz.
     */
    private static final Identifier TEX_BASE =
            Identifier.of("cobblemon", "textures/gui/pokedex/button_sound.png");
    private static final Identifier TEX_FLECHA =
            Identifier.of("cobblemon", "textures/gui/pokedex/button_sound_arrow.png");

    // El chasis de su Pokédex mide 345x207 y va centrado, igual que lo calcula
    // ella misma: (width - 345) / 2. La posición es relativa a esa esquina.
    private static final int BASE_ANCHO = 345, BASE_ALTO = 207;

    /**
     * Justo ENCIMA de su botón de grito.
     *
     * <p>No es a ojo: su {@code PokemonInfoWidget} se monta en
     * {@code (x + 180, y + 28)} y dentro coloca el grito en
     * {@code (pX + 115, pY + 83)}, o sea {@code (x + 295, y + 111)}. El nuestro
     * va 14 píxeles más arriba, que es su alto más un respiro.
     *
     * <p>Antes estaba en la esquina del panel de descripción y quedaba fuera de
     * sitio: allí no hay nada de sonido y parecía pegado.
     */
    // Son DOS piezas, igual que el suyo: el panel con la onda y la flecha
    // encima. Dibujar solo la flecha --que es lo que se hizo primero-- da un
    // triangulo suelto al doble de tamano que no se parece en nada al de abajo.
    //
    //   base    blitk(button_sound,  pX+114, pY+81, 44x20, escala 0,5) -> 22x10
    //   flecha  ScaledButton(pX+115, pY+83, 12x12,        escala 0,5) ->  6x6
    //
    // Con el widget en (x+180, y+28) eso cae en (x+294, y+109) y (x+295,
    // y+111). El nuestro va 14 pixeles mas arriba: el alto del panel mas un
    // respiro.
    private static final int BASE_X = 294, BASE_Y = 95;
    private static final int BASE_W = 22, BASE_H = 10;
    private static final int FLECHA_X = 295, FLECHA_Y = 97;
    private static final int FLECHA_W = 6, FLECHA_H = 6;

    /** Tamaños reales de las PNG. La flecha trae dos estados apilados. */
    private static final int TEX_BASE_W = 44, TEX_BASE_H = 20;
    private static final int TEX_FLECHA_W = 12, TEX_FLECHA_H = 24;

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
            alAbrirDesdeElEscaner(pantalla);
        });
    }

    /**
     * Si la Pokédex la ha abierto el escáner, suelta la voz sola.
     *
     * <p><b>Es lo que cierra el agujero de los ya registrados.</b> Cobblemon no
     * deja reescanear lo que ya tienes, pero sí deja <b>mantener pulsado</b>
     * sobre él para abrir la Pokédex por ese Pokémon — y ahí es donde el
     * jugador espera volver a oírlo.
     *
     * <p>Se distingue de abrirla a mano por {@code initSpecies}: su
     * {@code PokedexUsageContext} llama a {@code open(dex, tipos, speciesId)}
     * con la especie, y el objeto Pokédex la abre sin ella. Así abrir la
     * Pokédex normalmente <b>no</b> dispara ninguna voz, que sería un susto.
     */
    private static void alAbrirDesdeElEscaner(Screen pantalla) {
        try {
            Object id = pantalla.getClass().getMethod("getInitSpecies").invoke(pantalla);
            if (id != null) {
                VozPokedex.reproducir(
                        net.pokereport.luna.pokedex.VozService.normalizar(String.valueOf(id)));
            }
        } catch (Throwable t) {
            // Sin esto solo se pierde la reproducción automática; el botón sigue.
            if (!avisado) {
                avisado = true;
                net.pokereport.luna.LunaEternal.LOG.warn(
                        "Pokédex: no se puede saber si la abrió el escáner ({})", t.toString());
            }
        }
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
        int bx = origenX(p) + BASE_X, by = origenY(p) + BASE_Y;
        boolean encima = ratonX >= bx && ratonX < bx + BASE_W
                && ratonY >= by && ratonY < by + BASE_H;
        boolean hayVoz = net.pokereport.luna.pokedex.VozService.tieneVoz(especie);

        // ⚠ La mezcla alfa a mano, siempre. Ver docs/ui/dibujado.md §1.
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        // Apagado si esa especie no tiene voz: enseñar que el botón existe pero
        // que ahí no hay nada grabado es informacion; esconderlo, no.
        if (!hayVoz) {
            ctx.setShaderColor(1f, 1f, 1f, 0.35f);
        }
        ctx.drawTexture(TEX_BASE, bx, by, BASE_W, BASE_H,
                0f, 0f, TEX_BASE_W, TEX_BASE_H, TEX_BASE_W, TEX_BASE_H);
        // La mitad de abajo de la flecha es el estado pulsado.
        int v = encima && hayVoz ? TEX_FLECHA_H / 2 : 0;
        ctx.drawTexture(TEX_FLECHA,
                origenX(p) + FLECHA_X, origenY(p) + FLECHA_Y, FLECHA_W, FLECHA_H,
                0f, v, TEX_FLECHA_W, TEX_FLECHA_H / 2, TEX_FLECHA_W, TEX_FLECHA_H);
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
        int x = origenX(p) + BASE_X, y = origenY(p) + BASE_Y;
        if (ratonX < x || ratonX >= x + BASE_W || ratonY < y || ratonY >= y + BASE_H) {
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
