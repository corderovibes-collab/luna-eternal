package net.pokereport.luna.buhonero;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.pokereport.luna.buhonero.ReglasMercadoNegro.*;
class ReglasMercadoNegroTest {
    @Test void excluyeLegendariosMiticosYTerceraGeneracion() {
        for(int n:new int[]{0,144,145,146,150,151,243,244,245,249,250,251,252,1000}) assertFalse(especiePermitida(n),"dex "+n);
        for(int n:new int[]{1,18,122,149,201,248}) assertTrue(especiePermitida(n));
        assertEquals(240,java.util.stream.IntStream.rangeClosed(1,251).filter(ReglasMercadoNegro::especiePermitida).count());
    }
    @Test void nivelesDelUnoAlVeinte() {
        assertFalse(nivelPermitido(0));assertFalse(nivelPermitido(21));
        for(int n=1;n<=20;n++)assertTrue(nivelPermitido(n));
        assertEquals(10000,PRECIO);
    }
    @Test void rotacionRealSinReinicioDelPlazoAlVolver() {
        long inicio=1_700_000_000_000L;
        assertEquals(inicio,cicloActual(inicio,inicio+DIA-1));
        assertEquals(inicio+DIA,cicloActual(inicio,inicio+DIA));
        assertEquals(inicio+5*DIA,cicloActual(inicio,inicio+5*DIA+321));
        assertEquals(inicio,cicloActual(inicio,inicio-500));
    }
    @Test void comprarAlFinalDelCicloNoPermiteOtraCompraInmediata() {
        long inicio=1_700_000_000_000L,ultima=inicio+DIA-1000;
        assertTrue(puedeComprar(inicio,0,ultima));
        assertFalse(puedeComprar(inicio,0,inicio+DIA));
        assertFalse(puedeComprar(inicio+DIA,ultima,inicio+DIA));
        assertFalse(puedeComprar(inicio+DIA,ultima,ultima+DIA-1));
        assertTrue(puedeComprar(inicio+DIA,ultima,ultima+DIA));
    }
}
