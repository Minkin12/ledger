package dev.minkin.ledger;

import dev.minkin.ledger.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class TransferRepository {
    private static final Logger log = LoggerFactory.getLogger(TransferRepository.class);

    private final JdbcClient db;

    public TransferRepository(JdbcClient db) {
        this.db = db;
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

    public List<InsertedEntry> insertEntries(long transferId, List<ResolvedEntry> entries) {
        List<InsertedEntry> insertedEntries = new ArrayList<>();
        // will be fine for a few entries which it usually would be but could use optimization
        for (ResolvedEntry entry : entries) {
            long id = insertEntry(transferId, entry);
            insertedEntries.add(new InsertedEntry(id, entry));
        }
        return insertedEntries;
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

    private long insertEntry(long transferId, ResolvedEntry entry) {
        return db.sql("""
                        insert into entry (transfer_id, account_id, amount)
                        values (:transferId, :accountId, :amount)
                        returning id
                        """)
                .param("transferId", transferId)
                .param("accountId", entry.internalAccountId())
                .param("amount", entry.amount())
                .query(Long.class)
                .single();
    }

}
