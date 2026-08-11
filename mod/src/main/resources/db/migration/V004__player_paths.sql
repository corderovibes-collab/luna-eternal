-- =====================================================================
-- V004 — Las cinco Vías (PROG-001)
--
-- No hay "nivel de jugador": el progreso es un PERFIL de cinco reputaciones
-- independientes. Dos jugadores con el mismo tiempo jugado son personas
-- distintas, y esa diferencia es la que crea la demanda que el GTS necesita.
-- Ver docs/progression/progression-model.md §1.
--
-- Una fila por jugador y vía. Se crea al vuelo la primera vez que se toca,
-- así que no hay que sembrar nada al registrar al jugador.
-- =====================================================================

CREATE TABLE IF NOT EXISTS player_path (
    player_id   BIGINT UNSIGNED NOT NULL,
    path        ENUM('EXPLORADOR','ENTRENADOR','COLECCIONISTA',
                     'COMERCIANTE','CRIADOR') NOT NULL,
    level       INT             NOT NULL DEFAULT 0,
    xp          BIGINT          NOT NULL DEFAULT 0,
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_id, path),
    CONSTRAINT fk_path_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_path_level CHECK (level >= 0 AND level <= 5),
    CONSTRAINT ck_path_xp    CHECK (xp >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (4, 'las cinco vias de progresion')
ON DUPLICATE KEY UPDATE version = version;
