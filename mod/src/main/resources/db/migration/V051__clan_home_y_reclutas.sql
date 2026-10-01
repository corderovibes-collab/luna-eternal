-- Jerarquía completa y hogar persistente del clan.
-- RECLUTA se añade al final para conservar los índices del ENUM existentes.
ALTER TABLE clan_member
    MODIFY role ENUM('LIDER','OFICIAL','MIEMBRO','RECLUTA')
    NOT NULL DEFAULT 'RECLUTA';

CREATE TABLE IF NOT EXISTS clan_home (
    clan_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    dimension VARCHAR(128) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_clan_home_clan FOREIGN KEY (clan_id)
        REFERENCES clan(clan_id) ON DELETE CASCADE,
    CONSTRAINT fk_clan_home_player FOREIGN KEY (updated_by)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (51, 'hogar de clan y rol recluta')
ON DUPLICATE KEY UPDATE version = version;
