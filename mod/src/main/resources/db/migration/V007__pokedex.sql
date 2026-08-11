-- =====================================================================
-- V007 — Pokedex
--
-- Guardamos OBSERVACIONES, no Pokemon. El almacen de criaturas es de
-- Cobblemon y sigue siendolo: duplicarlo aqui daria dos fuentes de verdad
-- que se desincronizarian a la primera. Ver data-model.md §3.
--
-- Una fila por jugador y especie, creada la primera vez que la ve o la
-- captura. No se siembran las 1025 al registrar a nadie: seria un millon de
-- filas muertas con mil jugadores.
-- =====================================================================

CREATE TABLE IF NOT EXISTS pokedex_entry (
    player_id       BIGINT UNSIGNED NOT NULL,
    species         VARCHAR(64)     NOT NULL,
    dex_number      INT             NOT NULL,

    seen            TINYINT(1)      NOT NULL DEFAULT 0,
    caught          TINYINT(1)      NOT NULL DEFAULT 0,
    shiny_caught    TINYINT(1)      NOT NULL DEFAULT 0,

    caught_count    INT             NOT NULL DEFAULT 0,
    best_level      INT             NULL,
    first_caught_at DATETIME(3)     NULL,
    -- Fase lunar de la primera captura: el mundo tiene horarios, y saber
    -- CUANDO se capturo algo es parte del registro (vision.md §3.1).
    first_moon_phase TINYINT        NULL,

    PRIMARY KEY (player_id, species),
    KEY ix_dex_player_caught (player_id, caught),
    KEY ix_dex_number (player_id, dex_number),
    CONSTRAINT fk_dex_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (7, 'pokedex: observaciones por jugador y especie')
ON DUPLICATE KEY UPDATE version = version;
