package net.pokereport.luna.ui;

import net.pokereport.luna.economy.Currency;

import java.util.EnumMap;
import java.util.Map;

/**
 * Fotografía del estado del jugador para dibujar interfaces.
 *
 * <p>Existe para cumplir una regla dura: <b>los menús no consultan la base de
 * datos</b>. Los datos se cargan una vez en el hilo de E/S y se pasan ya
 * resueltos, así que dibujar un menú nunca bloquea el hilo del servidor
 * ({@code docs/technical/data-model.md} §4).
 */
public final class PlayerSnapshot {

    public final long playerId;
    public final String username;
    private final Map<Currency, Long> balances = new EnumMap<>(Currency.class);

    /** Fase lunar 0-7 del mundo. 0 = llena, 4 = nueva. */
    public int moonPhase;
    public boolean night;

    /** Estado de las cinco vías. */
    public java.util.Map<net.pokereport.luna.progression.Path,
                         net.pokereport.luna.progression.ProgressionService.PathState> paths
        = new java.util.EnumMap<>(net.pokereport.luna.progression.Path.class);

    /** Vía dominante, derivada de {@link #paths}. */
    public String dominantPath = "Sin definir";
    public int dominantLevel = 0;
    public String clan;
    public String job;
    public int badges;

    /** Últimos movimientos, para la Cartera. */
    public java.util.List<net.pokereport.luna.economy.EconomyService.Entry> recent =
        java.util.List.of();

    public PlayerSnapshot(long playerId, String username) {
        this.playerId = playerId;
        this.username = username;
        for (Currency c : Currency.values()) balances.put(c, 0L);
    }

    public long balance(Currency c) {
        return balances.getOrDefault(c, 0L);
    }

    public void setBalance(Currency c, long amount) {
        balances.put(c, amount);
    }

    /** Nombre legible de la fase lunar, tal y como la cuenta Minecraft. */
    public String moonName() {
        return switch (moonPhase) {
            case 0 -> "Luna llena";
            case 1 -> "Gibosa menguante";
            case 2 -> "Cuarto menguante";
            case 3 -> "Creciente menguante";
            case 4 -> "Luna nueva";
            case 5 -> "Creciente";
            case 6 -> "Cuarto creciente";
            default -> "Gibosa creciente";
        };
    }

    /**
     * Lo que el jugador ve sobre la luna. Es deliberadamente vago hasta que
     * conozca el mundo: el conocimiento es progresión, no un dato regalado
     * ({@code docs/game-design/vision.md} §3.1).
     */
    public String moonHint(boolean knowsTheWorld) {
        if (!knowsTheWorld) return "§8Algo cambia con la luna…";
        return switch (moonPhase) {
            case 0 -> "§fCriaturas que no salen ninguna otra noche.";
            case 4 -> "§8La oscuridad atrae a lo que rehúye la luz.";
            default -> "§7El mundo cambia con cada fase.";
        };
    }
}
