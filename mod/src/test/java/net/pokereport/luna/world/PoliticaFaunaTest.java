package net.pokereport.luna.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PoliticaFaunaTest {
    @Test void soloMobsVanillaNoPersonalizados() {
        assertTrue(PoliticaFauna.bloquear("minecraft",true,false,false,false));
        assertTrue(PoliticaFauna.bloquear("minecraft",true,false,false,true));
        assertFalse(PoliticaFauna.bloquear("minecraft",false,false,false,false));
        assertFalse(PoliticaFauna.bloquear("minecraft",true,true,false,false));
        assertFalse(PoliticaFauna.bloquear("cobblemon",true,false,false,false));
        assertFalse(PoliticaFauna.bloquear("mi_mod",true,false,false,false));
    }
    @Test void conservaNpcDeServicios() {
        assertTrue(PoliticaFauna.bloquear("rctmod",true,false,true,false));
        assertFalse(PoliticaFauna.bloquear("rctmod",true,false,true,true));
        assertFalse(PoliticaFauna.bloquear("rctmod",true,true,true,false));
    }
    @Test void densidadDiferenteSinAfectarOtrasDimensiones() {
        assertEquals(0.04f,PoliticaFauna.densidad("minecraft:overworld"));
        for(String id:new String[]{"salvaje","salvaje2","salvaje3","salvaje4","salvaje5","salvaje6"})
            assertEquals(3.0f,PoliticaFauna.densidad("lunaeternal:"+id));
        assertEquals(1f,PoliticaFauna.densidad("lunaeternal:ciudadela"));
        assertEquals(1f,PoliticaFauna.densidad("otro:salvaje"));

        assertTrue(PoliticaFauna.permitirPokemon("minecraft:overworld", 1, false, java.util.Set.of()));
        assertTrue(PoliticaFauna.permitirPokemon("minecraft:overworld", 251, false, java.util.Set.of()));
        assertFalse(PoliticaFauna.permitirPokemon("minecraft:overworld", 252, false, java.util.Set.of()));
        assertFalse(PoliticaFauna.permitirPokemon("lunaeternal:salvaje", 252, false, java.util.Set.of()));
        assertFalse(PoliticaFauna.permitirPokemon("minecraft:overworld", 25, true, java.util.Set.of()));
        assertFalse(PoliticaFauna.permitirPokemon("minecraft:overworld", 150, false,
                java.util.Set.of("legendary")));
        assertFalse(PoliticaFauna.permitirPokemon("minecraft:overworld", 151, false,
                java.util.Set.of("mythical")));
        assertTrue(PoliticaFauna.permitirPokemon("lunaeternal:salvaje", 150, false,
                java.util.Set.of("legendary")));
        assertTrue(PoliticaFauna.permitirPokemon("lunaeternal:salvaje6", 25, true,
                java.util.Set.of()));
        assertEquals(1.10f, PoliticaFauna.MULTIPLICADOR_ULTRARRARO_SALVAJE);
    }
}
