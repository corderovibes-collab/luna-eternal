package net.pokereport.luna.progression;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Las cinco Vías (PROG-001).
 *
 * <p>Deliberadamente <b>no existe un "nivel de jugador"</b>. Un número único
 * comprimiría a todos en la misma escala, convertiría cada actividad en
 * XP/hora y se terminaría al llegar al tope. Cinco reputaciones independientes
 * hacen que el progreso sea un <i>perfil</i>, no una cifra — y que dos
 * jugadores con el mismo tiempo sean personas distintas, que es la
 * precondición del comercio.
 */
public enum Path {

    EXPLORADOR("Explorador", "§a", Items.FILLED_MAP,
        "Descubrimientos, biomas y rutas",
        "Zonas remotas, viaje rápido"),

    ENTRENADOR("Entrenador", "§c", Items.IRON_SWORD,
        "Combates, medallas e incursiones",
        "Retos, PvP, contenido de grupo"),

    COLECCIONISTA("Coleccionista", "§b", Items.ENCHANTED_BOOK,
        "Pokédex, especies raras y formas",
        "Especies exclusivas, almacenamiento"),

    COMERCIANTE("Comerciante", "§6", Items.EMERALD,
        "Volumen y variedad de operaciones",
        "Funciones del GTS, historial de precios"),

    CRIADOR("Criador", "§d", Items.TURTLE_EGG,
        "Cría, IV/EV y linajes",
        "Competitivo, guardería avanzada");

    public static final int MAX_LEVEL = 5;

    public final String displayName;
    public final String color;
    public final Item icon;
    /** Cómo se sube. */
    public final String howToRaise;
    /** Qué abre. */
    public final String unlocks;

    Path(String displayName, String color, Item icon, String howToRaise, String unlocks) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.howToRaise = howToRaise;
        this.unlocks = unlocks;
    }

    /**
     * XP necesaria para pasar de {@code level} al siguiente.
     *
     * <p>Crece rápido a propósito: la lentitud del diseño no viene de que cada
     * nivel cueste mucho, sino de que <b>hay más vías que tiempo</b>. Aun así,
     * los últimos niveles deben ser un compromiso real.
     *
     * <p>Cifras sin calibrar: son un punto de partida, no un balance.
     */
    public static long xpForNextLevel(int level) {
        return switch (level) {
            case 0 -> 100;
            case 1 -> 400;
            case 2 -> 1_200;
            case 3 -> 3_000;
            case 4 -> 7_500;
            default -> Long.MAX_VALUE;   // nivel 5: no hay siguiente
        };
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> "—";
        };
    }
}
