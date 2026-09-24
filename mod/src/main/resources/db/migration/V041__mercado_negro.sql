-- Oferta personal del Buhonero y recibo persistente, atómico con el débito.
CREATE TABLE IF NOT EXISTS black_market_offer (
 player_id BIGINT NOT NULL PRIMARY KEY,
 cycle_ms BIGINT NOT NULL,
 species VARCHAR(64) NOT NULL,
 level INT NOT NULL,
 last_purchase_ms BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS black_market_purchase (
 player_id BIGINT NOT NULL,
 cycle_ms BIGINT NOT NULL,
 pokemon_uuid CHAR(36) CHARACTER SET ascii NOT NULL,
 pokemon_nbt MEDIUMBLOB NOT NULL,
 price BIGINT NOT NULL,
 purchased_ms BIGINT NOT NULL,
 delivered BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY (player_id, cycle_ms),
 UNIQUE KEY pokemon_receipt (pokemon_uuid),
 INDEX pending_player (player_id, delivered)
) ENGINE=InnoDB;

INSERT INTO schema_version (version, description)
VALUES (41, 'mercado negro personal del buhonero')
ON DUPLICATE KEY UPDATE version = version;
