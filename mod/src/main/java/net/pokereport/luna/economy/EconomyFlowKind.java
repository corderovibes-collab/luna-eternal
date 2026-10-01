package net.pokereport.luna.economy;

/** Naturaleza contable de un asiento; el signo por sí solo no la determina. */
public enum EconomyFlowKind {
    /** Moneda nueva que entra desde un sistema de juego. */
    FAUCET,
    /** Moneda que desaparece por un coste, tasa o impuesto. */
    SINK,
    /** Movimiento entre dos propietarios; no cambia la oferta. */
    TRANSFER,
    /** Entrada o salida temporal de custodia; no cambia la oferta. */
    ESCROW,
    /** Reversión de una operación previa; no es un faucet orgánico. */
    REFUND,
    /** Compra o gasto de moneda premium. */
    PREMIUM,
    /** Operación administrativa, excluida de KPIs orgánicos. */
    ADMIN,
    /** Autotest o dato de laboratorio. */
    TEST,
    /** Conversión/migración histórica. */
    MIGRATION
}
