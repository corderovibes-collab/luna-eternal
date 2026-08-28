-- Qué traje lleva puesto cada jugador.
--
-- ⚠⚠⚠ NO HAY TABLA DE «QUÉ TRAJES TIENES», Y ES A PROPÓSITO.
--
--    El traje lo da el RANGO, y el rango ya está guardado en `player_rank`. Una
--    segunda tabla de propiedad sería una copia del rango que puede quedarse
--    vieja: alguien sube a MAESTRO y sigue sin poder ponerse el traje porque
--    nadie le insertó la fila. Se DERIVA, y así no puede desincronizarse.
--
--    Consecuencia buena y no obvia: si a alguien se le BAJA el rango, deja de
--    poder ponerse el traje alto automáticamente, sin escribir una línea.
--
-- ⚠⚠ Y NO HAY OBJETO EN NINGUNA PARTE. Misma decisión que los sombreros: un
--    objeto ocuparía la ranura de la armadura, se caería al morir y sobre todo
--    SE PODRÍA REGALAR -- con lo que el traje dejaría de venir del rango, que es
--    lo único que lo sostiene. El servidor dice quién lleva cuál y el cliente lo
--    dibuja. No hay nada que tirar, comerciar ni perder.
--
-- ⚠ Una fila por jugador, y `suit` puede ser NULL: «no llevo ninguno» es un
--   estado real y frecuente, no la ausencia de la fila. Borrar la fila para
--   decir eso obligaría a distinguir «nunca tuvo» de «se lo quitó», y las dos
--   cosas significan lo mismo aquí.

CREATE TABLE IF NOT EXISTS player_suit (
  -- BIGINT UNSIGNED como player.player_id: con BIGINT a secas la clave ajena
  -- no se forma (errno 150) y la migracion revienta el arranque.
  player_id  BIGINT UNSIGNED NOT NULL,
  -- VARCHAR y no un ENUM de MariaDB. Ya nos mordio dos veces (V012 con los
  -- oficios y V020 con los rangos): un ENUM guarda EL INDICE, asi que reordenar
  -- convierte a unos en otros, y meter un valor que no esta en la lista NO DA
  -- ERROR -- guarda la cadena vacia.
  suit       VARCHAR(32)     NULL,
  changed_at TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (player_id),
  CONSTRAINT fk_suit_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (23, 'que traje lleva puesto cada jugador')
ON DUPLICATE KEY UPDATE version = version;
