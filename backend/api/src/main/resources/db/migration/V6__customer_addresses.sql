CREATE TABLE "${applicationSchema}".customer_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES "${applicationSchema}".customers(id) ON DELETE RESTRICT,
    address_type VARCHAR(40) NOT NULL CHECK (btrim(address_type) <> ''),
    street VARCHAR(200) NOT NULL CHECK (btrim(street) <> ''),
    house_number VARCHAR(40) NOT NULL CHECK (btrim(house_number) <> ''),
    complement VARCHAR(160),
    neighborhood VARCHAR(120) NOT NULL CHECK (btrim(neighborhood) <> ''),
    city VARCHAR(120) NOT NULL CHECK (btrim(city) <> ''),
    state VARCHAR(2) NOT NULL CHECK (state IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB',
        'PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')),
    postal_code VARCHAR(8) NOT NULL CHECK (postal_code ~ '^[0-9]{8}$'),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX customer_addresses_customer_idx ON "${applicationSchema}".customer_addresses(customer_id);
CREATE UNIQUE INDEX customer_addresses_one_primary_idx ON "${applicationSchema}".customer_addresses(customer_id) WHERE is_primary;
CREATE INDEX customer_addresses_region_idx ON "${applicationSchema}".customer_addresses(state,city) WHERE is_primary;
