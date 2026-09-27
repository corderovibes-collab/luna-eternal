package net.pokereport.luna.crianza;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CrianzaConfigTest {

    @Test
    void testCobbreedingConfigDesactivado() throws Exception {
        Path packConfig = Path.of("../build/pack/overrides/config/cobbreeding/main.json");
        if (!Files.exists(packConfig)) {
            packConfig = Path.of("build/pack/overrides/config/cobbreeding/main.json");
        }
        // El pack es un artefacto operacional ignorado por Git. En un worktree
        // limpio aún no existe; la prueba se ejecuta completa al validar el pack.
        assumeTrue(Files.exists(packConfig), "Requiere build/pack externo");
        String content = Files.readString(packConfig);
        assertTrue(content.contains("\"maxNumberOfActivatedPasturePerPlayer\": 0"), 
                "Cobbreeding debe tener maxNumberOfActivatedPasturePerPlayer en 0 para bloquear bypass de pasturas");
        assertTrue(content.contains("\"allowHoppersToPullFromPastureBlock\": false"),
                "Cobbreeding debe tener allowHoppersToPullFromPastureBlock en false");

        Path repoConfig = Path.of("../build/pack-repo/overrides/config/cobbreeding/main.json");
        if (!Files.exists(repoConfig)) {
            repoConfig = Path.of("build/pack-repo/overrides/config/cobbreeding/main.json");
        }
        assumeTrue(Files.exists(repoConfig), "Requiere build/pack-repo externo");
        String repoContent = Files.readString(repoConfig);
        assertTrue(repoContent.contains("\"maxNumberOfActivatedPasturePerPlayer\": 0"),
                "Cobbreeding en repo debe tener maxNumberOfActivatedPasturePerPlayer en 0");
        assertTrue(repoContent.contains("\"allowHoppersToPullFromPastureBlock\": false"),
                "Cobbreeding en repo debe tener allowHoppersToPullFromPastureBlock en false");
    }
}
