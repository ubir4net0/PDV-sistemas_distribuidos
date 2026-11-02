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

class SaleConfirmationIT {
    private String confirm(SaleSagaFixture f, UUID payment) { return f.jdbc.queryForObject("SELECT confirm_business_sale(?)", String.class, payment); }
    private void approve(SaleSagaFixture f, UUID payment) { assertThat(f.start(payment)).isEqualTo("EM_PROCESSAMENTO"); assertThat(f.result(payment, "APROVADO")).isEqualTo("APROVADO"); }
    @Test void upgradeCreatesIntentWithoutStockEffectAndRebuildReplayAreSafe() throws Exception {
        try (var f = new SaleSagaFixture("16")) {
            var o=f.operation("APROVADO", false); approve(f,o.payment());
            assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT-16);
            assertThat(f.count("receipts")).isZero(); assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class,o.product())).isEqualTo(10);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='pdv.sale.confirm.v1'", Long.class)).isEqualTo(1);
            assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA"); assertThat(f.database.flyway().migrate().migrationsExecuted).isZero();
            f.database.close(); assertThat(f.database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT); assertThat(f.database.jdbc().queryForObject("SELECT count(*) FROM receipts", Long.class)).isZero();
        }
    }
    @Test void confirmsOnceWithStableImmutableReceiptAndHistoryPreservingAvailable() throws Exception {
        try (var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); approve(f,o.payment()); assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA");
            String receipt=f.jdbc.queryForObject("SELECT snapshot::text FROM receipts",String.class);
            assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA"); assertThat(f.jdbc.queryForObject("SELECT snapshot::text FROM receipts",String.class)).isEqualTo(receipt);
            assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isEqualTo(8);
            assertThat(f.jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isZero();
            assertThat(f.jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE id=?",String.class,o.reservation())).isEqualTo("CONFIRMED");
            assertThat(f.count("sale_stock_movements")).isEqualTo(1); assertThat(f.count("inventory_movement_history")).isEqualTo(1); assertThat(f.count("sale_events")).isEqualTo(4);
            for (var table:List.of("receipts","sale_confirmations","sale_stock_movements")) { assertThatThrownBy(()->f.jdbc.update("DELETE FROM "+table)).isInstanceOf(DataAccessException.class); }
            assertThatThrownBy(()->f.jdbc.update("UPDATE sale_sagas SET version=version+1 WHERE sale_id=?",o.sale())).isInstanceOf(DataAccessException.class);
        }
    }
    @Test void deadlineCannotExpireAProtectedApproval() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); approve(f,o.payment());
            f.jdbc.execute("ALTER TABLE inventory_reservations DISABLE TRIGGER inventory_reservations_guard");
            try { f.jdbc.update("UPDATE inventory_reservations SET created_at=clock_timestamp()-interval '3 seconds',expires_at=clock_timestamp()-interval '1 second' WHERE id=?",o.reservation()); }
            finally { f.jdbc.execute("ALTER TABLE inventory_reservations ENABLE TRIGGER inventory_reservations_guard"); }
            assertThat(f.jdbc.queryForObject("SELECT expire_inventory_reservation(?)",Boolean.class,o.reservation())).isFalse(); assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA");
        }
    }
    @Test void noApprovalProofOrInvalidReservationCannotConfirm() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            for(String scenario:List.of("APROVADO","RECUSADO","TIMEOUT")) {
                var o=f.operation(scenario,false); assertThatThrownBy(()->confirm(f,o.payment())).isInstanceOf(DataAccessException.class);
                if(!scenario.equals("APROVADO")) { f.start(o.payment()); f.result(o.payment(),scenario.equals("TIMEOUT")?"INDETERMINADO":scenario); assertThatThrownBy(()->confirm(f,o.payment())).isInstanceOf(DataAccessException.class); }
            }
            var o=f.operation("APROVADO",false); approve(f,o.payment());
            f.jdbc.execute("ALTER TABLE inventory_reservations DISABLE TRIGGER inventory_reservations_guard");
            try { for(String state:List.of("EXPIRED","RELEASED")) {
                f.jdbc.update("UPDATE inventory_reservations SET status=?,payment_protected=false WHERE id=?",state,o.reservation());
                assertThatThrownBy(()->confirm(f,o.payment())).isInstanceOf(DataAccessException.class);
            } } finally { f.jdbc.execute("ALTER TABLE inventory_reservations ENABLE TRIGGER inventory_reservations_guard"); }
            assertThat(f.count("receipts")).isZero(); assertThat(f.count("sale_stock_movements")).isZero();
        }
    }
    @Test void receiptMovementReservationAndSaleAuditFailuresRollbackEverything() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); approve(f,o.payment());
            for(var fault:List.of("receipts|FALSE","sale_stock_movements|FALSE","inventory_reservation_events|kind<>'CONFIRMED'","sale_events|kind<>'CONCLUDED'")) {
                var parts=fault.split("\\|"); f.jdbc.execute("ALTER TABLE "+parts[0]+" ADD CONSTRAINT fixture_fail CHECK("+parts[1]+") NOT VALID");
                try { assertThatThrownBy(()->confirm(f,o.payment())).isInstanceOf(DataAccessException.class);
                    assertThat(f.count("receipts")).isZero(); assertThat(f.count("sale_confirmations")).isZero(); assertThat(f.count("sale_stock_movements")).isZero();
                    assertThat(f.jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?",Long.class,o.product())).isEqualTo(10);
                    assertThat(f.jdbc.queryForObject("SELECT status FROM sales WHERE id=?",String.class,o.sale())).isEqualTo("PAGAMENTO_APROVADO");
                } finally { f.jdbc.execute("ALTER TABLE "+parts[0]+" DROP CONSTRAINT fixture_fail"); }
            }
            assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA");
        }
    }
    @Test void concurrentConfirmationsAndLatePaymentResultDoNotDuplicateOrRegress() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); approve(f,o.payment()); var gate=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return confirm(f,o.payment());}); var b=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return confirm(f,o.payment());}); gate.countDown();
                assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).contains("CONCLUIDA").allMatch(v->v.equals("CONCLUIDA")||v.equals("BUSY"));
            }
            assertThat(confirm(f,o.payment())).isEqualTo("CONCLUIDA"); assertThat(f.result(o.payment(),"APROVADO")).isEqualTo("APROVADO"); assertThat(f.count("receipts")).isEqualTo(1); assertThat(f.count("sale_stock_movements")).isEqualTo(1);
        }
    }
    @Test void exhaustedSecondBalanceRollsBackFirstProductAndReceipt() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); UUID second=f.operation("APROVADO",false).product();
            // Owned fixture only: attach a second item before confirmation; preserve total/payment identity.
            f.jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,2,0)",o.sale(),second);
            f.jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,2)",o.reservation(),second);
            UUID last=f.jdbc.queryForObject("SELECT product_id FROM sale_items WHERE sale_id=? ORDER BY product_id DESC LIMIT 1",UUID.class,o.sale());
            approve(f,o.payment()); f.jdbc.update("UPDATE inventory_balances SET version=9223372036854775807 WHERE product_id=?",last);
            assertThatThrownBy(()->confirm(f,o.payment())).isInstanceOf(DataAccessException.class); assertThat(f.count("receipts")).isZero(); assertThat(f.count("sale_stock_movements")).isZero();
            assertThat(f.jdbc.queryForObject("SELECT min(physical) FROM inventory_balances",Long.class)).isEqualTo(10);
        }
    }
    @Test void reservationCannotBeConfirmedWithoutDurableEffects() throws Exception {
        try(var f=new SaleSagaFixture(null)) {
            var o=f.operation("APROVADO",false); approve(f,o.payment());
            assertThatThrownBy(()->f.jdbc.update("UPDATE inventory_reservations SET status='CONFIRMED',version=version+1 WHERE id=?",o.reservation())).isInstanceOf(DataAccessException.class);
        }
    }
}
