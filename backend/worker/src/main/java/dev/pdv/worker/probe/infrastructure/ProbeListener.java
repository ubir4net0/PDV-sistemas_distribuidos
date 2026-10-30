package dev.pdv.worker.probe.infrastructure;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import dev.pdv.worker.probe.contract.ProbeRequest;
import dev.pdv.worker.probe.contract.ProbeResponse;

@Component
class ProbeListener {
    private final JsonMapper mapper;
    private final ProbePublisher publisher;

    ProbeListener(JsonMapper mapper, ProbePublisher publisher) {
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @RabbitListener(id = "probe-listener", queues = "#{@probeTopology.requests()}")
    void consume(Message message) {
        if (message.getBody().length > 4096
                || !MessageProperties.CONTENT_TYPE_JSON.equals(message.getMessageProperties().getContentType())) {
            throw new AmqpRejectAndDontRequeueException("Invalid diagnostic encoding or size");
        }
        ProbeRequest request;
        try {
            // Fixed target type; producer-supplied Java type headers are not used.
            request = mapper.readValue(message.getBody(), ProbeRequest.class);
        } catch (RuntimeException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid diagnostic JSON");
        }
        if (request == null) {
            throw new AmqpRejectAndDontRequeueException("Missing diagnostic request");
        }
        publisher.publish(ProbeResponse.from(request));
    }
}
