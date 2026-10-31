CREATE TABLE "${applicationSchema}".categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE "${applicationSchema}".products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES "${applicationSchema}".categories(id) ON DELETE RESTRICT,
    sku VARCHAR(64) NOT NULL UNIQUE CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description VARCHAR(2000),
    sale_price NUMERIC(19,2) NOT NULL CHECK (sale_price >= 0),
    cost NUMERIC(19,2) NOT NULL CHECK (cost >= 0),
    unit VARCHAR(32) NOT NULL CHECK (btrim(unit) <> ''),
    minimum_stock BIGINT NOT NULL CHECK (minimum_stock >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'CADASTRADO' CHECK (status IN ('CADASTRADO','EM_REVISAO','ATIVO','INATIVO')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX products_category_idx ON "${applicationSchema}".products(category_id);
CREATE TABLE "${applicationSchema}".catalog_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES "${applicationSchema}".categories(id) ON DELETE RESTRICT,
    product_id UUID REFERENCES "${applicationSchema}".products(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    action VARCHAR(64) NOT NULL CHECK (action ~ '^[A-Z_]+$'),
    before_data JSONB NOT NULL CHECK (jsonb_typeof(before_data) = 'object'),
    after_data JSONB NOT NULL CHECK (jsonb_typeof(after_data) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((category_id IS NULL) <> (product_id IS NULL))
);
CREATE INDEX catalog_changes_category_idx ON "${applicationSchema}".catalog_changes(category_id,created_at,id);
CREATE INDEX catalog_changes_product_idx ON "${applicationSchema}".catalog_changes(product_id,created_at,id);
