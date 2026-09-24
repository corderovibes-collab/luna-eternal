-- =====================================================================
-- Luna Eternal · Migración V045 — Auditoría y estado desacoplado de kits exclusivos
-- =====================================================================

CREATE TABLE IF NOT EXISTS exclusive_kit_purchase (
    purchase_id     VARCHAR(64)     NOT NULL PRIMARY KEY,
    player_uuid     CHAR(36)        NOT NULL,
    player_id       BIGINT UNSIGNED NOT NULL,
    kit_id          VARCHAR(64)     NOT NULL,
    price           BIGINT          NOT NULL DEFAULT 2000,
    currency        VARCHAR(32)     NOT NULL DEFAULT 'REPORTCOIN',
    balance_before  BIGINT          NOT NULL,
    balance_after   BIGINT          NOT NULL,
    purchase_status VARCHAR(24)     NOT NULL DEFAULT 'COMPLETED',
    claim_status    VARCHAR(24)     NOT NULL DEFAULT 'PENDING',
    purchased_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    claimed_at      DATETIME(3)     NULL,
    KEY ix_ekp_player (player_id),
    KEY ix_ekp_uuid (player_uuid),
    KEY ix_ekp_kit (kit_id),
    KEY ix_ekp_status (purchase_status, claim_status),
    CONSTRAINT fk_ekp_player FOREIGN KEY (player_id)
        REFERENCES player (player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (45, 'auditoria y estado desacoplado de compras de kits exclusivos')
ON DUPLICATE KEY UPDATE version = version;
