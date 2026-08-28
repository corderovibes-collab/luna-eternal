-- Dónde se quedó cada jugador en cada mundo.
--
-- Peticion del usuario: «en mundo hogar el primer ida al mundo es aleatorio, el
-- segundo ya vas donde te quedaste la ultima vez».
--
-- ⚠⚠ POR QUE HACE FALTA UNA TABLA Y NO VALE LO QUE YA GUARDA MINECRAFT.
--
--    Minecraft recuerda UNA posicion por jugador: donde estaba al desconectar.
--    No recuerda «donde estaba en el Hogar» mientras esta en el Salvaje, que es
--    justo lo que se necesita: viajas al salvaje, juegas dos horas, vuelves a
--    casa y tienes que aparecer en TU casa, no en el ultimo sitio donde
--    estuviste que fue un bosque a mil bloques.
--
-- ⚠ La clave es (jugador, mundo) y no solo el jugador: cada mundo tiene su
--   propio «donde me quedé». Con una sola fila por jugador, entrar al salvaje
--   pisaria la posicion de casa y volver a casa dejaria en el sitio equivocado.
--
-- ⚠ Y NO se guarda para los mundos salvajes, aunque la tabla podria: ahi la
--   entrada es SIEMPRE aleatoria por diseño. La tabla no lo impide; lo impide
--   quien escribe, y esta escrito en `Regreso`.

CREATE TABLE IF NOT EXISTS world_return (
  -- BIGINT UNSIGNED como player.player_id: con BIGINT a secas la clave ajena
  -- no se forma (errno 150) y la migracion revienta el arranque.
  player_id BIGINT UNSIGNED NOT NULL,
  world_key VARCHAR(64)     NOT NULL,
  x         DOUBLE          NOT NULL,
  y         DOUBLE          NOT NULL,
  z         DOUBLE          NOT NULL,
  yaw       FLOAT           NOT NULL DEFAULT 0,
  pitch     FLOAT           NOT NULL DEFAULT 0,
  saved_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (player_id, world_key),
  CONSTRAINT fk_return_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (22, 'donde se quedo cada jugador en cada mundo')
ON DUPLICATE KEY UPDATE version = version;
