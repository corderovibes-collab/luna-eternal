-- Cazas y Crianza (HUNT-001).
--
-- Son las MISMAS para todo el servidor y rotan cada 12 horas: decision del
-- usuario. Eso hace que la gente hable de lo mismo a la vez ("¿alguien ha
-- visto un Lapras?") y ademas convierte la rotacion en UNA fila, no en N.
--
-- Solo cuenta CAPTURAR, no combatir: un combate contra otro jugador se
-- amaña en dos minutos y la caza dejaria de valer nada.

CREATE TABLE IF NOT EXISTS hunt_cycle (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  started_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ends_at     TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cycle_ends (ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hunt_target (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  cycle_id      BIGINT      NOT NULL,
  kind          ENUM('CAPTURA','CRIANZA') NOT NULL,
  species       VARCHAR(64) NOT NULL,
  needed        INT         NOT NULL,
  reward_dollar BIGINT      NOT NULL,
  reward_mark   BIGINT      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_target_cycle (cycle_id),
  CONSTRAINT fk_target_cycle FOREIGN KEY (cycle_id)
    REFERENCES hunt_cycle (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Progreso por jugador y objetivo.
--
-- claimed_at es lo que impide cobrar dos veces: la entrega comprueba que
-- sea NULL dentro de la MISMA transaccion que paga (R3/R4). Sin eso, dos
-- clics rapidos cobrarian dos veces.
CREATE TABLE IF NOT EXISTS hunt_progress (
  -- BIGINT UNSIGNED, igual que player.player_id. Con BIGINT a secas la
  -- clave ajena no se forma (errno 150) y la migracion revienta el arranque.
  player_id  BIGINT UNSIGNED NOT NULL,
  target_id  BIGINT       NOT NULL,
  done       INT          NOT NULL DEFAULT 0,
  claimed_at TIMESTAMP(3) NULL DEFAULT NULL,
  PRIMARY KEY (player_id, target_id),
  KEY idx_progress_target (target_id),
  CONSTRAINT fk_progress_target FOREIGN KEY (target_id)
    REFERENCES hunt_target (id) ON DELETE CASCADE,
  CONSTRAINT fk_progress_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SIN ESTA LINEA LA MIGRACION SE REAPLICA EN CADA ARRANQUE.
-- Cada migracion se registra a si misma; el motor solo comprueba la tabla.
-- Olvidarla aqui, combinado con unos DROP TABLE que habia al principio,
-- borraba las cazas de todo el servidor cada vez que se reiniciaba.
INSERT INTO schema_version (version, description)
VALUES (10, 'cazas y crianza')
ON DUPLICATE KEY UPDATE version = version;
