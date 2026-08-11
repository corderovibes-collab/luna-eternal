-- =====================================================================
-- V009 — Misiones
--
-- Se guarda el PROGRESO, no la definición. Las misiones viven en un JSON
-- regenerable; aquí solo va cuánto lleva cada jugador. Así se puede cambiar
-- el catálogo sin migrar nada.
--
-- 'claimed' separado de 'completed' a proposito: completar y cobrar son dos
-- momentos distintos. Sin esa separacion, una recompensa entregada durante
-- una caida se perderia o se daria dos veces — el mismo problema que resolvio
-- V006 en el GTS.
-- =====================================================================

CREATE TABLE IF NOT EXISTS quest_progress (
    player_id    BIGINT UNSIGNED NOT NULL,
    quest_id     VARCHAR(48)     NOT NULL,
    progress     BIGINT          NOT NULL DEFAULT 0,
    completed_at DATETIME(3)     NULL,
    claimed_at   DATETIME(3)     NULL,
    -- Para las diarias: en qué periodo se completó, y así saber si toca de nuevo.
    period_key   VARCHAR(16)     NOT NULL DEFAULT '',
    PRIMARY KEY (player_id, quest_id, period_key),
    KEY ix_quest_claimable (player_id, completed_at, claimed_at),
    CONSTRAINT fk_quest_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (9, 'misiones: progreso por jugador')
ON DUPLICATE KEY UPDATE version = version;
