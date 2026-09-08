-- PASE DE BATALLA: una sola via, de pago, y cien niveles (D-046, revoca D-045).
--
-- El esquema NO cambia. Lo unico que hace esta migracion es BORRAR LOS RECLAMOS
-- de la estructura anterior, y hay que explicar por que, porque borrar filas es
-- lo ultimo que se hace en este proyecto.
--
-- ⚠⚠⚠ EL NIVEL 3 YA NO DA LO MISMO QUE DABA AYER.
--
--    Ayer el pase tenia CINCUENTA niveles y DOS vias --una gratis con Plata y
--    objetos, otra de pago con cosmeticos--. Hoy son CIEN niveles, una sola via
--    de pago, y cada nivel da otra cosa: el 3 pasa de «900 de Plata» a «10
--    Pociones». Una fila de `pase_reclamo` dice «este jugador ya cobro el nivel
--    3», y con el catalogo nuevo eso significa **ya cobraste algo que nunca
--    recibiste**: el premio de hoy se quedaria bloqueado para siempre.
--
--    No es un fallo que de error: es un nivel en gris que nadie sabe por que
--    esta en gris.
--
-- ⚠⚠ Y SE PUEDE BORRAR SIN MIRAR A NADIE PORQUE EL SISTEMA TIENE UN DIA. Se
--    desplego el 2026-09-08 a las 02:16 y nadie ha comprado el pase todavia
--    (`premium` esta a 0 en todas las filas), asi que no hay ni un premio
--    entregado que este siendo borrado. Si esto se hiciera con el pase vivo,
--    la migracion correcta seria OTRA: rotar la temporada, que deja el historico
--    intacto y empieza de cero.
--
-- ⚠ LA XP NO SE TOCA. Es lo que el jugador se ha ganado, y ademas no hace falta:
--   el nivel es una FUNCION PURA de la XP total (`PaseNivel.nivelDe`), asi que
--   al cambiar la curva de 50 a 100 niveles todo el mundo se recoloca solo. Es
--   exactamente para esto que V032 guarda la XP y no el nivel.

DELETE FROM pase_reclamo;

-- ⚠ La columna `via` se queda. Sobra --solo hay una via-- y quitarla seria
--   reescribir una tabla viva para ahorrar ocho bytes por fila; el codigo
--   escribe siempre 'luna' y la clave primaria sigue haciendo su trabajo.

INSERT INTO schema_version (version, description)
VALUES (33, 'pase: una sola via de pago y cien niveles (D-046)')
ON DUPLICATE KEY UPDATE version = version;
