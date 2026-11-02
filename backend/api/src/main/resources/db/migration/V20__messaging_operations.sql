CREATE TABLE "${applicationSchema}".messaging_queue_samples (
    namespace VARCHAR(128) NOT NULL CHECK(namespace ~ '^pdv\.(business|test|smoke)\.[a-zA-Z0-9.-]{1,100}$'),
    kind VARCHAR(8) NOT NULL CHECK(kind IN ('REQUESTS','RESULTS','DEAD')),
    available BOOLEAN NOT NULL,
    ready BIGINT CHECK(ready>=0),
    consumers INTEGER CHECK(consumers>=0),
    observed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(namespace,kind),
    CHECK((available AND ready IS NOT NULL AND consumers IS NOT NULL) OR (NOT available AND ready IS NULL AND consumers IS NULL))
);
CREATE TABLE "${applicationSchema}".messaging_retry_operations (
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES "${applicationSchema}".outbox_events(id) ON DELETE RESTRICT,
    fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    reason VARCHAR(500) NOT NULL CHECK(length(btrim(reason))>0),
    response JSONB NOT NULL CHECK(jsonb_typeof(response)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(actor_id,request_id)
);
CREATE INDEX messaging_retry_event_idx ON "${applicationSchema}".messaging_retry_operations(event_id,created_at);
CREATE TRIGGER messaging_retry_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".messaging_retry_operations FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
