-- Hogares y Pwarps de jugadores para dimensión Hogar.
CREATE TABLE IF NOT EXISTS player_homes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL,
    dimension VARCHAR(64) NOT NULL DEFAULT 'lunaeternal:hogar',
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_player_home (player_id, name),
    INDEX idx_player (player_id),
    INDEX idx_public (is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (42, 'hogares y pwarps de jugadores')
ON DUPLICATE KEY UPDATE version = version;
