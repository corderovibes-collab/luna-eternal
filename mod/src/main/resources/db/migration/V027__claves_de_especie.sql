-- V027 — los identificadores de especie que se guardaron mal
--
-- ⚠⚠⚠ TRES SITIOS GUARDABAN EL NOMBRE VISIBLE COMO IDENTIFICADOR, y para 247
--     de las 251 especies de Kanto y Johto eso da el identificador bueno. Para
--     cuatro, no. Medido contra la base de produccion antes de escribir esto:
--
--       hunt_target.species    'mr. mime'   'farfetch’d'   <- REVIENTAN
--       pokedex_entry.species  'nidoran-f'  'nidoran-m'    <- se callan
--
-- ⚠⚠ Y LOS DE ABAJO SON LOS PEORES. El espacio y el apostrofo no valen en la
--    ruta de un Identifier, asi que al resolverlos salta una excepcion y se
--    nota. El GUION SI VALE: resuelve a null, sin error y sin traza, y esas dos
--    filas llevaban meses ahi.
--
-- ⚠ `gts_listing.species` NO SE TOCA. Ahi se guarda a proposito el nombre para
--   enseñarlo ('Charizard'), y quien dibuja el modelo ya lo normaliza. Cambiarlo
--   dejaria la lista del GTS en minusculas sin arreglar nada.

-- Las cazas: no hay clave que pueda chocar, y el ciclo rota cada 24 h de todas
-- formas. Se arregla igual para que el ciclo VIVO deje de reventar ya.
UPDATE hunt_target
   SET species = 'mrmime'    WHERE species = 'mr. mime';
UPDATE hunt_target
   SET species = 'farfetchd' WHERE species IN ('farfetch’d', 'farfetch''d');

-- ⚠⚠⚠ LA POKEDEX TIENE CLAVE PRIMARIA (player_id, species), asi que un UPDATE
--     a secas revienta si alguien tuviera YA la fila buena — se apunto un
--     Nidoran-M antes del arreglo y otro despues. Hoy no pasa, pero un dia que
--     pasara la migracion fallaria A MEDIAS y el servidor entraria en bucle.
--     Asi que primero se borra la que sobraria, y luego se renombra la otra.
DELETE mala FROM pokedex_entry mala
  JOIN pokedex_entry buena
    ON buena.player_id = mala.player_id
   AND buena.species = REPLACE(mala.species, '-', '')
 WHERE mala.species IN ('nidoran-f', 'nidoran-m');

UPDATE pokedex_entry SET species = 'nidoranf' WHERE species = 'nidoran-f';
UPDATE pokedex_entry SET species = 'nidoranm' WHERE species = 'nidoran-m';

INSERT INTO schema_version (version, description)
VALUES (27, 'identificadores de especie: nombre visible -> identificador');
