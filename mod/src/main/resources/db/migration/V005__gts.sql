-- =====================================================================
-- V005 — GTS: mercado entre jugadores (TRD-001)
--
-- LA REGLA QUE JUSTIFICA ESTA TABLA:
-- mientras un objeto está listado NO puede estar en poder del vendedor.
-- Si siguiera en su inventario podría operar con él a la vez que se vende,
-- y ese es el vector de duplicación número uno de todos los GTS mal hechos.
-- Por eso el payload vive AQUÍ: el mercado tiene la custodia.
--
-- Las columnas desnormalizadas (especie, nivel, IVs…) son deliberadas:
-- filtrar dentro de un blob serializado es inviable, y el brief pide filtrar
-- por 9 atributos. El blob sigue siendo la verdad; las columnas son el índice.
-- Ver docs/technical/data-model.md §3.
-- =====================================================================

CREATE TABLE IF NOT EXISTS gts_listing (
    listing_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seller_id     BIGINT UNSIGNED NOT NULL,
    state         ENUM('ACTIVE','RESERVED','SOLD','CANCELLED','EXPIRED')
                  NOT NULL DEFAULT 'ACTIVE',

    -- Qué se vende. 'ITEM' hoy; 'POKEMON' cuando Cobblemon esté instalado.
    kind          ENUM('ITEM','POKEMON') NOT NULL DEFAULT 'ITEM',
    payload       LONGBLOB        NOT NULL,
    payload_hash  CHAR(64)        NOT NULL,

    price         BIGINT          NOT NULL,
    currency      ENUM('POKEDOLLAR') NOT NULL DEFAULT 'POKEDOLLAR',

    -- Columnas de búsqueda. Se rellenan al publicar.
    display_name  VARCHAR(128)    NOT NULL,
    item_id       VARCHAR(128)    NULL,
    quantity      INT             NOT NULL DEFAULT 1,
    species       VARCHAR(64)     NULL,
    level         INT             NULL,
    is_shiny      TINYINT(1)      NOT NULL DEFAULT 0,
    iv_total      INT             NULL,

    listed_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at    DATETIME(3)     NOT NULL,
    buyer_id      BIGINT UNSIGNED NULL,
    sold_at       DATETIME(3)     NULL,

    KEY ix_gts_browse  (state, listed_at),
    KEY ix_gts_price   (state, price),
    KEY ix_gts_seller  (seller_id, state),
    KEY ix_gts_species (state, species),
    KEY ix_gts_expiry  (state, expires_at),

    CONSTRAINT fk_gts_seller FOREIGN KEY (seller_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT fk_gts_buyer FOREIGN KEY (buyer_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_gts_price CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (5, 'GTS: listados con custodia')
ON DUPLICATE KEY UPDATE version = version;
