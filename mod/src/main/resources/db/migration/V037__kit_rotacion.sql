-- V037 · ROTACION DE KITS EXCLUSIVOS: cuenta regresiva persistente.
--
-- La fecha de rotacion vive en la base para que todos los jugadores vean la
-- misma cuenta regresiva y sobreviva a los reinicios del servidor.

CREATE TABLE IF NOT EXISTS kit_rotacion (
  id          INT             NOT NULL DEFAULT 1,
  rota_en     DATETIME(3)     NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO kit_rotacion (id, rota_en)
VALUES (1, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 60 DAY));

INSERT INTO schema_version (version, description)
VALUES (37, 'rotacion de kits exclusivos: cuenta regresiva persistente')
ON DUPLICATE KEY UPDATE version = version;
