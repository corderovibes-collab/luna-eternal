package net.pokereport.luna.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A06: Configuración coherente de EasyAuth en modo offline.
 */
public class EasyAuthConfigTest {

    @Test
    @DisplayName("EasyAuth main.conf configura premium-auto-login=false y vanish-until-auth=false para coherencia con online-mode=false")
    void testEasyAuthMainConfig() throws Exception {
        Path confPath = Path.of("../build/auditoria-forense/snapshot/servidor/config/EasyAuth/main.conf");
        if (!Files.exists(confPath)) {
            confPath = Path.of("build/auditoria-forense/snapshot/servidor/config/EasyAuth/main.conf");
        }
        assertTrue(Files.exists(confPath), "EasyAuth main.conf debe existir");
        String content = Files.readString(confPath);

        assertTrue(content.contains("premium-auto-login=false"),
                "premium-auto-login debe ser false cuando online-mode=false en server.properties");
        assertTrue(content.contains("vanish-until-auth=false"),
                "vanish-until-auth debe ser false al no estar instalado el mod Vanish");
        assertTrue(content.contains("hide-player-coords=true"),
                "hide-player-coords debe mantenerse activo para proteger coordenadas");
        assertTrue(content.contains("dimension=\"lunaeternal:lobby\""),
                "world-spawn debe estar configurado en lunaeternal:lobby");
    }
}
