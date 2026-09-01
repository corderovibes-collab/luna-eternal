-- TESOROS: llaves, aperturas y piedad acumulada.
--
-- El sistema lo decidio D-020 y esta diseñado en docs/economy/treasures.md.
-- De sus cuatro reglas OBLIGATORIAS, tres viven en este fichero:
--
--   probabilidades publicas   -> en el codigo (Cofre.java), que es UNA tabla
--                                que leen el servidor y la pantalla
--   piedad acumulada          -> crate_pity
--   idempotencia              -> crate_open.idem, UNICO
--   libro de asientos         -> ledger_entry, que ya existe
--
-- ⚠⚠⚠ LA IDEMPOTENCIA ES LA QUE PROTEGE DE VERDAD, Y NO ES UN ADORNO.
--
--    Abrir un cofre son TRES cosas en sitios distintos: gastar una llave,
--    sortear el premio y entregarlo. Un doble clic --o un cliente que reenvie
--    el paquete-- puede colarse entre medias y abrir DOS VECES con UNA llave.
--    Y eso no da ningun error: da un jugador con dos legendarios y una llave
--    gastada.
--
--    La clave unica de `crate_open` lo corta EN LA BASE, venga de donde venga
--    la peticion. Es la misma decision que la clave primaria de `gym_badge` y
--    la de `clan_member`.

-- ---------------------------------------------------------------- llaves
--
-- ⚠ Una fila por (jugador, cofre) y no una tabla de llaves sueltas: lo que
--   importa es CUANTAS tienes de cada tipo, no cual es cada una. Con filas
--   individuales, dar cien llaves serian cien inserciones.
CREATE TABLE IF NOT EXISTS crate_key (
  player_id  BIGINT UNSIGNED NOT NULL,
  crate      VARCHAR(32)     NOT NULL,
  amount     INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id, crate),
  CONSTRAINT fk_key_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------- aperturas
--
-- El registro de auditoria: QUE salio, a QUIEN y CUANDO. Es lo que permite
-- contestar «me abri veinte y no salio nada» con datos en vez de con opiniones.
--
-- ⚠ `prize` guarda el identificador del premio tal cual, no un indice de la
--   tabla: la tabla de premios va a cambiar y un indice guardado se convertiria
--   en «salio otra cosa» retroactivamente. Es la leccion del bit de la medalla.
CREATE TABLE IF NOT EXISTS crate_open (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  player_id  BIGINT UNSIGNED NOT NULL,
  crate      VARCHAR(32)     NOT NULL,
  prize      VARCHAR(64)     NOT NULL,
  quantity   INT             NOT NULL DEFAULT 1,
  major      TINYINT(1)      NOT NULL DEFAULT 0,
  -- ⚠⚠ `pity` guarda si esa apertura salio POR PIEDAD y no por suerte. Sin
  --    esta columna no se puede comprobar despues que el sistema funciono, y
  --    «funciona» es justo lo que hay que poder demostrar de una caja de botin.
  by_pity    TINYINT(1)      NOT NULL DEFAULT 0,
  idem       VARCHAR(64)     NOT NULL,
  opened_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_crate_open_idem (idem),
  KEY ix_crate_open_player (player_id, opened_at),
  CONSTRAINT fk_open_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------ piedad acumulada
--
-- Cuantas aperturas lleva sin premio mayor, por cofre.
--
-- ⚠⚠ ES LO QUE ACOTA EL GASTO MAXIMO para conseguir algo concreto, y por eso
--    D-020 la puso como obligatoria: es la diferencia entre «tienes una
--    probabilidad» y «como mucho te va a costar esto». Es tambien lo que las
--    regulaciones de cajas de botin miran con lupa.
CREATE TABLE IF NOT EXISTS crate_pity (
  player_id  BIGINT UNSIGNED NOT NULL,
  crate      VARCHAR(32)     NOT NULL,
  since      INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id, crate),
  CONSTRAINT fk_pity_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------------------------------- la llave del Gacha Diario
--
-- Se gana con UNA HORA DE JUEGO ACTIVO, no por conectarse.
--
-- ⚠⚠ ACTIVO, y ahi esta la diferencia: por conectarse, la recompensa se la
--    lleva quien deja el juego abierto de fondo. Por tiempo activo se la lleva
--    quien juega. Y el jugador lo ve: la pantalla dice cuanto le falta.
--
-- ⚠ `day` es la fecha del servidor. Una fila por jugador y dia: asi «ya
--   reclamaste hoy» es una columna y no una resta de marcas de tiempo, que es
--   donde se cuelan los fallos de zona horaria.
CREATE TABLE IF NOT EXISTS player_activity (
  player_id  BIGINT UNSIGNED NOT NULL,
  day        DATE            NOT NULL,
  seconds    INT             NOT NULL DEFAULT 0,
  claimed    TINYINT(1)      NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id, day),
  CONSTRAINT fk_activity_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (26, 'tesoros: llaves, aperturas, piedad y actividad')
ON DUPLICATE KEY UPDATE version = version;
