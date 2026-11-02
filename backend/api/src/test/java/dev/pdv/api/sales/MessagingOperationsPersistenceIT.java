package dev.pdv.api.sales;

import dev.pdv.api.support.SaleSagaFixture;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingOperationsPersistenceIT {
    @Test void upgradesV19PreservingCanonicalDeliveryAndConstrainsQueueObservations() throws Exception {
        try(var f=new SaleSagaFixture("19")) {
            var operation=f.operation("TIMEOUT",false); String before=f.jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE payment_id=?",String.class,operation.payment());
            assertThat(f.database.configured("20").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE payment_id=?",String.class,operation.payment())).isEqualTo(before);
            f.jdbc.update("INSERT INTO messaging_queue_samples(namespace,kind,available) VALUES ('pdv.test.fixture','DEAD',false)");
            assertThatThrownBy(()->f.jdbc.update("UPDATE messaging_queue_samples SET ready=0 WHERE NOT available")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.update("INSERT INTO messaging_queue_samples(namespace,kind,available,ready,consumers) VALUES ('unowned','DEAD',true,0,0)")).isInstanceOf(DataAccessException.class);
            assertThat(f.database.configured("20").migrate().migrationsExecuted).isZero();
        }
    }
}
