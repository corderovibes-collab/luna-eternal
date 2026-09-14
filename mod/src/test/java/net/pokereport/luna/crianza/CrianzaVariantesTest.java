package net.pokereport.luna.crianza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A09: Herencia de variantes regionales, habilidades ocultas y movimientos huevo.
 */
public class CrianzaVariantesTest {

    @Test
    @DisplayName("Madre con Everstone y aspecto regional transmite su forma al bebe")
    void testDeterminarAspectoRegionalMadreConEverstone() {
        Set<String> aspectosMadre = Set.of("alolan");
        Set<String> aspectosPadre = Set.of();

        String aspecto = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, true, false,
                aspectosPadre, false, false,
                0.1
        );

        assertEquals("alolan", aspecto, "La cria debe heredar la forma Alola de la madre con Everstone");
    }

    @Test
    @DisplayName("Madre regional sin Everstone no transmite forma (revierte a forma canonica)")
    void testDeterminarAspectoRegionalSinEverstoneRevierteAComun() {
        Set<String> aspectosMadre = Set.of("alolan");
        Set<String> aspectosPadre = Set.of();

        String aspecto = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, false, false,
                aspectosPadre, false, false,
                0.1
        );

        assertNull(aspecto, "Sin Everstone, la cria debe revertir a la forma base estandar");
    }

    @Test
    @DisplayName("Padre regional con Everstone y Ditto transmite su forma regional")
    void testDeterminarAspectoRegionalPadreConDittoYEverstone() {
        Set<String> aspectosMadre = Set.of();
        Set<String> aspectosPadre = Set.of("galarian");

        String aspecto = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, false, true, // Madre es Ditto
                aspectosPadre, true, false,  // Padre con Everstone
                0.1
        );

        assertEquals("galarian", aspecto, "Con Ditto, el padre con Everstone debe transmitir su forma Galar");
    }

    @Test
    @DisplayName("Ditto con Everstone no transmite aspectos como si fuera progenitor de especie")
    void testDeterminarAspectoRegionalDittoNoTransmiteAspecto() {
        Set<String> aspectosMadre = Set.of("alolan");
        Set<String> aspectosPadre = Set.of();

        String aspecto = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, true, true,  // Madre es Ditto con aspecto hipotetico
                aspectosPadre, false, false, // Padre sin Everstone
                0.1
        );

        assertNull(aspecto, "Ditto no debe transmitir su aspecto regional a la cria");
    }

    @Test
    @DisplayName("Ambos progenitores con Everstone y formas distintas resuelven 50/50")
    void testDeterminarAspectoRegionalAmbosConEverstone() {
        Set<String> aspectosMadre = Set.of("alolan");
        Set<String> aspectosPadre = Set.of("galarian");

        String aspectoMadre = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, true, false,
                aspectosPadre, true, false,
                0.2 // RNG < 0.5 -> Madre
        );
        assertEquals("alolan", aspectoMadre);

        String aspectoPadre = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, true, false,
                aspectosPadre, true, false,
                0.8 // RNG >= 0.5 -> Padre
        );
        assertEquals("galarian", aspectoPadre);
    }

    @Test
    @DisplayName("Aspectos no regionales (cosmeticos, sombrios, etc.) no son transmitidos por Everstone")
    void testAspectosNoRegionalesNoHeredan() {
        Set<String> aspectosMadre = Set.of("cosmetico_sombrero", "gigante", "custom_skin");

        String aspecto = CrianzaReglas.determinarAspectoRegional(
                aspectosMadre, true, false,
                Set.of(), false, false,
                0.5
        );

        assertNull(aspecto, "Solo aspectos regionales (alolan, galarian, hisuian, paldean) deben ser heredables");
    }

    @Test
    @DisplayName("Madre con Habilidad Oculta puede transmitirla")
    void testHerenciaHabilidadOcultaMadre() {
        boolean puede = CrianzaReglas.puedeTransmitirHabilidadOculta(true, false, false, false);
        assertTrue(puede, "La madre con Habilidad Oculta siempre puede transmitirla");
    }

    @Test
    @DisplayName("Padre con Habilidad Oculta NO puede transmitirla sin Ditto")
    void testHerenciaHabilidadOcultaPadreSinDittoNoTransmite() {
        boolean puede = CrianzaReglas.puedeTransmitirHabilidadOculta(false, true, false, false);
        assertFalse(puede, "El padre con Habilidad Oculta en pareja normal no transmite su HO (regla canonica)");
    }

    @Test
    @DisplayName("Padre con Habilidad Oculta si puede transmitirla si cria con Ditto")
    void testHerenciaHabilidadOcultaPadreConDittoTransmite() {
        boolean puede = CrianzaReglas.puedeTransmitirHabilidadOculta(false, true, true, false);
        assertTrue(puede, "El macho o sin genero transmite su HO cuando cria con Ditto");
    }

    @Test
    @DisplayName("Ningun progenitor con HO no genera posibilidad de transmitir HO")
    void testHerenciaHabilidadOcultaNingunoNoTransmite() {
        boolean puede = CrianzaReglas.puedeTransmitirHabilidadOculta(false, false, false, false);
        assertFalse(puede, "Sin progenitores con HO no debe haber posibilidad de heredarla");
    }

    @Test
    @DisplayName("Filtrado de movimientos huevo de ambos padres sin duplicados y solo movimientos legales")
    void testFiltrarMovimientosHuevo() {
        List<String> movsPadre = List.of("Hydro Pump", "Tackle", "Aqua Jet");
        List<String> movsMadre = List.of("Growl", "Aqua Jet", "Belly Drum");
        Set<String> eggMovesValidos = Set.of("aqua jet", "belly drum", "dragon dance");

        List<String> heredados = CrianzaReglas.filtrarMovimientosHuevo(movsMadre, movsPadre, eggMovesValidos);

        assertEquals(2, heredados.size(), "Debe heredar exactamente 2 movimientos huevo validos");
        assertTrue(heredados.contains("Aqua Jet"), "Debe heredar Aqua Jet");
        assertTrue(heredados.contains("Belly Drum"), "Debe heredar Belly Drum");
        assertFalse(heredados.contains("Hydro Pump"), "Hydro Pump no es egg move");
        assertFalse(heredados.contains("Tackle"), "Tackle no es egg move");
        assertFalse(heredados.contains("Growl"), "Growl no es egg move");
    }
}
