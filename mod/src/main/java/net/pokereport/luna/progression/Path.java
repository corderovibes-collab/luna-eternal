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
        "Competitivo, guardería avanzada"),

    // ---- los OFICIOS (V012) --------------------------------------------
    //
    // ⚠ VAN DETRAS DE LOS CINCO ORIGINALES Y NO SE REORDENAN NUNCA. El ENUM
    //   de MariaDB guarda el INDICE, no el texto: cambiar el orden convertiria
    //   a todos los Exploradores en Entrenadores, en silencio.
    //
    // ⚠ Y COCINERO NO ESTA. Cobblemon 1.7 tiene olla de cocina pero no publica
    //   ningun evento para ella --se revisaron sus 98--, asi que engancharlo
    //   pide un mixin dentro de su codigo. Declararlo ahora dejaria un oficio
    //   que NUNCA da XP, que es el fallo silencioso de siempre. Entra cuando
    //   entre su enganche.

    MINERO("Minero", "§7", Items.IRON_PICKAXE,
        "Picar menas y piedra",
        "Mejores vetas, pagas mayores"),

    PESCADOR("Pescador", "§9", Items.FISHING_ROD,
        "Pescar con caña Poké",
        "Cañas y cebos, pagas mayores"),

    AGRICULTOR("Agricultor", "§2", Items.WHEAT,
        "Cosechar bayas y bellotas",
        "Semillas raras, pagas mayores");

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

    /**
     * Plata que se paga al subir UN nivel de esta Vía.
     *
     * <p>⚠ <b>Solo los OFICIOS pagan.</b> Las cinco Vías originales
     * —Explorador, Entrenador, Coleccionista, Comerciante, Criador— desbloquean
     * <i>contenido</i>; los oficios dan <i>dinero</i>. Mezclarlo haría que subir
     * de Vía fuera una fuente de ingresos, y P3 dice sumideros antes que
     * fuentes: una economía sin sitios donde gastar se infla sola.
     *
     * <p>⚠ Crece con el nivel porque el esfuerzo crece: el salto de IV a V cuesta
     * 7.500 XP y el de 0 a I cuesta 100. Pagar lo mismo por los dos haría que
     * nadie pasara del segundo nivel.
     *
     * <p><b>Cifras sin calibrar</b>, como toda la economía: son un punto de
     * partida. {@code /luna economia} dirá si sobra o falta cuando alguien juegue.
     */
    public long plataPorNivel(int nivelAlcanzado) {
        if (!esOficio()) {
            return 0;
        }
        return switch (nivelAlcanzado) {
            case 1 -> 500;
            case 2 -> 1_500;
            case 3 -> 4_000;
            case 4 -> 10_000;
            case 5 -> 25_000;
            default -> 0;
        };
    }

    /** Un oficio se trabaja y paga; una Vía se recorre y desbloquea. */
    public boolean esOficio() {
        return this == MINERO || this == PESCADOR || this == AGRICULTOR;
    }

    /** Los oficios, en orden. Es lo que hay que completar para el premio. */
    public static java.util.List<Path> oficios() {
        var salida = new java.util.ArrayList<Path>();
        for (Path p : values()) {
            if (p.esOficio()) {
                salida.add(p);
            }
        }
        return java.util.List.copyOf(salida);
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> "—";
        };
    }
}
