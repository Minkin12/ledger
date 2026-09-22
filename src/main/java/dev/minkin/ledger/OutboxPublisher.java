package dev.minkin.ledger;

import dev.minkin.configuration.properties.NatsProperties;
import dev.minkin.ledger.types.OutboxDto;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JetStream jetStream;
    private final OutboxRepository outboxRepository;
    private final NatsProperties.PublisherProperties publisherProperties;


    public OutboxPublisher(JetStream jetStream,
                           OutboxRepository outboxRepository,
                           NatsProperties natsProperties
                           ) {
        this.jetStream = jetStream;
        this.outboxRepository = outboxRepository;
        this.publisherProperties = natsProperties.publisher().get("outbox");
    }

    @Scheduled(fixedRateString = "${dev.minkin.ledger.nats.publisher.outbox.publisher-delay-ms:60000}")
    public void publishOutboxEvents() throws JetStreamApiException, IOException {

        List<OutboxDto> batch;
        List<UUID> eventIds = new ArrayList<>();
        do {
            batch = outboxRepository.getOutboxEntries(publisherProperties.batchSize());
            for (OutboxDto entry : batch) {
                PublishAck pa = publish(entry);
                if (pa.isDuplicate()){
                log.debug("EventId: {} is a duplicate", entry.eventId());
                }
                eventIds.add(entry.eventId());
            }
            if (!eventIds.isEmpty()) {
                outboxRepository.batchUpdateOutboxPublishedTime(eventIds);
            }
            eventIds.clear();
        } while (batch.size() == publisherProperties.batchSize());



    }

    private PublishAck publish(OutboxDto entry) throws JetStreamApiException, IOException {
        Message msg = NatsMessage.builder()
                .subject(entry.subject())
                .headers(new Headers().add("Nats-Msg-Id", entry.eventId().toString()))
                .data(entry.payload(), StandardCharsets.UTF_8)
                .build();
        return jetStream.publish(msg);
    }

}
