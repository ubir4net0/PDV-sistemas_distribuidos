CREATE TABLE "${applicationSchema}".inventory_balances (
    product_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".products(id) ON DELETE RESTRICT,
    physical BIGINT NOT NULL DEFAULT 0 CHECK (physical >= 0),
    reserved BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= physical),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE "${applicationSchema}".inventory_openings (
    product_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".inventory_balances(product_id) ON DELETE RESTRICT,
    source VARCHAR(32) NOT NULL DEFAULT 'ZERO_INITIALIZATION' CHECK (source = 'ZERO_INITIALIZATION'),
    physical BIGINT NOT NULL DEFAULT 0 CHECK (physical = 0),
    reserved BIGINT NOT NULL DEFAULT 0 CHECK (reserved = 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version = 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO "${applicationSchema}".inventory_balances(product_id) SELECT id FROM "${applicationSchema}".products;
INSERT INTO "${applicationSchema}".inventory_openings(product_id) SELECT product_id FROM "${applicationSchema}".inventory_balances;
CREATE FUNCTION "${applicationSchema}".initialize_inventory() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO "${applicationSchema}".inventory_balances(product_id) VALUES (NEW.id);
    INSERT INTO "${applicationSchema}".inventory_openings(product_id) VALUES (NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER products_initialize_inventory AFTER INSERT ON "${applicationSchema}".products
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".initialize_inventory();
CREATE FUNCTION "${applicationSchema}".reject_inventory_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Inventory history is immutable' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER inventory_openings_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_openings
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
