package dev.pdv.worker.database;

import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Imported only by explicitly enabled database jobs; not component-scanned by default. */
public class WorkerDatabaseConfiguration {
    @Bean
    public DataSource workerDataSource(@Value("${PDV_DB_URL}") String url, @Value("${PDV_DB_USER}") String user,
            @Value("${PDV_DB_PASSWORD}") String password, @Value("${pdv.database.schema:${pdv.reservation-expiry.schema:pdv}}") String schema) {
        if (!schema.matches("[a-z][a-z0-9_]{0,62}")) { throw new IllegalArgumentException("Invalid worker schema"); }
        var source = new DriverManagerDataSource(url, user, password); source.setSchema(schema);
        var properties = new Properties(); properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "10"); source.setConnectionProperties(properties); return source;
    }
    @Bean
    public JdbcTemplate workerJdbc(DataSource source) { var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); return jdbc; }
}
