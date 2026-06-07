-- ================================================================
-- V3 — Investimentos multi-banco
-- Sprint 5 · F4
-- ================================================================

CREATE TABLE investment_products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_id         UUID           REFERENCES documents(id) ON DELETE SET NULL,
    bank                VARCHAR(100)   NOT NULL,   -- "Itaú", "Nubank", "XP", etc.
    product_name        VARCHAR(255)   NOT NULL,
    product_type        VARCHAR(50)    NOT NULL
        CHECK (product_type IN ('CDB','LCI','LCA','PGBL','VGBL','FI','STOCKS','ETF','TREASURY','OTHER')),
    current_balance     NUMERIC(15, 2) NOT NULL,
    invested_amount     NUMERIC(15, 2),
    profitability_pct   NUMERIC(8, 4),             -- % de rentabilidade
    reference_date      DATE           NOT NULL,   -- data de referência do extrato
    maturity_date       DATE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investments_user_id ON investment_products(user_id);
CREATE INDEX idx_investments_bank    ON investment_products(bank);
CREATE INDEX idx_investments_type    ON investment_products(product_type);
