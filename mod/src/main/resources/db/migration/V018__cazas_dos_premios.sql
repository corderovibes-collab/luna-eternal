-- Un segundo objeto por objetivo.
--
-- Peticion del usuario: «ademas de plata debe dar otros items de recompensa,
-- x cantidad de pokeballs normal, si es 2 estrellas otro tipo de pokeball
-- puede ser ultra ball etc, pero que no esten rotas o les de ventaja, y
-- caramelos o cosas asi».
--
-- Con UN solo hueco habia que elegir entre balls o caramelos. Con dos, cada
-- objetivo da una ball (para seguir cazando) y algo mas (curacion o
-- experiencia), que es lo que convierte el premio en un kit y no en un numero.
--
-- ⚠ NADA QUE DE VENTAJA DE COMBATE, dicho por el usuario. Ni vitaminas, ni
--   objetos equipables, ni Caramelos Raros: balls, pociones y caramelos de
--   experiencia. Todo eso ACELERA lo que ya estabas haciendo; no cambia lo que
--   puedes hacer.
--
-- ⚠⚠ Y COMO EN V017: el premio se guarda EN LA FILA. La pantalla lo enseña y
--    entre enseñarlo y cobrarlo pasan hasta 24 h.

ALTER TABLE hunt_target
  ADD COLUMN reward_item2 VARCHAR(64) NULL DEFAULT NULL AFTER reward_qty,
  ADD COLUMN reward_qty2  INT         NOT NULL DEFAULT 0 AFTER reward_item2;

INSERT INTO schema_version (version, description)
VALUES (18, 'cazas: segundo objeto de premio')
ON DUPLICATE KEY UPDATE version = version;
