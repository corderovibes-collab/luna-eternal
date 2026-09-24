package net.pokereport.luna.buhonero;

import java.util.Set;

/** Reglas de negocio compartidas; todos los plazos usan milisegundos reales. */
public final class ReglasMercadoNegro {
    public static final long DIA = 86_400_000L;
    public static final long PRECIO = 10_000L;
    private static final Set<Integer> EXCLUIDOS = Set.of(144,145,146,150,151,243,244,245,249,250,251);
    private ReglasMercadoNegro() {}
    public static boolean especiePermitida(int dex) { return dex >= 1 && dex <= 251 && !EXCLUIDOS.contains(dex); }
    public static boolean nivelPermitido(int nivel) { return nivel >= 1 && nivel <= 20; }
    public static long cicloActual(long inicio, long ahora) {
        return inicio + Math.max(0, (ahora - inicio) / DIA) * DIA;
    }
    public static boolean puedeComprar(long ciclo, long ultimaCompra, long ahora) {
        return ahora >= ciclo && ahora < ciclo + DIA && (ultimaCompra == 0 || ahora >= ultimaCompra + DIA);
    }
}
