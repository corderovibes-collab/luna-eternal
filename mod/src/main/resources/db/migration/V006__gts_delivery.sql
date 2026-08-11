-- =====================================================================
-- V006 — Entrega diferida del GTS
--
-- EL AGUJERO QUE CIERRA:
-- el dinero vive en la base y los objetos en el inventario, asi que no hay
-- transaccion atomica entre ambos. Si el servidor cae entre el COMMIT de la
-- compra y la entrega del objeto, el comprador ha pagado y no tiene nada, y
-- el payload se queda en una fila marcada SOLD sin que nadie lo reclame.
--
-- Con delivered_at, la entrega deja de ser un efecto secundario y pasa a ser
-- un estado consultable: lo pendiente se entrega al conectar. Cubre tres
-- casos, no solo el de la caida:
--   · comprado y no entregado          -> al comprador
--   · cancelado y no devuelto          -> al vendedor
--   · caducado y no devuelto           -> al vendedor
--
-- El caso de caducidad NO existia antes: un listado que vencia se quedaba en
-- EXPIRED y el objeto no volvia a su dueno jamas.
-- =====================================================================

ALTER TABLE gts_listing
    ADD COLUMN delivered_at DATETIME(3) NULL AFTER sold_at;

-- Lo ya vendido antes de esta migracion se da por entregado: se entrego en el
-- momento, cuando la entrega era sincrona.
UPDATE gts_listing SET delivered_at = sold_at WHERE state = 'SOLD';

-- Indice para la consulta de reclamaciones al conectar. Va por las dos vias
-- porque un jugador puede tener pendientes como comprador y como vendedor.
CREATE INDEX ix_gts_claim_buyer  ON gts_listing (buyer_id,  state, delivered_at);
CREATE INDEX ix_gts_claim_seller ON gts_listing (seller_id, state, delivered_at);

INSERT INTO schema_version (version, description)
VALUES (6, 'entrega diferida del GTS: nada se pierde si el servidor cae')
ON DUPLICATE KEY UPDATE version = version;
