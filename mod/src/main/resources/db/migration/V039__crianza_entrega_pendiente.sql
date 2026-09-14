-- V039 · ENTREGA TRANSACCIONAL E IDEMPOTENTE DE CRIAS
--
-- Registro de entregas pendientes de crias para garantizar que ninguna cria
-- se pierda ni duplique ante desconexiones, caidas del servidor o almacenamiento lleno.

CREATE TABLE IF NOT EXISTS crianza_entrega_pendiente (
  delivery_id   VARCHAR(36)     NOT NULL PRIMARY KEY,
  player_uuid   VARCHAR(36)     NOT NULL,
  slot_idx      INT             NOT NULL,
  pokemon_uuid  VARCHAR(36)     NOT NULL,
  especie       VARCHAR(64)     NOT NULL,
  pokemon_nbt   MEDIUMBLOB      NOT NULL,
  estado        VARCHAR(24)     NOT NULL DEFAULT 'PENDING',
  creado_ms     BIGINT          NOT NULL,
  entregado_ms  BIGINT          NULL,
  INDEX idx_crianza_pend_jugador (player_uuid, estado),
  INDEX idx_crianza_pend_slot (player_uuid, slot_idx, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (39, 'entrega transaccional e idempotente de crias (A02)')
ON DUPLICATE KEY UPDATE version = version;
