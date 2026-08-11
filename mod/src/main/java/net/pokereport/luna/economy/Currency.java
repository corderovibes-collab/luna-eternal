package net.pokereport.luna.economy;

/**
 * Las tres monedas del servidor (ECO-001 §2).
 *
 * <p><b>Regla roja del proyecto:</b> ninguna se convierte en otra, en ninguna
 * dirección y bajo ninguna circunstancia. De esa única regla salen las tres
 * garantías del diseño:
 *
 * <ul>
 *   <li>no se puede comprar poder — el ReportCoin no llega al PokéDólar</li>
 *   <li>no se puede comprar progresión — las Marcas solo se ganan jugando</li>
 *   <li>pagar no infla la economía — el ReportCoin no entra en el mercado</li>
 * </ul>
 *
 * <p>El día que exista una conversión, las tres son una sola moneda y todo el
 * diseño económico deja de tener efecto.
 */
public enum Currency {

    /** Transferible. La economía real: se gana jugando y se comercia. */
    POKEDOLLAR("PokéDólares", "§6", true, false),

    /**
     * Vinculada al jugador. Se gana con logros y descubrimientos; paga
     * desbloqueos de progresión. No se comercia — por eso no puede inflar
     * nada, y por eso la progresión no se compra.
     */
    MARK("Marcas", "§b", false, false),

    /**
     * Premium. Se compra con dinero real y solo paga identidad y comodidad.
     *
     * <p><b>No es transferible a propósito.</b> Si lo fuera, los jugadores lo
     * venderían por PokéDólares y habríamos creado la conversión prohibida por
     * la puerta de atrás, sin decidirlo nadie.
     */
    REPORTCOIN("ReportCoins", "§e", false, true);

    public final String displayName;
    /** Color de chat, para que cada moneda se reconozca de un vistazo. */
    public final String color;
    /** ¿Puede moverse entre jugadores? */
    public final boolean tradeable;
    /** ¿Se adquiere con dinero real? */
    public final boolean purchasable;

    Currency(String displayName, String color, boolean tradeable, boolean purchasable) {
        this.displayName = displayName;
        this.color = color;
        this.tradeable = tradeable;
        this.purchasable = purchasable;
    }

    public String format(long amount) {
        return color + String.format("%,d", amount) + " " + displayName + "§r";
    }
}
