-- LAS MEDALLAS DE GIMNASIO.
--
-- ⚠⚠⚠ NO SON UN OBJETO, Y ES ORDEN DEL USUARIO: «obtiene la medalla pero no
--    fisica, la obtienen ya en el PokePad». Es la misma decision que los trajes
--    (V023) y por el mismo motivo, que ahi esta escrito entero: un objeto se
--    tira, se pierde al morir y SE PUEDE REGALAR -- y una medalla regalada deja
--    de decir «yo gane a Brock», que es lo unico que una medalla significa.
--
-- ⚠⚠⚠ LA CLAVE PRIMARIA ES (player_id, gym), Y ESA ES LA REGLA.
--
--    «Una medalla por gimnasio y por jugador» no lo dice una comprobacion en
--    Java: lo dice la clave. Asi, ganarla dos veces FALLA EN LA BASE venga de
--    donde venga la peticion -- dos combates que terminan a la vez, un cliente
--    modificado, o un reintento. En Java se comprueba tambien, para dar un
--    mensaje que se entienda, pero la que manda es esta.
--    Misma decision que `clan_member`, y por el mismo motivo.
--
-- ⚠⚠ Y NO SE GUARDA UNA MASCARA DE BITS, aunque el PokePad dibuje una.
--
--    Una mascara en una columna seria mas compacta y no se podria consultar:
--    «cuanta gente tiene la de Brock» seria un barrido con aritmetica de bits, y
--    «cuando la gano» no cabria en ninguna parte. La mascara se COMPONE al leer
--    --que es una linea-- y lo que se guarda es el hecho: quien, cual, cuando.
--
-- ⚠ `gym` es VARCHAR y no un ENUM de MariaDB. Tercera vez que se escribe y
--   sigue siendo cierto (V012 oficios, V020 rangos, V023 trajes): un ENUM guarda
--   EL INDICE, asi que reordenar la lista convierte unas medallas en otras, y
--   meter un valor que no este NO DA ERROR -- guarda la cadena vacia.

CREATE TABLE IF NOT EXISTS gym_badge (
  -- BIGINT UNSIGNED como player.player_id: con BIGINT a secas la clave ajena no
  -- se forma (errno 150) y la migracion revienta el arranque.
  player_id BIGINT UNSIGNED NOT NULL,
  gym       VARCHAR(32)     NOT NULL,
  won_at    TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (player_id, gym),
  -- Para «quien tiene la de Brock» sin barrer la tabla entera.
  KEY ix_gym_badge_gym (gym),
  CONSTRAINT fk_gym_badge_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (24, 'medallas de gimnasio')
ON DUPLICATE KEY UPDATE version = version;
