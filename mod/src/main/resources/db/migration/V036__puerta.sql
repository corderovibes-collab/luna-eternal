-- V036 · LA PUERTA: el lobby es la unica entrada al mundo.
--
-- Quien no ha cruzado nunca empieza en el lobby, se registra, y un NPC le
-- lleva a la ciudadela. Quien ya cruzo entra donde lo dejo, como siempre.
--
-- ⚠⚠⚠ ESTA MIGRACION RELLENA, Y SIN EL RELLENO ROMPE A TODO EL MUNDO. Si la
--    tabla naciera vacia, «¿ha cruzado la puerta?» pasaria a preguntarse a una
--    tabla sin una sola fila: TODOS los que llevan semanas jugando serian
--    «nuevos» y apareceria cada uno en el lobby, lejos de su casa y de sus
--    cosas, sin un solo error en el log.
--    Es exactamente lo que ya paso al pasar los trajes del rango a una tabla
--    (V028, y quedo escrito: «sin el relleno todo LEYENDA entraria y no podria
--    ponerse el suyo»). Aqui seria peor, porque afecta a DONDE APARECE la gente.
--
-- ⚠⚠ UNA FILA SIGNIFICA «YA CRUZO», y no hay ningun booleano. Un booleano
--    admite el estado «existe la fila y dice que no», que no significa nada
--    distinto de no tener fila -- y ese es el hueco por el que un dia entra una
--    fila a medias. Misma decision que `kit_claim`.
--
-- ⚠ `cruzada_ms = 0` marca a los heredados: no sabemos cuando cruzaron porque
--   la puerta no existia. Distinguirlos de los que crucen de verdad es lo unico
--   que permitira medir cuanta gente nueva pasa por aqui.

CREATE TABLE IF NOT EXISTS player_puerta (
  player_id   BIGINT UNSIGNED NOT NULL,
  cruzada_ms  BIGINT          NOT NULL,
  PRIMARY KEY (player_id),
  CONSTRAINT fk_puerta_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- El relleno. Todo el que ya existe en `player` ha entrado alguna vez, o sea
-- que ya cruzo: mandarlo al lobby seria castigarle por una funcion nueva.
INSERT INTO player_puerta (player_id, cruzada_ms)
SELECT player_id, 0 FROM player
ON DUPLICATE KEY UPDATE player_id = player_id;

INSERT INTO schema_version (version, description)
VALUES (36, 'la puerta: el lobby como unica entrada, con relleno de los que ya jugaban')
ON DUPLICATE KEY UPDATE version = version;
