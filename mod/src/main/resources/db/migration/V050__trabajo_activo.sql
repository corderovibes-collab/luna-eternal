CREATE TABLE IF NOT EXISTS player_job (
    player_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    job_id VARCHAR(32) NOT NULL,
    selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_at DATETIME(3) NULL,
    activities BIGINT UNSIGNED NOT NULL DEFAULT 0,
    silver_earned BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_player_job_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (50, 'trabajo activo seleccionable y estadisticas persistentes')
ON DUPLICATE KEY UPDATE version = version;
