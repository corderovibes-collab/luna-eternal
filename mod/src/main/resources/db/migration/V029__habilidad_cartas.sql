-- HABILIDAD DE APARICION: una carta activa por jugador, con reloj.
--
-- Decision del usuario (2026-09-03): una carta se puede activar para que su
-- especie tenga mas probabilidad de aparecer salvaje, 5 minutos por vez y con
-- una hora de espera entre activaciones.
--
-- ⚠⚠ UNA FILA POR JUGADOR (PK player_id), NO UN HISTORICO. Solo importa la
--    ULTIMA activacion: de ahi salen tanto si la ventana sigue abierta como
--    cuanto falta para poder volver a activar. Un historico seria una tabla
--    que crece para siempre sin que nadie la lea.
--
-- ⚠⚠⚠ `started_ms` ES LO UNICO QUE HACE FALTA PERSISTIR, y es a proposito.
--    La VENTANA ACTIVA (5 min) vive en memoria del servidor --igual que el
--    cooldown de HealService-- porque perderla en un reinicio es solo una
--    ventana corta que se corta antes de tiempo, no un exploit. Pero el
--    RELOJ DE UNA HORA SI tiene que sobrevivir a un reinicio: si viviera solo
--    en memoria, reiniciar el servidor resetearia el cooldown de todo el
--    mundo a la vez, y activar-reiniciar-activar seria la forma de saltarselo.
CREATE TABLE IF NOT EXISTS card_ability (
  player_id   BIGINT UNSIGNED NOT NULL,
  species     VARCHAR(32)     NOT NULL,
  rarity      VARCHAR(16)     NOT NULL,
  grade       INT             NOT NULL DEFAULT 0,
  started_ms  BIGINT          NOT NULL,
  PRIMARY KEY (player_id),
  CONSTRAINT fk_ability_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (29, 'cartas: habilidad de aparicion, una carta activa por jugador')
ON DUPLICATE KEY UPDATE version = version;
