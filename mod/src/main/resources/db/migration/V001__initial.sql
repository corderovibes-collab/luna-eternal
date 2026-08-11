-- =====================================================================
-- Luna Eternal · Migración V001 — identidad y economía
-- Ver docs/technical/data-model.md
-- Reglas aplicadas: R1 clave sustituta · R2 dinero entero
--                   R3 libro de asientos · R4 idempotencia · R5 enums
-- =====================================================================

CREATE TABLE IF NOT EXISTS schema_version (
    version     INT           NOT NULL PRIMARY KEY,
    description VARCHAR(191)  NOT NULL,
    applied_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- R1 · player_id es la clave de TODO. mc_uuid es un atributo, no la clave.
--      Esto aísla el esquema de la decisión online-mode (D-010) y de los
--      cambios de nombre.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS player (
    player_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    mc_uuid     CHAR(36)        NOT NULL,
    username    VARCHAR(16)     NOT NULL,
    first_seen  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_seen   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    play_ticks  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uk_player_uuid (mc_uuid),
    KEY ix_player_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- R2 · BIGINT, nunca FLOAT. El error de redondeo es un exploit.
--      Esta tabla es una CACHÉ: la verdad está en ledger_entry (R3).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS player_economy (
    player_id   BIGINT UNSIGNED NOT NULL,
    currency    ENUM('POKEDOLLAR','MARK') NOT NULL,
    balance     BIGINT          NOT NULL DEFAULT 0,
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_id, currency),
    CONSTRAINT fk_econ_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_balance_nonneg CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- R3 · El libro de asientos es la verdad. Solo se inserta, nunca se borra
--      ni se actualiza. Da auditoría, detección de duplicación, rollback
--      selectivo y la telemetría de ECO-001 §7 con un GROUP BY.
-- R4 · idempotency_key UNIQUE: el segundo intento falla por índice en vez
--      de duplicar dinero.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entry (
    entry_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_id       BIGINT UNSIGNED NOT NULL,
    currency        ENUM('POKEDOLLAR','MARK') NOT NULL,
    delta           BIGINT          NOT NULL,
    balance_after   BIGINT          NOT NULL,
    reason          VARCHAR(48)     NOT NULL,
    ref_type        VARCHAR(32)     NULL,
    ref_id          BIGINT UNSIGNED NULL,
    idempotency_key CHAR(36)        NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ledger_idem (idempotency_key),
    KEY ix_ledger_player_time (player_id, created_at),
    KEY ix_ledger_reason_time (reason, created_at),
    CONSTRAINT fk_ledger_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_delta_nonzero CHECK (delta <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (1, 'identidad y economia: player, player_economy, ledger_entry')
ON DUPLICATE KEY UPDATE version = version;
