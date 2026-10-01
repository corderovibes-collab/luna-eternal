-- V054 — economía observable y preparada para lanzamiento.
-- No cambia saldos ni precios. Añade clasificación, exclusión de QA y snapshots.

ALTER TABLE ledger_entry ADD COLUMN flow_kind VARCHAR(16) NULL AFTER reason;
ALTER TABLE ledger_entry ADD COLUMN system_name VARCHAR(32) NULL AFTER flow_kind;
ALTER TABLE ledger_entry ADD COLUMN counterparty_id BIGINT UNSIGNED NULL AFTER ref_id;
ALTER TABLE ledger_entry ADD COLUMN asset_id VARCHAR(128) NULL AFTER counterparty_id;
ALTER TABLE ledger_entry ADD COLUMN quantity BIGINT NULL AFTER asset_id;
ALTER TABLE ledger_entry ADD COLUMN actor_id BIGINT UNSIGNED NULL AFTER quantity;
ALTER TABLE ledger_entry ADD COLUMN context_json LONGTEXT NULL AFTER actor_id;
ALTER TABLE ledger_entry ADD INDEX ix_ledger_flow_time (currency, flow_kind, created_at);
ALTER TABLE ledger_entry ADD INDEX ix_ledger_system_time (system_name, created_at);

CREATE TABLE IF NOT EXISTS economy_excluded_player (
    player_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    reason VARCHAR(120) NOT NULL,
    excluded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_economy_excluded_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Clasifica también el historial de QA. No altera deltas ni saldos.
UPDATE ledger_entry
SET system_name = COALESCE(NULLIF(ref_type,''),
        LEFT(SUBSTRING_INDEX(reason,'_',1),32)),
    flow_kind = CASE
        WHEN LOWER(reason) LIKE 'autotest%' THEN 'TEST'
        WHEN LOWER(reason) LIKE 'admin\_%' THEN 'ADMIN'
        WHEN LOWER(reason) = 'retirada_marcas' OR LOWER(COALESCE(ref_type,'')) = 'migration' THEN 'MIGRATION'
        WHEN LOWER(COALESCE(ref_type,'')) = 'transfer'
          OR LOWER(reason) IN ('gts_buy','gts_sale','mercado_venta') THEN 'TRANSFER'
        WHEN LOWER(reason) LIKE 'mercado_reservar%'
          OR LOWER(reason) LIKE 'mercado_vuelta%'
          OR LOWER(reason) LIKE 'mercado_cancelar%'
          OR LOWER(reason) LIKE 'mercado_caducar%'
          OR LOWER(reason) IN ('clan_aportar','clan_sacar') THEN 'ESCROW'
        WHEN LOWER(reason) LIKE '%refund%' OR LOWER(reason) LIKE '%reembolso%' THEN 'REFUND'
        WHEN currency = 'REPORTCOIN' THEN 'PREMIUM'
        WHEN delta > 0 THEN 'FAUCET'
        ELSE 'SINK'
    END
WHERE flow_kind IS NULL;

CREATE TABLE IF NOT EXISTS economy_snapshot (
    snapshot_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    currency VARCHAR(16) NOT NULL,
    wallet_supply BIGINT NOT NULL,
    market_escrow BIGINT NOT NULL DEFAULT 0,
    clan_treasury BIGINT NOT NULL DEFAULT 0,
    total_supply BIGINT NOT NULL,
    active_wallet_supply BIGINT NOT NULL DEFAULT 0,
    active_players BIGINT NOT NULL DEFAULT 0,
    p10 BIGINT NOT NULL DEFAULT 0,
    p25 BIGINT NOT NULL DEFAULT 0,
    p50 BIGINT NOT NULL DEFAULT 0,
    p75 BIGINT NOT NULL DEFAULT 0,
    p90 BIGINT NOT NULL DEFAULT 0,
    p95 BIGINT NOT NULL DEFAULT 0,
    p99 BIGINT NOT NULL DEFAULT 0,
    faucets_24h BIGINT NOT NULL DEFAULT 0,
    sinks_24h BIGINT NOT NULL DEFAULT 0,
    transfer_volume_24h BIGINT NOT NULL DEFAULT 0,
    unclassified_24h BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY ix_economy_snapshot_currency_time (currency, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS npc_trade_daily (
    trade_date DATE NOT NULL,
    category_id VARCHAR(32) NOT NULL,
    item_id VARCHAR(128) NOT NULL,
    bought_qty BIGINT UNSIGNED NOT NULL DEFAULT 0,
    bought_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sold_qty BIGINT UNSIGNED NOT NULL DEFAULT 0,
    sold_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (trade_date, category_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS economy_control_state (
    control_key VARCHAR(64) NOT NULL PRIMARY KEY,
    control_value VARCHAR(255) NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO economy_control_state (control_key, control_value)
VALUES ('pricing_mode', 'OBSERVATION')
ON DUPLICATE KEY UPDATE control_key = control_key;

INSERT INTO schema_version (version, description)
VALUES (54, 'economia observable: clasificacion, exclusiones, snapshots y tienda')
ON DUPLICATE KEY UPDATE version = version;
