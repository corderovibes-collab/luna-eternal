-- V035 · EL PASEO POR EL SANTUARIO: visitar memoriales da XP del pase, y
--        honrar diez nichos da una Ultra Ball.
--
-- ⚠⚠⚠ LA XP POR VISITAR ES UNA FUENTE QUE CRECE CON EL NUMERO DE NICHOS, Y ESO
--    ES LO PELIGROSO DE ESTE SISTEMA. Hoy hay 341 nichos construidos: sin techo,
--    «una vez cada 24 h por nicho» son 341 cobros al dia por hacer clic derecho,
--    sin jugar a nada -- y el numero SUBE cada vez que el equipo construye mas.
--    Es la unica fuente de XP del pase que no depende de jugar sino de cuanto
--    hemos construido nosotros.
--    Por eso el techo NO esta en cuanto paga cada visita sino en CUANTAS VISITAS
--    CUENTAN AL DIA (`PaseXp.VISITAS_DIA`): asi, construir mil nichos mas no
--    mueve ni una XP.
--
-- ⚠⚠ NI UN CONTADOR MAS DE LOS NECESARIOS. «Cuantos nichos he honrado hoy» NO
--    se guarda: se CUENTA sobre `santuario_honor`, que ya lleva una fila por
--    (nicho, jugador) con su ventana. Un contador aparte seria un segundo sitio
--    donde vive la misma verdad, y el dia que se desincronizara pagaria una
--    Ultra Ball de mas o de menos sin dar ningun error.
--
-- ⚠ `santuario_visita` SI hace falta: no hay ninguna tabla que sepa que un
--   jugador ABRIO un memorial. Honrar y mirar son cosas distintas -- se puede
--   mirar sin honrar, y de hecho es lo normal.

-- Que memoriales ha abierto cada jugador, y cuando por ultima vez.
--
-- ⚠ Una fila por (jugador, nicho) y se PISA al volver a visitar: no interesa el
--   historial, interesa «¿ya cobre este hoy?». Guardar cada visita seria una
--   tabla que crece para siempre con 341 nichos y ninguna pregunta que
--   responder.
CREATE TABLE IF NOT EXISTS santuario_visita (
  player_id  BIGINT UNSIGNED NOT NULL,
  nicho_id   VARCHAR(32)     NOT NULL,
  visto_ms   BIGINT          NOT NULL,
  PRIMARY KEY (player_id, nicho_id),
  KEY ix_visita_dia (player_id, visto_ms),
  CONSTRAINT fk_visita_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE,
  CONSTRAINT fk_visita_nicho FOREIGN KEY (nicho_id)
    REFERENCES santuario (nicho_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cuando cobro cada jugador su Ultra Ball por honrar diez nichos.
--
-- ⚠⚠ UNA FILA POR JUGADOR Y LA CLAVE PRIMARIA ES EL JUGADOR: cobrar dos veces
--    el mismo ciclo FALLA EN LA BASE, no en un `if`. Es la misma decision que
--    `clan_member` y que `pase_reclamo` -- dos clics rapidos, un cliente
--    modificado o un reintento dan igual.
CREATE TABLE IF NOT EXISTS santuario_premio (
  player_id  BIGINT UNSIGNED NOT NULL,
  ultimo_ms  BIGINT          NOT NULL,
  cobrados   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id),
  CONSTRAINT fk_premio_player FOREIGN KEY (player_id)
    REFERENCES player (player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (35, 'santuario: visitas que dan XP del pase y la Ultra Ball por diez honores')
ON DUPLICATE KEY UPDATE version = version;
