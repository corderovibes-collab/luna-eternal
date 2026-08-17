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
    REPORTCOIN("LunaCoins", "§e", false, true);

    /**
     * Nombre que ve el jugador. <b>No es final a propósito:</b> se puede
     * cambiar desde la configuración sin tocar la base de datos.
     *
     * <p><b>Decidido el 2026-08-16: la moneda premium se llama «LunaCoins».</b>
     * El identificador interno sigue siendo {@code REPORTCOIN} y no se toca —
     * está en la base de datos. Separar el nombre visible del interno convirtió
     * esa decisión en esta línea en vez de una migración de esquema (D-018), que
     * era exactamente para lo que se separó.
     *
     * <p><b>Y en el PokePad solo se enseñan DOS monedas</b>, PokéDólares y
     * LunaCoins. Las Marcas siguen existiendo —son lo que impide que la
     * progresión se compre— pero no salen en la pantalla principal.
     */
    public String displayName;
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

    /** Aplica los nombres visibles de la configuración. */
    public static void applyDisplayNames(String pokedollar, String mark, String premium) {
        if (pokedollar != null && !pokedollar.isBlank()) POKEDOLLAR.displayName = pokedollar;
        if (mark != null && !mark.isBlank()) MARK.displayName = mark;
        if (premium != null && !premium.isBlank()) REPORTCOIN.displayName = premium;
    }

    public String format(long amount) {
        return color + String.format("%,d", amount) + " " + displayName + "§r";
    }
}
