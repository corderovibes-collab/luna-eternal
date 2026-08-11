package net.pokereport.luna.ui;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Estados de cualquier elemento de interfaz (brief §27,
 * {@code docs/ui/navigation.md} §5).
 *
 * <p>La regla: <b>nada falla en silencio y nada se oculta</b>. Un elemento
 * bloqueado se ve, se explica y dice qué hacer para desbloquearlo. Un icono que
 * simplemente no aparece deja al jugador sin saber que existe; uno que aparece
 * y da error le deja sin saber por qué.
 */
public enum LockState {

    /** Disponible. Se puede usar ahora. */
    AVAILABLE("§a", Items.LIME_STAINED_GLASS_PANE, null),

    /** Bloqueado por progresión. Debe explicar el requisito. */
    LOCKED("§7", Items.GRAY_STAINED_GLASS_PANE, "§c🔒 Bloqueado"),

    /** Desbloqueándose (procesos con duración). */
    UNLOCKING("§e", Items.YELLOW_STAINED_GLASS_PANE, "§e⏳ Desbloqueando…"),

    /** Recién desbloqueado: se resalta hasta que el jugador lo vea. */
    UNLOCKED("§b", Items.LIGHT_BLUE_STAINED_GLASS_PANE, "§b✦ ¡Nuevo!"),

    /** En espera. Debe decir cuánto queda. */
    COOLDOWN("§6", Items.ORANGE_STAINED_GLASS_PANE, "§6⏱ En espera"),

    /** Apagado por el servidor. Debe decir el motivo. */
    DISABLED("§8", Items.BLACK_STAINED_GLASS_PANE, "§8✖ No disponible"),

    /** Algo ha fallado. Debe decir qué hacer. */
    ERROR("§c", Items.RED_STAINED_GLASS_PANE, "§c⚠ Error");

    public final String color;
    /** Panel de relleno asociado, para marcos y separadores. */
    public final Item pane;
    /** Etiqueta que se añade a la descripción, o {@code null} si no procede. */
    public final String label;

    LockState(String color, Item pane, String label) {
        this.color = color;
        this.pane = pane;
        this.label = label;
    }

    /** ¿Responde al clic? Solo lo disponible y lo recién desbloqueado. */
    public boolean clickable() {
        return this == AVAILABLE || this == UNLOCKED;
    }
}
