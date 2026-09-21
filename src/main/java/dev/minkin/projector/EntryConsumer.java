package dev.minkin.projector;

import dev.minkin.configuration.properties.NatsProperties;
import dev.minkin.ledger.types.events.EntryEvent;
import dev.minkin.projector.BalanceProjector;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.Headers;
import io.nats.client.support.NatsJetStreamConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class EntryConsumer {
    private static final Logger log = LoggerFactory.getLogger(EntryConsumer.class);

    private final Connection connection;
    private final JetStream jetStream;
    private final BalanceProjector balanceProjector;
    private final ObjectMapper objectMapper;
    private final NatsProperties natsProperties;

    public EntryConsumer(Connection connection,
                         JetStream jetStream,
                         BalanceProjector balanceProjector,
                         ObjectMapper objectMapper,
                         NatsProperties natsProperties,
                         StreamInfo ledgerEventStream) {
        // ledgerEventStream is injected to force the stream to exist before we subscribe
        this.connection = connection;
        this.jetStream = jetStream;
        this.balanceProjector = balanceProjector;
        this.objectMapper = objectMapper;
        this.natsProperties = natsProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() throws Exception {
        NatsProperties.ConsumerProperties properties = natsProperties.consumer().get("entry");

        Dispatcher dispatcher = connection.createDispatcher();

        ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                .durable(properties.consumerName())
                .maxDeliver(properties.maxDeliver())
                .ackWait(properties.ackWait())
                .build();

        PushSubscribeOptions options = PushSubscribeOptions.builder()
                .stream(natsProperties.streamName())
                .configuration(consumerConfig)
                .build();

        jetStream.subscribe(properties.subject(), dispatcher, this::handle, false, options);

        log.info("Subscribed to {} as durable {} on stream {}",
                properties.subject(), properties.consumerName(), natsProperties.streamName());
    }

    private void handle(Message msg) {
        try {
            UUID eventId = extractEventId(msg);
            EntryEvent entryEvent = objectMapper.readValue(msg.getData(), EntryEvent.class);
            balanceProjector.apply(eventId, entryEvent);
            msg.ack();
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("Unprocessable message on {}, terminating: {}", msg.getSubject(), e.getMessage());
            msg.term();
        } catch (Exception e) {
            log.error("Event failed on {}, will retry", msg.getSubject(), e);
            msg.nak();
        }
    }

    private UUID extractEventId(Message msg) {
        Headers headers = msg.getHeaders();
        if (headers == null) {
            throw new IllegalArgumentException("message has no headers");
        }
        String id = headers.getFirst(NatsJetStreamConstants.MSG_ID_HDR);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing " + NatsJetStreamConstants.MSG_ID_HDR);
        }
        return UUID.fromString(id);
    }
}