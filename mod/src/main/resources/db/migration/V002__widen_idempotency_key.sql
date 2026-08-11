-- =====================================================================
-- V002 — ensanchar idempotency_key
--
-- Motivo: las operaciones compuestas derivan una clave por cada pata
-- (transfer -> ":out" / ":in"; una venta de GTS tendrá tres). Un UUID ocupa
-- exactamente los 36 caracteres de CHAR(36), así que cualquier sufijo
-- desbordaba la columna y la transferencia fallaba SIEMPRE.
--
-- Detectado por /luna autotest (MOD-005) antes de que llegara a producción.
--
-- VARCHAR en vez de CHAR: CHAR rellena con espacios hasta la longitud fija,
-- lo que con claves de longitud variable desperdicia espacio y complica las
-- comparaciones.
-- =====================================================================

ALTER TABLE ledger_entry
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL;

INSERT INTO schema_version (version, description)
VALUES (2, 'idempotency_key a VARCHAR(64) para claves compuestas')
ON DUPLICATE KEY UPDATE version = version;
