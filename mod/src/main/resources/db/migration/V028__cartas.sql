-- CARTAS: los dos relojes de la pantalla de sobres.
--
-- La pantalla tiene TRES zonas y solo DOS filas aqui, y eso es correcto: la
-- de LunaCoins no tiene reloj --se abre las veces que quieras-- asi que no
-- tiene nada que recordar.
--
--   DIARIO  gratis, un sobre cada 24 h
--   PLATA   cuesta Plata, un sobre cada 24 h
--   (luna)  cuesta LunaCoins, sin limite  -> no aparece en esta tabla
--
-- ⚠⚠⚠ DOS FILAS Y NO UNA COLUMNA `claimed_at` SUELTA. Es lo que impide que
--    cobrar el sobre gratis gaste tambien el de pago. Con un solo reloj, la
--    pantalla ensenaria dos zonas y el jugador descubriria --pagando-- que
--    comparten temporizador. No daria ningun error: daria a alguien que ha
--    pagado por nada.
--
-- ⚠⚠ Y LA CLAVE PRIMARIA ES (player_id, kind), NO UN `id` CON INDICE. Asi
--    «un reloj por jugador y por tipo» lo dice LA BASE, venga de donde venga
--    la peticion: dos clics rapidos, un paquete reenviado o un cliente
--    modificado chocan contra la clave en vez de contra un `if` en Java.
--    Misma decision que `clan_member` y que `gym_badge`.

-- ------------------------------------------------------------- los relojes
--
-- ⚠⚠ `claimed_ms` ES UN BIGINT DE EPOCH, NO UN TIMESTAMP, y es a proposito.
--
--    Un TIMESTAMP arrastra zona horaria, y aqui el numero no se lee nunca
--    como fecha: se le suman 24 h y se manda al cliente como INSTANTE
--    ABSOLUTO para que EL dibuje la cuenta atras (si el servidor mandara
--    «faltan 7 h», el numero se quedaria viejo al minuto). Un epoch en
--    milisegundos es lo mismo en las dos maquinas y no hay nada que convertir.
--
--    Es ademas lo que ya hace Cazas con el fin de su ciclo.
CREATE TABLE IF NOT EXISTS card_pack_claim (
  player_id   BIGINT UNSIGNED NOT NULL,
  kind        VARCHAR(16)     NOT NULL,
  claimed_ms  BIGINT          NOT NULL,
  PRIMARY KEY (player_id, kind),
  CONSTRAINT fk_pack_claim_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------------- el registro
--
-- QUE sobre se dio, a QUIEN, CUANDO y CUANTO costo.
--
-- ⚠⚠ NO es contabilidad --de eso ya se encarga `ledger_entry` con cada cobro--
--    sino la respuesta a «pague y no me llego». Un sobre es un OBJETO, y
--    entregar un objeto es la unica parte de todo esto que NO puede vivir
--    dentro de la transaccion: un inventario no es una tabla. Si el inventario
--    esta lleno el sobre cae al suelo, y si el jugador se desconecta en ese
--    instante, esta fila es lo unico que queda para saber que existio.
--
-- ⚠⚠⚠ `idem` UNICO ES LO QUE CORTA EL DOBLE COBRO. Abrir un sobre son tres
--    cosas en sitios distintos --mirar el reloj, cobrar, entregar-- y un doble
--    clic puede colarse entre medias. Sin esta clave, el jugador paga dos
--    veces y recibe un sobre. Misma leccion que `crate_open`.
CREATE TABLE IF NOT EXISTS card_pack_grant (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  player_id   BIGINT UNSIGNED NOT NULL,
  kind        VARCHAR(16)     NOT NULL,
  currency    VARCHAR(16)     NULL,
  price       BIGINT          NOT NULL DEFAULT 0,
  idem        VARCHAR(64)     NOT NULL,
  granted_at  TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_pack_grant_idem (idem),
  KEY ix_pack_grant_player (player_id, granted_at),
  CONSTRAINT fk_pack_grant_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (28, 'cartas: relojes de sobre diario y de plata, y registro de entregas')
ON DUPLICATE KEY UPDATE version = version;
