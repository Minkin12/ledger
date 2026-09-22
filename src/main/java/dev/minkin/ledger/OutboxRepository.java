package dev.minkin.ledger;

import dev.minkin.ledger.types.OutboxDto;
import dev.minkin.ledger.types.events.Event;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcClient db;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public void insertOutbox(UUID eventId, String subject, Event event) {
        String payload = objectMapper.writeValueAsString(event);
        this.db.sql("""
                        insert into outbox (event_id, subject, payload)
                        values (:eventId, :subject, :payload)
                        """)
                .param("eventId", eventId)
                .param("subject", subject)
                .param("payload", payload)
                .update();
    }

    public void batchUpdateOutboxPublishedTime(List<UUID> eventIds) {
        this.db.sql("update outbox set published_at = now() where event_id in (:eventIds)")
                .param("eventIds", eventIds)
                .update();
    }

    public List<OutboxDto> getOutboxEntries(long batchSize) {
        return this.db.sql("""
                with unprocessed as (select * from outbox where published_at is null order by id)
                select * from unprocessed order by id
                limit :batchSize
                """)
                .param("batchSize", batchSize)
                .query(OutboxDto.class)
                .list();
    }
}
