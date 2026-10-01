-- Luna Eternal · V049 — retirada segura de Marcas
-- Conserva el saldo original, lo migra 1:1 a Plata y mantiene el ledger
-- histórico MARK para auditoría. MARK deja de ser una moneda activa.

CREATE TABLE IF NOT EXISTS legacy_mark_balance (
    player_id       BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    original_balance BIGINT NOT NULL,
    archived_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    migrated_at     DATETIME(3) NULL,
    CONSTRAINT fk_legacy_mark_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO legacy_mark_balance (player_id, original_balance)
SELECT player_id, balance
FROM player_economy
WHERE currency = 'MARK';

INSERT IGNORE INTO player_economy (player_id, currency, balance)
SELECT player_id, 'POKEDOLLAR', 0
FROM legacy_mark_balance;

-- La actualización de saldo y el marcador se hace en UNA sentencia para que
-- una reentrada tras corte no pueda sumar dos veces.
UPDATE player_economy e
JOIN legacy_mark_balance a ON a.player_id = e.player_id
SET e.balance = e.balance + a.original_balance,
    a.migrated_at = CURRENT_TIMESTAMP(3)
WHERE e.currency = 'POKEDOLLAR'
  AND a.migrated_at IS NULL;

INSERT IGNORE INTO ledger_entry
    (player_id, currency, delta, balance_after, reason, ref_type, ref_id,
     idempotency_key, created_at)
SELECT a.player_id, 'POKEDOLLAR', a.original_balance, e.balance,
       'retirada_marcas', 'migration', 49,
       CONCAT('migration:49:mark:', a.player_id), CURRENT_TIMESTAMP(3)
FROM legacy_mark_balance a
JOIN player_economy e ON e.player_id = a.player_id
                     AND e.currency = 'POKEDOLLAR'
WHERE a.original_balance > 0;

DELETE FROM player_economy WHERE currency = 'MARK';

INSERT INTO schema_version (version, description)
VALUES (49, 'retirada segura de marcas con archivo y conversion 1 a 1 a plata')
ON DUPLICATE KEY UPDATE version = version;
