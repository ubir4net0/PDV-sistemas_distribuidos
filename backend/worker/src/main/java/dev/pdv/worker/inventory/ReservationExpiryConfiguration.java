package dev.pdv.worker.inventory;

import java.util.Properties;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pdv.reservation-expiry.enabled", havingValue = "true")
@EnableScheduling
public class ReservationExpiryConfiguration {
    @Bean
    DataSource reservationDataSource(@Value("${PDV_DB_URL}") String url, @Value("${PDV_DB_USER}") String user,
            @Value("${PDV_DB_PASSWORD}") String password, @Value("${pdv.reservation-expiry.schema:pdv}") String schema) {
        if (!schema.matches("[a-z][a-z0-9_]{0,62}")) { throw new IllegalArgumentException("Invalid reservation schema"); }
        var source = new DriverManagerDataSource(url, user, password); source.setSchema(schema);
        var properties = new Properties(); properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "10"); source.setConnectionProperties(properties);
        return source;
    }
    @Bean
    JdbcTemplate reservationJdbc(DataSource source, @Value("${pdv.reservation-expiry.schema:pdv}") String schema) {
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regprocedure(?) IS NOT NULL", Boolean.class, schema + ".expire_inventory_reservation(uuid)"))) {
            throw new IllegalStateException("Run API migrations before enabling reservation expiry");
        }
        return jdbc;
    }
    @Bean
    ReservationExpiry reservationExpiry(JdbcTemplate jdbc, DataSource source, @Value("${pdv.reservation-expiry.batch-size:100}") int batchSize) {
        LoggerFactory.getLogger(ReservationExpiryConfiguration.class).info("Reservation expiry enabled; transactional database guard required");
        return new ReservationExpiry(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), batchSize);
    }
}
