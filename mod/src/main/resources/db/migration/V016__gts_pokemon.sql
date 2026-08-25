-- =====================================================================
-- V016 · EL MERCADO DE POKEMON: filtrar de verdad, y tasar
--
-- Peticion del usuario (2026-08-24): filtros avanzados por IVs y EVs uno a
-- uno, por naturaleza, genero, shiny y tera; y un PRECIO ESTIMADO que salga
-- de las estadisticas del ejemplar y se vaya sincronizando con lo que la
-- gente paga de verdad.
--
-- `gts_listing` ya guardaba especie, nivel, shiny y el TOTAL de IVs, y su
-- comentario original ya decia por que: «filtrar dentro de un blob
-- serializado es inviable, y el brief pide filtrar por 9 atributos. El blob
-- sigue siendo la verdad; las columnas son el indice». Esto es exactamente
-- esa idea llevada hasta donde hacia falta.
--
-- Detalle del tasador en docs/trading/mercado.md §5-bis
-- =====================================================================


-- ---------------------------------------------------------------------
-- LOS SEIS IVs Y LOS SEIS EVs, UNO A UNO
--
-- ⚠⚠ EL TOTAL NO BASTA, Y NO ES POR COMODIDAD. El mercado competitivo no
--    busca «muchos IVs»: busca «31 en Ataque». Un filtro por total deja
--    fuera justo la pregunta que se hace todo el mundo, y no hay forma de
--    responderla sin las columnas -- dentro del blob no se puede filtrar.
--
-- ⚠ TINYINT UNSIGNED: un IV va de 0 a 31 y un EV de 0 a 252. Caben de
--   sobra, y el tipo pequeño importa porque son DOCE columnas nuevas por
--   fila.
-- ---------------------------------------------------------------------
ALTER TABLE gts_listing
    ADD COLUMN iv_hp   TINYINT UNSIGNED NULL,
    ADD COLUMN iv_atk  TINYINT UNSIGNED NULL,
    ADD COLUMN iv_def  TINYINT UNSIGNED NULL,
    ADD COLUMN iv_spa  TINYINT UNSIGNED NULL,
    ADD COLUMN iv_spd  TINYINT UNSIGNED NULL,
    ADD COLUMN iv_spe  TINYINT UNSIGNED NULL,
    ADD COLUMN ev_hp   TINYINT UNSIGNED NULL,
    ADD COLUMN ev_atk  TINYINT UNSIGNED NULL,
    ADD COLUMN ev_def  TINYINT UNSIGNED NULL,
    ADD COLUMN ev_spa  TINYINT UNSIGNED NULL,
    ADD COLUMN ev_spd  TINYINT UNSIGNED NULL,
    ADD COLUMN ev_spe  TINYINT UNSIGNED NULL;


-- ---------------------------------------------------------------------
-- LO DEMAS QUE SE FILTRA Y SE ENSEÑA
--
-- ⚠ `perfect_ivs` es redundante --se puede contar de las seis columnas-- y
--   esta a proposito: es POR LO QUE SE ORDENA. «Los mejores primero» con un
--   recuento calculado obliga a mirar seis columnas por fila y tira
--   cualquier indice. Guardarlo es una escritura al publicar contra un
--   escaneo en cada busqueda.
-- ---------------------------------------------------------------------
ALTER TABLE gts_listing
    ADD COLUMN nature       VARCHAR(32)  NULL,
    ADD COLUMN ability      VARCHAR(64)  NULL,
    ADD COLUMN gender       VARCHAR(16)  NULL,
    ADD COLUMN tera_type    VARCHAR(32)  NULL,
    ADD COLUMN ball         VARCHAR(64)  NULL,
    ADD COLUMN ev_total     SMALLINT UNSIGNED NULL,
    ADD COLUMN perfect_ivs  TINYINT UNSIGNED NULL,

    -- La rareza que dice Cobblemon en las etiquetas de la especie:
    -- COMUN . STARTER . FOSIL . PARADOJA . ULTRAENTE . LEGENDARIO . MITICO
    -- ⚠ Se guarda AL PUBLICAR y no se consulta al vuelo: las etiquetas viven
    --   en los datos de un mod, y un dia ese mod cambia. Lo que se vendio
    --   como legendario tiene que seguir constando como tal.
    ADD COLUMN rarity       VARCHAR(16)  NULL;


-- ---------------------------------------------------------------------
-- EL ESTIMADO AL PUBLICAR
--
-- ⚠⚠⚠ ESTA COLUMNA ES LO QUE HACE POSIBLE QUE EL TASADOR APRENDA.
--
--     La correccion del mercado no es «la mediana de lo que se paga por esa
--     especie»: eso mezclaria un shiny 6x31 con un ejemplar de nivel 5, y la
--     mediana de esa mezcla no describe a ninguno de los dos.
--
--     Lo que se corrige es LA CALIBRACION DE LA FORMULA:
--
--         ratio = precio_real / estimated
--
--     y la mediana de esos ratios dice «la formula se queda corta un 30 %»,
--     que si es una afirmacion util. Sin guardar el estimado del momento, ese
--     ratio no se puede calcular NUNCA -- recalcular la formula hoy daria
--     otro numero, porque la formula habra cambiado.
--
-- ⚠ Y solo cuentan las ventas CERRADAS. Un precio que nadie ha pagado no es
--   informacion: publicar un Magikarp a diez millones moveria la referencia
--   gratis.
-- ---------------------------------------------------------------------
ALTER TABLE gts_listing
    ADD COLUMN estimated    BIGINT       NULL;


-- Los indices de las busquedas que de verdad se hacen.
-- ⚠ Todos empiezan por `state`: sin eso, buscar recorreria tambien lo
--   vendido y lo caducado, que con el tiempo es la mayor parte de la tabla.
CREATE INDEX ix_gts_calidad ON gts_listing (state, species, perfect_ivs);
CREATE INDEX ix_gts_rareza  ON gts_listing (state, rarity, price);
-- Y este es el del TASADOR: «las ventas cerradas de esta especie que tenian
-- estimado», que es la consulta de la correccion.
CREATE INDEX ix_gts_tasador ON gts_listing (species, state, sold_at);


INSERT INTO schema_version (version, description)
VALUES (16, 'GTS: filtros por IV/EV, rareza y el estimado del tasador')
ON DUPLICATE KEY UPDATE version = version;
