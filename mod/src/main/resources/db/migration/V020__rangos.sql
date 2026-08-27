-- EL SISTEMA DE RANGOS (PHASE 10, adelantado por la mochila).
--
-- Hasta hoy `Tablist.rankOf` era provisional y decia la verdad en su comentario:
-- «todo el mundo es JUGADOR salvo los operadores». No habia rango guardado en
-- ningun sitio, asi que renombrarlos NO le cambia el rango a nadie -- no hay
-- nada que migrar. Es la unica ventana en que este cambio sale gratis.
--
-- Los rangos de jugador los eligio el usuario:
--   NOVATO . ELITE . CAMPEON . MAESTRO . LEYENDA
-- y encima quedan los de equipo, que son operativos y no se ganan jugando:
--   ADMIN . DEV . MODERADOR
--
-- ⚠⚠⚠ VARCHAR Y NO UN ENUM DE MARIADB, Y ESTO YA NOS MORDIO UNA VEZ.
--
--     `player_path.path` era un ENUM y añadir tres oficios obligo a la
--     migracion V012. Peor todavia: un ENUM guarda EL INDICE, no el texto, asi
--     que reordenar los valores convierte a unos jugadores en otros. Y meter un
--     valor que no esta en la lista NO DA ERROR: guarda la cadena vacia con un
--     aviso que no mira nadie.
--
--     Un rango nuevo tiene que ser una linea de Java, no una migracion.
--
-- ⚠ El valor por defecto es NOVATO y NO NULL: «sin rango» y «el rango mas bajo»
--   serian dos estados para lo mismo, y el codigo tendria que tratar los dos.

ALTER TABLE player
  ADD COLUMN rank_id VARCHAR(16) NOT NULL DEFAULT 'NOVATO' AFTER username;

-- ⚠ Se indexa porque la pregunta «¿quienes son LEYENDA?» la va a hacer la
--   administracion, y sin indice es un barrido de la tabla entera.
CREATE INDEX ix_player_rank ON player (rank_id);

INSERT INTO schema_version (version, description)
VALUES (20, 'rangos de jugador')
ON DUPLICATE KEY UPDATE version = version;
