package dev.pdv.api.sales;

import dev.pdv.api.support.MigrationBaseline;
import dev.pdv.api.support.SaleSagaFixture;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentReconciliationIT {
    private String release(SaleSagaFixture f, UUID payment) { return f.jdbc.queryForObject("SELECT release_business_sale(?)",String.class,payment); }
    private void result(SaleSagaFixture f, UUID payment, String outcome) {
        assertThat(f.jdbc.queryForObject("SELECT prepare_business_payment(?)",String.class,payment)).isEqualTo(outcome);
        assertThat(f.jdbc.queryForObject("SELECT apply_business_payment_result(?,?)",String.class,payment,outcome)).isEqualTo(outcome);
    }
    private void decision(SaleSagaFixture f, UUID payment, String outcome) { f.jdbc.update("INSERT INTO provider_resolutions(payment_id,outcome,reason,actor_id) SELECT p.id,?,'Synthetic decision',actor_id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE p.id=?",outcome,payment); }
    private String apply(SaleSagaFixture f, UUID payment, String outcome) { return f.jdbc.queryForObject("SELECT apply_business_reconciliation(?,?)",String.class,payment,outcome); }
    @Test void upgradePreservesUnknownAndOnlyEnqueuesRefusalThenRebuildReplayAreSafe() throws Exception {
        try(var f=new SaleSagaFixture("17")) {
            var refused=f.operation("RECUSADO",false); result(f,refused.payment(),"RECUSADO"); var unknown=f.operation("TIMEOUT",false); result(f,unknown.payment(),"INDETERMINADO");
            assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT-17);
            assertThat(f.count("provider_resolutions")).isZero(); assertThat(f.count("reservation_releases")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT sum(reserved) FROM inventory_balances",Long.class)).isEqualTo(4);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='pdv.sale.release.v1'",Long.class)).isEqualTo(1);
            assertThat(release(f,refused.payment())).isEqualTo("RELEASED"); assertThatThrownBy(()->release(f,unknown.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.database.flyway().migrate().migrationsExecuted).isZero(); f.database.close(); assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT);
        }
    }
    @Test void refusalReleasesReservedOnlyOnceAndAllHistoryIsImmutable() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("RECUSADO",false); result(f,o.payment(),"RECUSADO"); assertThat(release(f,o.payment())).isEqualTo("RELEASED"); assertThat(release(f,o.payment())).isEqualTo("RELEASED");
            assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isEqualTo(10);
            assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isZero();
            assertThat(f.jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?",Boolean.class,o.reservation())).isTrue();
            assertThat(f.count("reservation_releases")).isEqualTo(1); assertThat(f.count("reservation_release_items")).isEqualTo(1); assertThat(f.count("receipts")).isZero();
            for(var table:List.of("reservation_releases","reservation_release_items")) { assertThatThrownBy(()->f.jdbc.update("DELETE FROM "+table)).isInstanceOf(DataAccessException.class); }
            assertThat(f.jdbc.queryForObject("SELECT expire_inventory_reservation(?)",Boolean.class,o.reservation())).isFalse();
        }
    }
    @Test void unknownStaysProtectedPastDeadlineAndCannotBeForgedIntoARelease() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("TIMEOUT",false); result(f,o.payment(),"INDETERMINADO");
            f.jdbc.execute("ALTER TABLE inventory_reservations DISABLE TRIGGER inventory_reservations_guard");
            try { f.jdbc.update("UPDATE inventory_reservations SET created_at=clock_timestamp()-interval '3 seconds',expires_at=clock_timestamp()-interval '1 second' WHERE id=?",o.reservation()); }
            finally { f.jdbc.execute("ALTER TABLE inventory_reservations ENABLE TRIGGER inventory_reservations_guard"); }
            assertThat(f.jdbc.queryForObject("SELECT expire_inventory_reservation(?)",Boolean.class,o.reservation())).isFalse();
            assertThatThrownBy(()->release(f,o.payment())).isInstanceOf(DataAccessException.class); assertThatThrownBy(()->apply(f,o.payment(),"RECUSADO")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.update("UPDATE payments SET status='RECUSADO',version=version+1 WHERE id=?",o.payment())).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.update("UPDATE inventory_reservations SET status='RELEASED',version=version+1 WHERE id=?",o.reservation())).isInstanceOf(DataAccessException.class);
            assertThat(f.count("reservation_releases")).isZero(); assertThat(f.count("provider_resolutions")).isZero();
        }
    }
    @Test void bothLateOutcomesPreserveOriginalUnknownAndFinishThroughExistingSteps() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            for(String outcome:List.of("APROVADO","RECUSADO")) {
                var o=f.operation("TIMEOUT",false); result(f,o.payment(),"INDETERMINADO"); decision(f,o.payment(),outcome);
                assertThat(apply(f,o.payment(),outcome)).isEqualTo(outcome); assertThat(apply(f,o.payment(),outcome)).isEqualTo(outcome);
                if(outcome.equals("APROVADO")) { assertThat(f.jdbc.queryForObject("SELECT confirm_business_sale(?)",String.class,o.payment())).isEqualTo("CONCLUIDA"); }
                else { assertThat(release(f,o.payment())).isEqualTo("RELEASED"); }
                assertThat(f.jdbc.queryForObject("SELECT apply_business_payment_result(?,'INDETERMINADO')",String.class,o.payment())).isEqualTo("INDETERMINADO");
                assertThat(f.jdbc.queryForObject("SELECT outcome FROM payment_results WHERE payment_id=?",String.class,o.payment())).isEqualTo("INDETERMINADO");
                assertThatThrownBy(()->apply(f,o.payment(),outcome.equals("APROVADO")?"RECUSADO":"APROVADO")).isInstanceOf(DataAccessException.class);
            }
            assertThat(f.count("receipts")).isEqualTo(1); assertThat(f.count("reservation_releases")).isEqualTo(1);
            assertThatThrownBy(()->f.jdbc.update("UPDATE provider_resolutions SET reason='overwrite'")).isInstanceOf(DataAccessException.class);
        }
    }
    @Test void resolutionRequiresUnknownAndItsOutboxFailureRollsBackProof() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("TIMEOUT",false); assertThatThrownBy(()->decision(f,o.payment(),"APROVADO")).isInstanceOf(DataAccessException.class); result(f,o.payment(),"INDETERMINADO");
            f.jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT fixture_no_reconcile CHECK(event_type<>'pdv.payment.reconciled.v1') NOT VALID");
            try { assertThatThrownBy(()->decision(f,o.payment(),"APROVADO")).isInstanceOf(DataAccessException.class); assertThat(f.count("provider_resolutions")).isZero(); }
            finally { f.jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT fixture_no_reconcile"); }
            decision(f,o.payment(),"APROVADO"); assertThat(f.count("provider_resolutions")).isEqualTo(1);
        }
    }
    @Test void releaseAuditOrItemFailureRollsBackGroupAndEveryBalance() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("RECUSADO",false); result(f,o.payment(),"RECUSADO");
            for(var fault:List.of("reservation_release_items|FALSE","inventory_reservation_events|kind<>'RELEASED'","sale_sagas|reservation_released_at IS NULL")) {
                var part=fault.split("\\|"); f.jdbc.execute("ALTER TABLE "+part[0]+" ADD CONSTRAINT fixture_fail CHECK("+part[1]+") NOT VALID");
                try { assertThatThrownBy(()->release(f,o.payment())).isInstanceOf(DataAccessException.class); assertThat(f.count("reservation_releases")).isZero(); assertThat(f.count("reservation_release_items")).isZero();
                    assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isEqualTo(2);
                    assertThat(f.jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE id=?",String.class,o.reservation())).isEqualTo("ACTIVE");
                } finally { f.jdbc.execute("ALTER TABLE "+part[0]+" DROP CONSTRAINT fixture_fail"); }
            }
            assertThat(release(f,o.payment())).isEqualTo("RELEASED");
        }
    }
    @Test void failedLateApprovalAuditRollsBackPaymentSaleAndSagaButRetainsDecision() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("TIMEOUT",false); result(f,o.payment(),"INDETERMINADO"); decision(f,o.payment(),"APROVADO");
            f.jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT fixture_fail CHECK(kind<>'APROVADO') NOT VALID");
            try { assertThatThrownBy(()->apply(f,o.payment(),"APROVADO")).isInstanceOf(DataAccessException.class);
                assertThat(f.jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,o.payment())).isEqualTo("INDETERMINADO"); assertThat(f.count("provider_resolutions")).isEqualTo(1);
                assertThat(f.jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='pdv.sale.confirm.v1'",Long.class)).isZero();
            } finally { f.jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT fixture_fail"); }
            assertThat(apply(f,o.payment(),"APROVADO")).isEqualTo("APROVADO");
        }
    }
    @Test void concurrentReleaseIsUniqueAndExpiryCannotFreeUnknown() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("RECUSADO",false); result(f,o.payment(),"RECUSADO"); var gate=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return release(f,o.payment());}); var b=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return release(f,o.payment());});gate.countDown();
                assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).contains("RELEASED").allMatch(v->v.equals("RELEASED")||v.equals("BUSY"));
            }
            assertThat(release(f,o.payment())).isEqualTo("RELEASED"); assertThat(f.count("reservation_releases")).isEqualTo(1);
        }
    }
    @Test void exhaustedSecondBalanceCannotPartiallyReleaseAMultipleItemSale() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("RECUSADO",false); UUID second=f.operation("RECUSADO",false).product();
            f.jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,2,0)",o.sale(),second);
            f.jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,2)",o.reservation(),second);
            f.jdbc.update("UPDATE inventory_balances SET reserved=4 WHERE product_id=?",second);
            UUID last=f.jdbc.queryForObject("SELECT product_id FROM sale_items WHERE sale_id=? ORDER BY product_id DESC LIMIT 1",UUID.class,o.sale());
            result(f,o.payment(),"RECUSADO"); f.jdbc.update("UPDATE inventory_balances SET version=9223372036854775807 WHERE product_id=?",last);
            assertThatThrownBy(()->release(f,o.payment())).isInstanceOf(DataAccessException.class);
            assertThat(f.count("reservation_releases")).isZero(); assertThat(f.count("reservation_release_items")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT sum(reserved) FROM inventory_balances",Long.class)).isEqualTo(6);
        }
    }
}
