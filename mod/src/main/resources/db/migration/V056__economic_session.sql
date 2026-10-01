-- Fase 4 y 5: Active Economic Time y Checkpoints
ALTER TABLE player ADD COLUMN active_ticks BIGINT NOT NULL DEFAULT 0;

CREATE TABLE economic_session (
    session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT UNSIGNED NOT NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_checkpoint DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    play_seconds INT NOT NULL DEFAULT 0,
    active_seconds INT NOT NULL DEFAULT 0,
    INDEX idx_ecosess_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
