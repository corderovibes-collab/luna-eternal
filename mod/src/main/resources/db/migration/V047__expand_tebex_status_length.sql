-- V047: Ampliar longitud de columnas status en tablas Tebex para soportar PENDING_PLAYER_RESOLUTION (25 caracteres)

ALTER TABLE tebex_fulfillment 
    MODIFY COLUMN status VARCHAR(32) NOT NULL;

ALTER TABLE tebex_payment 
    MODIFY COLUMN status VARCHAR(32) NOT NULL;
