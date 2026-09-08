-- SANTUARIO: los nichos de Monumentos.
--
-- Cada nicho es un hueco de 3x3 en la zona de Monumentos. Se ALQUILA 24 h por
-- Plata y se COMPRA para siempre por LunaCoins (decision del usuario). Encima
-- vive un memorial: foto, titulo, descripcion y honores que le dan los demas.
--
-- ⚠⚠ LAS COORDENADAS NO VIVEN AQUI. La tabla guarda SOLO la reclamacion
--    (quien, hasta cuando, que memorial). Donde esta cada nicho en el mundo
--    lo dice la config del servidor (`config/lunaeternal/santuario.json`), y
--    el servicio inserta aqui una fila por cada nicho que la config declara.
--    Separados, un nicho se puede mover de sitio sin tocar ni una fila, y el
--    estado economico no depende de geometria que cambian los constructores.

-- ---------------------------------------------------------------- las fotos
--
-- ⚠⚠ EL ESTADO ES UN VARCHAR, NO UN ENUM, Y ES LA LECCION DE V012. Un ENUM de
--    MariaDB guarda el INDICE, asi que meter un estado nuevo en medio convierte
--    unas filas en otras; y un valor que no esta en la lista NO DA ERROR --
--    guarda la cadena vacia con un aviso que nadie mira. Un VARCHAR solo dice
--    lo que la fila dice, y quien valida es el codigo (y lo vigila el
--    autotest).
--
-- ⚠⚠ `sha1` NO ES UNICA, y es a proposito: dos jugadores pueden subir la
--    MISMA imagen. En disco se guarda una sola vez (el fichero se llama como
--    su sha1), pero cada dueno tiene SU fila -- la propiedad es de quien la
--    subio, no del contenido.
CREATE TABLE IF NOT EXISTS santuario_foto (
  foto_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_id   BIGINT UNSIGNED NOT NULL,
  sha1       CHAR(40)        NOT NULL,
  estado     VARCHAR(16)     NOT NULL DEFAULT 'PENDIENTE',
  subida_ms  BIGINT          NOT NULL,
  PRIMARY KEY (foto_id),
  KEY ix_foto_sha1 (sha1),
  KEY ix_foto_owner (owner_id),
  CONSTRAINT fk_foto_owner FOREIGN KEY (owner_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- los nichos
--
-- ⚠⚠ `expira_ms` ES UN BIGINT DE EPOCH, NO UN TIMESTAMP, por el mismo motivo
--    que `card_pack_claim.claimed_ms`: el numero solo se compara con «ahora»
--    y se envia al cliente como cuenta atras, asi que no hay nada que
--    convertir y ninguna zona horaria que arrastrar.
--
-- ⚠ `owner_id` es NULL = nicho libre. La fila EXISTE siempre --la crea el
--    servicio desde la config al arrancar-- porque el estado de un nicho no
--    es «existe o no existe», es «libre o de alguien».
--
-- ⚠⚠ `honores` vive aqui y NO se recalcula sumando `santuario_honor`: las
--    filas de honores se BORRAN cuando el nicho se libera, y el total
--    tambien. Recalcular desde una tabla que se vacia dejaria el contador a
--    cero por la puerta de atras. El autotest comprueba que el total iguala
--    a la suma mientras el nicho esta reclamado.
CREATE TABLE IF NOT EXISTS santuario (
  nicho_id     VARCHAR(32)  NOT NULL,
  owner_id     BIGINT UNSIGNED NULL,
  permanente   TINYINT(1)   NOT NULL DEFAULT 0,
  expira_ms    BIGINT       NULL,
  foto_id      BIGINT UNSIGNED NULL,
  titulo       VARCHAR(32)  NOT NULL DEFAULT '',
  descripcion  VARCHAR(320) NOT NULL DEFAULT '',
  honores      BIGINT       NOT NULL DEFAULT 0,
  tocado_ms    BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (nicho_id),
  CONSTRAINT fk_santuario_owner FOREIGN KEY (owner_id)
    REFERENCES player (player_id) ON DELETE SET NULL,
  CONSTRAINT fk_santuario_foto FOREIGN KEY (foto_id)
    REFERENCES santuario_foto (foto_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------- los honores
--
-- ⚠⚠ LA CLAVE PRIMARIA ES (nicho_id, player_id), NO UN `id` CON INDICE. Asi
--    «un presupuesto de honores por jugador y por nicho» lo dice LA BASE,
--    venga de donde venga la peticion: dos clics rapidos o un paquete
--    reenviado chocan contra la clave en vez de contra un `if` en Java. Misma
--    decision que `clan_member`, `gym_badge` y `card_pack_claim`.
--
-- ⚠⚠ LA VENTANA VA JUNTA AL CONTADOR, EN LA MISMA FILA. Si fueran dos
--    tablas --«cuando empezo su dia» y «cuantos lleva»-- el dia que una se
--    resetee y la otra no, el jugador tendria diez honores con una ventana
--    fresca, o ninguno con una vieja, SIN NINGUN ERROR. Es la misma familia
--    que «son dos tablas» de la pantalla del inicial.
CREATE TABLE IF NOT EXISTS santuario_honor (
  nicho_id   VARCHAR(32)     NOT NULL,
  player_id  BIGINT UNSIGNED NOT NULL,
  ventana_ms BIGINT          NOT NULL,
  usados     INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (nicho_id, player_id),
  KEY ix_honor_player (player_id),
  CONSTRAINT fk_honor_nicho FOREIGN KEY (nicho_id)
    REFERENCES santuario (nicho_id) ON DELETE CASCADE,
  CONSTRAINT fk_honor_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------- el rastro de honores
--
-- ⚠⚠ `idem` UNICO ES LO QUE CORTA EL DOBLE CONTEO, y no es dinero pero es un
--    contador PUBLICO: un paquete reenviado no puede sumar dos honores. Sin
--    esta clave, el cliente que repite la peticion porque no llego la
--    respuesta infla el numero de un memorial sin querer -- y con un cliente
--    modificado, a proposito (P6). Misma leccion que `crate_open` y
--    `card_pack_grant`.
--
-- ⚠ Y de propina es auditoria: quien honro que memorial y cuando, para poder
--    contestar «no, a ti no te ha honrado nadie todavia» con datos y no de
--    memoria.
CREATE TABLE IF NOT EXISTS santuario_honor_click (
  idem       VARCHAR(64)     NOT NULL,
  nicho_id   VARCHAR(32)     NOT NULL,
  player_id  BIGINT UNSIGNED NOT NULL,
  hecho_ms   BIGINT          NOT NULL,
  PRIMARY KEY (idem),
  KEY ix_click_nicho (nicho_id, hecho_ms),
  CONSTRAINT fk_click_nicho FOREIGN KEY (nicho_id)
    REFERENCES santuario (nicho_id) ON DELETE CASCADE,
  CONSTRAINT fk_click_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (31, 'santuario: nichos de monumentos, fotos de memorial y honores diarios')
ON DUPLICATE KEY UPDATE version = version;
