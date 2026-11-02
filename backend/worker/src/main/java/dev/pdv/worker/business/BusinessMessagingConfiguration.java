package dev.pdv.worker.business;

import dev.pdv.worker.database.WorkerDatabaseConfiguration;
import javax.sql.DataSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pdv.business.enabled", havingValue = "true")
@EnableScheduling
@Import(WorkerDatabaseConfiguration.class)
public class BusinessMessagingConfiguration {
    @Bean
    public BusinessTopology businessTopology(@Value("${pdv.business.prefix:pdv.business.payments}") String prefix, @Value("${pdv.simulated-payments.enabled:false}") boolean polling) {
        if (polling) { throw new IllegalStateException("Choose business messaging or local payment polling"); }
        return new BusinessTopology(prefix);
    }
    @Bean
    public Declarables businessDeclarations(BusinessTopology names) {
        var exchange = new DirectExchange(names.exchange()); var errors = new DirectExchange(names.errors());
        var requests = QueueBuilder.durable(names.requests()).deadLetterExchange(names.errors()).deadLetterRoutingKey("rejected").build();
        var results = QueueBuilder.durable(names.results()).deadLetterExchange(names.errors()).deadLetterRoutingKey("rejected").build(); var dead = QueueBuilder.durable(names.dead()).build();
        return new Declarables(exchange, errors, requests, results, dead, BindingBuilder.bind(requests).to(exchange).with("payment.request"), BindingBuilder.bind(results).to(exchange).with("payment.result"), BindingBuilder.bind(dead).to(errors).with("rejected"));
    }
    @Bean
    public SimpleRabbitListenerContainerFactory businessListenerFactory(ConnectionFactory source, @Value("${pdv.business.consumers-auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(source); factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(1); factory.setPrefetchCount(1); factory.setAutoStartup(autoStartup); factory.setDefaultRequeueRejected(false); factory.setMissingQueuesFatal(true);
        factory.setMessageConverter(new MessageConverter() {
            @Override public Object fromMessage(Message message) { return message.getBody(); }
            @Override public Message toMessage(Object value, MessageProperties properties) { throw new UnsupportedOperationException("No listener replies"); }
        });
        factory.setErrorHandler(failure -> { throw new AmqpRejectAndDontRequeueException("Business listener failure"); }); return factory;
    }
    @Bean
    public BusinessInbox businessInbox(JdbcTemplate jdbc, DataSource source) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regprocedure('prepare_business_payment(uuid)') IS NOT NULL AND to_regprocedure('apply_business_payment_result(uuid,text)') IS NOT NULL", Boolean.class))) { throw new IllegalStateException("Run API outbox migrations first"); }
        return new BusinessInbox(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)));
    }
    @Bean public BusinessConsumer businessConsumer(BusinessInbox inbox, JsonMapper json) { return new BusinessConsumer(inbox, json); }
    @Bean public ConfirmedBusinessPublisher confirmedBusinessPublisher(RabbitTemplate template, BusinessTopology names, JsonMapper json, @Value("${pdv.business.publish-timeout-ms:5000}") long timeout) { return new ConfirmedBusinessPublisher(template, names, json, timeout); }
    @Bean public BusinessOutbox businessOutbox(JdbcTemplate jdbc, DataSource source, ConfirmedBusinessPublisher sender, JsonMapper json, @Value("${pdv.business.batch-size:10}") int batch) { return new BusinessOutbox(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), sender, json, batch); }
}
