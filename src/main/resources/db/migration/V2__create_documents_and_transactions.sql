-- ================================================================
-- V2 — Documentos, transações e categorias
-- Sprint 2–3 · F1 + F2
-- ================================================================

-- Documentos enviados pelo usuário (PDFs, XLSX, imagens)
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name       VARCHAR(255)  NOT NULL,
    file_type       VARCHAR(50)   NOT NULL,  -- "PDF" | "XLSX" | "CSV" | "IMAGE"
    s3_key          VARCHAR(500)  NOT NULL,  -- caminho no S3/MinIO
    status          VARCHAR(50)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PROCESSING','DONE','ERROR')),
    error_message   TEXT,
    parsed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Categorias (padrão do sistema + personalizadas pelo usuário)
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         REFERENCES users(id) ON DELETE CASCADE,  -- NULL = categoria global
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    icon        VARCHAR(50),   -- nome do ícone no frontend
    color       VARCHAR(7),    -- hex, ex: "#FF5733"
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Transações extraídas dos documentos
CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_id     UUID           REFERENCES documents(id) ON DELETE SET NULL,
    transaction_date DATE          NOT NULL,
    description     VARCHAR(500)   NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    type            VARCHAR(20)    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    category_id     UUID           REFERENCES categories(id) ON DELETE SET NULL,
    -- NULL = categorizado por IA | FALSE = usuário discordou | TRUE = usuário confirmou
    category_confirmed  BOOLEAN,
    notes           TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Regras de categorização por palavra-chave (aprendizado do usuário)
CREATE TABLE keyword_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword     VARCHAR(255) NOT NULL,
    category_id UUID         NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, keyword)
);

-- Índices
CREATE INDEX idx_documents_user_id      ON documents(user_id);
CREATE INDEX idx_documents_status       ON documents(status);
CREATE INDEX idx_transactions_user_id   ON transactions(user_id);
CREATE INDEX idx_transactions_date      ON transactions(transaction_date);
CREATE INDEX idx_transactions_category  ON transactions(category_id);
CREATE INDEX idx_keyword_rules_user_id  ON keyword_rules(user_id);

-- Categorias padrão do sistema (globais, user_id = NULL)
INSERT INTO categories (id, name, type, icon, color, is_system) VALUES
    (gen_random_uuid(), 'Salário',          'INCOME',  'wallet',      '#22C55E', TRUE),
    (gen_random_uuid(), '13º Salário',      'INCOME',  'gift',        '#16A34A', TRUE),
    (gen_random_uuid(), 'Férias',           'INCOME',  'beach',       '#4ADE80', TRUE),
    (gen_random_uuid(), 'PLR / Bônus',      'INCOME',  'trophy',      '#86EFAC', TRUE),
    (gen_random_uuid(), 'Restituição IR',   'INCOME',  'receipt',     '#BBF7D0', TRUE),
    (gen_random_uuid(), 'Vale Refeição',    'INCOME',  'utensils',    '#D1FAE5', TRUE),
    (gen_random_uuid(), 'Vale Alimentação', 'INCOME',  'shopping-bag','#ECFDF5', TRUE),
    (gen_random_uuid(), 'Moradia',          'EXPENSE', 'home',        '#3B82F6', TRUE),
    (gen_random_uuid(), 'Alimentação',      'EXPENSE', 'utensils',    '#EF4444', TRUE),
    (gen_random_uuid(), 'Transporte',       'EXPENSE', 'car',         '#F97316', TRUE),
    (gen_random_uuid(), 'Saúde',            'EXPENSE', 'heart',       '#EC4899', TRUE),
    (gen_random_uuid(), 'Lazer',            'EXPENSE', 'smile',       '#A855F7', TRUE),
    (gen_random_uuid(), 'Educação',         'EXPENSE', 'book',        '#6366F1', TRUE),
    (gen_random_uuid(), 'Outros',           'EXPENSE', 'more-horizontal', '#6B7280', TRUE);
