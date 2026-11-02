CREATE TABLE "${applicationSchema}".inventory_reservations (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','EXPIRED','RELEASED','CONFIRMED')),
    payment_protected BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (expires_at >= created_at),
    CHECK (status <> 'EXPIRED' OR NOT payment_protected)
);
CREATE INDEX inventory_reservations_due_idx ON "${applicationSchema}".inventory_reservations(expires_at,id)
    WHERE status='ACTIVE' AND NOT payment_protected;
CREATE INDEX inventory_reservations_owner_idx ON "${applicationSchema}".inventory_reservations(actor_id,created_at,id);
CREATE TABLE "${applicationSchema}".inventory_reservation_items (
    reservation_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_reservations(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_balances(product_id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id,product_id)
);
CREATE TABLE "${applicationSchema}".inventory_reservation_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_reservations(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL CHECK (version >= 0),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('CREATED','RENEWED','PAYMENT_PROTECTED','EXPIRED')),
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (reservation_id,version)
);
CREATE TABLE "${applicationSchema}".inventory_reservation_operations (
    actor_id UUID NOT NULL REFERENCES "${applicationSchema}".users(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[a-f0-9]{64}$'),
    reservation_id UUID NOT NULL REFERENCES "${applicationSchema}".inventory_reservations(id) ON DELETE RESTRICT,
    response JSONB NOT NULL CHECK (jsonb_typeof(response)='object'),
    PRIMARY KEY (actor_id,request_id)
);
CREATE TRIGGER inventory_reservation_items_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_reservation_items
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
CREATE TRIGGER inventory_reservation_events_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_reservation_events
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
CREATE TRIGGER inventory_reservation_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_reservation_operations
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_inventory_history_change();
CREATE FUNCTION "${applicationSchema}".guard_inventory_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Reservation deletion is forbidden' USING ERRCODE='55000'; END IF;
    IF NEW.id<>OLD.id OR NEW.actor_id<>OLD.actor_id OR NEW.created_at<>OLD.created_at
        OR OLD.status<>'ACTIVE' OR OLD.version=9223372036854775807 OR NEW.version<>OLD.version+1
        OR (OLD.payment_protected AND NOT NEW.payment_protected)
        OR NEW.status NOT IN ('ACTIVE','EXPIRED')
        OR (NEW.status='EXPIRED' AND (NEW.payment_protected OR OLD.expires_at>clock_timestamp()))
        OR (NEW.expires_at<>OLD.expires_at AND (OLD.payment_protected OR OLD.expires_at<=clock_timestamp() OR NEW.status<>'ACTIVE' OR NEW.expires_at<=clock_timestamp()))
    THEN RAISE EXCEPTION 'Invalid reservation transition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER inventory_reservations_guard BEFORE UPDATE OR DELETE ON "${applicationSchema}".inventory_reservations
    FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_inventory_reservation();
CREATE FUNCTION "${applicationSchema}".record_inventory_reservation_event(reservation UUID, event_kind TEXT) RETURNS void LANGUAGE sql AS $$
    INSERT INTO "${applicationSchema}".inventory_reservation_events(reservation_id,version,kind,snapshot)
    SELECT r.id,r.version,event_kind,jsonb_build_object('reservation',to_jsonb(r),'balances',
        (SELECT jsonb_agg(to_jsonb(b) ORDER BY b.product_id) FROM "${applicationSchema}".inventory_balances b
        JOIN "${applicationSchema}".inventory_reservation_items i ON i.product_id=b.product_id WHERE i.reservation_id=r.id))
    FROM "${applicationSchema}".inventory_reservations r WHERE r.id=reservation;
$$;
-- Shared by the API coordinator and the independent expiry worker; invoker privileges only.
CREATE FUNCTION "${applicationSchema}".protect_inventory_reservation(reservation UUID) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE r "${applicationSchema}".inventory_reservations%ROWTYPE;
BEGIN
    SELECT * INTO r FROM "${applicationSchema}".inventory_reservations WHERE id=reservation FOR UPDATE;
    IF NOT FOUND OR r.status<>'ACTIVE' THEN RETURN FALSE; END IF;
    IF r.payment_protected THEN RETURN TRUE; END IF;
    IF r.expires_at<=clock_timestamp() OR r.version=9223372036854775807 THEN RETURN FALSE; END IF;
    UPDATE "${applicationSchema}".inventory_reservations SET payment_protected=TRUE,version=version+1,updated_at=clock_timestamp() WHERE id=reservation;
    PERFORM "${applicationSchema}".record_inventory_reservation_event(reservation,'PAYMENT_PROTECTED');
    RETURN TRUE;
END;
$$;
CREATE FUNCTION "${applicationSchema}".expire_inventory_reservation(reservation UUID) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE r "${applicationSchema}".inventory_reservations%ROWTYPE; item RECORD;
BEGIN
    SELECT * INTO r FROM "${applicationSchema}".inventory_reservations WHERE id=reservation FOR UPDATE SKIP LOCKED;
    IF NOT FOUND OR r.status<>'ACTIVE' OR r.payment_protected OR r.expires_at>clock_timestamp() OR r.version=9223372036854775807 THEN RETURN FALSE; END IF;
    FOR item IN SELECT * FROM "${applicationSchema}".inventory_reservation_items WHERE reservation_id=reservation ORDER BY product_id LOOP
        UPDATE "${applicationSchema}".inventory_balances SET reserved=reserved-item.quantity,version=version+1,updated_at=clock_timestamp()
            WHERE product_id=item.product_id AND reserved>=item.quantity AND version<9223372036854775807;
        IF NOT FOUND THEN RAISE EXCEPTION 'Reservation balance conflict' USING ERRCODE='23514'; END IF;
    END LOOP;
    UPDATE "${applicationSchema}".inventory_reservations SET status='EXPIRED',version=version+1,updated_at=clock_timestamp() WHERE id=reservation;
    PERFORM "${applicationSchema}".record_inventory_reservation_event(reservation,'EXPIRED');
    RETURN TRUE;
END;
$$;
