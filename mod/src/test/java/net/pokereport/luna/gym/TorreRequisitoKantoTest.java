package net.pokereport.luna.gym;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para el requisito de acceso a la Torre de Batalla:
 * 8 medallas de Gimnasio de Kanto + Campeón de Kanto (Blue).
 */
public class TorreRequisitoKantoTest {

    @AfterEach
    void tearDown() {
        MedallaService.olvidarTodo();
    }

    @Test
    @DisplayName("Sin medallas: acceso denegado y 9 retos pendientes")
    void testSinMedallas() {
        assertFalse(MedallaService.tieneKantoCompleto(0));
        List<Gimnasio.Gimnasio_> pendientes = MedallaService.kantoPendientes(0);
        assertEquals(9, pendientes.size(), "Kanto debe requerir exactamente 8 líderes + 1 campeón");
    }

    @Test
    @DisplayName("8 líderes derrotados pero falta el Campeón Blue: acceso denegado")
    void testSoloLideresSinCampeon() {
        // Bits 0 a 7 activados (Brock hasta Giovanni)
        int mascara8Lideres = 0;
        for (int i = 0; i < 8; i++) {
            mascara8Lideres |= (1 << i);
        }

        assertFalse(MedallaService.tieneKantoCompleto(mascara8Lideres),
                "Tener solo los 8 líderes sin el Campeón no debe permitir el acceso");

        List<Gimnasio.Gimnasio_> pendientes = MedallaService.kantoPendientes(mascara8Lideres);
        assertEquals(1, pendientes.size());
        assertEquals("campeon_kanto", pendientes.get(0).id());
        assertEquals("Blue", pendientes.get(0).lider());
    }

    @Test
    @DisplayName("Campeón derrotado pero faltan líderes: acceso denegado")
    void testCampeonSinLideres() {
        Gimnasio.Gimnasio_ campeon = Gimnasio.de("campeon_kanto");
        assertNotNull(campeon);
        int mascaraCampeon = 1 << campeon.sala();

        assertFalse(MedallaService.tieneKantoCompleto(mascaraCampeon));
        List<Gimnasio.Gimnasio_> pendientes = MedallaService.kantoPendientes(mascaraCampeon);
        assertEquals(8, pendientes.size(), "Deben faltar los 8 líderes");
    }

    @Test
    @DisplayName("8 líderes de Kanto + Campeón de Kanto: acceso permitido")
    void testKantoCompleto() {
        int mascaraCompleta = 0;
        for (Gimnasio.Gimnasio_ g : Gimnasio.deRegion(Gimnasio.Region.KANTO)) {
            mascaraCompleta |= (1 << g.sala());
        }

        assertTrue(MedallaService.tieneKantoCompleto(mascaraCompleta),
                "Con los 8 líderes y el Campeón, el acceso debe ser concedido");
        assertTrue(MedallaService.kantoPendientes(mascaraCompleta).isEmpty(),
                "No debe haber ningún reto pendiente");
    }

    @Test
    @DisplayName("Validación con UUID a través de la caché de MedallaService")
    void testValidacionConUuid() {
        UUID jugadorId = UUID.randomUUID();
        assertFalse(MedallaService.tieneKantoCompleto(jugadorId));

        int mascaraCompleta = 0;
        for (Gimnasio.Gimnasio_ g : Gimnasio.deRegion(Gimnasio.Region.KANTO)) {
            mascaraCompleta |= (1 << g.sala());
        }

        MedallaService.ponerEnCache(jugadorId, mascaraCompleta);
        assertTrue(MedallaService.tieneKantoCompleto(jugadorId),
                "El método por UUID debe consultar la caché y conceder acceso");
    }
}
