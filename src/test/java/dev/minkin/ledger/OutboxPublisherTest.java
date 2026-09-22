package dev.minkin.ledger;

import dev.minkin.configuration.properties.NatsProperties;
import dev.minkin.ledger.types.OutboxDto;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private JetStream jetStream;
    private OutboxRepository outboxRepository;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        jetStream = Mockito.mock(JetStream.class);
        outboxRepository = Mockito.mock(OutboxRepository.class);
        NatsProperties natsProperties = new NatsProperties(
                "nats://localhost:4222", "STREAM", "ledger.>", 16, Duration.ofDays(10),
                Map.of(),
                Map.of("outbox", new NatsProperties.PublisherProperties(2)));
        publisher = new OutboxPublisher(jetStream, outboxRepository, natsProperties);
    }

    @Test
    void publishOutboxEvents_drainsBatchesUntilAShortBatchIsReturned() throws Exception {
        OutboxDto first = new OutboxDto(UUID.randomUUID(), "ledger.entry.1.1", "{\"a\":1}");
        OutboxDto second = new OutboxDto(UUID.randomUUID(), "ledger.entry.2.2", "{\"a\":2}");
        OutboxDto third = new OutboxDto(UUID.randomUUID(), "ledger.entry.3.3", "{\"a\":3}");

        when(outboxRepository.getOutboxEntries(2L))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(third));

        PublishAck ack = Mockito.mock(PublishAck.class);
        when(ack.isDuplicate()).thenReturn(false);
        when(jetStream.publish(any(Message.class))).thenReturn(ack);

        publisher.publishOutboxEvents();

        verify(outboxRepository, times(2)).getOutboxEntries(2L);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream, times(3)).publish(messageCaptor.capture());
        List<String> publishedSubjects = messageCaptor.getAllValues().stream().map(Message::getSubject).toList();
        assertEquals(List.of("ledger.entry.1.1", "ledger.entry.2.2", "ledger.entry.3.3"), publishedSubjects);
    }

    @Test
    void publishOutboxEvents_doesNothingWhenOutboxIsEmpty() throws Exception {
        when(outboxRepository.getOutboxEntries(2L)).thenReturn(List.of());

        publisher.publishOutboxEvents();

        verify(jetStream, times(0)).publish(any(Message.class));
        verify(outboxRepository, times(0)).batchUpdateOutboxPublishedTime(any());
    }
}
