-- =====================================================================
-- V003 — moneda premium ReportCoin
--
-- Decisión del usuario (2026-08-11): modelo F2P con paquetes de pago, con
-- una moneda premium separada al estilo de los Diosescoins.
--
-- Es el diseño CORRECTO precisamente porque va aparte: no se vende la moneda
-- del juego, se vende un token que nunca toca el mercado. Ver ECO-001 §2.
--
-- REPORTCOIN no es transferible entre jugadores. Si lo fuera, se revendería
-- por PokéDólares y existiría de facto la conversión que el diseño prohíbe.
-- =====================================================================

ALTER TABLE player_economy
    MODIFY COLUMN currency ENUM('POKEDOLLAR','MARK','REPORTCOIN') NOT NULL;

ALTER TABLE ledger_entry
    MODIFY COLUMN currency ENUM('POKEDOLLAR','MARK','REPORTCOIN') NOT NULL;

INSERT INTO schema_version (version, description)
VALUES (3, 'moneda premium REPORTCOIN')
ON DUPLICATE KEY UPDATE version = version;
