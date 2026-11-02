ALTER TABLE "${applicationSchema}".payments ADD CONSTRAINT payments_sale_id_id_key UNIQUE(sale_id,id);
CREATE TABLE "${applicationSchema}".sale_sagas (
    sale_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".sales(id) ON DELETE RESTRICT,
    payment_id UUID NOT NULL UNIQUE,
    phase VARCHAR(32) NOT NULL CHECK(phase IN ('CRIADO','EM_PROCESSAMENTO','APROVADO','RECUSADO','INDETERMINADO')),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY(sale_id,payment_id) REFERENCES "${applicationSchema}".payments(sale_id,id) ON DELETE RESTRICT
);
INSERT INTO "${applicationSchema}".sale_sagas(sale_id,payment_id,phase) SELECT sale_id,id,status FROM "${applicationSchema}".payments;
CREATE TABLE "${applicationSchema}".payment_results (
    payment_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".payments(id) ON DELETE RESTRICT,
    outcome VARCHAR(32) NOT NULL CHECK(outcome IN ('APROVADO','RECUSADO','INDETERMINADO')),
    response JSONB NOT NULL CHECK(jsonb_typeof(response)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER payment_results_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".payment_results FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
ALTER TABLE "${applicationSchema}".sale_events DROP CONSTRAINT sale_events_kind_check;
ALTER TABLE "${applicationSchema}".sale_events ADD CONSTRAINT sale_events_kind_check CHECK(kind IN ('CHECKOUT','PAYMENT_STARTED','APROVADO','RECUSADO','INDETERMINADO'));
CREATE FUNCTION "${applicationSchema}".initialize_sale_saga() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO "${applicationSchema}".sale_sagas(sale_id,payment_id,phase) VALUES(NEW.sale_id,NEW.id,NEW.status);
    RETURN NEW;
END;
$$;
CREATE TRIGGER payments_initialize_saga AFTER INSERT ON "${applicationSchema}".payments FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".initialize_sale_saga();

-- Confirmation is modeled here; its stock/receipt effects remain PDV-039. No public transition endpoint.
CREATE FUNCTION "${applicationSchema}".sale_transition_allowed(previous TEXT, destination TEXT, payment TEXT, reservation TEXT) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT COALESCE(CASE
        WHEN previous='AGUARDANDO_PAGAMENTO' AND destination='AGUARDANDO_PAGAMENTO' THEN payment IN ('EM_PROCESSAMENTO','INDETERMINADO') AND reservation='ACTIVE'
        WHEN previous IN ('AGUARDANDO_PAGAMENTO','PAGAMENTO_APROVADO') AND destination='PAGAMENTO_APROVADO' THEN payment='APROVADO' AND reservation='ACTIVE'
        WHEN previous IN ('AGUARDANDO_PAGAMENTO','PAGAMENTO_RECUSADO') AND destination='PAGAMENTO_RECUSADO' THEN payment='RECUSADO' AND reservation='ACTIVE'
        WHEN previous IN ('PAGAMENTO_APROVADO','CONCLUIDA') AND destination='CONCLUIDA' THEN payment='APROVADO' AND reservation='CONFIRMED'
        ELSE FALSE END,FALSE);
$$;
CREATE FUNCTION "${applicationSchema}".guard_sale_state() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment TEXT; reservation TEXT;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Sale deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT p.status,r.status INTO payment,reservation FROM "${applicationSchema}".payments p
        JOIN "${applicationSchema}".inventory_reservations r ON r.id=OLD.reservation_id WHERE p.sale_id=OLD.id;
    IF (NEW.id,NEW.number,NEW.cart_id,NEW.actor_id,NEW.customer_id,NEW.reservation_id,NEW.total,NEW.created_at)
        IS DISTINCT FROM (OLD.id,OLD.number,OLD.cart_id,OLD.actor_id,OLD.customer_id,OLD.reservation_id,OLD.total,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR NOT "${applicationSchema}".sale_transition_allowed(OLD.status,NEW.status,payment,reservation)
    THEN RAISE EXCEPTION 'Invalid sale transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER sales_immutable ON "${applicationSchema}".sales;
CREATE TRIGGER sales_state_guard BEFORE UPDATE OR DELETE ON "${applicationSchema}".sales FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_sale_state();
CREATE FUNCTION "${applicationSchema}".guard_payment_state() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r "${applicationSchema}".inventory_reservations%ROWTYPE; expected TEXT;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payment deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT ir.* INTO r FROM "${applicationSchema}".inventory_reservations ir JOIN "${applicationSchema}".sales s ON s.reservation_id=ir.id WHERE s.id=OLD.sale_id;
    expected:=CASE OLD.scenario WHEN 'TIMEOUT' THEN 'INDETERMINADO' ELSE OLD.scenario END;
    IF (NEW.id,NEW.sale_id,NEW.method,NEW.scenario,NEW.amount,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.sale_id,OLD.method,OLD.scenario,OLD.amount,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR r.status IS DISTINCT FROM 'ACTIVE' OR NOT r.payment_protected
        OR NOT ((OLD.status='CRIADO' AND NEW.status='EM_PROCESSAMENTO' AND r.expires_at>clock_timestamp())
            OR (OLD.status='EM_PROCESSAMENTO' AND NEW.status=expected AND EXISTS(SELECT 1 FROM "${applicationSchema}".payment_results WHERE payment_id=OLD.id AND outcome=NEW.status)))
    THEN RAISE EXCEPTION 'Invalid payment transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER payments_immutable ON "${applicationSchema}".payments;
CREATE TRIGGER payments_state_guard BEFORE UPDATE OR DELETE ON "${applicationSchema}".payments FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_payment_state();
CREATE FUNCTION "${applicationSchema}".guard_sale_saga() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment TEXT;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Saga deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT status INTO payment FROM "${applicationSchema}".payments WHERE id=OLD.payment_id;
    IF (NEW.sale_id,NEW.payment_id,NEW.created_at) IS DISTINCT FROM (OLD.sale_id,OLD.payment_id,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1 OR NEW.phase IS DISTINCT FROM payment
        OR NOT ((OLD.phase='CRIADO' AND NEW.phase='EM_PROCESSAMENTO') OR (OLD.phase='EM_PROCESSAMENTO' AND NEW.phase IN ('APROVADO','RECUSADO','INDETERMINADO')))
    THEN RAISE EXCEPTION 'Invalid Saga transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER sale_sagas_guard BEFORE UPDATE OR DELETE ON "${applicationSchema}".sale_sagas FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_sale_saga();
CREATE FUNCTION "${applicationSchema}".record_sale_saga_event(sale UUID, event_kind TEXT) RETURNS void LANGUAGE sql AS $$
    INSERT INTO "${applicationSchema}".sale_events(sale_id,version,kind,snapshot)
    SELECT s.id,s.version,event_kind,jsonb_build_object('sale',to_jsonb(s),'payment',to_jsonb(p),'reservation',to_jsonb(r),'saga',to_jsonb(g))
    FROM "${applicationSchema}".sales s JOIN "${applicationSchema}".payments p ON p.sale_id=s.id
    JOIN "${applicationSchema}".inventory_reservations r ON r.id=s.reservation_id JOIN "${applicationSchema}".sale_sagas g ON g.sale_id=s.id WHERE s.id=sale;
$$;
-- Both commands lock sale -> payment -> Saga -> reservation. Expiry never acquires a sale lock.
CREATE FUNCTION "${applicationSchema}".start_sale_payment(operation UUID) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE sale UUID; s "${applicationSchema}".sales%ROWTYPE; p "${applicationSchema}".payments%ROWTYPE; g "${applicationSchema}".sale_sagas%ROWTYPE;
BEGIN
    SELECT sale_id INTO sale FROM "${applicationSchema}".payments WHERE id=operation;
    IF NOT FOUND THEN RETURN 'NOT_FOUND'; END IF;
    SELECT * INTO s FROM "${applicationSchema}".sales WHERE id=sale FOR UPDATE SKIP LOCKED;
    IF NOT FOUND THEN RETURN 'BUSY'; END IF;
    SELECT * INTO p FROM "${applicationSchema}".payments WHERE id=operation FOR UPDATE;
    SELECT * INTO g FROM "${applicationSchema}".sale_sagas WHERE sale_id=sale FOR UPDATE;
    IF NOT FOUND OR g.phase<>p.status THEN RAISE EXCEPTION 'Saga state conflict' USING ERRCODE='23514'; END IF;
    IF p.status<>'CRIADO' THEN RETURN p.status; END IF;
    IF s.status<>'AGUARDANDO_PAGAMENTO' THEN RAISE EXCEPTION 'Sale state conflict' USING ERRCODE='23514'; END IF;
    IF NOT "${applicationSchema}".protect_inventory_reservation(s.reservation_id) THEN RETURN 'RESERVATION_UNAVAILABLE'; END IF;
    UPDATE "${applicationSchema}".payments SET status='EM_PROCESSAMENTO',version=version+1,updated_at=clock_timestamp() WHERE id=operation;
    UPDATE "${applicationSchema}".sales SET version=version+1,updated_at=clock_timestamp() WHERE id=sale;
    UPDATE "${applicationSchema}".sale_sagas SET phase='EM_PROCESSAMENTO',version=version+1,updated_at=clock_timestamp() WHERE sale_id=sale;
    PERFORM "${applicationSchema}".record_sale_saga_event(sale,'PAYMENT_STARTED');
    RETURN 'EM_PROCESSAMENTO';
END;
$$;
CREATE FUNCTION "${applicationSchema}".record_simulated_payment(operation UUID, result TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE sale UUID; s "${applicationSchema}".sales%ROWTYPE; p "${applicationSchema}".payments%ROWTYPE; g "${applicationSchema}".sale_sagas%ROWTYPE; known TEXT; expected TEXT;
BEGIN
    SELECT sale_id INTO sale FROM "${applicationSchema}".payments WHERE id=operation;
    IF NOT FOUND THEN RETURN 'NOT_FOUND'; END IF;
    SELECT * INTO s FROM "${applicationSchema}".sales WHERE id=sale FOR UPDATE SKIP LOCKED;
    IF NOT FOUND THEN RETURN 'BUSY'; END IF;
    SELECT * INTO p FROM "${applicationSchema}".payments WHERE id=operation FOR UPDATE;
    SELECT * INTO g FROM "${applicationSchema}".sale_sagas WHERE sale_id=sale FOR UPDATE;
    IF NOT FOUND OR g.phase<>p.status THEN RAISE EXCEPTION 'Saga state conflict' USING ERRCODE='23514'; END IF;
    expected:=CASE p.scenario WHEN 'TIMEOUT' THEN 'INDETERMINADO' ELSE p.scenario END;
    IF result IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Payment decision conflict' USING ERRCODE='23514'; END IF;
    SELECT outcome INTO known FROM "${applicationSchema}".payment_results WHERE payment_id=operation;
    IF FOUND THEN
        IF known<>result OR p.status<>known THEN RAISE EXCEPTION 'Payment decision conflict' USING ERRCODE='23514'; END IF;
        RETURN known;
    END IF;
    IF p.status<>'EM_PROCESSAMENTO' THEN RETURN 'NOT_STARTED'; END IF;
    INSERT INTO "${applicationSchema}".payment_results(payment_id,outcome,response)
        VALUES(operation,result,jsonb_build_object('version',1,'paymentId',operation,'saleId',sale,'method',p.method,'scenario',p.scenario,'amount',p.amount,'outcome',result));
    UPDATE "${applicationSchema}".payments SET status=result,version=version+1,updated_at=clock_timestamp() WHERE id=operation;
    UPDATE "${applicationSchema}".sales SET status=CASE result WHEN 'APROVADO' THEN 'PAGAMENTO_APROVADO' WHEN 'RECUSADO' THEN 'PAGAMENTO_RECUSADO' ELSE 'AGUARDANDO_PAGAMENTO' END,
        version=version+1,updated_at=clock_timestamp() WHERE id=sale;
    UPDATE "${applicationSchema}".sale_sagas SET phase=result,version=version+1,updated_at=clock_timestamp() WHERE sale_id=sale;
    PERFORM "${applicationSchema}".record_sale_saga_event(sale,result);
    RETURN result;
END;
$$;
