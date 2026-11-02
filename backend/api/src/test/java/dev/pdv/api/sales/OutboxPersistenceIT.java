package dev.pdv.api.sales;

import dev.pdv.api.support.MigrationBaseline;
import dev.pdv.api.support.SaleSagaFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPersistenceIT {
    @Test void upgradeBackfillsIntentWithStableIdentityAndRebuildsWithoutEditingPriorMigrations() throws Exception {
        try (var f = new SaleSagaFixture("15")) {
            var o = f.operation("APROVADO", false); assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT-15);
            UUID event = f.jdbc.queryForObject("SELECT id FROM outbox_events WHERE payment_id=?", UUID.class, o.payment());
            assertThat(f.jdbc.queryForObject("SELECT enqueue_business_event(?,'pdv.payment.requested.v1',?,NULL)", UUID.class, "payment-request:"+o.payment(), o.payment())).isEqualTo(event);
            assertThat(f.count("outbox_deliveries")).isEqualTo(1);
            assertThatThrownBy(() -> f.jdbc.update("DELETE FROM outbox_events WHERE id=?", event)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> f.jdbc.queryForObject("SELECT enqueue_business_event(?,'pdv.payment.result.v1',?,'APROVADO')", UUID.class, "payment-request:"+o.payment(), o.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.database.flyway().migrate().migrationsExecuted).isZero(); f.database.close();
            assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT); assertThat(f.database.jdbc().queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
        }
    }
    @Test void preparationPersistsDecisionAndResponseIntentBeforeCoordinatorAppliesResult() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", false); assertThat(f.count("outbox_events")).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, o.payment())).isEqualTo("APROVADO");
            assertThat(f.count("simulation_decisions")).isEqualTo(1); assertThat(f.count("outbox_events")).isEqualTo(2); assertThat(f.count("payment_results")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, o.payment())).isEqualTo("EM_PROCESSAMENTO");
            assertThat(f.jdbc.queryForObject("SELECT apply_business_payment_result(?,'APROVADO')", String.class, o.payment())).isEqualTo("APROVADO");
            assertThat(f.jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, o.payment())).isEqualTo("APROVADO");
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type IN ('pdv.payment.requested.v1','pdv.payment.result.v1')", Long.class)).isEqualTo(2);
            assertThat(f.count("outbox_events")).isEqualTo(3); assertThat(f.count("payment_results")).isEqualTo(1); assertThat(f.count("sale_events")).isEqualTo(3);
            assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(10);
        }
    }
    @Test void missingResponseIntentRollsBackDecisionStartAndBarrierThenRetries() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("TIMEOUT", false); f.jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT fixture_fail_response CHECK(event_type<>'pdv.payment.result.v1') NOT VALID");
            assertThatThrownBy(() -> f.jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, o.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.count("simulation_decisions")).isZero(); assertThat(f.count("outbox_events")).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isFalse();
            assertThat(f.jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, o.payment())).isEqualTo("CRIADO");
            f.jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT fixture_fail_response");
            assertThat(f.jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, o.payment())).isEqualTo("INDETERMINADO");
        }
    }
    @Test void unprovenResultIsRejectedAndExpiredRequestDoesNotChargeOrRevive() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", true);
            assertThatThrownBy(() -> f.jdbc.queryForObject("SELECT apply_business_payment_result(?,'APROVADO')", String.class, o.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, o.payment())).isEqualTo("IGNORED");
            assertThat(f.count("simulation_decisions")).isZero(); assertThat(f.count("payment_results")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isFalse();
        }
    }
}
