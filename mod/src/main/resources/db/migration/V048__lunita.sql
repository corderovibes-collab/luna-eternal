-- V048 · LUNITA: memoria persistente y regalo anti-duplicación
CREATE TABLE IF NOT EXISTS lunita_player_memory (
    player_id         BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    last_interaction  DATETIME(3)     NULL,
    last_reward       DATETIME(3)     NULL,
    greetings         INT UNSIGNED    NOT NULL DEFAULT 0,
    affinity          INT UNSIGNED    NOT NULL DEFAULT 0,
    CONSTRAINT fk_lunita_memory_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    KEY ix_lunita_reward (last_reward)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (48, 'lunita: memoria persistente y recompensa cada 12 horas')
ON DUPLICATE KEY UPDATE version = version;
