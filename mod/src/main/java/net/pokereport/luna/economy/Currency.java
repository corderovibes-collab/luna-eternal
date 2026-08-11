package net.pokereport.luna.economy;

/**
 * Las dos monedas del servidor (ECO-001 §2).
 *
 * <p><b>Regla dura:</b> no existe conversión entre ellas en ninguna dirección.
 * El día que exista, son una sola moneda y el diseño deja de funcionar.
 */
public enum Currency {

    /** Transferible. La economía real: se gana, se gasta y se comercia. */
    POKEDOLLAR("PokeDolares", true),

    /**
     * Vinculada al jugador. Se gana con logros y descubrimientos.
     * No se comercia, no se deposita, no se regala: por eso no puede
     * causar inflación — nunca entra en el mercado.
     */
    MARK("Marcas", false);

    public final String displayName;
    public final boolean tradeable;

    Currency(String displayName, boolean tradeable) {
        this.displayName = displayName;
        this.tradeable = tradeable;
    }
}
