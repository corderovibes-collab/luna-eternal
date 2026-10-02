package net.pokereport.luna.lunita;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LunitaAssetsTest {
    @Test void packagedModelTextureAndAnimationsAreCompatible() {
        assertDoesNotThrow(LunitaAssets::verify);
    }
}
