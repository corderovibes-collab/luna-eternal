-- LA MOCHILA: filas de inventario que se desbloquean por rango.
--
-- Peticion del usuario, con capturas de referencia: unos huecos ENCIMA del
-- inventario normal, las filas bloqueadas con un candado rojo, y un cartel al
-- pasar el raton que dice que rango hace falta.
--
-- ⚠⚠ NO SE USA SOPHISTICATED BACKPACKS, y no es por capricho:
--
--    1. Su almacen vive DENTRO de un item, y el usuario dijo explicitamente
--       que no quiere item ni receta ni forma de conseguirlo.
--    2. No sabe nada de NUESTROS rangos: sus niveles son mejoras del item
--       (cuero -> hierro -> oro), no un candado por rango.
--
--    Es la misma razon que D-040 dio para no adoptar un mod de clanes: el mod
--    guardaria el estado en SU almacen, y aqui la puerta la abre nuestro
--    sistema de rangos.
--
-- ⚠ UNA FILA POR HUECO OCUPADO, no un BLOB con la mochila entera. Un BLOB
--   obliga a reescribirlo todo por mover un objeto, y sobre todo hace
--   imposible mirar por la base que tiene alguien -- que es lo primero que hace
--   falta cuando llega un reporte de «he perdido algo».
--
-- ⚠⚠ `payload` LLEVA EL OBJETO ENTERO SERIALIZADO (encantamientos, nombre,
--    componentes), al contrario que el escaparate, que solo guarda
--    identificador y cantidad. Alli se podia porque solo entran objetos
--    corrientes; aqui entra CUALQUIER COSA que el jugador meta, y guardar solo
--    el identificador le borraria los encantamientos SIN DAR NINGUN ERROR.

CREATE TABLE IF NOT EXISTS backpack_slot (
  -- BIGINT UNSIGNED, igual que player.player_id: con BIGINT a secas la clave
  -- ajena no se forma (errno 150) y la migracion revienta el arranque.
  player_id  BIGINT UNSIGNED NOT NULL,
  slot       SMALLINT        NOT NULL,
  payload    BLOB            NOT NULL,
  PRIMARY KEY (player_id, slot),
  CONSTRAINT fk_backpack_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (21, 'mochila por rangos')
ON DUPLICATE KEY UPDATE version = version;
