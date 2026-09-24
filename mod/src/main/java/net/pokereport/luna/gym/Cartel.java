package net.pokereport.luna.gym;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.gym.Gimnasio.Gimnasio_;
import net.pokereport.luna.ui.Iconos;

/**
 * EL CARTEL FLOTANTE SOBRE CADA LÍDER DE GIMNASIO.
 *
 * <p>Muestra de forma clara y estilizada la información esencial del líder:
 * nombre, medalla/tipo, rango de nivel de combate, medallas requeridas y el
 * botón de interacción con glifo de clic derecho.
 *
 * <h2>Estilo visual unificado (Oak y Torre de Batalla)</h2>
 * <ul>
 *   <li>Fondo: negro azulado de alto contraste al 90 % ({@code 0xE6060B14}).
 *       Legible frente a cualquier bloque, iluminación o sombreado.</li>
 *   <li>Raíz vacía {@link Text#empty()} para que los colores no tiñan el glifo.</li>
 *   <li>Icono {@link Iconos#clicDerecho()} con su texto acompañante.</li>
 * </ul>
 */
public final class Cartel {

    private Cartel() {}

    /** La etiqueta que llevan todos los carteles de gimnasio. */
    public static final String MARCA = "luna_cartel";

    /** Fondo: negro azulado al 90 % de opacidad. */
    private static final int FONDO = 0xE6060B14;

    /** Ancho de línea antes de partir texto. */
    private static final int ANCHO = 240;

    /** Alcance visual relativo (multiplicador de 64): ~54 bloques. */
    private static final float ALCANCE = 0.85f;

    /** Cuánto por encima de los pies del líder flota el texto. */
    private static final double ALTURA = 2.45;

    /** El radio que se barre al limpiar (3.5 para no alcanzar líderes vecinos en Ciudadela). */
    private static final double RADIO = 3.5;

    /** Etiqueta específica por líder. */
    public static String marcaDe(Gimnasio_ g) {
        return "luna_cartel_" + g.id();
    }

    /**
     * Coloca el cartel para la recepción en la Ciudadela.
     */
    public static void poner(ServerWorld mundo, Gimnasio_ g, Vec3d pies) {
        poner(mundo, g, pies, false);
    }

    /**
     * Coloca el cartel estilizado sobre un líder, en recepción o arena.
     */
    /**
     * Coloca el cartel estilizado sobre un líder, en recepción o arena.
     */
    public static void poner(ServerWorld mundo, Gimnasio_ g, Vec3d pies, boolean enArena) {
        var caja = Box.of(new Vec3d(pies.x, pies.y + ALTURA, pies.z), 5.0, 5.0, 5.0);
        String tagG = marcaDe(g);
        String liderNom = g.lider().toLowerCase(java.util.Locale.ROOT);
        DisplayEntity.TextDisplayEntity existente = null;

        for (var text : mundo.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, caja, x -> true)) {
            if (text.getCommandTags().contains(tagG) || text.getCommandTags().contains(MARCA)) {
                if (existente == null && text.getCommandTags().contains(tagG)) {
                    existente = text;
                } else {
                    text.discard();
                }
            } else {
                try {
                    Text txt = text.getText();
                    if (txt != null && txt.getString().toLowerCase(java.util.Locale.ROOT).contains(liderNom)) {
                        text.discard();
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (existente != null) {
            existente.setPosition(pies.x, pies.y + ALTURA, pies.z);
            existente.setText(texto(g, enArena));
            existente.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
            existente.setBackground(FONDO);
            existente.setLineWidth(ANCHO);
            existente.setViewRange(ALCANCE);
            existente.setNoGravity(true);
            return;
        }

        var cartel = EntityType.TEXT_DISPLAY.create(mundo);
        if (cartel == null) {
            LunaEternal.LOG.warn("No se pudo crear el cartel de {}", g.id());
            return;
        }
        cartel.setPosition(pies.x, pies.y + ALTURA, pies.z);
        cartel.setText(texto(g, enArena));
        cartel.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        cartel.setBackground(FONDO);
        cartel.setLineWidth(ANCHO);
        cartel.setViewRange(ALCANCE);
        cartel.setNoGravity(true);
        cartel.addCommandTag(MARCA);
        cartel.addCommandTag(marcaDe(g));
        mundo.spawnEntity(cartel);
    }

    /** Quita los carteles de ese líder que haya cerca de ese punto. */
    public static void quitar(ServerWorld mundo, Gimnasio_ g, Vec3d donde) {
        var caja = Box.of(new Vec3d(donde.x, donde.y + ALTURA, donde.z), 5.0, 5.0, 5.0);
        String tagG = marcaDe(g);
        String liderNom = g.lider().toLowerCase(java.util.Locale.ROOT);

        for (var text : mundo.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, caja, x -> true)) {
            if (text.getCommandTags().contains(tagG) || text.getCommandTags().contains(MARCA)) {
                text.discard();
            } else {
                try {
                    Text txt = text.getText();
                    if (txt != null && txt.getString().toLowerCase(java.util.Locale.ROOT).contains(liderNom)) {
                        text.discard();
                    }
                } catch (Throwable ignored) {}
            }
        }
    }


    /**
     * Construye el texto estilizado del cartel del líder.
     */
    public static MutableText texto(Gimnasio_ g, boolean enArena) {
        MutableText t = Text.empty();

        // 1. Título principal: Nombre con adornos dorados
        String titulo = g.campeon()
                ? "✦ CAMPEÓN " + g.lider().toUpperCase(java.util.Locale.ROOT) + " ✦\n"
                : "✦ LÍDER " + g.lider().toUpperCase(java.util.Locale.ROOT) + " ✦\n";
        t.append(Text.literal(titulo).formatted(Formatting.GOLD, Formatting.BOLD));

        // 2. Gimnasio y Medalla
        String subtitulo = (g.campeon() ? "Liga " + g.region().nombre : "Gimnasio " + g.medalla()) + "\n";
        t.append(Text.literal(subtitulo).formatted(Formatting.WHITE));

        // 3. Nivel de combate (escala N-1 y N, ej: 14 - 15)
        int nivelMin = Math.max(1, g.nivel() - 1);
        t.append(Text.literal("⚔ Combate a Nv. " + nivelMin + " - " + g.nivel())
                .formatted(Formatting.AQUA));
        t.append(Text.literal(" §7(Tope Nv. " + g.nivel() + ")\n"));

        // 4. Requisitos de medallas (en recepción)
        if (!enArena) {
            t.append(Text.literal("Requisito: ").formatted(Formatting.GRAY));
            if (g.medallas() == 0) {
                t.append(Text.literal("Sin medallas previas\n").formatted(Formatting.GREEN));
            } else {
                t.append(Text.literal(g.medallas() + (g.medallas() == 1 ? " medalla\n" : " medallas\n"))
                        .formatted(Formatting.YELLOW));
            }
        }

        // 5. Estado de construcción
        if (!Gimnasio.construido(g)) {
            t.append(Text.literal("(Próximamente disponible)\n").formatted(Formatting.DARK_GRAY));
        }

        // 6. Indicador de clic con icono personalizado
        if (enArena) {
            t.append(Iconos.clicDerecho())
             .append(Text.literal(" ¡Clic derecho para luchar!").formatted(Formatting.RED, Formatting.BOLD));
        } else {
            t.append(Iconos.clicDerecho())
             .append(Text.literal(" Clic derecho para hablar").formatted(Formatting.AQUA));
        }

        return t;
    }
}
