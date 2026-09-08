-- PASE DE BATALLA LUNA: temporada, progreso y recompensas cobradas.
--
-- El diseño completo esta en docs/economy/pase-batalla.md y lo decide D-045.
-- Aqui solo vive el ESTADO; la curva de niveles, cuanto da cada accion y que
-- recompensa lleva cada nivel son CONTENIDO y viven en el codigo, igual que
-- `Cofre.java` para los tesoros y `Catalogo.java` para los cosmeticos.
--
-- ⚠⚠⚠ SE GUARDA LA XP TOTAL DE LA TEMPORADA, NO EL NIVEL.
--
--    Guardar «nivel 12 y 340 sueltos» es lo que hace `player_path`, y alli
--    esta bien porque las Vias no cambian de curva. Un pase SI: la curva es
--    calibracion, y calibrar es justo lo que este proyecto tiene pendiente en
--    toda la economia. Con nivel guardado, tocar la curva NO recalcularia a
--    nadie -- la gente se quedaria con el nivel viejo y una XP suelta que ya
--    no significa lo mismo, SIN UN SOLO ERROR.
--
--    Con la XP total, el nivel es una FUNCION PURA de un numero (`PaseNivel`),
--    asi que cambiar la curva recoloca a todo el mundo de forma consistente.
--    Es la misma decision que «la mascara de medallas se compone al leer».
--
-- ⚠⚠ Y POR ESO `pase_reclamo` ES POR NIVEL Y NO SE DEDUCE DE LA XP: lo cobrado
--    esta cobrado. Si el nivel se recalculara y alguien bajara, no se le puede
--    quitar lo que ya tiene en el inventario -- y sin esta tabla se lo podria
--    volver a cobrar al subir otra vez.

-- ------------------------------------------------------------- la temporada
--
-- UNA sola fila (id = 1). Una tabla de una fila parece raro y es lo correcto:
-- el numero de temporada es estado del SERVIDOR, tiene que sobrevivir a un
-- reinicio y lo leen consultas que ya estan en la base. Un fichero JSON
-- --que es lo que hace la Torre-- obliga a mantener dos fuentes de verdad en
-- sitios distintos y no se puede unir con `pase_jugador` en una consulta.
--
-- ⚠ `empieza_ms` / `acaba_ms` son BIGINT de epoch y no TIMESTAMP, por el mismo
--   motivo que `card_pack_claim.claimed_ms`: el numero solo se compara con
--   «ahora» y viaja al cliente como cuenta atras. Nada que convertir, ninguna
--   zona horaria que arrastrar.
CREATE TABLE IF NOT EXISTS pase_temporada (
  id         TINYINT UNSIGNED NOT NULL DEFAULT 1,
  numero     INT              NOT NULL DEFAULT 1,
  empieza_ms BIGINT           NOT NULL,
  acaba_ms   BIGINT           NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- La primera temporada empieza al aplicar la migracion y dura 60 dias.
INSERT INTO pase_temporada (id, numero, empieza_ms, acaba_ms)
VALUES (1, 1, UNIX_TIMESTAMP() * 1000, (UNIX_TIMESTAMP() + 60 * 86400) * 1000)
ON DUPLICATE KEY UPDATE id = id;

-- --------------------------------------------------------- el progreso
--
-- Una fila por (jugador, temporada). La temporada va en la CLAVE y no se
-- borra al rotar: asi se puede mirar hacia atras --«¿cuanta gente termino la
-- temporada 1?»-- y una temporada nueva no puede pisar el progreso de la
-- anterior por un fallo de borrado.
--
-- ⚠⚠⚠ `dia`, `xp_hoy` y `tope_hoy` SON EL TOPE DIARIO, Y ES LA PIEZA QUE
--    IMPIDE QUE EL PASE SE COMPLETE EN UNA SEMANA.
--
--    Sin tope, la fuente mas rentable de todas se convierte en la unica que
--    alguien usa, y el pase se acaba el primer fin de semana. Con tope, NINGUNA
--    fuente puede desbordar: da igual que la Torre pague 200 por ronda o que
--    aparezca una fuente nueva mañana -- el techo del dia es el mismo.
--
--    `tope_hoy` se guarda en vez de calcularse porque lleva DESCANSO
--    ACUMULADO: quien no juega un dia se lleva ese tope al siguiente, hasta
--    tres dias. Un jugador de fin de semana no queda fuera del pase.
--
-- ⚠⚠ `premium` ES UNA COLUMNA Y NO UNA TABLA DE COMPRAS: la pregunta que se
--    hace mil veces es «¿este jugador tiene la via Luna de ESTA temporada?», y
--    esa es una respuesta por fila. El registro economico de la compra vive
--    donde vive todo el dinero: en `ledger_entry`, con su clave de
--    idempotencia (R3, R4).
CREATE TABLE IF NOT EXISTS pase_jugador (
  player_id BIGINT UNSIGNED NOT NULL,
  temporada INT              NOT NULL,
  xp        BIGINT           NOT NULL DEFAULT 0,
  premium   TINYINT(1)       NOT NULL DEFAULT 0,
  dia       DATE                 NULL,
  xp_hoy    INT              NOT NULL DEFAULT 0,
  tope_hoy  INT              NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id, temporada),
  CONSTRAINT fk_pase_jugador_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------- lo ya cobrado
--
-- ⚠⚠⚠ LA CLAVE PRIMARIA ES (jugador, temporada, nivel, via), Y ESA ES LA
--    REGLA. No la dice una comprobacion en Java: la dice la clave, asi que
--    cobrar dos veces el mismo premio FALLA EN LA BASE venga de donde venga la
--    peticion -- dos clics rapidos, un cliente modificado, un reintento de red.
--    Misma decision que `gym_badge` y que `clan_member`.
--
-- ⚠ `via` es VARCHAR y no un ENUM de MariaDB. Es la leccion de V012: un ENUM
--   guarda el INDICE, asi que reordenarlo convierte unas filas en otras, y un
--   valor que no este en la lista NO DA ERROR -- guarda la cadena vacia.
CREATE TABLE IF NOT EXISTS pase_reclamo (
  player_id  BIGINT UNSIGNED   NOT NULL,
  temporada  INT               NOT NULL,
  nivel      SMALLINT UNSIGNED NOT NULL,
  via        VARCHAR(8)        NOT NULL,
  cobrado_en TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id, temporada, nivel, via),
  KEY ix_pase_reclamo (player_id, temporada),
  CONSTRAINT fk_pase_reclamo_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ⚠ CADA MIGRACION SE REGISTRA A SI MISMA. El motor solo comprueba la tabla,
--   no la ejecucion: sin este INSERT el arranque falla en seco.
INSERT INTO schema_version (version, description)
VALUES (32, 'pase de batalla: temporada, progreso y reclamos')
ON DUPLICATE KEY UPDATE version = version;
