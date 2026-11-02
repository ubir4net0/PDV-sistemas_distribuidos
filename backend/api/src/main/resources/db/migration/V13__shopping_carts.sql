CREATE TABLE "${applicationSchema}".carts (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES "${applicationSchema}".customers(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id,actor_id)
);
CREATE INDEX carts_owner_idx ON "${applicationSchema}".carts(actor_id,created_at,id);
CREATE TABLE "${applicationSchema}".cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES "${applicationSchema}".carts(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".products(id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    UNIQUE (cart_id,product_id)
);
CREATE TABLE "${applicationSchema}".cart_operations (
    actor_id UUID NOT NULL,
    request_id UUID NOT NULL,
    cart_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    response JSONB NOT NULL CHECK (jsonb_typeof(response)='object'),
    PRIMARY KEY (actor_id,request_id),
    FOREIGN KEY (cart_id,actor_id) REFERENCES "${applicationSchema}".carts(id,actor_id) ON DELETE RESTRICT
);
CREATE TABLE "${applicationSchema}".cart_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('CREATED','SET_ITEM','REMOVE_ITEM')),
    before_data JSONB NOT NULL CHECK (jsonb_typeof(before_data)='object'),
    after_data JSONB NOT NULL CHECK (jsonb_typeof(after_data)='object'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (cart_id,actor_id) REFERENCES "${applicationSchema}".carts(id,actor_id) ON DELETE RESTRICT,
    UNIQUE (cart_id,version)
);
CREATE FUNCTION "${applicationSchema}".reject_cart_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Cart history is immutable' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER cart_changes_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".cart_changes
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER cart_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".cart_operations
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
