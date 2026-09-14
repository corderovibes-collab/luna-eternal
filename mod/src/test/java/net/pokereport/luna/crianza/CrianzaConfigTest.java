package net.pokereport.luna.crianza;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class CrianzaConfigTest {

    @Test
    void testCobbreedingConfigDesactivado() throws Exception {
        Path packConfig = Path.of("../build/pack/overrides/config/cobbreeding/main.json");
        if (!Files.exists(packConfig)) {
            packConfig = Path.of("build/pack/overrides/config/cobbreeding/main.json");
        }
        assertTrue(Files.exists(packConfig), "El archivo de configuracion de cobbreeding debe existir en build/pack");
        String content = Files.readString(packConfig);
        assertTrue(content.contains("\"maxNumberOfActivatedPasturePerPlayer\": 0"), 
                "Cobbreeding debe tener maxNumberOfActivatedPasturePerPlayer en 0 para bloquear bypass de pasturas");
        assertTrue(content.contains("\"allowHoppersToPullFromPastureBlock\": false"),
                "Cobbreeding debe tener allowHoppersToPullFromPastureBlock en false");

        Path repoConfig = Path.of("../build/pack-repo/overrides/config/cobbreeding/main.json");
        if (!Files.exists(repoConfig)) {
            repoConfig = Path.of("build/pack-repo/overrides/config/cobbreeding/main.json");
        }
        assertTrue(Files.exists(repoConfig), "El archivo de configuracion de cobbreeding debe existir en build/pack-repo");
        String repoContent = Files.readString(repoConfig);
        assertTrue(repoContent.contains("\"maxNumberOfActivatedPasturePerPlayer\": 0"),
                "Cobbreeding en repo debe tener maxNumberOfActivatedPasturePerPlayer en 0");
        assertTrue(repoContent.contains("\"allowHoppersToPullFromPastureBlock\": false"),
                "Cobbreeding en repo debe tener allowHoppersToPullFromPastureBlock en false");
    }
}
