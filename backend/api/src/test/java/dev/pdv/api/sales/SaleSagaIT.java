package dev.pdv.api.sales;

import dev.pdv.api.support.MigrationBaseline;
import dev.pdv.api.support.SaleSagaFixture;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleSagaIT {
    @ParameterizedTest @CsvSource({"APROVADO,APROVADO,PAGAMENTO_APROVADO", "RECUSADO,RECUSADO,PAGAMENTO_RECUSADO", "TIMEOUT,INDETERMINADO,AGUARDANDO_PAGAMENTO"})
    void recordsDeterministicOutcomeAndReentersWithoutDuplicatingAnyEffect(String scenario, String outcome, String saleStatus) throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation(scenario, false); assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO"); assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO");
            assertThat(f.result(o.payment(), outcome)).isEqualTo(outcome); assertThat(f.result(o.payment(), outcome)).isEqualTo(outcome); assertThat(f.start(o.payment())).isEqualTo(outcome);
            assertThat(f.jdbc.queryForObject("SELECT status FROM sales WHERE id=?", String.class, o.sale())).isEqualTo(saleStatus);
            assertThat(f.jdbc.queryForObject("SELECT phase FROM sale_sagas WHERE sale_id=?", String.class, o.sale())).isEqualTo(outcome);
            assertThat(f.jdbc.queryForObject("SELECT version FROM sale_sagas WHERE sale_id=?", Long.class, o.sale())).isEqualTo(2);
            assertThat(f.jdbc.queryForObject("SELECT version FROM sales WHERE id=?", Long.class, o.sale())).isEqualTo(2);
            assertThat(f.count("payment_results")).isEqualTo(1); assertThat(f.count("sale_events")).isEqualTo(3);
            assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(10);
            assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(2);
            assertThat(f.jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, o.reservation())).isFalse();
            assertThatThrownBy(() -> f.jdbc.update("UPDATE payment_results SET outcome='RECUSADO' WHERE payment_id=?", o.payment())).isInstanceOf(DataAccessException.class);
        }
    }
    @Test void exhaustiveStateMatrixRequiresPaymentAndRealReservationConfirmation() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var states = List.of("RASCUNHO", "AGUARDANDO_PAGAMENTO", "PAGAMENTO_APROVADO", "PAGAMENTO_RECUSADO", "CONCLUIDA", "CANCELAMENTO_PENDENTE", "CANCELADA");
            for (var from : states) { for (var to : states) { for (var payment : List.of("CRIADO", "EM_PROCESSAMENTO", "APROVADO", "RECUSADO", "INDETERMINADO")) { for (var reservation : List.of("ACTIVE", "EXPIRED", "RELEASED", "CONFIRMED")) {
                boolean expected = (from.equals("AGUARDANDO_PAGAMENTO") && to.equals("AGUARDANDO_PAGAMENTO") && List.of("EM_PROCESSAMENTO", "INDETERMINADO").contains(payment) && reservation.equals("ACTIVE"))
                    || (List.of("AGUARDANDO_PAGAMENTO", "PAGAMENTO_APROVADO").contains(from) && to.equals("PAGAMENTO_APROVADO") && payment.equals("APROVADO") && reservation.equals("ACTIVE"))
                    || (List.of("AGUARDANDO_PAGAMENTO", "PAGAMENTO_RECUSADO").contains(from) && to.equals("PAGAMENTO_RECUSADO") && payment.equals("RECUSADO") && reservation.equals("ACTIVE"))
                    || (List.of("PAGAMENTO_APROVADO", "CONCLUIDA").contains(from) && to.equals("CONCLUIDA") && payment.equals("APROVADO") && reservation.equals("CONFIRMED"));
                assertThat(f.jdbc.queryForObject("SELECT sale_transition_allowed(?,?,?,?)", Boolean.class, from, to, payment, reservation)).as("%s/%s/%s/%s", from, to, payment, reservation).isEqualTo(expected);
            } } } }
            assertThat(f.jdbc.queryForObject("SELECT sale_transition_allowed(NULL,NULL,NULL,NULL)", Boolean.class)).isFalse();
            var o = f.operation("APROVADO", false); f.start(o.payment()); f.result(o.payment(), "APROVADO");
            assertThatThrownBy(() -> f.jdbc.update("UPDATE sales SET status='CONCLUIDA',version=version+1 WHERE id=?", o.sale())).isInstanceOf(DataAccessException.class);
            assertThat(f.jdbc.queryForObject("SELECT status FROM sales WHERE id=?", String.class, o.sale())).isEqualTo("PAGAMENTO_APROVADO");
        }
    }
    @Test void rejectsOutOfOrderConflictingAndForgedTransitions() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", false); assertThat(f.result(o.payment(), "APROVADO")).isEqualTo("NOT_STARTED"); assertThat(f.count("payment_results")).isZero();
            assertThatThrownBy(() -> f.jdbc.update("UPDATE payments SET status='EM_PROCESSAMENTO',version=1 WHERE id=?", o.payment())).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> f.jdbc.update("UPDATE sale_sagas SET phase='APROVADO',version=1 WHERE sale_id=?", o.sale())).isInstanceOf(DataAccessException.class);
            f.start(o.payment()); assertThatThrownBy(() -> f.result(o.payment(), "RECUSADO")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> f.result(o.payment(), null)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> f.jdbc.update("UPDATE payments SET amount=0,version=version+1 WHERE id=?", o.payment())).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> f.jdbc.update("UPDATE sales SET status='CANCELADA',version=version+1 WHERE id=?", o.sale())).isInstanceOf(DataAccessException.class);
            f.result(o.payment(), "APROVADO"); assertThatThrownBy(() -> f.result(o.payment(), "INDETERMINADO")).isInstanceOf(DataAccessException.class);
            assertThat(f.count("payment_results")).isEqualTo(1); assertThat(f.start(UUID.randomUUID())).isEqualTo("NOT_FOUND");
        }
    }
    @Test void expiryWinsBeforePaymentAndCannotBeRevived() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", true); assertThat(f.jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, o.reservation())).isTrue();
            assertThat(f.start(o.payment())).isEqualTo("RESERVATION_UNAVAILABLE"); assertThat(f.count("payment_results")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, o.payment())).isEqualTo("CRIADO");
            assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isZero();
        }
    }
    @Test void committedStartAndUnknownOutcomeProtectPastTtlAndCanResumeFromAnotherConnection() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("TIMEOUT", false); f.jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '2 seconds',version=version+1 WHERE id=?", o.reservation());
            assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO"); Thread.sleep(2100);
            var restarted = f.database.jdbc(); assertThat(restarted.queryForObject("SELECT start_sale_payment(?)", String.class, o.payment())).isEqualTo("EM_PROCESSAMENTO");
            assertThat(restarted.queryForObject("SELECT record_simulated_payment(?,'INDETERMINADO')", String.class, o.payment())).isEqualTo("INDETERMINADO");
            assertThat(restarted.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, o.reservation())).isFalse();
            assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(2);
        }
    }
    @Test void startAuditFailureRollsBackPaymentSagaAndReservationBarrier() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", false); f.jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT fixture_fail_start CHECK(kind<>'PAYMENT_STARTED') NOT VALID");
            assertThatThrownBy(() -> f.start(o.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isFalse();
            assertThat(f.jdbc.queryForObject("SELECT phase FROM sale_sagas WHERE sale_id=?", String.class, o.sale())).isEqualTo("CRIADO");
            assertThat(f.jdbc.queryForObject("SELECT version FROM sales WHERE id=?", Long.class, o.sale())).isZero(); assertThat(f.count("sale_events")).isEqualTo(1);
            f.jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT fixture_fail_start"); assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO");
        }
    }
    @Test void outcomeAuditFailureLeavesProcessingProtectedAndRetryCommitsOnce() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("APROVADO", false); f.start(o.payment()); f.jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT fixture_fail_result CHECK(kind<>'APROVADO') NOT VALID");
            assertThatThrownBy(() -> f.result(o.payment(), "APROVADO")).isInstanceOf(DataAccessException.class); assertThat(f.count("payment_results")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT phase FROM sale_sagas WHERE sale_id=?", String.class, o.sale())).isEqualTo("EM_PROCESSAMENTO");
            assertThat(f.jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isTrue();
            f.jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT fixture_fail_result"); assertThat(f.result(o.payment(), "APROVADO")).isEqualTo("APROVADO"); assertThat(f.count("payment_results")).isEqualTo(1);
        }
    }
    @Test void concurrentCoordinatorsAndDuplicateResultsHaveOneEffect() throws Exception {
        try (var f = new SaleSagaFixture(null)) {
            var o = f.operation("RECUSADO", false); var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<String> task = () -> { start.await(5, TimeUnit.SECONDS); return f.start(o.payment()); };
                var a = pool.submit(task); var b = pool.submit(task); start.countDown(); assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).allMatch(s -> List.of("BUSY", "EM_PROCESSAMENTO").contains(s));
            }
            assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO"); var gate = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<String> task = () -> { gate.await(5, TimeUnit.SECONDS); return f.result(o.payment(), "RECUSADO"); };
                var a = pool.submit(task); var b = pool.submit(task); gate.countDown(); assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).allMatch(s -> List.of("BUSY", "RECUSADO").contains(s));
            }
            assertThat(f.result(o.payment(), "RECUSADO")).isEqualTo("RECUSADO"); assertThat(f.count("sale_events")).isEqualTo(3); assertThat(f.count("payment_results")).isEqualTo(1);
        }
    }
    @Test void upgradePreservesV14SaleAndInitializesSagaThenRebuildsAndReplays() throws Exception {
        try (var f = new SaleSagaFixture("14")) {
            var o = f.operation("APROVADO", false); assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT-14);
            assertThat(f.jdbc.queryForObject("SELECT total FROM sales WHERE id=?", java.math.BigDecimal.class, o.sale())).isEqualByComparingTo("24.68");
            assertThat(f.jdbc.queryForObject("SELECT phase FROM sale_sagas WHERE sale_id=?", String.class, o.sale())).isEqualTo("CRIADO");
            assertThat(f.start(o.payment())).isEqualTo("EM_PROCESSAMENTO"); assertThat(f.database.flyway().migrate().migrationsExecuted).isZero(); f.database.close();
            assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT); assertThat(f.database.jdbc().queryForObject("SELECT count(*) FROM sale_sagas", Integer.class)).isZero();
        }
    }
}
