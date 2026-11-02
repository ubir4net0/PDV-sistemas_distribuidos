package dev.pdv.worker.business;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

public class BusinessConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessConsumer.class);
    private final BusinessInbox inbox;
    private final JsonMapper json;
    public BusinessConsumer(BusinessInbox inbox, JsonMapper json) { this.inbox = inbox; this.json = json; }
    @RabbitListener(id = "business-requests", queues = "#{@businessTopology.requests()}", containerFactory = "businessListenerFactory")
    public void request(Message message, Channel channel) throws IOException { consume(message, channel, BusinessInbox.SIMULATOR, BusinessEnvelope.REQUEST); }
    @RabbitListener(id = "business-results", queues = "#{@businessTopology.results()}", containerFactory = "businessListenerFactory")
    public void result(Message message, Channel channel) throws IOException { consume(message, channel, BusinessInbox.COORDINATOR, BusinessEnvelope.RESULT); }
    private void consume(Message message, Channel channel, String consumer, String expected) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag(); BusinessEnvelope envelope;
        try {
            if (message.getBody().length>4096 || !MessageProperties.CONTENT_TYPE_JSON.equals(message.getMessageProperties().getContentType())) { throw new BusinessEnvelope.Rejected("Invalid encoding"); }
            envelope = json.readValue(message.getBody(), BusinessEnvelope.class);
            if (envelope==null || !(expected.equals(envelope.type()) || (BusinessEnvelope.RESULT.equals(expected) && BusinessEnvelope.CONFIRM.equals(envelope.type())))
                    || !envelope.eventId().toString().equals(message.getMessageProperties().getMessageId())) { throw new BusinessEnvelope.Rejected("Invalid envelope identity"); }
        } catch (RuntimeException invalid) { LOG.warn("Invalid business message sent to DLQ"); channel.basicReject(tag, false); return; }
        for (int attempt = 1; attempt<=3; attempt++) {
            try { inbox.process(consumer, envelope); channel.basicAck(tag, false); return; }
            catch (BusinessEnvelope.Rejected | DataIntegrityViolationException permanent) { break; }
            catch (DataAccessException transientFailure) {
                if (attempt<3) {
                    try { Thread.sleep(100L*attempt); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        LOG.warn("Business message failed; inspect its DLQ before replay"); channel.basicReject(tag, false);
    }
}
