-- =====================================================================
-- Luna Eternal · Migración V044 — Reversión segura y proveniencia de efectos Tebex
-- =====================================================================

-- 1. Ampliación de columnas en tebex_fulfillment para ciclo de vida de reversión y eventos financieros
ALTER TABLE tebex_fulfillment
    ADD COLUMN reversal_status VARCHAR(24) NOT NULL DEFAULT 'NONE',
    ADD COLUMN reversal_reason VARCHAR(255) NULL,
    ADD COLUMN reversal_at DATETIME(3) NULL,
    ADD COLUMN financial_event VARCHAR(24) NOT NULL DEFAULT 'INITIAL';

CREATE INDEX ix_tebex_ful_rev_status ON tebex_fulfillment (reversal_status);
CREATE INDEX ix_tebex_ful_fin_event ON tebex_fulfillment (financial_event);

-- 2. Journal auditable de efectos granulares por entrega
CREATE TABLE IF NOT EXISTS tebex_fulfillment_effect (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fulfillment_id          BIGINT UNSIGNED NOT NULL,
    effect_type             VARCHAR(32)     NOT NULL, -- 'RANK_CHANGE', 'SUIT_GRANT', 'COIN_CREDIT'
    resource_id             VARCHAR(64)     NOT NULL, -- e.g. 'rank', 'REPORTCOIN', suit id
    before_value            VARCHAR(64)     NULL,
    after_value             VARCHAR(64)     NULL,
    created_by_fulfillment  BOOLEAN         NOT NULL DEFAULT TRUE,
    reversed                BOOLEAN         NOT NULL DEFAULT FALSE,
    reversed_at             DATETIME(3)     NULL,
    KEY ix_tebex_eff_ful (fulfillment_id),
    CONSTRAINT fk_tebex_eff_ful FOREIGN KEY (fulfillment_id)
        REFERENCES tebex_fulfillment (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO schema_version (version, description)
VALUES (44, 'tebex reversal lifecycle y proveniencia de efectos')
ON DUPLICATE KEY UPDATE version = version;
