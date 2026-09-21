package dev.minkin.ledger;

import dev.minkin.ledger.types.*;
import dev.minkin.ledger.types.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class LedgerRepository {
    private static final Logger log = LoggerFactory.getLogger(LedgerRepository.class);

    private final JdbcClient db;
    private final ObjectMapper objectMapper;

    public LedgerRepository(JdbcClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }


    public Map<String, Long> resolveAccounts(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            log.debug("Account ids is empty");
            return Map.of();
        }
        List<Map.Entry<String, Long>> rows = this.db
                .sql("select id,external_id from account where external_id in (:accountIds)")
                .param("accountIds", accountIds)
                .query((rs, rowNum) -> Map.entry(rs.getString("external_id"), rs.getLong("id")))
                .list();

        Map<String, Long> result = new HashMap<>();
        rows.forEach(entry -> result.put(entry.getKey(), entry.getValue()));

        return result;
    }

    public InsertedTransfer insertTransfer(String key, byte[] hash, String reason) {
        return this.db.sql("""
                        insert into transfer (idempotency_key, request_hash, reason)
                        values (:key, :hash, :reason)
                        returning id, created_at
                        """)
                .param("key", key)
                .param("hash", hash)
                .param("reason", reason)
                .query(InsertedTransfer.class)
                .single();
    }

    public List<Long> insertEntries(long transferId, List<ResolvedEntry> entries) {
        List<Long> entryIds = new ArrayList<>();
        // will be fine for a few entries which it usually would be but could use optimization
        for (ResolvedEntry entry : entries) {
            entryIds.add(insertEntry(transferId, entry));
        }
        return entryIds;
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

    public void batchUpdateOutboxPublishedTime(List<UUID> eventIds){
        this.db.sql("update outbox set published_at = now() where event_id in (:eventIds)")
                .param("eventIds", eventIds)
                .update();

    }

    public Optional<TransferDto> findByIdempotencyKey(String key) {
        return this.db.sql("select * from transfer where idempotency_key = :key")
                .param("key", key)
                .query(TransferDto.class)
                .optional();
    }

    public List<EntryDto> findEntriesByTransferId(long transferId) {
        return this.db.sql("select * from entry where transfer_id = :transferId")
                .param("transferId", transferId)
                .query(EntryDto.class)
                .list();

    }

    public List<ResolvedEntryDto> findResolvedEntriesByTransferId(long transferId) {
        return db.sql("""
                        select e.id as entry_id, a.external_id, e.amount
                        from entry e
                        join account a on a.id = e.account_id
                        where e.transfer_id = :transferId
                        order by e.id
                        """)
                .param("transferId", transferId)
                .query(ResolvedEntryDto.class)
                .list();
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

    private long insertEntry(long transferId, ResolvedEntry entry) {
        return db.sql("""
                        insert into entry (transfer_id, account_id, amount)
                        values (:transferId, :accountId, :amount)
                        returning id
                        """)
                .param("transferId", transferId)
                .param("accountId", entry.accountId())
                .param("amount", entry.amount())
                .query(Long.class)
                .single();
    }

}
