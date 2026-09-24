package net.pokereport.luna.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para la interacción con paradas de viaje (moto-taxi Miraidon)
 * y la apertura segura de la pantalla de Viajes en el Poképad.
 */
public class ParadaEntityTest {

    @Test
    @DisplayName("esTextoParada reconoce nombres y etiquetas válidos de moto-taxi")
    void testEsTextoParadaValidos() {
        assertTrue(ParadaValidador.esTextoParada("TAXICHURRO"), "TAXICHURRO debe ser detectado como parada");
        assertTrue(ParadaValidador.esTextoParada("miraidon"), "miraidon en minúsculas debe ser detectado");
        assertTrue(ParadaValidador.esTextoParada("MIRAIDON"), "MIRAIDON en mayúsculas debe ser detectado");
        assertTrue(ParadaValidador.esTextoParada("luna_parada"), "luna_parada debe ser detectada");
        assertTrue(ParadaValidador.esTextoParada("parada_laboratorio"), "parada_laboratorio debe ser detectada");
        assertTrue(ParadaValidador.esTextoParada("Moto-Taxi"), "Moto-Taxi debe ser detectado");
        assertTrue(ParadaValidador.esTextoParada("taxi"), "taxi debe ser detectado");
    }

    @Test
    @DisplayName("esTextoParada rechaza textos no asociados a paradas")
    void testEsTextoParadaInvalidos() {
        assertFalse(ParadaValidador.esTextoParada(null), "null debe ser rechazado");
        assertFalse(ParadaValidador.esTextoParada(""), "cadena vacía debe ser rechazada");
        assertFalse(ParadaValidador.esTextoParada("   "), "espacios en blanco deben ser rechazados");
        assertFalse(ParadaValidador.esTextoParada("pikachu"), "pikachu no es una parada");
        assertFalse(ParadaValidador.esTextoParada("luna_decorativo"), "luna_decorativo genérico no es parada");
        assertFalse(ParadaValidador.esTextoParada("charizard_volador"), "charizard no es parada");
    }

    @Test
    @DisplayName("Decorativos.java implementa detección de paradas, bypass de creativo agachado y etiquetado")
    void testDecorativosSource() throws Exception {
        Path path = Path.of("src/main/java/net/pokereport/luna/world/Decorativos.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/main/java/net/pokereport/luna/world/Decorativos.java");
        }
        assertTrue(Files.exists(path), "Decorativos.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains("MARCA_PARADA = \"luna_parada\""), "Debe tener la constante MARCA_PARADA como luna_parada");
        assertTrue(src.contains("public static boolean esParada("), "Debe tener el método esParada(Entity)");
        assertTrue(src.contains("jugador.isCreative() && jugador.isSneaking()"), "Debe permitir bypass a jugadores en creativo agachados");
        assertTrue(src.contains("Red.enviarViajes(sp, true)"), "Debe enviar el paquete para abrir Viajes");
        assertTrue(src.contains("\"miraidon\".equalsIgnoreCase(especie)"), "Al colocar Miraidon decorativo debe añadir MARCA_PARADA");
    }

    @Test
    @DisplayName("LunaCliente.java ejecuta la apertura de ViajesScreen en el hilo de render")
    void testLunaClienteRenderThreadDispatch() throws Exception {
        Path path = Path.of("src/client/java/net/pokereport/luna/client/LunaCliente.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/client/java/net/pokereport/luna/client/LunaCliente.java");
        }
        assertTrue(Files.exists(path), "LunaCliente.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains("ctx.client().execute(() -> {"), "Debe despachar la apertura de pantalla en el hilo principal del cliente");
        assertTrue(src.contains("ViajesScreen(null)"), "Debe abrir ViajesScreen cuando carga.abrir() es verdadero");
    }

    @Test
    @DisplayName("LunaCommand.java registra el comando /luna paradas etiquetar")
    void testLunaCommandEtiquetarRegistrado() throws Exception {
        Path path = Path.of("src/main/java/net/pokereport/luna/command/LunaCommand.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/main/java/net/pokereport/luna/command/LunaCommand.java");
        }
        assertTrue(Files.exists(path), "LunaCommand.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains(".then(literal(\"etiquetar\")"), "Debe registrar el subcomando etiquetar");
        assertTrue(src.contains("Decorativos.MARCA_PARADA"), "El subcomando etiquetar debe añadir MARCA_PARADA a los Miraidon");
    }

    @Test
    @DisplayName("LunaEternal inicia el servicio servidor de las pokeparadas lunares")
    void testPokeparadasServerHooksRegistrados() throws Exception {
        Path path = Path.of("src/main/java/net/pokereport/luna/LunaEternal.java");
        if (!Files.exists(path)) {
            path = Path.of("mod/src/main/java/net/pokereport/luna/LunaEternal.java");
        }
        assertTrue(Files.exists(path), "LunaEternal.java debe existir");
        String src = Files.readString(path);

        assertTrue(src.contains("LunarStops.serverHooks()"),
                "El servidor debe iniciar el worker y los hooks de las pokeparadas; registrar solo el bloque no basta");
    }
}
