ALTER TABLE "${applicationSchema}".inventory_reservations ADD CONSTRAINT inventory_reservations_id_actor_key UNIQUE(id,actor_id);
CREATE TABLE "${applicationSchema}".sales (
    id UUID PRIMARY KEY,
    number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    cart_id UUID NOT NULL UNIQUE,
    actor_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES "${applicationSchema}".customers(id) ON DELETE RESTRICT,
    reservation_id UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'AGUARDANDO_PAGAMENTO' CHECK (status IN ('AGUARDANDO_PAGAMENTO','PAGAMENTO_APROVADO','CONCLUIDA','PAGAMENTO_RECUSADO','CANCELAMENTO_PENDENTE','CANCELADA')),
    total NUMERIC(60,2) NOT NULL CHECK(total>=0),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(id,actor_id),
    FOREIGN KEY(cart_id,actor_id) REFERENCES "${applicationSchema}".carts(id,actor_id) ON DELETE RESTRICT,
    FOREIGN KEY(reservation_id,actor_id) REFERENCES "${applicationSchema}".inventory_reservations(id,actor_id) ON DELETE RESTRICT
);
CREATE INDEX sales_owner_idx ON "${applicationSchema}".sales(actor_id,created_at,id);
CREATE TABLE "${applicationSchema}".sale_items (
    sale_id UUID NOT NULL REFERENCES "${applicationSchema}".sales(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES "${applicationSchema}".products(id) ON DELETE RESTRICT,
    quantity BIGINT NOT NULL CHECK(quantity>0),
    unit_price NUMERIC(19,2) NOT NULL CHECK(unit_price>=0),
    subtotal NUMERIC(60,2) GENERATED ALWAYS AS (quantity::numeric*unit_price) STORED,
    PRIMARY KEY(sale_id,product_id)
);
CREATE TABLE "${applicationSchema}".payments (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL UNIQUE REFERENCES "${applicationSchema}".sales(id) ON DELETE RESTRICT,
    method VARCHAR(32) NOT NULL CHECK(method IN ('CARTAO_CREDITO','CARTAO_DEBITO','PIX','BOLETO','DINHEIRO')),
    scenario VARCHAR(16) NOT NULL CHECK(scenario IN ('APROVADO','RECUSADO','TIMEOUT')),
    amount NUMERIC(60,2) NOT NULL CHECK(amount>=0),
    status VARCHAR(32) NOT NULL DEFAULT 'CRIADO' CHECK(status IN ('CRIADO','EM_PROCESSAMENTO','APROVADO','RECUSADO','INDETERMINADO')),
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE "${applicationSchema}".payment_intents (
    payment_id UUID PRIMARY KEY REFERENCES "${applicationSchema}".payments(id) ON DELETE RESTRICT,
    payload JSONB NOT NULL CHECK(jsonb_typeof(payload)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE "${applicationSchema}".checkout_operations (
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL CHECK(idempotency_key ~ '^[A-Za-z0-9_.:-]{1,128}$'),
    sale_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    http_status INTEGER NOT NULL CHECK(http_status=201),
    response JSONB NOT NULL CHECK(jsonb_typeof(response)='object'),
    PRIMARY KEY(actor_id,idempotency_key),
    FOREIGN KEY(sale_id,actor_id) REFERENCES "${applicationSchema}".sales(id,actor_id) ON DELETE RESTRICT
);
CREATE TABLE "${applicationSchema}".sale_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES "${applicationSchema}".sales(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL CHECK(version>=0),
    kind VARCHAR(32) NOT NULL CHECK(kind IN ('CHECKOUT')),
    snapshot JSONB NOT NULL CHECK(jsonb_typeof(snapshot)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(sale_id,version)
);
CREATE TRIGGER sales_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".sales FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER payments_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".payments FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER sale_items_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".sale_items FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER payment_intents_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".payment_intents FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER checkout_operations_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".checkout_operations FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE TRIGGER sale_events_immutable BEFORE UPDATE OR DELETE ON "${applicationSchema}".sale_events FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".reject_cart_history_change();
CREATE FUNCTION "${applicationSchema}".guard_checked_out_cart() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cart UUID;
BEGIN
    IF TG_TABLE_NAME='cart_items' THEN
        IF TG_OP='UPDATE' AND NEW.cart_id<>OLD.cart_id THEN RAISE EXCEPTION 'Cart item cannot move' USING ERRCODE='23514'; END IF;
        cart:=CASE WHEN TG_OP='DELETE' THEN OLD.cart_id ELSE NEW.cart_id END;
        PERFORM 1 FROM "${applicationSchema}".carts WHERE id=cart FOR UPDATE;
    ELSE cart:=OLD.id;
    END IF;
    IF EXISTS(SELECT 1 FROM "${applicationSchema}".sales WHERE cart_id=cart) THEN RAISE EXCEPTION 'Cart is checked out' USING ERRCODE='23514'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER carts_checkout_guard BEFORE UPDATE OR DELETE ON "${applicationSchema}".carts FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_checked_out_cart();
CREATE TRIGGER cart_items_checkout_guard BEFORE INSERT OR UPDATE OR DELETE ON "${applicationSchema}".cart_items FOR EACH ROW EXECUTE FUNCTION "${applicationSchema}".guard_checked_out_cart();
