CREATE TABLE "${applicationSchema}".suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj VARCHAR(14) NOT NULL UNIQUE CHECK (cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    legal_name VARCHAR(200) NOT NULL CHECK (btrim(legal_name) <> ''),
    trade_name VARCHAR(200),
    contact VARCHAR(200),
    phone VARCHAR(32),
    email VARCHAR(320),
    address VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE "${applicationSchema}".products ADD COLUMN supplier_id UUID
    REFERENCES "${applicationSchema}".suppliers(id) ON DELETE RESTRICT;
CREATE INDEX products_supplier_idx ON "${applicationSchema}".products(supplier_id,name,id);
CREATE INDEX suppliers_active_page_idx ON "${applicationSchema}".suppliers(active,legal_name,id);
CREATE INDEX suppliers_name_page_idx ON "${applicationSchema}".suppliers(legal_name,id);
CREATE TABLE "${applicationSchema}".supplier_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES "${applicationSchema}".suppliers(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    action VARCHAR(64) NOT NULL CHECK (action ~ '^[A-Z_]+$'),
    before_data JSONB NOT NULL CHECK (jsonb_typeof(before_data) = 'object'),
    after_data JSONB NOT NULL CHECK (jsonb_typeof(after_data) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX supplier_changes_supplier_idx ON "${applicationSchema}".supplier_changes(supplier_id,created_at,id);
