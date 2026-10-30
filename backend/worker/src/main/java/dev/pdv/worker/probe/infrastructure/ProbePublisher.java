package dev.pdv.worker.probe.infrastructure;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import dev.pdv.worker.probe.contract.ProbeResponse;

@Component
class ProbePublisher {
    private final RabbitTemplate template;
    private final JsonMapper mapper;
    private final ProbeTopology names;
    private final long timeoutMs;

    ProbePublisher(RabbitTemplate template, JsonMapper mapper, ProbeTopology names,
            @Value("${pdv.probe.publish-timeout-ms}") long timeoutMs) {
        this.template = template;
        this.mapper = mapper;
        this.names = names;
        this.timeoutMs = timeoutMs;
    }

    void publish(ProbeResponse response) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        properties.setCorrelationId(response.requestId().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        template.send(names.exchange(), "response",
                new Message(mapper.writeValueAsBytes(response), properties), correlation);
        try {
            var confirm = correlation.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new AmqpException("Diagnostic response was not routed and confirmed");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Diagnostic publication interrupted");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new AmqpException("Diagnostic publication outcome unknown");
        }
    }
}
