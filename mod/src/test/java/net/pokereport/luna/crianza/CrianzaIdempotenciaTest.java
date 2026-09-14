package net.pokereport.luna.crianza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A02: Entrega transaccional e idempotente de crías.
 * Verifica la definición de la migración V039 y la máquina de estados de entrega idempotente.
 */
public class CrianzaIdempotenciaTest {

    @Test
    @DisplayName("V039 Migration file exists and defines crianza_entrega_pendiente correctly")
    void testMigrationV039File() throws Exception {
        Path migPath = Path.of("src/main/resources/db/migration/V039__crianza_entrega_pendiente.sql");
        if (!Files.exists(migPath)) {
            migPath = Path.of("mod/src/main/resources/db/migration/V039__crianza_entrega_pendiente.sql");
        }
        assertTrue(Files.exists(migPath), "V039__crianza_entrega_pendiente.sql debe existir");
        String sql = Files.readString(migPath);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS crianza_entrega_pendiente"), "Debe crear la tabla crianza_entrega_pendiente");
        assertTrue(sql.contains("delivery_id"), "Debe contener delivery_id");
        assertTrue(sql.contains("player_uuid"), "Debe contener player_uuid");
        assertTrue(sql.contains("pokemon_nbt"), "Debe contener pokemon_nbt");
        assertTrue(sql.contains("estado"), "Debe contener estado");
        assertTrue(sql.contains("idx_crianza_pend_jugador"), "Debe tener índice por jugador y estado");
        assertTrue(sql.contains("INSERT INTO schema_version"), "Debe registrarse en schema_version con versión 39");
    }

    /**
     * Modelo abstracto de la máquina de estados de entrega para validar los escenarios de A02.
     */
    static class DeliveryStateMachine {
        enum Estado { PENDING, DELIVERED }

        static class Entrega {
            final String deliveryId;
            final String playerUuid;
            final int slotIdx;
            final UUID pokemonUuid;
            final String especie;
            Estado estado;
            long creadoMs;
            Long entregadoMs;

            Entrega(String deliveryId, String playerUuid, int slotIdx, UUID pokemonUuid, String especie) {
                this.deliveryId = deliveryId;
                this.playerUuid = playerUuid;
                this.slotIdx = slotIdx;
                this.pokemonUuid = pokemonUuid;
                this.especie = especie;
                this.estado = Estado.PENDING;
                this.creadoMs = System.currentTimeMillis();
            }
        }

        static class StorageSim {
            final Set<UUID> party = new HashSet<>();
            final Set<UUID> pc = new HashSet<>();
            int maxParty = 6;
            int maxPc = 30;

            boolean has(UUID uuid) {
                return party.contains(uuid) || pc.contains(uuid);
            }

            Boolean add(UUID uuid) {
                if (has(uuid)) {
                    // Idempotencia: ya está presente
                    return true;
                }
                if (party.size() < maxParty) {
                    party.add(uuid);
                    return true;
                }
                if (pc.size() < maxPc) {
                    pc.add(uuid);
                    return true;
                }
                return false; // Storage full
            }
        }

        static boolean procesarEntrega(Entrega entrega, StorageSim storage) {
            UUID pokeUuid = entrega.pokemonUuid;
            if (storage.has(pokeUuid)) {
                // Idempotencia: ya entregado previamente (ej. crash justo después del add)
                entrega.estado = Estado.DELIVERED;
                entrega.entregadoMs = System.currentTimeMillis();
                return true;
            }

            if (storage.add(pokeUuid)) {
                entrega.estado = Estado.DELIVERED;
                entrega.entregadoMs = System.currentTimeMillis();
                return true;
            }

            // Almacenamiento lleno: permanece en PENDING sin perderse
            return false;
        }
    }

    @Test
    @DisplayName("Escenario 1: Entrega limpia al equipo (Party)")
    void testEntregaLimpiaEquipo() {
        var storage = new DeliveryStateMachine.StorageSim();
        UUID pokeUuid = UUID.randomUUID();
        var entrega = new DeliveryStateMachine.Entrega("del-1", "player-1", 0, pokeUuid, "Pikachu");

        assertEquals(DeliveryStateMachine.Estado.PENDING, entrega.estado);
        boolean ok = DeliveryStateMachine.procesarEntrega(entrega, storage);

        assertTrue(ok);
        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega.estado);
        assertTrue(storage.party.contains(pokeUuid));
        assertNotNull(entrega.entregadoMs);
    }

    @Test
    @DisplayName("Escenario 2: Entrega al PC cuando el equipo está lleno")
    void testEntregaPcCuandoEquipoLleno() {
        var storage = new DeliveryStateMachine.StorageSim();
        for (int i = 0; i < 6; i++) {
            storage.party.add(UUID.randomUUID());
        }
        UUID pokeUuid = UUID.randomUUID();
        var entrega = new DeliveryStateMachine.Entrega("del-2", "player-1", 0, pokeUuid, "Charmander");

        boolean ok = DeliveryStateMachine.procesarEntrega(entrega, storage);

        assertTrue(ok);
        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega.estado);
        assertTrue(storage.pc.contains(pokeUuid));
        assertFalse(storage.party.contains(pokeUuid));
    }

    @Test
    @DisplayName("Escenario 3: Almacenamiento completamente lleno retiene PENDING y no pierde la cría")
    void testAlmacenamientoLlenoRetienePending() {
        var storage = new DeliveryStateMachine.StorageSim();
        storage.maxParty = 1;
        storage.maxPc = 1;
        storage.party.add(UUID.randomUUID());
        storage.pc.add(UUID.randomUUID());

        UUID pokeUuid = UUID.randomUUID();
        var entrega = new DeliveryStateMachine.Entrega("del-3", "player-1", 0, pokeUuid, "Bulbasaur");

        boolean ok = DeliveryStateMachine.procesarEntrega(entrega, storage);

        assertFalse(ok, "La entrega debe fallar si no hay espacio");
        assertEquals(DeliveryStateMachine.Estado.PENDING, entrega.estado, "El estado debe permanecer PENDING");
        assertNull(entrega.entregadoMs);

        // Liberar espacio y reintentar
        storage.party.clear();
        boolean retryOk = DeliveryStateMachine.procesarEntrega(entrega, storage);
        assertTrue(retryOk, "Al liberar espacio el reintento debe completarse");
        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega.estado);
        assertTrue(storage.party.contains(pokeUuid));
    }

    @Test
    @DisplayName("Escenario 4: Idempotencia - No duplicar si el Pokémon ya fue añadido a almacenamiento")
    void testIdempotenciaNoDuplica() {
        var storage = new DeliveryStateMachine.StorageSim();
        UUID pokeUuid = UUID.randomUUID();
        storage.party.add(pokeUuid); // Simula que el crash ocurrió inmediatamente después de party.add(baby)

        var entrega = new DeliveryStateMachine.Entrega("del-4", "player-1", 0, pokeUuid, "Squirtle");
        assertEquals(DeliveryStateMachine.Estado.PENDING, entrega.estado);

        boolean ok = DeliveryStateMachine.procesarEntrega(entrega, storage);

        assertTrue(ok, "Debe considerarse exitoso");
        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega.estado, "Debe actualizar a DELIVERED");
        assertEquals(1, storage.party.size(), "No debe haber duplicados en el almacenamiento");
    }

    @Test
    @DisplayName("Escenario 5: Recuperación de múltiples entregas pendientes tras reconexión")
    void testRecuperacionMultiplesPendientes() {
        var storage = new DeliveryStateMachine.StorageSim();
        var entrega1 = new DeliveryStateMachine.Entrega("del-5a", "player-1", 0, UUID.randomUUID(), "Eevee");
        var entrega2 = new DeliveryStateMachine.Entrega("del-5b", "player-1", 1, UUID.randomUUID(), "Dratini");

        assertTrue(DeliveryStateMachine.procesarEntrega(entrega1, storage));
        assertTrue(DeliveryStateMachine.procesarEntrega(entrega2, storage));

        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega1.estado);
        assertEquals(DeliveryStateMachine.Estado.DELIVERED, entrega2.estado);
        assertEquals(2, storage.party.size());
    }
}
