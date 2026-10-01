ALTER TABLE player_homes
    ADD COLUMN description VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN category VARCHAR(24) NOT NULL DEFAULT 'OTROS',
    ADD COLUMN visits BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN last_visit_at DATETIME(3) NULL,
    ADD INDEX idx_player_homes_public_visits (is_public, visits);

CREATE TABLE pwarp_favorite (
    player_id BIGINT UNSIGNED NOT NULL,
    home_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_id, home_id),
    CONSTRAINT fk_pwarp_favorite_player FOREIGN KEY (player_id) REFERENCES player(player_id) ON DELETE CASCADE,
    CONSTRAINT fk_pwarp_favorite_home FOREIGN KEY (home_id) REFERENCES player_homes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pwarp_recent (
    player_id BIGINT UNSIGNED NOT NULL,
    home_id BIGINT NOT NULL,
    visited_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    visit_count INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (player_id, home_id),
    INDEX idx_pwarp_recent_player_time (player_id, visited_at),
    CONSTRAINT fk_pwarp_recent_player FOREIGN KEY (player_id) REFERENCES player(player_id) ON DELETE CASCADE,
    CONSTRAINT fk_pwarp_recent_home FOREIGN KEY (home_id) REFERENCES player_homes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
