package dev.pdv.worker;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import dev.pdv.worker.probe.contract.ProbeRequest;
import dev.pdv.worker.probe.contract.ProbeResponse;
import dev.pdv.worker.probe.infrastructure.ProbeTopology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class WorkerAmqpTests {
    private static final String PREFIX = "pdv.test." + UUID.randomUUID();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pdv.probe.prefix", () -> PREFIX);
    }

    @Autowired RabbitTemplate template;
    @Autowired RabbitAdmin admin;
    @Autowired JsonMapper mapper;
    @Autowired ProbeTopology names;
    @Autowired RabbitListenerEndpointRegistry listeners;

    @BeforeEach
    void clearOwnTestQueues() {
        admin.purgeQueue(names.responses());
        admin.purgeQueue(names.dead());
    }

    @AfterAll
    void cleanupOwnTopology() {
        listeners.stop();
        admin.deleteQueue(names.requests());
        admin.deleteQueue(names.responses());
        admin.deleteQueue(names.dead());
        admin.deleteExchange(names.exchange());
        admin.deleteExchange(names.errorsExchange());
    }

    @Test
    void consumesAndPublishesTypedJsonIgnoringProducerClassHeaders() {
        var id = UUID.randomUUID();
        var message = request(mapper.writeValueAsBytes(new ProbeRequest(ProbeRequest.TYPE, id, "diagnostic")));
        message.getMessageProperties().setHeader("__TypeId__", "java.lang.Runtime");
        template.send(names.exchange(), "request", message);
        var result = receive(names.responses());
        var response = mapper.readValue(result.getBody(), ProbeResponse.class);
        assertThat(response.type()).isEqualTo("pdv.probe.response.v1");
        assertThat(response.requestId()).isEqualTo(id);
        assertThat(response.text()).isEqualTo("diagnostic");
        assertThat(result.getMessageProperties().getCorrelationId()).isEqualTo(id.toString());
        assertThat(result.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(template.receive(names.dead())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "wrongVersion", "nullId", "tooLongText", "tooLargeBody", "wrongMime", "serializedMime", "unknownField", "nullBody"})
    void rejectsInvalidMessagesIntoDlqWithoutLoggingTheirBody(String scenario, CapturedOutput output) {
        var marker = "DO_NOT_LOG_" + UUID.randomUUID();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", ProbeRequest.TYPE);
        payload.put("requestId", UUID.randomUUID());
        payload.put("text", marker);
        switch (scenario) {
            case "wrongVersion" -> payload.put("type", "pdv.probe.request.v99");
            case "nullId" -> payload.put("requestId", null);
            case "tooLongText" -> payload.put("text", "x".repeat(257));
            case "unknownField" -> payload.put("unexpected", "ignored?");
            default -> { }
        }
        byte[] body = switch (scenario) {
            case "malformed" -> ("{invalid:" + marker).getBytes(StandardCharsets.UTF_8);
            case "tooLargeBody" -> "x".repeat(4097).getBytes(StandardCharsets.UTF_8);
            case "nullBody" -> "null".getBytes(StandardCharsets.UTF_8);
            default -> mapper.writeValueAsBytes(payload);
        };
        var message = request(body);
        if (scenario.equals("wrongMime")) message.getMessageProperties().setContentType("text/plain");
        if (scenario.equals("serializedMime")) message.getMessageProperties().setContentType("application/x-java-serialized-object");
        template.send(names.exchange(), "request", message);
        var rejected = receive(names.dead());
        assertThat(rejected.getBody()).isEqualTo(body);
        assertThat(rejected.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(template.receive(names.responses())).isNull();
        assertThat(output.getAll()).doesNotContain(marker);
    }

    @Test
    void rejectsInputIfResponseHasNoRoute() {
        var binding = BindingBuilder.bind(new Queue(names.responses()))
                .to(new DirectExchange(names.exchange())).with("response");
        admin.removeBinding(binding);
        try {
            var body = mapper.writeValueAsBytes(new ProbeRequest(ProbeRequest.TYPE, UUID.randomUUID(), "unroutable"));
            template.send(names.exchange(), "request", request(body));
            assertThat(receive(names.dead()).getBody()).isEqualTo(body);
            assertThat(template.receive(names.responses())).isNull();
        } finally {
            admin.declareBinding(binding);
        }
    }

    private Message request(byte[] body) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body, properties);
    }

    private Message receive(String queue) {
        return await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> template.receive(queue), message -> message != null);
    }
}
