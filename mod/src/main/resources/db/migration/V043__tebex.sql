-- =====================================================================
-- Luna Eternal · Migración V043 — Transacciones Tebex y blindaje de idempotencia
-- =====================================================================

-- 1. Ensanchamiento a VARCHAR(255) para máxima seguridad de claves compuestas en el libro de asientos
ALTER TABLE ledger_entry
    MODIFY COLUMN idempotency_key VARCHAR(255) NOT NULL;

-- 2. Registro global de la orden / transacción bancaria de Tebex
CREATE TABLE IF NOT EXISTS tebex_payment (
    transaction_id  VARCHAR(255)    NOT NULL PRIMARY KEY,
    player_uuid     CHAR(36)        NOT NULL,
    status          VARCHAR(24)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY ix_tebex_pay_uuid (player_uuid),
    KEY ix_tebex_pay_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Registro detallado de entrega por cada paquete individual
CREATE TABLE IF NOT EXISTS tebex_fulfillment (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(255)    NOT NULL,
    package_id      VARCHAR(64)     NOT NULL,
    player_uuid     CHAR(36)        NOT NULL,
    player_id       BIGINT UNSIGNED     NULL,
    product_type    VARCHAR(16)     NOT NULL,
    product_value   VARCHAR(64)     NOT NULL,
    status          VARCHAR(24)     NOT NULL DEFAULT 'PENDING',
    received_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at    DATETIME(3)         NULL,
    failure_reason  VARCHAR(255)        NULL,
    UNIQUE KEY uk_tebex_tx_pkg (transaction_id, package_id),
    KEY ix_tebex_ful_uuid (player_uuid),
    KEY ix_tebex_ful_status (status),
    CONSTRAINT fk_tebex_ful_payment FOREIGN KEY (transaction_id)
        REFERENCES tebex_payment (transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_tebex_ful_player FOREIGN KEY (player_id)
        REFERENCES player (player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (43, 'auditoria desacoplada tebex_payment y tebex_fulfillment con idempotency_key 255')
ON DUPLICATE KEY UPDATE version = version;
