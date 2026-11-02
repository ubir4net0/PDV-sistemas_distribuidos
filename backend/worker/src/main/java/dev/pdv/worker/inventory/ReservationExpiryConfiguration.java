package dev.pdv.worker.inventory;

import dev.pdv.worker.database.WorkerDatabaseConfiguration;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pdv.reservation-expiry.enabled", havingValue = "true")
@EnableScheduling
@Import(WorkerDatabaseConfiguration.class)
public class ReservationExpiryConfiguration {
    @Bean
    ReservationExpiry reservationExpiry(JdbcTemplate jdbc, DataSource source, @Value("${pdv.reservation-expiry.batch-size:100}") int batchSize) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regprocedure('expire_inventory_reservation(uuid)') IS NOT NULL", Boolean.class))) {
            throw new IllegalStateException("Run API migrations before enabling reservation expiry");
        }
        LoggerFactory.getLogger(ReservationExpiryConfiguration.class).info("Reservation expiry enabled; transactional database guard required");
        return new ReservationExpiry(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), batchSize);
    }
}
