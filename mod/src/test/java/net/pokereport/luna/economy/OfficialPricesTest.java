package net.pokereport.luna.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.pokereport.luna.cosmetics.Catalogo;
import net.pokereport.luna.crate.Cofre;
import net.pokereport.luna.kit.EntitlementService;

class OfficialPricesTest {
    @Test void preciosCosmeticosSonLosOficiales() {
        for (var p : Catalogo.todas()) {
            if (p.precio() == 0) continue;
            int esperado = switch (p.categoria()) {
                case Catalogo.AURAS -> 50;
                case Catalogo.SOMBREROS -> 100;
                case Catalogo.MASCOTAS -> 250;
                default -> fail("Categoría desconocida " + p.categoria());
            };
            assertEquals(esperado, p.precio(), p.id());
        }
    }

    @Test void llavesPremiumTienenPrecioOficial() {
        assertEquals(1500, Cofre.de("legendario").precio());
        assertEquals(2500, Cofre.de("legendario_shiny").precio());
    }

    @Test void bypassSoloContieneLosUuidAutorizados() {
        var e = new EntitlementService();
        assertTrue(e.bypass(UUID.fromString("432ef323-8ac3-3ba3-8175-aaf88c5589cf")));
        assertTrue(e.bypass(UUID.fromString("2387efe1-8498-3d4d-8b98-4aff8b50790c")));
        assertFalse(e.bypass(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }
}
