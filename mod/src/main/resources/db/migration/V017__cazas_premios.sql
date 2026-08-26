-- Las cazas pasan a pagar OBJETOS, no solo dinero.
--
-- Peticion del usuario: «puede ser plata ejemplo 500 de plata, pokeballs
-- normales y basicas, algun caramelo pequeño o algo basico, y asi va
-- aumentando dependiendo de la estrella».
--
-- ⚠⚠ EL PREMIO SE GUARDA EN LA FILA, NO SE CALCULA AL COBRAR.
--
--    La pantalla lo ENSEÑA al pasar el raton, y entre que lo enseña y que el
--    jugador lo cobra pueden pasar 24 horas. Si el premio saliera de una tabla
--    en Java, tocar esa tabla pagaria algo DISTINTO de lo que se prometio --
--    sin ningun error, y al jugador le pareceria que le hemos engañado.
--
--    Es la misma regla que el precio de la tienda: lo que se enseña y lo que se
--    cobra tienen que salir del mismo sitio.
--
-- ⚠ `reward_item` admite NULO a proposito: un objetivo puede pagar solo dinero.
--   Un '' seria un identificador vacio, que es un objeto que no existe.

ALTER TABLE hunt_target
  ADD COLUMN reward_item VARCHAR(64) NULL DEFAULT NULL AFTER reward_mark,
  ADD COLUMN reward_qty  INT         NOT NULL DEFAULT 0 AFTER reward_item;

-- ⚠ Los ciclos que ya existen se quedan sin objeto, y esta bien: pagaran solo
--   dinero, que es lo que se les prometio cuando se crearon. El siguiente ciclo
--   --como mucho 24 h despues-- ya nace con premio completo.

INSERT INTO schema_version (version, description)
VALUES (17, 'cazas: premios en objetos')
ON DUPLICATE KEY UPDATE version = version;
