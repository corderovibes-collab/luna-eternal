-- La rareza, guardada en la fila.
--
-- Hasta hoy la rareza se DERIVABA de la posicion al leer («el primero de su
-- tipo es ★»), y eso era correcto mientras la rareza FUERA la posicion. Desde
-- que la decide el Pokemon --su BST, su ratio de captura, si es forma final--
-- derivarla al leer tiene un problema que no salta a la vista:
--
-- ⚠⚠ EL PREMIO YA ESTA GUARDADO EN LA FILA (V017). Si la rareza se recalculara
--    al leer y alguien tocara la formula, un objetivo creado ayer enseñaria
--    ★★ y pagaria lo de ★ -- sin ningun error, y con las dos cosas «bien»
--    cada una por su lado.
--
--    Es exactamente la razon por la que el premio se guarda: lo que se ENSEÑA
--    y lo que se COBRA tienen que salir del mismo sitio y del mismo momento.
--
-- ⚠ Los ciclos que ya existen se quedan a 0, y `leer` lo trata como «no
--   guardada»: para esos se sigue derivando de la posicion, que es lo que se
--   les prometio al crearlos. El siguiente ciclo ya nace con su rareza.

ALTER TABLE hunt_target
  ADD COLUMN rarity TINYINT NOT NULL DEFAULT 0 AFTER kind;

INSERT INTO schema_version (version, description)
VALUES (19, 'cazas: rareza guardada en la fila')
ON DUPLICATE KEY UPDATE version = version;
