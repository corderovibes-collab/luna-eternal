-- V038 · CRIANZA EN EL POKEPAD: ranuras, progenitores y huevos.
--
-- Persistencia de las 7 ranuras de crianza por jugador: desbloqueo con
-- LunaCoins, asignacion de pareja, tiempos de gestacion y estado del huevo.

CREATE TABLE IF NOT EXISTS crianza_ranura (
  player_uuid           VARCHAR(36)     NOT NULL,
  ranura_idx            INT             NOT NULL,
  desbloqueada_monedas  TINYINT(1)      NOT NULL DEFAULT 0,
  madre_uuid            VARCHAR(36)     NULL,
  padre_uuid            VARCHAR(36)     NULL,
  madre_especie         VARCHAR(64)     NULL,
  padre_especie         VARCHAR(64)     NULL,
  inicio_ms             BIGINT          NOT NULL DEFAULT 0,
  duracion_ms           BIGINT          NOT NULL DEFAULT 0,
  huevo_listo           TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, ranura_idx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (38, 'crianza en el pokepad: ranuras, progenitores y huevos')
ON DUPLICATE KEY UPDATE version = version;
