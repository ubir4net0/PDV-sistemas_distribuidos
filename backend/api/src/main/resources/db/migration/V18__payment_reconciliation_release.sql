CREATE TABLE "${applicationSchema}".provider_resolutions (
    payment_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".payments(id),
    outcome VARCHAR(32) NOT NULL CHECK(outcome IN ('APROVADO','RECUSADO')),
    reason VARCHAR(500) NOT NULL CHECK(length(btrim(reason)) BETWEEN 1 AND 500),
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE "${applicationSchema}".payment_command_operations (
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id),
    request_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES "${applicationSchema}".payments(id),
    kind VARCHAR(16) NOT NULL CHECK(kind IN ('RESOLUTION','RECONCILE')),
    fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    response JSONB NOT NULL CHECK(jsonb_typeof(response)='object'),
    http_status INTEGER NOT NULL CHECK(http_status IN (200,202)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE "${applicationSchema}".reservation_releases (
    sale_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".sales(id),
    payment_id UUID NOT NULL UNIQUE,
    reservation_id UUID NOT NULL UNIQUE REFERENCES "${applicationSchema}".inventory_reservations(id),
    released_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY(sale_id,payment_id) REFERENCES "${applicationSchema}".payments(sale_id,id)
);
CREATE TABLE "${applicationSchema}".reservation_release_items (
    sale_id UUID NOT NULL REFERENCES "${applicationSchema}".reservation_releases(sale_id),
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_balances(product_id),
    quantity BIGINT NOT NULL CHECK(quantity>0),
    physical BIGINT NOT NULL CHECK(physical>=reserved_before),
    reserved_before BIGINT NOT NULL CHECK(reserved_before>=quantity),
    reserved_after BIGINT NOT NULL CHECK(reserved_after=reserved_before-quantity),
    version_before BIGINT NOT NULL CHECK(version_before>=0 AND version_before<9223372036854775807),
    version_after BIGINT NOT NULL CHECK(version_after=version_before+1),
    PRIMARY KEY(sale_id,product_id),
    FOREIGN KEY(sale_id,product_id) REFERENCES "${applicationSchema}".sale_items(sale_id,product_id)
);
CREATE TRIGGER provider_resolutions_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".provider_resolutions FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER payment_command_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".payment_command_operations FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER reservation_releases_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".reservation_releases FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER reservation_release_items_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".reservation_release_items FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
ALTER TABLE "${applicationSchema}".sale_sagas ADD COLUMN reservation_released_at TIMESTAMPTZ;
ALTER TABLE "${applicationSchema}".inventory_reservation_events DROP CONSTRAINT inventory_reservation_events_kind_check;
ALTER TABLE "${applicationSchema}".inventory_reservation_events ADD CONSTRAINT inventory_reservation_events_kind_check CHECK(kind IN ('CREATED','RENEWED','PAYMENT_PROTECTED','EXPIRED','CONFIRMED','RELEASED'));
CREATE FUNCTION "${applicationSchema}".reconciliation_proven(operation UUID, result TEXT) RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM "${applicationSchema}".payments p
        JOIN "${applicationSchema}".payment_results r ON r.payment_id=p.id JOIN "${applicationSchema}".provider_resolutions d ON d.payment_id=p.id
        WHERE p.id=operation AND p.scenario='TIMEOUT' AND r.outcome='INDETERMINADO' AND d.outcome=result);
$$;
CREATE OR REPLACE FUNCTION "${applicationSchema}".payment_approval_proven(operation UUID) RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".payment_results r ON r.payment_id=p.id
        WHERE p.id=operation AND p.status='APROVADO' AND (r.outcome='APROVADO' OR "${applicationSchema}".reconciliation_proven(operation,'APROVADO')));
$$;
CREATE FUNCTION "${applicationSchema}".payment_refusal_proven(operation UUID) RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".payment_results r ON r.payment_id=p.id
        WHERE p.id=operation AND p.status='RECUSADO' AND (r.outcome='RECUSADO' OR "${applicationSchema}".reconciliation_proven(operation,'RECUSADO')));
$$;
CREATE FUNCTION "${applicationSchema}".apply_business_reconciliation(operation UUID, result TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE sale UUID; s "${applicationSchema}".sales%ROWTYPE; p "${applicationSchema}".payments%ROWTYPE; g "${applicationSchema}".sale_sagas%ROWTYPE;
BEGIN
    SELECT sale_id INTO sale FROM "${applicationSchema}".payments WHERE id=operation;
    IF NOT FOUND THEN RETURN 'NOT_FOUND'; END IF;
    SELECT * INTO s FROM "${applicationSchema}".sales WHERE id=sale FOR UPDATE SKIP LOCKED;
    IF NOT FOUND THEN RETURN 'BUSY'; END IF;
    SELECT * INTO p FROM "${applicationSchema}".payments WHERE id=operation FOR UPDATE;
    SELECT * INTO g FROM "${applicationSchema}".sale_sagas WHERE sale_id=sale FOR UPDATE;
    PERFORM 1 FROM "${applicationSchema}".inventory_reservations WHERE id=s.reservation_id FOR UPDATE;
    IF NOT "${applicationSchema}".reconciliation_proven(operation,result) THEN RAISE EXCEPTION 'Missing reconciliation proof' USING ERRCODE='23514'; END IF;
    IF p.status=result AND g.phase=result THEN RETURN result; END IF;
    IF p.status<>'INDETERMINADO' OR g.phase<>'INDETERMINADO' OR s.status<>'AGUARDANDO_PAGAMENTO'
    THEN RAISE EXCEPTION 'Invalid reconciliation state' USING ERRCODE='23514'; END IF;
    UPDATE "${applicationSchema}".payments SET status=result,version=version+1,updated_at=clock_timestamp() WHERE id=operation;
    UPDATE "${applicationSchema}".sales SET status=CASE result WHEN 'APROVADO' THEN 'PAGAMENTO_APROVADO' ELSE 'PAGAMENTO_RECUSADO' END,version=version+1,updated_at=clock_timestamp() WHERE id=sale;
    UPDATE "${applicationSchema}".sale_sagas SET phase=result,version=version+1,updated_at=clock_timestamp() WHERE sale_id=sale;
    PERFORM "${applicationSchema}".record_sale_saga_event(sale,result);
    RETURN result;
END;
$$;
CREATE FUNCTION "${applicationSchema}".release_business_sale(operation UUID) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE sale UUID; s "${applicationSchema}".sales%ROWTYPE; g "${applicationSchema}".sale_sagas%ROWTYPE;
    r "${applicationSchema}".inventory_reservations%ROWTYPE; b "${applicationSchema}".inventory_balances%ROWTYPE; item RECORD; released TIMESTAMPTZ:=clock_timestamp();
BEGIN
    SELECT sale_id INTO sale FROM "${applicationSchema}".payments WHERE id=operation;
    IF NOT FOUND THEN RETURN 'NOT_FOUND'; END IF;
    SELECT * INTO s FROM "${applicationSchema}".sales WHERE id=sale FOR UPDATE SKIP LOCKED;
    IF NOT FOUND THEN RETURN 'BUSY'; END IF;
    PERFORM 1 FROM "${applicationSchema}".payments WHERE id=operation FOR UPDATE;
    SELECT * INTO g FROM "${applicationSchema}".sale_sagas WHERE sale_id=sale FOR UPDATE;
    SELECT * INTO r FROM "${applicationSchema}".inventory_reservations WHERE id=s.reservation_id FOR UPDATE;
    IF r.status='RELEASED' AND g.reservation_released_at IS NOT NULL AND EXISTS(SELECT 1 FROM "${applicationSchema}".reservation_releases c
        WHERE c.sale_id=sale AND c.payment_id=operation AND c.reservation_id=r.id) THEN RETURN 'RELEASED'; END IF;
    IF s.status<>'PAGAMENTO_RECUSADO' OR g.phase<>'RECUSADO' OR NOT "${applicationSchema}".payment_refusal_proven(operation)
        OR r.status<>'ACTIVE' OR NOT r.payment_protected OR r.actor_id<>s.actor_id OR r.version=9223372036854775807
        OR NOT EXISTS(SELECT 1 FROM "${applicationSchema}".inventory_reservation_items WHERE reservation_id=r.id)
        OR EXISTS(SELECT 1 FROM (SELECT product_id,quantity FROM "${applicationSchema}".sale_items WHERE sale_id=sale) a
            FULL JOIN (SELECT product_id,quantity FROM "${applicationSchema}".inventory_reservation_items WHERE reservation_id=r.id) i USING(product_id)
            WHERE a.quantity IS DISTINCT FROM i.quantity)
    THEN RAISE EXCEPTION 'Invalid refusal release' USING ERRCODE='23514'; END IF;
    INSERT INTO "${applicationSchema}".reservation_releases(sale_id,payment_id,reservation_id,released_at) VALUES(sale,operation,r.id,released);
    FOR item IN SELECT * FROM "${applicationSchema}".inventory_reservation_items WHERE reservation_id=r.id ORDER BY product_id LOOP
        SELECT * INTO b FROM "${applicationSchema}".inventory_balances WHERE product_id=item.product_id FOR UPDATE;
        IF NOT FOUND OR b.reserved<item.quantity OR b.version=9223372036854775807 THEN RAISE EXCEPTION 'Release balance conflict' USING ERRCODE='23514'; END IF;
        UPDATE "${applicationSchema}".inventory_balances SET reserved=reserved-item.quantity,version=version+1,updated_at=released WHERE product_id=item.product_id;
        INSERT INTO "${applicationSchema}".reservation_release_items(sale_id,product_id,quantity,physical,reserved_before,reserved_after,version_before,version_after)
            VALUES(sale,item.product_id,item.quantity,b.physical,b.reserved,b.reserved-item.quantity,b.version,b.version+1);
    END LOOP;
    UPDATE "${applicationSchema}".inventory_reservations SET status='RELEASED',version=version+1,updated_at=released WHERE id=r.id;
    PERFORM "${applicationSchema}".record_inventory_reservation_event(r.id,'RELEASED');
    UPDATE "${applicationSchema}".sale_sagas SET reservation_released_at=released,version=version+1,updated_at=released WHERE sale_id=sale;
    RETURN 'RELEASED';
END;
$$;
ALTER TABLE "${applicationSchema}".outbox_events DROP CONSTRAINT outbox_events_event_type_check;
ALTER TABLE "${applicationSchema}".outbox_events ADD CONSTRAINT outbox_events_event_type_check CHECK(event_type IN ('pdv.payment.requested.v1','pdv.payment.result.v1','pdv.sale.confirm.v1','pdv.payment.reconciled.v1','pdv.sale.release.v1'));
CREATE FUNCTION "${applicationSchema}".enqueue_provider_resolution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM "${applicationSchema}".enqueue_business_event('payment-reconcile:'||NEW.payment_id,'pdv.payment.reconciled.v1',NEW.payment_id,NEW.outcome);
    RETURN NEW;
END;
$$;
CREATE TRIGGER provider_resolution_outbox AFTER INSERT ON "${applicationSchema}".provider_resolutions FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".enqueue_provider_resolution();
CREATE FUNCTION "${applicationSchema}".enqueue_sale_release() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE operation UUID;
BEGIN
    IF NEW.status='PAGAMENTO_RECUSADO' AND OLD.status IS DISTINCT FROM NEW.status THEN
        SELECT id INTO operation FROM "${applicationSchema}".payments WHERE sale_id=NEW.id;
        PERFORM "${applicationSchema}".enqueue_business_event('sale-release:'||operation,'pdv.sale.release.v1',operation,'RECUSADO');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER sales_release_outbox AFTER UPDATE ON "${applicationSchema}".sales FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".enqueue_sale_release();
SELECT "${applicationSchema}".enqueue_business_event('sale-release:'||p.id,'pdv.sale.release.v1',p.id,'RECUSADO')
    FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".sales s ON s.id=p.sale_id WHERE s.status='PAGAMENTO_RECUSADO';

CREATE OR REPLACE FUNCTION "${applicationSchema}".guard_sale_saga() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment TEXT; finishing boolean; releasing boolean; progressing boolean;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Saga deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT status INTO payment FROM "${applicationSchema}".payments WHERE id=OLD.payment_id;
    finishing:=OLD.phase='APROVADO' AND NEW.phase='APROVADO' AND OLD.receipt_id IS NULL AND NEW.receipt_id IS NOT NULL
        AND NEW.stock_confirmed_at IS NOT NULL AND OLD.reservation_released_at IS NULL AND NEW.reservation_released_at IS NULL
        AND EXISTS(SELECT 1 FROM "${applicationSchema}".sale_confirmations c JOIN "${applicationSchema}".sales s ON s.id=c.sale_id
            WHERE c.sale_id=OLD.sale_id AND c.receipt_id=NEW.receipt_id AND c.payment_id=OLD.payment_id AND s.status='CONCLUIDA');
    releasing:=OLD.phase='RECUSADO' AND NEW.phase='RECUSADO' AND OLD.reservation_released_at IS NULL AND NEW.reservation_released_at IS NOT NULL
        AND NEW.receipt_id IS NULL AND NEW.stock_confirmed_at IS NULL AND EXISTS(SELECT 1 FROM "${applicationSchema}".reservation_releases c
            JOIN "${applicationSchema}".inventory_reservations r ON r.id=c.reservation_id
            WHERE c.sale_id=OLD.sale_id AND c.payment_id=OLD.payment_id AND r.status='RELEASED');
    progressing:=OLD.receipt_id IS NULL AND NEW.receipt_id IS NULL AND NEW.stock_confirmed_at IS NULL
        AND OLD.reservation_released_at IS NULL AND NEW.reservation_released_at IS NULL
        AND ((OLD.phase='CRIADO' AND NEW.phase='EM_PROCESSAMENTO') OR (OLD.phase='EM_PROCESSAMENTO' AND NEW.phase IN ('APROVADO','RECUSADO','INDETERMINADO'))
            OR (OLD.phase='INDETERMINADO' AND NEW.phase IN ('APROVADO','RECUSADO') AND "${applicationSchema}".reconciliation_proven(OLD.payment_id,NEW.phase)));
    IF (NEW.sale_id,NEW.payment_id,NEW.created_at) IS DISTINCT FROM (OLD.sale_id,OLD.payment_id,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1 OR NEW.phase IS DISTINCT FROM payment
        OR NOT (finishing OR releasing OR progressing)
    THEN RAISE EXCEPTION 'Invalid Saga transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION "${applicationSchema}".guard_provider_resolution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".payment_results r ON r.payment_id=p.id
        WHERE p.id=NEW.payment_id AND p.scenario='TIMEOUT' AND p.status='INDETERMINADO' AND r.outcome='INDETERMINADO')
    THEN RAISE EXCEPTION 'Resolution requires an uncertain simulation' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER provider_resolution_guard BEFORE INSERT ON "${applicationSchema}".provider_resolutions FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_provider_resolution();

-- Preserve earlier transitions; permit terminal reconciliation only with durable proof.
CREATE OR REPLACE FUNCTION "${applicationSchema}".guard_payment_state() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r "${applicationSchema}".inventory_reservations%ROWTYPE; expected TEXT;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payment deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT ir.* INTO r FROM "${applicationSchema}".inventory_reservations ir JOIN "${applicationSchema}".sales s ON s.reservation_id=ir.id WHERE s.id=OLD.sale_id;
    expected:=CASE OLD.scenario WHEN 'TIMEOUT' THEN 'INDETERMINADO' ELSE OLD.scenario END;
    IF (NEW.id,NEW.sale_id,NEW.method,NEW.scenario,NEW.amount,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.sale_id,OLD.method,OLD.scenario,OLD.amount,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR r.status IS DISTINCT FROM 'ACTIVE' OR NOT r.payment_protected
        OR NOT ((OLD.status='CRIADO' AND NEW.status='EM_PROCESSAMENTO' AND r.expires_at>clock_timestamp())
            OR (OLD.status='EM_PROCESSAMENTO' AND NEW.status=expected AND EXISTS(SELECT 1 FROM "${applicationSchema}".payment_results WHERE payment_id=OLD.id AND outcome=NEW.status))
            OR (OLD.status='INDETERMINADO' AND NEW.status IN ('APROVADO','RECUSADO') AND "${applicationSchema}".reconciliation_proven(OLD.id,NEW.status)))
    THEN RAISE EXCEPTION 'Invalid payment transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION "${applicationSchema}".guard_inventory_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Reservation deletion is forbidden' USING ERRCODE='55000'; END IF;
    IF NEW.id<>OLD.id OR NEW.actor_id<>OLD.actor_id OR NEW.created_at<>OLD.created_at
        OR OLD.status<>'ACTIVE' OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR (OLD.payment_protected AND NOT NEW.payment_protected)
        OR NEW.status NOT IN ('ACTIVE','EXPIRED','CONFIRMED','RELEASED')
        OR (NEW.status='EXPIRED' AND (NEW.payment_protected OR OLD.expires_at>clock_timestamp()))
        OR (NEW.expires_at<>OLD.expires_at AND (OLD.payment_protected OR OLD.expires_at<=clock_timestamp() OR NEW.status<>'ACTIVE' OR NEW.expires_at<=clock_timestamp()))
    THEN RAISE EXCEPTION 'Invalid reservation transition' USING ERRCODE='23514'; END IF;
    IF NEW.status='CONFIRMED' AND (NOT OLD.payment_protected OR NOT EXISTS(
        SELECT 1 FROM "${applicationSchema}".sale_confirmations c JOIN "${applicationSchema}".sales s ON s.id=c.sale_id
        WHERE c.reservation_id=OLD.id AND s.reservation_id=OLD.id AND s.actor_id=OLD.actor_id
        AND s.status='PAGAMENTO_APROVADO' AND "${applicationSchema}".payment_approval_proven(c.payment_id)
        AND NOT EXISTS(SELECT 1 FROM "${applicationSchema}".inventory_reservation_items i
            LEFT JOIN "${applicationSchema}".sale_stock_movements m ON m.sale_id=c.sale_id AND m.product_id=i.product_id
            WHERE i.reservation_id=OLD.id AND (m.id IS NULL OR m.quantity<>i.quantity OR m.operation_id<>c.payment_id))
    )) THEN RAISE EXCEPTION 'Missing confirmation proof' USING ERRCODE='23514'; END IF;
    IF NEW.status='RELEASED' AND (NOT OLD.payment_protected OR NOT EXISTS(
        SELECT 1 FROM "${applicationSchema}".reservation_releases c JOIN "${applicationSchema}".sales s ON s.id=c.sale_id
        WHERE c.reservation_id=OLD.id AND s.reservation_id=OLD.id AND s.actor_id=OLD.actor_id
        AND s.status='PAGAMENTO_RECUSADO' AND "${applicationSchema}".payment_refusal_proven(c.payment_id)
        AND NOT EXISTS(SELECT 1 FROM "${applicationSchema}".inventory_reservation_items i
            LEFT JOIN "${applicationSchema}".reservation_release_items m ON m.sale_id=c.sale_id AND m.product_id=i.product_id
            WHERE i.reservation_id=OLD.id AND (m.sale_id IS NULL OR m.quantity<>i.quantity))
    )) THEN RAISE EXCEPTION 'Missing release proof' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION "${applicationSchema}".record_simulated_payment(operation UUID, result TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
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
        IF known<>result OR (p.status<>known AND NOT (known='INDETERMINADO' AND p.status IN ('APROVADO','RECUSADO')
            AND "${applicationSchema}".reconciliation_proven(operation,p.status))) THEN RAISE EXCEPTION 'Payment decision conflict' USING ERRCODE='23514'; END IF;
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
