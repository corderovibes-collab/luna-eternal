-- QUÉ TRAJES TIENE CADA JUGADOR.
--
-- ⚠⚠⚠ ESTO REVOCA LO QUE DICE V023, Y HAY QUE LEER LAS DOS JUNTAS.
--
--    V023 dice, con todas las letras, que NO hay tabla de propiedad porque el
--    traje «se deriva del rango». Era la decisión correcta para el modelo de
--    entonces: el rango te abría el suyo y todos los de debajo.
--
--    El modelo cambió (decisión del usuario, 2026-09-03): **cada traje se
--    adquiere por separado**. Comprar LEYENDA da LEYENDA y nada más; ÉLITE se
--    compra aparte, y quien ya tenga ÉLITE puede subir a CAMPEÓN con descuento
--    y SE QUEDA CON LOS DOS. Eso no se puede derivar de un rango: un jugador
--    tiene UN rango y puede tener VARIOS trajes.
--
--    ⚠⚠ Y lo que V023 daba gratis, aquí hay que pagarlo a mano: allí, bajar de
--       rango retiraba el traje alto **sin escribir una línea**. Aquí no, y es
--       lo correcto -- lo comprado no se quita porque cambie el rango. Lo que sí
--       hay que hacer es retirar el traje puesto cuando se REVOCA (devolución,
--       contracargo), y de eso se encarga `TrajeService.revisar`.
--
-- ⚠⚠ EL ENTRENADOR NO VA EN ESTA TABLA. Es gratis para todo el mundo, y meterlo
--    obligaría a insertar una fila por jugador que existe -- incluidos los que
--    todavía no han entrado nunca. Un traje gratis es una regla, no un dato:
--    vive en el código (`Traje.gratis()`), donde no puede quedarse a medias.
--
-- ⚠ La clave primaria es (player_id, suit), no un id nuevo. Así, conceder dos
--   veces el mismo traje FALLA EN LA BASE venga de donde venga la petición --
--   un doble clic en Tebex, un reintento del webhook, dos comandos a la vez--
--   en vez de dejar dos filas que luego alguien tiene que saber ignorar. Es la
--   misma decisión que `clan_member` y que las medallas.

CREATE TABLE IF NOT EXISTS player_suit_owned (
  -- BIGINT UNSIGNED como player.player_id: con BIGINT a secas la clave ajena
  -- no se forma (errno 150) y la migracion revienta el arranque.
  player_id  BIGINT UNSIGNED NOT NULL,
  -- VARCHAR y no un ENUM de MariaDB, como en V023 y por lo mismo: un ENUM
  -- guarda EL INDICE, asi que reordenar convierte unos trajes en otros, y un
  -- valor que no este en la lista NO DA ERROR -- guarda la cadena vacia.
  suit       VARCHAR(32)     NOT NULL,
  granted_at TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (player_id, suit),
  CONSTRAINT fk_suit_owned_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ⚠⚠⚠ Y AQUI SE LE DA A CADA UNO LO QUE YA TENIA. Sin este relleno, la
--    migracion le QUITA a todo el mundo el traje que hoy puede ponerse: hasta
--    ahora el permiso salia del rango, y a partir de ahora sale de esta tabla,
--    que nace vacia. Un jugador de LEYENDA entraria y se encontraria con que ya
--    no puede ponerse el suyo, sin un solo error en el log.
--
--    Se concede EL DE SU RANGO, no todos los de debajo: es exactamente el
--    modelo nuevo, y es lo que ese jugador pago.
--
-- ⚠ `LOWER(rank_id)` porque el rango se guarda en mayusculas ('LEYENDA') y el
--   identificador del traje va en minusculas ('leyenda'). Los rangos de equipo
--   --ADMIN, DEV, MODERADOR-- no estan en la lista a proposito: no son trajes.
INSERT IGNORE INTO player_suit_owned (player_id, suit)
SELECT player_id, LOWER(rank_id)
  FROM player
 WHERE rank_id IN ('ELITE', 'CAMPEON', 'MAESTRO', 'LEYENDA');

INSERT INTO schema_version (version, description)
VALUES (28, 'que trajes ha adquirido cada jugador')
ON DUPLICATE KEY UPDATE version = version;
