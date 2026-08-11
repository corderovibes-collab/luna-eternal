-- =====================================================================
-- V008 — Kits
--
-- El cooldown vive AQUI, no en memoria ni en el cliente (P6). Un cooldown en
-- memoria se reinicia al reiniciar el servidor, y ese es el exploit mas
-- barato que existe: reclamar, esperar un reinicio, reclamar otra vez.
--
-- Se guarda tambien cuantas veces se ha reclamado. No es estadistica: es lo
-- que permite auditar cuanto valor ha entrado por esta via, que segun
-- ECO-001 §3 NUNCA debe ser la fuente principal.
-- =====================================================================

CREATE TABLE IF NOT EXISTS kit_claim (
    player_id     BIGINT UNSIGNED NOT NULL,
    kit_id        VARCHAR(48)     NOT NULL,
    last_claimed  DATETIME(3)     NOT NULL,
    times_claimed INT             NOT NULL DEFAULT 1,
    PRIMARY KEY (player_id, kit_id),
    KEY ix_kit_time (kit_id, last_claimed),
    CONSTRAINT fk_kit_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (8, 'kits: reclamaciones y cooldown persistidos')
ON DUPLICATE KEY UPDATE version = version;
