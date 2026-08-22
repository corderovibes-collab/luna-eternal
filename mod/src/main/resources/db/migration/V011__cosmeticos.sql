-- Cosmeticos que posee cada jugador.
--
-- D-039: los cosmeticos NO se consiguen jugando. Solo comprandolos con
-- LunaCoins o en eventos que organicemos. Eso convierte esta tabla en la UNICA
-- fuente de verdad: si no hay via de mundo, no hay nada que reconciliar con el
-- inventario ni con el mundo, y el cliente jamas concede nada.
--
-- ⚠ NO hay tabla de catalogo, Y ES DELIBERADO.
--
--   Que cosmeticos EXISTEN y cuanto valen se declara en el codigo del servidor
--   (`Catalogo.java`), no en la base. Motivo: el catalogo es contenido, no
--   estado. Meterlo en la base obligaria a una migracion --o a un panel-- por
--   cada cosmetico nuevo, y a mantener sincronizados unos identificadores que
--   ademas tienen que existir como aspectos de Cobblemon para poder dibujarse.
--   Lo que SI es estado, y por eso vive aqui, es quien tiene que.

CREATE TABLE IF NOT EXISTS player_cosmetics (
    player_id      BIGINT UNSIGNED NOT NULL,

    -- El identificador del catalogo, p.ej. `charizard_knight`. VARCHAR y no un
    -- entero: un id legible sobrevive a que el catalogo se reordene, y en un
    -- volcado de la base se entiende sin cruzar con otra tabla.
    cosmetic_id    VARCHAR(64)     NOT NULL,

    -- Como llego. Se guarda porque un reembolso, una auditoria o una queja
    -- ("yo no compre esto") necesitan distinguir una compra de un regalo de
    -- evento, y despues del hecho ya no hay forma de saberlo.
    origen         ENUM('compra', 'evento', 'regalo') NOT NULL DEFAULT 'compra',

    -- Lo que se pago, en LunaCoins. 0 para los de evento. Sirve para el mismo
    -- caso de arriba: sin esto, un reembolso tendria que fiarse del precio
    -- ACTUAL del catalogo, que puede haber cambiado desde la compra.
    precio_pagado  INT             NOT NULL DEFAULT 0,

    obtenido_en    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ⚠ LA CLAVE PRIMARIA ES (jugador, cosmetico), Y ESO ES LO QUE IMPIDE
    --   COMPRARLO DOS VECES. No es una optimizacion de indice: es la unica
    --   defensa que sigue en pie si la comprobacion previa del servicio corre
    --   dos veces a la vez -- dos clics rapidos, o dos peticiones en vuelo.
    --   La segunda inserccion choca contra la clave y la transaccion se
    --   deshace, en vez de cobrar dos veces por lo mismo.
    PRIMARY KEY (player_id, cosmetic_id),

    CONSTRAINT fk_cosmetics_player
        FOREIGN KEY (player_id) REFERENCES players (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Lo que lleva PUESTO cada jugador, una fila por categoria.
--
-- Aparte de `player_cosmetics` porque son dos preguntas distintas: "que tienes"
-- se consulta al abrir la tienda, y "que llevas" lo necesita cualquiera que te
-- vea. Y equipar no puede ser una columna de la otra tabla: obligaria a apagar
-- la fila anterior y encender la nueva en dos pasos, con un instante en el que
-- llevas dos mascotas o ninguna.
CREATE TABLE IF NOT EXISTS player_cosmetic_equipped (
    player_id   BIGINT UNSIGNED NOT NULL,
    categoria   VARCHAR(32)     NOT NULL,
    cosmetic_id VARCHAR(64)     NOT NULL,

    -- Una sola pieza por categoria: la clave lo garantiza sin que el codigo
    -- tenga que acordarse.
    PRIMARY KEY (player_id, categoria),

    CONSTRAINT fk_equipped_player
        FOREIGN KEY (player_id) REFERENCES players (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
