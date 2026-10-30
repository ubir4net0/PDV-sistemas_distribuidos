package dev.pdv.worker.probe.infrastructure;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;
import dev.pdv.worker.probe.contract.ProbeResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ProbePublisherTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProbeTopology names = new ProbeTopology("pdv.test.publisher");
    private final ProbeResponse response = new ProbeResponse("pdv.probe.response.v1", UUID.randomUUID(), "unit");
    private final ProbePublisher publisher = new ProbePublisher(template, mapper, names, 50);

    @Test
    void publishesPersistentJsonWithRequestCorrelationAndWaitsForAck() {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            var properties = message.getMessageProperties();
            assertThat(mapper.readValue(message.getBody(), ProbeResponse.class)).isEqualTo(response);
            assertThat(properties.getCorrelationId()).isEqualTo(response.requestId().toString());
            assertThat(properties.getContentType()).isEqualTo("application/json");
            assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq(names.exchange()), eq("response"), any(Message.class), any(CorrelationData.class));
        assertThatCode(() -> publisher.publish(response)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeConfirmation() {
        onSend(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack")));
        assertThatThrownBy(() -> publisher.publish(response))
                .isInstanceOf(AmqpException.class).hasMessage("Diagnostic response was not routed and confirmed");
    }

    @Test
    void rejectsReturnedMessageEvenWithPositiveConfirmation() {
        onSend(correlation -> {
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312,
                    "NO_ROUTE", names.exchange(), "response"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        });
        assertThatThrownBy(() -> publisher.publish(response))
                .isInstanceOf(AmqpException.class).hasMessage("Diagnostic response was not routed and confirmed");
    }

    @Test
    void hidesUnderlyingFailureWhenConfirmationCompletesExceptionally() {
        onSend(correlation -> correlation.getFuture().completeExceptionally(new IllegalStateException("private body")));
        assertThatThrownBy(() -> publisher.publish(response)).isInstanceOf(AmqpException.class)
                .hasMessage("Diagnostic publication outcome unknown").hasNoCause();
    }

    @Test
    @Timeout(10)
    void treatsMissingConfirmationAsUnknownOutcomeWithinBoundedTime() {
        assertThatThrownBy(() -> publisher.publish(response)).isInstanceOf(AmqpException.class)
                .hasMessage("Diagnostic publication outcome unknown").hasNoCause();
    }

    @Test
    void restoresInterruptFlagWithoutLeakingCause() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publisher.publish(response)).isInstanceOf(AmqpException.class)
                    .hasMessage("Diagnostic publication interrupted").hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void onSend(Consumer<CorrelationData> action) {
        doAnswer(invocation -> {
            action.accept(invocation.getArgument(3));
            return null;
        }).when(template).send(eq(names.exchange()), eq("response"), any(Message.class), any(CorrelationData.class));
    }
}
