-- =====================================================================
-- Luna Eternal · Migración V046 — Soporte Username Offline Tebex y Compras Pendientes Primer Join
-- =====================================================================

-- 1. Permitir player_uuid NULL en tebex_payment y agregar username_raw
ALTER TABLE tebex_payment
    MODIFY COLUMN player_uuid CHAR(36) NULL,
    ADD COLUMN username_raw VARCHAR(64) NULL;

-- 2. Permitir player_uuid NULL en tebex_fulfillment, agregar purchase_quantity y username_raw
ALTER TABLE tebex_fulfillment
    MODIFY COLUMN player_uuid CHAR(36) NULL,
    ADD COLUMN purchase_quantity INT NOT NULL DEFAULT 1,
    ADD COLUMN username_raw VARCHAR(64) NULL;

-- 3. Índice para búsqueda de entitlements pendientes por username en el evento de join
CREATE INDEX ix_tebex_ful_pending_user ON tebex_fulfillment (status, username_raw);

INSERT INTO schema_version (version, description)
VALUES (46, 'soporte username offline tebex, compras pendientes primer join y purchase_quantity')
ON DUPLICATE KEY UPDATE version = version;
