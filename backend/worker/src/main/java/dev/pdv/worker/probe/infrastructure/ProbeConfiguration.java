package dev.pdv.worker.probe.infrastructure;

import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ProbeConfiguration {
    @Bean
    ProbeTopology probeTopology(@Value("${pdv.probe.prefix}") String prefix) {
        return new ProbeTopology(prefix);
    }

    @Bean
    JsonMapper probeJsonMapper() {
        return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Bean
    Declarables probeDeclarations(ProbeTopology names) {
        var exchange = new DirectExchange(names.exchange());
        var errors = new DirectExchange(names.errorsExchange());
        var requests = QueueBuilder.durable(names.requests())
                .deadLetterExchange(names.errorsExchange()).deadLetterRoutingKey("rejected").build();
        var responses = QueueBuilder.durable(names.responses()).build();
        var dead = QueueBuilder.durable(names.dead()).build();
        return new Declarables(exchange, errors, requests, responses, dead,
                BindingBuilder.bind(requests).to(exchange).with("request"),
                BindingBuilder.bind(responses).to(exchange).with("response"),
                BindingBuilder.bind(dead).to(errors).with("rejected"));
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        // CorrelationData tracks returns; never log a returned message body.
        template.setReturnsCallback(returned -> { });
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Never deserialize Java objects based on MIME type or producer headers.
        factory.setMessageConverter(new MessageConverter() {
            @Override
            public Object fromMessage(Message message) { return message.getBody(); }
            @Override
            public Message toMessage(Object value, MessageProperties properties) {
                throw new UnsupportedOperationException("Listener does not serialize replies");
            }
        });
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(true);
        factory.setErrorHandler(error -> {
            LoggerFactory.getLogger(ProbeConfiguration.class).warn("Diagnostic message rejected; inspect its DLQ");
            // Do not chain the original exception: it may embed the request body.
            throw new AmqpRejectAndDontRequeueException("Diagnostic message rejected");
        });
        return factory;
    }
}
