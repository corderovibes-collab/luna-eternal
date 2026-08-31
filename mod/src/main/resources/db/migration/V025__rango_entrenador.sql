-- EL RANGO MAS BAJO PASA DE «NOVATO» A «ENTRENADOR» (decision del usuario,
-- 2026-08-30).
--
-- ⚠⚠⚠ ESTO NO ES SOLO UN RENOMBRADO DE JAVA: EL VALOR ESTA GUARDADO.
--
--    `player.rank_id` es un VARCHAR con el NOMBRE del rango dentro, y V020 lo
--    eligio asi A PROPOSITO para que añadir un rango fuera una linea de Java en
--    vez de una migracion. El precio de aquella decision se paga hoy: renombrar
--    la constante deja las filas viejas diciendo 'NOVATO'.
--
--    ⚠⚠ Y NO DARIA NINGUN ERROR, que es lo peor que puede pasar. `Rank.de` cae
--       al rango por defecto ante un nombre que no reconoce --y el rango por
--       defecto es justo este-- asi que todo seguiria funcionando: la mochila
--       abriria su fila, el tablist pintaria su etiqueta, y lo unico raro seria
--       un aviso en el log por cada jugador que entrara. Una fila que nombra
--       algo que ya no existe, y un sistema que lo disimula.
--
--    ⚠ V020 escribio que aquel renombrado salio gratis porque «no habia ni un
--      rango guardado en ningun sitio». Ese ya no es el caso: esa ventana se
--      cerro el dia que se guardo el primero.
--
-- ⚠⚠ HAY QUE CAMBIAR TAMBIEN EL VALOR POR DEFECTO DE LA COLUMNA. Sin eso las
--    filas viejas quedan bien y cada jugador NUEVO nace con 'NOVATO': el mismo
--    fallo, pero solo para los que lleguen despues, que es la clase de cosa que
--    se descubre semanas mas tarde y sin relacionarla con su causa.
--
-- ⚠ 'ENTRENADOR' son 10 caracteres y la columna admite 16. Se comprueba porque
--   ya nos mordio una vez: una columna cuatro caracteres demasiado corta hacia
--   que TODAS las transferencias fallaran, y lo encontro el autotest.

UPDATE player SET rank_id = 'ENTRENADOR' WHERE rank_id = 'NOVATO';

ALTER TABLE player
  MODIFY COLUMN rank_id VARCHAR(16) NOT NULL DEFAULT 'ENTRENADOR';

-- El traje del rango mas bajo se llama como su rango, asi que su identificador
-- viaja con el.
--
-- ⚠ Hoy no lo lleva nadie: los cinco trajes estan sin arte y `listo` impide
--   ponerselos. Se migra igual porque la fila PUEDE existir, y una fila que
--   apunta a un traje inexistente se dibuja exactamente igual que «no llevas
--   ninguno» -- o sea que seria invisible.
UPDATE player_suit SET suit = 'entrenador' WHERE suit = 'novato';

INSERT INTO schema_version (version, description)
VALUES (25, 'el rango mas bajo pasa a llamarse ENTRENADOR')
ON DUPLICATE KEY UPDATE version = version;
