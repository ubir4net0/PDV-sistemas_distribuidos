CREATE TABLE "${applicationSchema}".operation_telemetry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service VARCHAR(8) NOT NULL CHECK(service IN ('api','worker')),
    event VARCHAR(32) NOT NULL CHECK(event IN ('api_checkout','api_checkout_replay','business_publish','business_consume')),
    trace_id CHAR(32) NOT NULL CHECK(trace_id ~ '^[a-f0-9]{32}$' AND trace_id<>'00000000000000000000000000000000'),
    span_id CHAR(16) NOT NULL CHECK(span_id ~ '^[a-f0-9]{16}$' AND span_id<>'0000000000000000'),
    parent_span_id CHAR(16) CHECK(parent_span_id ~ '^[a-f0-9]{16}$' AND parent_span_id<>'0000000000000000'),
    outcome VARCHAR(16) NOT NULL CHECK(outcome IN ('OK','REPLAY','UNCONFIRMED','REJECTED')),
    duration_ms BIGINT NOT NULL CHECK(duration_ms>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX operation_telemetry_time_idx ON "${applicationSchema}".operation_telemetry(created_at DESC);
CREATE INDEX operation_telemetry_trace_idx ON "${applicationSchema}".operation_telemetry(trace_id,created_at DESC);
CREATE TRIGGER operation_telemetry_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".operation_telemetry FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE FUNCTION "${applicationSchema}".record_operation_telemetry(s TEXT,e TEXT,t TEXT,sp TEXT,p TEXT,o TEXT,ms BIGINT) RETURNS JSONB LANGUAGE sql AS $$
    INSERT INTO "${applicationSchema}".operation_telemetry(service,event,trace_id,span_id,parent_span_id,outcome,duration_ms)
    VALUES(s,e,t,sp,p,o,ms) RETURNING jsonb_build_object('id',id,'service',service,'event',event,'traceId',trace_id,'spanId',span_id,'parentSpanId',parent_span_id,'outcome',outcome,'durationMs',duration_ms,'createdAt',created_at);
$$;
