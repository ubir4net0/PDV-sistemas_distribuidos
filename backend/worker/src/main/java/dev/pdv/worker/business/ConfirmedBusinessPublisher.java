package dev.pdv.worker.business;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

public class ConfirmedBusinessPublisher implements BusinessOutbox.Sender {
    private final RabbitTemplate template;
    private final BusinessTopology names;
    private final JsonMapper json;
    private final long timeout;
    public ConfirmedBusinessPublisher(RabbitTemplate template, BusinessTopology names, JsonMapper json, long timeout) {
        if (timeout<1 || timeout>10000) { throw new IllegalArgumentException("Invalid confirm timeout"); }
        this.template = template; this.names = names; this.json = json; this.timeout = timeout;
    }
    @Override public void send(BusinessEnvelope envelope) {
        var p = new MessageProperties(); p.setContentType(MessageProperties.CONTENT_TYPE_JSON); p.setContentEncoding("UTF-8"); p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        p.setMessageId(envelope.eventId().toString()); p.setCorrelationId(envelope.paymentId().toString()); var correlation = new CorrelationData(envelope.eventId().toString());
        template.send(names.exchange(), BusinessEnvelope.REQUEST.equals(envelope.type()) ? "payment.request" : "payment.result", new Message(json.writeValueAsBytes(envelope), p), correlation);
        try {
            var result = correlation.getFuture().get(timeout, TimeUnit.MILLISECONDS);
            if (!result.ack() || correlation.getReturned()!=null) { throw new AmqpException("Business publication unconfirmed or unrouted"); }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AmqpException("Business publication interrupted"); }
        catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) { throw new AmqpException("Business publication outcome unknown"); }
    }
}
