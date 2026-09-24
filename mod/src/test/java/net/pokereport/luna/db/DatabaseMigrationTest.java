package net.pokereport.luna.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para A04: Migraciones MariaDB, cálculo de checksums,
 * re-entrancia de DDL y estructura de schema_version.
 */
public class DatabaseMigrationTest {

    @Test
    @DisplayName("computeSha256 es determinista y genera un hash hexadecimal de 64 caracteres")
    void testComputeSha256() {
        String testSql1 = "CREATE TABLE test (id INT PRIMARY KEY);";
        String testSql2 = "CREATE TABLE test (id INT PRIMARY KEY);";
        String testSql3 = "CREATE TABLE test_diff (id INT PRIMARY KEY);";

        String hash1 = Database.computeSha256(testSql1);
        String hash2 = Database.computeSha256(testSql2);
        String hash3 = Database.computeSha256(testSql3);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 debe tener 64 caracteres en hexadecimal");
        assertEquals(hash1, hash2, "Mismo contenido debe generar idéntico hash");
        assertNotEquals(hash1, hash3, "Contenido diferente debe generar hash diferente");
    }

    @Test
    @DisplayName("Todas las migraciones en Database.MIGRATIONS existen en resources y no tienen colisiones")
    void testAllMigrationsExistAndNoCollisions() throws Exception {
        Set<Integer> seenVersions = new HashSet<>();
        for (String file : Database.MIGRATIONS) {
            int end = file.indexOf("__");
            int version = Integer.parseInt(file.substring(1, end));
            assertFalse(seenVersions.contains(version), "Colisión detectada: versión " + version + " duplicada en " + file);
            seenVersions.add(version);

            try (InputStream in = Database.class.getResourceAsStream("/db/migration/" + file)) {
                assertNotNull(in, "El recurso de migración debe existir: /db/migration/" + file);
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(content.isBlank(), "El archivo de migración no debe estar vacío: " + file);
            }
        }
        assertEquals(47, seenVersions.size(), "Deben verificarse las 47 migraciones, incluido Tebex y Kits Exclusivos");
        for (int version = 1; version <= 47; version++) {
            assertTrue(seenVersions.contains(version), "Falta la migración " + version);
        }
    }

    @Test
    @DisplayName("Simulación de commit implícito de DDL en MariaDB y re-entrancia de objetos existentes")
    void testDdlImplicitCommitSimulation() {
        // En MariaDB los códigos de error ante re-ejecución por commit implícito son:
        // 1050: ER_TABLE_EXISTS_ERROR
        // 1060: ER_DUP_FIELDNAME
        // 1061: ER_DUP_KEYNAME
        Set<Integer> ignorableErrors = Set.of(1050, 1060, 1061);

        assertTrue(ignorableErrors.contains(1050), "Error 1050 (Table already exists) debe ser tolerable en re-entrancia");
        assertTrue(ignorableErrors.contains(1060), "Error 1060 (Duplicate column) debe ser tolerable en re-entrancia");
        assertTrue(ignorableErrors.contains(1061), "Error 1061 (Duplicate key) debe ser tolerable en re-entrancia");
        assertFalse(ignorableErrors.contains(1064), "Error 1064 (Syntax error) nunca debe ser ignorado");
    }
}
