-- =====================================================================
-- V012 · Los OFICIOS: minero, pescador y agricultor
--
-- Decision del usuario: «puede hacer varios trabajos y con el boton de
-- atras y adelante... mineria, cocina, pesca de pokemons... si sube de
-- nivel en cada una va a recibir Plata, y si completa todo recibe una
-- pequeña cantidad de LunaCoins, muy poquitas».
--
-- ⚠⚠ POR QUE HACE FALTA UNA MIGRACION PARA AÑADIR TRES NOMBRES
--
-- `player_path.path` es un ENUM de MariaDB con los cinco valores escritos
-- dentro. No es un VARCHAR: insertar 'MINERO' en el ENUM viejo NO da un
-- error claro -- MariaDB guarda la cadena VACIA y suelta un aviso que
-- nadie mira--, asi que el oficio se "guardaria" y al leerlo no existiria.
--
-- Se eligio ENUM a proposito en V004 (la columna es la clave primaria
-- junto al jugador, y el ENUM ocupa un byte en vez de una cadena), y esta
-- es la factura de esa decision: cada oficio nuevo cuesta una migracion.
-- Sigue siendo la eleccion correcta, pero conviene saber lo que cuesta.
--
-- ⚠ ALTER TABLE ... MODIFY sobre un ENUM CONSERVA las filas existentes
--   mientras los valores viejos sigan en la lista nueva y EN EL MISMO
--   ORDEN: MariaDB guarda el indice, no el texto. Reordenarlos convertiria
--   a todos los Exploradores en Entrenadores, en silencio.
--   Por eso los cinco de antes van primero y en su orden original.
--
-- ⚠ COCINERO NO ESTA, y es deliberado. Cobblemon 1.7 tiene olla de cocina
--   (`CookingPotMenu`, `CookingPotRecipe`) pero NO PUBLICA NINGUN EVENTO
--   para ella: se comprobaron sus 98 eventos y no hay ninguno de cocinar.
--   Engancharlo pide un mixin dentro de su codigo, y este mod no tiene
--   mixins todavia. Añadir el valor al ENUM ahora dejaria un oficio que
--   NUNCA da XP -- que es justo el fallo silencioso que este proyecto
--   lleva toda la semana pagando. Entra cuando entre su enganche.
-- =====================================================================

ALTER TABLE player_path
    MODIFY path ENUM('EXPLORADOR','ENTRENADOR','COLECCIONISTA',
                     'COMERCIANTE','CRIADOR',
                     'MINERO','PESCADOR','AGRICULTOR') NOT NULL;

-- ---------------------------------------------------------------------
-- La recompensa por completar TODOS los oficios.
--
-- ⚠ NO hace falta tabla: la paga la economia con una CLAVE DE
--   IDEMPOTENCIA derivada del jugador (`oficios_completos:<player_id>`), y
--   el motor rechaza la segunda con ALREADY_APPLIED.
--
--   Una clave derivada del OBJETO en vez de la OPERACION suele estar mal
--   --ya nos mordio con los cosmeticos-- pero aqui es correcta, y la
--   diferencia importa: aquello se podia DESHACER (retirar un cosmetico y
--   volver a comprarlo), y esto no. Los niveles no bajan, asi que
--   «completar todos los oficios» ocurre como mucho una vez en la vida de
--   una cuenta.
-- ---------------------------------------------------------------------

INSERT INTO schema_version (version, description)
VALUES (12, 'Oficios: minero, pescador y agricultor')
ON DUPLICATE KEY UPDATE version = version;
