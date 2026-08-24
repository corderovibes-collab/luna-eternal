-- =====================================================================
-- V015 · EL MERCADO: LIBRO DE ORDENES
--
-- Peticion del usuario: «economia super avanzada estilo Albion... poder
-- crear ofertas de venta y de compra bien estructuradas».
--
-- Lo que el GTS ya tenia era un TABLON DE ANUNCIOS: un vendedor pone una
-- cosa a un precio y alguien la acepta. Correcto, con custodia y con
-- impuesto progresivo -- pero solo empuja un lado.
--
-- ⚠⚠ ESTO ES PARA OBJETOS Y NO PARA POKEMON, Y NO ES UNA LIMITACION: es la
--    decision que estructura el sistema (D-041). Una orden de compra dice
--    «pago 500 por UNA UNIDAD DE X», y eso solo significa algo si todas las
--    X son iguales. «Pago 500 por un Charizard» es una orden sin sentido:
--    recibirias el peor Charizard del servidor, porque es el que cualquier
--    vendedor racional entregaria.
--
--    Los Pokemon siguen en `gts_listing`, que es exactamente el mecanismo
--    que les corresponde: se mira ESE ejemplar y se compra ESE.
--
-- Detalle completo en docs/trading/mercado.md
-- =====================================================================


-- ---------------------------------------------------------------------
-- LAS ORDENES
--
-- ⚠⚠⚠ LA CUSTODIA ES DOBLE, Y ES EL INVARIANTE QUE SOSTIENE TODO.
--
--     VENTA  retiene los OBJETOS   (ya lo hacia el GTS)
--     COMPRA retiene el DINERO     (esto es lo nuevo, y es lo que se olvida)
--
--     Sin la segunda: pones una compra de 1.000.000, te gastas el dinero, y
--     cuando alguien vende el servidor tiene que pagar lo que tu no tenias.
--     El vendedor ya entrego, asi que cobra si o si -- y ese dinero sale de
--     la nada. Es la forma numero uno de romper una economia de mercado.
--
-- ⚠⚠ Y AQUI NO HAY `payload`, AL CONTRARIO QUE EN gts_listing. No es un
--    olvido: es lo que significa FUNGIBLE.
--
--    Solo se admiten objetos SIN datos propios --sin encantamientos, sin
--    nombre puesto, sin componentes--, asi que `item_id` mas una cantidad
--    describe la mercancia POR COMPLETO. Eso cierra de golpe una familia
--    entera de fallos: si dos ordenes "iguales" pudieran llevar dentro
--    cosas distintas, el libro estaria mintiendo -- comprarias la mas
--    barata y recibirias la peor, que es exactamente el problema que hace
--    que los Pokemon no puedan ir aqui.
--
--    Una picoleta encantada NO es fungible. Esas se venden por el GTS.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS market_order (
    order_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_id    BIGINT UNSIGNED NOT NULL,

    -- ⚠ ENUM, y el orden IMPORTA: MariaDB guarda el INDICE, no el texto.
    --   Cambiar estos dos de sitio convertiria cada compra en una venta y
    --   viceversa, sin un solo error. Van asi y asi se quedan.
    side         ENUM('COMPRA','VENTA') NOT NULL,

    -- El identificador de Minecraft/Cobblemon: `cobblemon:poke_ball`.
    -- ⚠ Se guarda el TEXTO y no un id numerico del registro: los numeros del
    --   registro los reparten los mods al arrancar y cambian entre
    --   arranques. Es la misma leccion de «los bloques viajan como un
    --   numero» que costo 5.687 bloques de desfase.
    item_id      VARCHAR(128)    NOT NULL,

    -- ⚠ PRECIO POR UNIDAD, nunca el total. Con el total, un llenado parcial
    --   obliga a dividir -- y dividir enteros pierde restos, que en dinero
    --   es crear o destruir Plata en cada operacion (R2).
    unit_price   BIGINT          NOT NULL,

    -- ⚠ `qty_filled` y NO `qty_restante`. La cantidad original no se toca
    --   jamas: con una cantidad decreciente se pierde cuanto se pidio, y sin
    --   ese dato el historico no puede decir «se pidieron 500 y se sirvieron
    --   30» -- que es justo lo que dice si un precio era realista.
    qty_total    INT             NOT NULL,
    qty_filled   INT             NOT NULL DEFAULT 0,

    state        ENUM('ABIERTA','COMPLETA','CANCELADA','CADUCADA')
                 NOT NULL DEFAULT 'ABIERTA',

    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at   DATETIME(3)     NOT NULL,
    closed_at    DATETIME(3)     NULL,

    -- ⚠ Lo devuelto se MARCA, no se adivina. Sin esta columna, un barrido
    --   que corra dos veces devuelve dos veces -- y eso duplica objetos y
    --   dinero. Misma solucion que `delivered_at` del GTS (V006).
    refunded_at  DATETIME(3)     NULL,

    -- EL INDICE QUE DE VERDAD SE USA: «las ordenes vivas de este objeto por
    -- este lado, ordenadas por precio». Es la consulta del cruce Y la del
    -- panel, y corre en cada operacion.
    KEY ix_libro    (item_id, side, state, unit_price, order_id),
    KEY ix_mias     (player_id, state),
    KEY ix_caducar  (state, expires_at),
    KEY ix_devolver (state, refunded_at),

    CONSTRAINT fk_mo_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,

    -- Las redes de ultima hora. El codigo comprueba antes; estas aguantan si
    -- algun dia un camino nuevo se olvida.
    CONSTRAINT ck_mo_price CHECK (unit_price > 0),
    CONSTRAINT ck_mo_qty   CHECK (qty_total > 0 AND qty_filled >= 0
                                  AND qty_filled <= qty_total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- LAS OPERACIONES EJECUTADAS
--
-- Cada cruce deja una fila. Es lo que hace posible el historico, el indice
-- de precios y responder a «¿por cuanto se vendio esto la semana pasada?».
--
-- ⚠ Se guardan las DOS ordenes y los DOS jugadores. Con solo las ordenes,
--   investigar un cruce raro obliga a unir contra `market_order`, que para
--   entonces puede estar cerrada; y con solo los jugadores se pierde que
--   orden concreta se lleno.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS market_trade (
    trade_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id      VARCHAR(128)    NOT NULL,

    -- ⚠ EL PRECIO DE EJECUCION ES EL DE LA ORDEN QUE YA ESTABA EN EL LIBRO,
    --   no el del que llega. Si fuera al reves, poner una orden generosa te
    --   costaria exactamente lo que ofreciste aunque hubiera oferta barata,
    --   y entonces nadie pondria ordenes por encima del minimo -- que es
    --   justo lo que mata la liquidez. Ver mercado.md §3.3.
    unit_price   BIGINT          NOT NULL,
    qty          INT             NOT NULL,

    buyer_id     BIGINT UNSIGNED NOT NULL,
    seller_id    BIGINT UNSIGNED NOT NULL,
    buy_order    BIGINT UNSIGNED NOT NULL,
    sell_order   BIGINT UNSIGNED NOT NULL,

    -- Lo que se llevo el servidor. Se guarda para poder auditar el sumidero
    -- sin recalcular los tramos, que pueden haber cambiado desde entonces.
    tax          BIGINT          NOT NULL DEFAULT 0,

    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    KEY ix_trade_item   (item_id, created_at),
    KEY ix_trade_compra (buyer_id, created_at),
    KEY ix_trade_venta  (seller_id, created_at),

    CONSTRAINT fk_mt_buyer FOREIGN KEY (buyer_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT fk_mt_seller FOREIGN KEY (seller_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_mt_price CHECK (unit_price > 0),
    CONSTRAINT ck_mt_qty CHECK (qty > 0),
    -- ⚠⚠ NADIE SE CRUZA CONSIGO MISMO. Lo comprueba el codigo, y ademas la
    --    base: cruzarte contigo permite fijar el precio que quieras y mover
    --    el indice que va a medir la inflacion de TODO el servidor.
    CONSTRAINT ck_mt_distintos CHECK (buyer_id <> seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- LO QUE SE LE DEBE A ALGUIEN
--
-- ⚠⚠ EL DINERO NO NECESITA ESTA TABLA Y LOS OBJETOS SI, y esa asimetria es
--    la razon de que exista. Un saldo vive en la base: se abona y ya esta,
--    estes conectado o no. Un objeto tiene que entrar en un inventario, y
--    un inventario solo existe mientras su dueño esta dentro.
--
--    Sin esto, comprar mientras el vendedor esta desconectado --que es el
--    caso NORMAL en un mercado asincrono-- obligaria a elegir entre no
--    dejar comprar o perder los objetos. El GTS ya aprendio esta leccion
--    por las malas en V006: un listado que caducaba se quedaba en EXPIRED y
--    el objeto no volvia a su dueño jamas.
--
-- ⚠ `delivered_at` es lo que hace que entregar dos veces sea imposible. Se
--   marca en la MISMA transaccion que mete los objetos.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS market_claim (
    claim_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_id    BIGINT UNSIGNED NOT NULL,
    item_id      VARCHAR(128)    NOT NULL,
    qty          INT             NOT NULL,

    -- `compra` (te llego lo que pediste) . `cancelar` . `caducar`
    reason       VARCHAR(24)     NOT NULL,

    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    delivered_at DATETIME(3)     NULL,

    KEY ix_claim_pendiente (player_id, delivered_at),

    CONSTRAINT fk_mc_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_mc_qty CHECK (qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT INTO schema_version (version, description)
VALUES (15, 'mercado: libro de ordenes de compra y venta para objetos')
ON DUPLICATE KEY UPDATE version = version;
