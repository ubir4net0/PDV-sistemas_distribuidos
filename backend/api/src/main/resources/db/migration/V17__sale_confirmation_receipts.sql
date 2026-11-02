CREATE TABLE "${applicationSchema}".receipts (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL UNIQUE REFERENCES "${applicationSchema}".sales(id),
    snapshot JSONB NOT NULL CHECK(jsonb_typeof(snapshot)='object'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(sale_id,id)
);
CREATE TABLE "${applicationSchema}".sale_confirmations (
    sale_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".sales(id),
    payment_id UUID NOT NULL UNIQUE,
    reservation_id UUID NOT NULL UNIQUE REFERENCES "${applicationSchema}".inventory_reservations(id),
    receipt_id UUID NOT NULL UNIQUE,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY(sale_id,payment_id) REFERENCES "${applicationSchema}".payments(sale_id,id),
    FOREIGN KEY(sale_id,receipt_id) REFERENCES "${applicationSchema}".receipts(sale_id,id)
);
CREATE TABLE "${applicationSchema}".sale_stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES "${applicationSchema}".sale_confirmations(sale_id),
    operation_id UUID NOT NULL REFERENCES "${applicationSchema}".payments(id),
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_balances(product_id),
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id),
    type VARCHAR(16) NOT NULL DEFAULT 'SALE' CHECK(type='SALE'),
    quantity BIGINT NOT NULL CHECK(quantity>0),
    delta BIGINT NOT NULL CHECK(delta=-quantity),
    reason VARCHAR(500) NOT NULL,
    physical_before BIGINT NOT NULL CHECK(physical_before>=quantity),
    physical_after BIGINT NOT NULL CHECK(physical_after=physical_before-quantity),
    reserved_before BIGINT NOT NULL CHECK(reserved_before>=quantity AND reserved_before<=physical_before),
    reserved_after BIGINT NOT NULL CHECK(reserved_after=reserved_before-quantity AND reserved_after<=physical_after),
    version_before BIGINT NOT NULL CHECK(version_before>=0 AND version_before<9223372036854775807),
    version_after BIGINT NOT NULL CHECK(version_after=version_before+1),
    balance_created_at TIMESTAMPTZ NOT NULL,
    balance_updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(sale_id,product_id), UNIQUE(product_id,version_after),
    FOREIGN KEY(sale_id,product_id) REFERENCES "${applicationSchema}".sale_items(sale_id,product_id)
);
CREATE TRIGGER receipts_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".receipts FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER sale_confirmations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".sale_confirmations FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER sale_stock_movements_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".sale_stock_movements FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE VIEW "${applicationSchema}".inventory_movement_history AS
    SELECT * FROM "${applicationSchema}".inventory_movements
    UNION ALL
    SELECT id,operation_id,product_id,actor_id,type,quantity,delta,reason,physical_before,physical_after,
        reserved_before,reserved_after,version_before,version_after,balance_created_at,balance_updated_at,created_at
    FROM "${applicationSchema}".sale_stock_movements;
ALTER TABLE "${applicationSchema}".sale_sagas ADD COLUMN receipt_id UUID REFERENCES "${applicationSchema}".receipts(id),
    ADD COLUMN stock_confirmed_at TIMESTAMPTZ,
    ADD CHECK((receipt_id IS NULL)=(stock_confirmed_at IS NULL));
ALTER TABLE "${applicationSchema}".sale_events DROP CONSTRAINT sale_events_kind_check;
ALTER TABLE "${applicationSchema}".sale_events ADD CONSTRAINT sale_events_kind_check CHECK(kind IN ('CHECKOUT','PAYMENT_STARTED','APROVADO','RECUSADO','INDETERMINADO','CONCLUDED'));
ALTER TABLE "${applicationSchema}".inventory_reservation_events DROP CONSTRAINT inventory_reservation_events_kind_check;
ALTER TABLE "${applicationSchema}".inventory_reservation_events ADD CONSTRAINT inventory_reservation_events_kind_check CHECK(kind IN ('CREATED','RENEWED','PAYMENT_PROTECTED','EXPIRED','CONFIRMED'));
CREATE FUNCTION "${applicationSchema}".payment_approval_proven(operation UUID) RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".payment_results r ON r.payment_id=p.id
        WHERE p.id=operation AND p.status='APROVADO' AND r.outcome='APROVADO');
$$;
CREATE OR REPLACE FUNCTION "${applicationSchema}".guard_inventory_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Reservation deletion is forbidden' USING ERRCODE='55000'; END IF;
    IF NEW.id<>OLD.id OR NEW.actor_id<>OLD.actor_id OR NEW.created_at<>OLD.created_at
        OR OLD.status<>'ACTIVE' OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR (OLD.payment_protected AND NOT NEW.payment_protected)
        OR NEW.status NOT IN ('ACTIVE','EXPIRED','CONFIRMED')
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
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION "${applicationSchema}".guard_sale_saga() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE payment TEXT; finishing boolean;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Saga deletion is forbidden' USING ERRCODE='55000'; END IF;
    SELECT status INTO payment FROM "${applicationSchema}".payments WHERE id=OLD.payment_id;
    finishing:=OLD.phase='APROVADO' AND NEW.phase='APROVADO' AND OLD.receipt_id IS NULL AND NEW.receipt_id IS NOT NULL
        AND NEW.stock_confirmed_at IS NOT NULL AND EXISTS(SELECT 1 FROM "${applicationSchema}".sale_confirmations c
            JOIN "${applicationSchema}".sales s ON s.id=c.sale_id WHERE c.sale_id=OLD.sale_id AND c.receipt_id=NEW.receipt_id
            AND c.payment_id=OLD.payment_id AND s.status='CONCLUIDA');
    IF (NEW.sale_id,NEW.payment_id,NEW.created_at) IS DISTINCT FROM (OLD.sale_id,OLD.payment_id,OLD.created_at)
        OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1 OR NEW.phase IS DISTINCT FROM payment
        OR NOT (finishing OR (OLD.receipt_id IS NULL AND NEW.receipt_id IS NULL AND NEW.stock_confirmed_at IS NULL
            AND ((OLD.phase='CRIADO' AND NEW.phase='EM_PROCESSAMENTO') OR (OLD.phase='EM_PROCESSAMENTO' AND NEW.phase IN ('APROVADO','RECUSADO','INDETERMINADO')))))
    THEN RAISE EXCEPTION 'Invalid Saga transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION "${applicationSchema}".confirm_business_sale(operation UUID) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE sale UUID; s "${applicationSchema}".sales%ROWTYPE; p "${applicationSchema}".payments%ROWTYPE;
    g "${applicationSchema}".sale_sagas%ROWTYPE; r "${applicationSchema}".inventory_reservations%ROWTYPE;
    b "${applicationSchema}".inventory_balances%ROWTYPE; item RECORD; receipt UUID:=gen_random_uuid(); issued TIMESTAMPTZ:=clock_timestamp();
BEGIN
    SELECT sale_id INTO sale FROM "${applicationSchema}".payments WHERE id=operation;
    IF NOT FOUND THEN RETURN 'NOT_FOUND'; END IF;
    SELECT * INTO s FROM "${applicationSchema}".sales WHERE id=sale FOR UPDATE SKIP LOCKED;
    IF NOT FOUND THEN RETURN 'BUSY'; END IF;
    SELECT * INTO p FROM "${applicationSchema}".payments WHERE id=operation FOR UPDATE;
    SELECT * INTO g FROM "${applicationSchema}".sale_sagas WHERE sale_id=sale FOR UPDATE;
    SELECT * INTO r FROM "${applicationSchema}".inventory_reservations WHERE id=s.reservation_id FOR UPDATE;
    IF s.status='CONCLUIDA' AND r.status='CONFIRMED' AND EXISTS(SELECT 1 FROM "${applicationSchema}".sale_confirmations c
        WHERE c.sale_id=sale AND c.payment_id=operation AND c.reservation_id=r.id AND c.receipt_id=g.receipt_id)
    THEN RETURN 'CONCLUIDA'; END IF;
    IF s.status<>'PAGAMENTO_APROVADO' OR NOT "${applicationSchema}".payment_approval_proven(operation)
        OR g.phase<>'APROVADO' OR g.receipt_id IS NOT NULL OR r.status<>'ACTIVE' OR NOT r.payment_protected
        OR p.amount<>s.total OR r.actor_id<>s.actor_id OR s.version=9223372036854775807 OR g.version=9223372036854775807
        OR r.version=9223372036854775807 OR NOT EXISTS(SELECT 1 FROM "${applicationSchema}".sale_items WHERE sale_id=sale)
        OR EXISTS(SELECT 1 FROM (SELECT product_id,quantity FROM "${applicationSchema}".sale_items WHERE sale_id=sale) a
            FULL JOIN (SELECT product_id,quantity FROM "${applicationSchema}".inventory_reservation_items WHERE reservation_id=r.id) i USING(product_id)
            WHERE a.quantity IS DISTINCT FROM i.quantity)
        OR s.total<>(SELECT sum(subtotal) FROM "${applicationSchema}".sale_items WHERE sale_id=sale)
    THEN RAISE EXCEPTION 'Invalid sale confirmation' USING ERRCODE='23514'; END IF;
    INSERT INTO "${applicationSchema}".receipts(id,sale_id,snapshot,issued_at) VALUES(receipt,sale,jsonb_build_object(
        'id',receipt,'saleId',sale,'number',s.number,'customerId',s.customer_id,'paymentId',operation,'method',p.method,'total',s.total,'issuedAt',issued,
        'items',(SELECT jsonb_agg(jsonb_build_object('productId',product_id,'quantity',quantity,'unitPrice',unit_price,'subtotal',subtotal) ORDER BY product_id)
            FROM "${applicationSchema}".sale_items WHERE sale_id=sale)),issued);
    INSERT INTO "${applicationSchema}".sale_confirmations(sale_id,payment_id,reservation_id,receipt_id,confirmed_at) VALUES(sale,operation,r.id,receipt,issued);
    FOR item IN SELECT * FROM "${applicationSchema}".sale_items WHERE sale_id=sale ORDER BY product_id LOOP
        SELECT * INTO b FROM "${applicationSchema}".inventory_balances WHERE product_id=item.product_id FOR UPDATE;
        IF NOT FOUND OR b.reserved<item.quantity OR b.version=9223372036854775807 THEN RAISE EXCEPTION 'Confirmation balance conflict' USING ERRCODE='23514'; END IF;
        UPDATE "${applicationSchema}".inventory_balances SET physical=physical-item.quantity,reserved=reserved-item.quantity,version=version+1,updated_at=issued WHERE product_id=item.product_id;
        INSERT INTO "${applicationSchema}".sale_stock_movements(sale_id,operation_id,product_id,actor_id,quantity,delta,reason,
            physical_before,physical_after,reserved_before,reserved_after,version_before,version_after,balance_created_at,balance_updated_at,created_at)
            VALUES(sale,operation,item.product_id,s.actor_id,item.quantity,-item.quantity,'SALE:'||sale,
                b.physical,b.physical-item.quantity,b.reserved,b.reserved-item.quantity,b.version,b.version+1,b.created_at,issued,issued);
    END LOOP;
    UPDATE "${applicationSchema}".inventory_reservations SET status='CONFIRMED',version=version+1,updated_at=issued WHERE id=r.id;
    PERFORM "${applicationSchema}".record_inventory_reservation_event(r.id,'CONFIRMED');
    UPDATE "${applicationSchema}".sales SET status='CONCLUIDA',version=version+1,updated_at=issued WHERE id=sale;
    UPDATE "${applicationSchema}".sale_sagas SET receipt_id=receipt,stock_confirmed_at=issued,version=version+1,updated_at=issued WHERE sale_id=sale;
    PERFORM "${applicationSchema}".record_sale_saga_event(sale,'CONCLUDED');
    RETURN 'CONCLUIDA';
END;
$$;
ALTER TABLE "${applicationSchema}".outbox_events DROP CONSTRAINT outbox_events_event_type_check;
ALTER TABLE "${applicationSchema}".outbox_events ADD CONSTRAINT outbox_events_event_type_check CHECK(event_type IN ('pdv.payment.requested.v1','pdv.payment.result.v1','pdv.sale.confirm.v1'));
CREATE FUNCTION "${applicationSchema}".enqueue_sale_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE operation UUID;
BEGIN
    IF NEW.status='PAGAMENTO_APROVADO' AND OLD.status IS DISTINCT FROM NEW.status THEN
        SELECT id INTO operation FROM "${applicationSchema}".payments WHERE sale_id=NEW.id;
        PERFORM "${applicationSchema}".enqueue_business_event('sale-confirm:'||operation,'pdv.sale.confirm.v1',operation,'APROVADO');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER sales_confirmation_outbox AFTER UPDATE ON "${applicationSchema}".sales FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".enqueue_sale_confirmation();
SELECT "${applicationSchema}".enqueue_business_event('sale-confirm:'||p.id,'pdv.sale.confirm.v1',p.id,'APROVADO')
    FROM "${applicationSchema}".payments p JOIN "${applicationSchema}".sales s ON s.id=p.sale_id WHERE s.status='PAGAMENTO_APROVADO';
