-- =====================================================================
-- V014 · CLANES: HISTORIAL DEL TESORO, REGISTRO DE ACCIONES Y UN TOPE
--
-- Peticion del usuario: «el historial de meter y sacar dinero, un sistema
-- de seguridad dentro del clan».
--
-- ⚠⚠ POR QUE HACEN FALTA DOS TABLAS Y NO UNA.
--
--    El dinero y el poder se rompen de formas distintas y se investigan de
--    formas distintas. «¿Quien vacio el tesoro?» se responde con una lista
--    de cantidades; «¿como llego ese a oficial?» se responde con una lista
--    de decisiones. Meterlo todo en una tabla con una columna `detalle` de
--    texto obliga a parsear texto para sumar dinero, y sumar dinero es
--    justo lo que NO puede depender de parsear texto (R2).
--
-- ⚠ NINGUNA DE LAS DOS SE PUEDE EDITAR NI BORRAR DESDE EL JUEGO. Un
--   registro que el sospechoso pueda limpiar no es un registro. No hay
--   DELETE en ningun camino del codigo: se borran solo cuando se disuelve
--   el clan, y por CASCADE.
-- =====================================================================


-- ---------------------------------------------------------------------
-- EL HISTORIAL DEL TESORO
--
-- ⚠ ESTO NO SUSTITUYE A `ledger_entry`, LO COMPLEMENTA, y son las dos
--   mitades del mismo asiento:
--
--     ledger_entry   el lado del JUGADOR   (su saldo baja 1.000)
--     clan_ledger    el lado del CLAN      (el tesoro sube 1.000)
--
--   Contabilidad de doble entrada de toda la vida. Con solo la primera no
--   se puede listar «los movimientos de ESTE clan» sin recorrer el libro
--   entero de todos los jugadores; con solo la segunda no cuadra el saldo
--   del jugador. Las dos se escriben en la MISMA transaccion, asi que o
--   estan las dos o no esta ninguna.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clan_ledger (
    entry_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    clan_id     BIGINT UNSIGNED NOT NULL,

    -- Quien lo movio. Se guarda el id y NO el nombre: los nombres cambian
    -- (online-mode=false) y un historial que miente sobre quien fue no
    -- sirve para nada (D-010).
    player_id   BIGINT UNSIGNED NOT NULL,

    -- ⚠ CON SIGNO: positivo entra, negativo sale. Guardar `cantidad` y un
    --   `tipo` aparte permite que los dos se contradigan --un APORTAR con
    --   cantidad negativa-- y entonces la suma del historial no cuadra con
    --   el tesoro. Con signo, la suma ES el tesoro. BIGINT, nunca FLOAT.
    delta       BIGINT          NOT NULL,

    -- El tesoro DESPUES del movimiento. Redundante a proposito: permite
    -- detectar un hueco (dos filas seguidas que no encajan) sin recalcular
    -- la serie entera, que es como se descubre una escritura perdida.
    balance_after BIGINT        NOT NULL,

    reason      VARCHAR(32)     NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- El indice que de verdad se usa: «los ultimos N de este clan».
    KEY ix_cl_clan (clan_id, entry_id),
    -- Y este es el del TOPE DIARIO: «cuanto ha sacado este de aqui hoy».
    KEY ix_cl_quien (clan_id, player_id, created_at),
    CONSTRAINT fk_cl_clan FOREIGN KEY (clan_id)
        REFERENCES clan(clan_id) ON DELETE CASCADE,
    CONSTRAINT fk_cl_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_cl_delta CHECK (delta <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- EL REGISTRO DE ACCIONES
--
-- Todo lo que cambia quien esta dentro y quien manda. Es lo que convierte
-- «me han echado y no se por que» en una linea con nombre y hora.
--
-- ⚠ `target_id` es NULL cuando la accion no va contra nadie (fundar,
--   disolver). Un 0 seria mas comodo de escribir y mentiria: 0 no es «nadie»,
--   es un player_id que algun dia puede existir.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clan_log (
    log_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    clan_id     BIGINT UNSIGNED NOT NULL,
    actor_id    BIGINT UNSIGNED NOT NULL,
    target_id   BIGINT UNSIGNED NULL,

    -- FUNDAR . INVITAR . ENTRAR . SALIR . ECHAR . ASCENDER . DEGRADAR
    -- TRASPASAR . DISOLVER . TOPE
    action      VARCHAR(16)     NOT NULL,
    detail      VARCHAR(64)     NOT NULL DEFAULT '',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    KEY ix_log_clan (clan_id, log_id),
    CONSTRAINT fk_log_clan FOREIGN KEY (clan_id)
        REFERENCES clan(clan_id) ON DELETE CASCADE,
    CONSTRAINT fk_log_actor FOREIGN KEY (actor_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
    -- ⚠ `target_id` NO lleva clave ajena A PROPOSITO. Con ON DELETE RESTRICT
    --   impediria borrar a un jugador para siempre por aparecer en un
    --   registro historico, y con CASCADE borraria el registro -- que es
    --   justo lo que un registro no puede hacer. Se guarda el numero y se
    --   resuelve el nombre al leer; si el jugador ya no existe, se enseña
    --   el id, que sigue siendo mas que nada.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- EL TOPE DIARIO DE LOS OFICIALES
--
-- ⚠⚠ ES LA PIEZA DE SEGURIDAD DE VERDAD, y es lo que faltaba. Hasta ahora
--    un oficial podia vaciar el tesoro entero de una sentada, y el unico
--    consuelo era que quedara escrito DESPUES. Un registro documenta el
--    robo; un tope lo impide.
--
-- ⚠ SOLO AFECTA A LOS OFICIALES. El lider no tiene tope porque el tope
--   seria suyo: puede subirlo, asi que ponerselo solo añade un clic. Lo
--   que se protege es «he ascendido a alguien y me ha vaciado la caja».
--
-- ⚠ POR DEFECTO 10.000, que son DOS Revivir a los precios de hoy. Es
--   deliberadamente bajo: un tope que no molesta nunca no protege nunca, y
--   el lider puede subirlo en un clic si su clan se fia.
--
-- ⚠ 0 = NO PUEDEN SACAR NADA. No es «sin limite»: «sin limite» se escribe
--   con un numero grande, y hacer que el 0 signifique infinito es como se
--   acaba con un tesoro vacio por escribir mal una cifra.
-- ---------------------------------------------------------------------
ALTER TABLE clan
    ADD COLUMN officer_daily_limit BIGINT NOT NULL DEFAULT 10000;

-- El CHECK va aparte: en un ADD COLUMN, MariaDB no siempre lo aplica a las
-- filas que ya existen, y aqui todas nacen con el valor por defecto.
ALTER TABLE clan
    ADD CONSTRAINT ck_clan_limit CHECK (officer_daily_limit >= 0);


INSERT INTO schema_version (version, description)
VALUES (14, 'clanes: historial del tesoro, registro de acciones y tope diario')
ON DUPLICATE KEY UPDATE version = version;
