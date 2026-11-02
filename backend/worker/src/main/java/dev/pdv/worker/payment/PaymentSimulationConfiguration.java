package dev.pdv.worker.payment;

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
@ConditionalOnProperty(name = "pdv.simulated-payments.enabled", havingValue = "true")
@EnableScheduling
@Import(WorkerDatabaseConfiguration.class)
public class PaymentSimulationConfiguration {
    @Bean
    PaymentSimulation paymentSimulation(JdbcTemplate jdbc, DataSource source, @Value("${pdv.simulated-payments.batch-size:100}") int batchSize) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regprocedure('start_sale_payment(uuid)') IS NOT NULL AND to_regprocedure('record_simulated_payment(uuid,text)') IS NOT NULL", Boolean.class))) {
            throw new IllegalStateException("Run API migrations before enabling simulated payments");
        }
        LoggerFactory.getLogger(PaymentSimulationConfiguration.class).info("Local simulated payments enabled; no financial provider");
        return new PaymentSimulation(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), batchSize);
    }
}
