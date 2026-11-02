CREATE TABLE "${applicationSchema}".outbox_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL CHECK(event_type IN ('pdv.payment.requested.v1','pdv.payment.result.v1')),
    payment_id UUID NOT NULL REFERENCES "${applicationSchema}".payments(id) ON DELETE RESTRICT,
    payload JSONB NOT NULL CHECK(jsonb_typeof(payload)='object' AND payload ?& ARRAY['type','eventId','paymentId','outcome']),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK(payload->>'type'=event_type AND payload->>'eventId'=id::text AND payload->>'paymentId'=payment_id::text)
);
CREATE TABLE "${applicationSchema}".outbox_deliveries (
    event_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".outbox_events(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_error VARCHAR(32) CHECK(last_error IN ('UNCONFIRMED','BROKER_UNAVAILABLE')),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX outbox_deliveries_due_idx ON "${applicationSchema}".outbox_deliveries(next_attempt_at,event_id) WHERE status='PENDING';
CREATE TABLE "${applicationSchema}".inbox_messages (
    consumer VARCHAR(64) NOT NULL CHECK(consumer IN ('payment-simulator-v1','sale-coordinator-v1')),
    event_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES "${applicationSchema}".payments(id) ON DELETE RESTRICT,
    fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    disposition VARCHAR(32) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(consumer,event_id)
);
CREATE TABLE "${applicationSchema}".simulation_decisions (
    payment_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".payments(id) ON DELETE RESTRICT,
    outcome VARCHAR(32) NOT NULL CHECK(outcome IN ('APROVADO','RECUSADO','INDETERMINADO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER outbox_events_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".outbox_events FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER inbox_messages_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inbox_messages FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER simulation_decisions_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".simulation_decisions FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE FUNCTION "${applicationSchema}".initialize_outbox_delivery() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO "${applicationSchema}".outbox_deliveries(event_id) VALUES(NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER outbox_events_delivery AFTER INSERT ON "${applicationSchema}".outbox_events FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".initialize_outbox_delivery();
CREATE FUNCTION "${applicationSchema}".enqueue_business_event(identity_key TEXT, kind TEXT, operation UUID, outcome TEXT) RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE event UUID:=gen_random_uuid(); stored "${applicationSchema}".outbox_events%ROWTYPE;
BEGIN
    INSERT INTO "${applicationSchema}".outbox_events(id,event_key,event_type,payment_id,payload)
    VALUES(event,identity_key,kind,operation,jsonb_build_object('type',kind,'eventId',event,'paymentId',operation,'outcome',outcome))
    ON CONFLICT(event_key) DO NOTHING;
    SELECT * INTO stored FROM "${applicationSchema}".outbox_events WHERE event_key=identity_key;
    IF stored.event_type IS DISTINCT FROM kind OR stored.payment_id IS DISTINCT FROM operation OR (stored.payload->>'outcome') IS DISTINCT FROM outcome
    THEN RAISE EXCEPTION 'Outbox identity conflict' USING ERRCODE='23514'; END IF;
    RETURN stored.id;
END;
$$;
CREATE FUNCTION "${applicationSchema}".enqueue_payment_request() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM "${applicationSchema}".enqueue_business_event('payment-request:'||NEW.payment_id,'pdv.payment.requested.v1',NEW.payment_id,NULL);
    RETURN NEW;
END;
$$;
CREATE TRIGGER payment_intents_outbox AFTER INSERT ON "${applicationSchema}".payment_intents FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".enqueue_payment_request();
SELECT "${applicationSchema}".enqueue_business_event('payment-request:'||payment_id,'pdv.payment.requested.v1',payment_id,NULL)
    FROM "${applicationSchema}".payment_intents ORDER BY created_at,payment_id;

-- Request inbox transaction owns start + immutable decision + response intent. No broker call here.
CREATE FUNCTION "${applicationSchema}".prepare_business_payment(operation UUID) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE state TEXT; expected TEXT; stored TEXT; known TEXT;
BEGIN
    state:="${applicationSchema}".start_sale_payment(operation);
    IF state='BUSY' THEN RETURN 'BUSY'; END IF;
    IF state IN ('NOT_FOUND','RESERVATION_UNAVAILABLE') THEN RETURN 'IGNORED'; END IF;
    SELECT CASE scenario WHEN 'TIMEOUT' THEN 'INDETERMINADO' ELSE scenario END INTO expected FROM "${applicationSchema}".payments WHERE id=operation;
    SELECT outcome INTO known FROM "${applicationSchema}".payment_results WHERE payment_id=operation;
    IF FOUND AND known<>expected THEN RAISE EXCEPTION 'Simulation identity conflict' USING ERRCODE='23514'; END IF;
    INSERT INTO "${applicationSchema}".simulation_decisions(payment_id,outcome) VALUES(operation,expected) ON CONFLICT(payment_id) DO NOTHING;
    SELECT outcome INTO stored FROM "${applicationSchema}".simulation_decisions WHERE payment_id=operation;
    IF stored IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Simulation identity conflict' USING ERRCODE='23514'; END IF;
    PERFORM "${applicationSchema}".enqueue_business_event('payment-result:'||operation,'pdv.payment.result.v1',operation,stored);
    RETURN stored;
END;
$$;
-- Stable coordinator adapter; stock/receipt and reconciliation evolve in their own migrations.
CREATE FUNCTION "${applicationSchema}".apply_business_payment_result(operation UUID, outcome TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM "${applicationSchema}".simulation_decisions WHERE payment_id=operation AND simulation_decisions.outcome=apply_business_payment_result.outcome)
    THEN RAISE EXCEPTION 'Missing simulation proof' USING ERRCODE='23514'; END IF;
    RETURN "${applicationSchema}".record_simulated_payment(operation,outcome);
END;
$$;
CREATE FUNCTION "${applicationSchema}".retry_outbox_event(event UUID) RETURNS boolean LANGUAGE plpgsql AS $$
BEGIN
    UPDATE "${applicationSchema}".outbox_deliveries SET status='PENDING',attempts=0,next_attempt_at=clock_timestamp(),last_error=NULL,version=version+1,updated_at=clock_timestamp()
        WHERE event_id=event AND status='DEAD' AND version<9223372036854775807;
    RETURN FOUND;
END;
$$;
