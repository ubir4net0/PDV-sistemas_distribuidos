package dev.pdv.api.sales;

import dev.pdv.api.support.SaleSagaFixture;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryPersistenceIT {
    @Test void upgradesV18WithoutChangingSaleAndRejectsUnsafeLogs() throws Exception {
        try(var f=new SaleSagaFixture("18")) {
            var operation=f.operation("TIMEOUT",false); assertThat(f.database.configured("19").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT status FROM sales WHERE id=?",String.class,operation.sale())).isEqualTo("AGUARDANDO_PAGAMENTO");
            String trace=operation.payment().toString().replace("-","");
            String sql="SELECT record_operation_telemetry('api',? ,?,? ,NULL,'OK',0)::text";
            assertThat(f.jdbc.queryForObject(sql,String.class,"api_checkout",trace,trace.substring(0,16))).contains("traceId","spanId");
            assertThatThrownBy(()->f.jdbc.queryForObject(sql,String.class,"payload injected",trace,trace.substring(0,16))).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.update("UPDATE operation_telemetry SET outcome='REPLAY'")).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(()->f.jdbc.update("DELETE FROM operation_telemetry")).isInstanceOf(DataAccessException.class);
            assertThat(f.database.configured("19").migrate().migrationsExecuted).isZero();
        }
    }
}
