CREATE TABLE "${applicationSchema}".customer_changes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES "${applicationSchema}".customers(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    action VARCHAR(64) NOT NULL CHECK (action ~ '^[A-Z_]+$'),
    before_data JSONB NOT NULL CHECK (jsonb_typeof(before_data) = 'object'),
    after_data JSONB NOT NULL CHECK (jsonb_typeof(after_data) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX customer_changes_customer_time_idx ON "${applicationSchema}".customer_changes(customer_id, created_at, id);
CREATE INDEX customer_changes_actor_idx ON "${applicationSchema}".customer_changes(actor_id);
