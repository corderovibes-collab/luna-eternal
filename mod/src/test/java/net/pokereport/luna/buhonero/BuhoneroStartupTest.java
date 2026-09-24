package net.pokereport.luna.buhonero;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Evita que una integración conserve el NPC y la UI pero pierda su servicio servidor. */
public class BuhoneroStartupTest {
    @Test
    @DisplayName("LunaEternal inicia el servicio servidor del Buhonero")
    void servicioRegistradoEnElArranque() throws Exception {
        Path path = Path.of("src/main/java/net/pokereport/luna/LunaEternal.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/main/java/net/pokereport/luna/LunaEternal.java");
        }
        assertTrue(Files.exists(path), "LunaEternal.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains("BuhoneroService.registrar()"),
                "Registrar los payloads no basta: el servidor debe iniciar el worker y los hooks del Buhonero");
    }

    @Test
    @DisplayName("LunaCliente registra el receptor que abre la pantalla del Buhonero")
    void receptorRegistradoEnElCliente() throws Exception {
        Path path = Path.of("src/client/java/net/pokereport/luna/client/LunaCliente.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/client/java/net/pokereport/luna/client/LunaCliente.java");
        }
        assertTrue(Files.exists(path), "LunaCliente.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains("BuhoneroScreen.registrar()"),
                "El cliente debe registrar el receptor buhonero_oferta para anunciarlo durante el login");
    }
}
