package dev.minkin.projector;

import dev.minkin.ledger.types.EntryDto;
import dev.minkin.projector.types.AccountBalanceDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ProjectionRepository {
    private final JdbcClient db;

    public ProjectionRepository(JdbcClient db) {
        this.db = db;
    }

    public List<EntryDto> batchFetchEntries(long cursor, long batchSize) {
        return this.db.sql("""
                        select id, transfer_id, account_id, amount, created_at
                        from entry where id > :cursor
                        order by id limit :batchSize""")
                .param("cursor", cursor)
                .param("batchSize", batchSize)
                .query(EntryDto.class)
                .list();
    }

    public void upsertAccountBalance(AccountBalanceDto accountBalanceDto) {
        this.db.sql("""
                        insert into account_balance (account_id, balance, last_entry_id)
                        values (:accountId, :amount, :entryId)
                        on conflict (account_id) do update
                        set balance = account_balance.balance + excluded.balance,
                            last_entry_id = excluded.last_entry_id""")
                .param("accountId", accountBalanceDto.accountId())
                .param("amount", accountBalanceDto.balance())
                .param("entryId", accountBalanceDto.lastEntryId())
                .update();
    }

    public long getPollingCursor() {
        return this.db.sql("select coalesce(max(last_entry_id), 0) from account_balance")
                .query(Long.class)
                .single();
    }

    public int insertProcessedEvent(UUID eventId) {
        return this.db.sql("""
                        insert into processed_event
                        (event_id) values (:id)
                        on conflict do nothing""")
                .param("id", eventId)
                .update();
    }
}
