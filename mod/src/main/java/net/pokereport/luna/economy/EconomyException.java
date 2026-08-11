package net.pokereport.luna.economy;

/** Fallo esperado de una operación económica. No es un bug: es una regla. */
public class EconomyException extends Exception {

    public enum Kind {
        /** El jugador no tiene suficiente saldo. */
        INSUFFICIENT_FUNDS,
        /** La operación ya se ejecutó (misma clave de idempotencia, R4). */
        ALREADY_APPLIED,
        /** La moneda no permite esta operación (p. ej. comerciar Marcas). */
        NOT_TRADEABLE,
        /** Importe inválido (cero o negativo donde no procede). */
        INVALID_AMOUNT
    }

    public final Kind kind;

    public EconomyException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }
}
