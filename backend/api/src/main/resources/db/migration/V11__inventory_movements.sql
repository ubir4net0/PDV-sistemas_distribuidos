CREATE TABLE "${applicationSchema}".inventory_operations (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    response JSONB NOT NULL CHECK (jsonb_typeof(response) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(actor_id,request_id), UNIQUE(id,actor_id)
);
CREATE TABLE "${applicationSchema}".inventory_movements (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_balances(product_id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('ENTRY','EXIT','ADJUST')),
    quantity BIGINT NOT NULL CHECK (quantity >= 0),
    delta BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (btrim(reason) <> ''),
    physical_before BIGINT NOT NULL CHECK (physical_before >= 0),
    physical_after BIGINT NOT NULL CHECK (physical_after >= 0),
    reserved_before BIGINT NOT NULL CHECK (reserved_before >= 0 AND reserved_before <= physical_before),
    reserved_after BIGINT NOT NULL CHECK (reserved_after = reserved_before AND reserved_after <= physical_after),
    version_before BIGINT NOT NULL CHECK (version_before >= 0),
    version_after BIGINT NOT NULL CHECK (version_after = version_before + 1),
    balance_created_at TIMESTAMPTZ NOT NULL,
    balance_updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY(operation_id,actor_id) REFERENCES "${applicationSchema}".inventory_operations(id,actor_id) ON DELETE RESTRICT,
    UNIQUE(operation_id,product_id), UNIQUE(product_id,version_after),
    CHECK (delta = physical_after - physical_before),
    CHECK ((type='ENTRY' AND quantity>0 AND physical_after=physical_before+quantity)
        OR (type='EXIT' AND quantity>0 AND physical_after=physical_before-quantity)
        OR (type='ADJUST' AND physical_after=quantity))
);
CREATE TRIGGER inventory_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_operations
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
CREATE TRIGGER inventory_movements_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_movements
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
