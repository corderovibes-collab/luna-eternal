-- Paradas lunares: progreso independiente por jugador y ubicación.
CREATE TABLE IF NOT EXISTS lunar_stop_progress (
 player_id BIGINT NOT NULL,
 station VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 claims BIGINT NOT NULL DEFAULT 0,
 last_claim_ms BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY (player_id, station)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lunar_stop_delivery (
 id CHAR(36) CHARACTER SET ascii NOT NULL PRIMARY KEY,
 player_id BIGINT NOT NULL,
 gifts TEXT NOT NULL,
 delivered BOOLEAN NOT NULL DEFAULT FALSE,
 created_ms BIGINT NOT NULL,
 INDEX pending_player (player_id, delivered)
) ENGINE=InnoDB;

INSERT INTO schema_version (version, description)
VALUES (40, 'pokeparadas lunares y recompensas persistentes')
ON DUPLICATE KEY UPDATE version = version;
