package dev.minkin.reconciler;

import dev.minkin.reconciler.types.BalanceDiscrepancy;
import dev.minkin.reconciler.types.OrphanedBalance;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReconciliationRepository {
    private final JdbcClient db;

    public ReconciliationRepository(JdbcClient db) {
        this.db = db;
    }

    public List<BalanceDiscrepancy> findBalanceDiscrepancies() {
        return this.db.sql("""
                        select e.account_id,
                               e.computed_balance,
                               coalesce(b.balance, 0) as stored_balance
                        from (select account_id, sum(amount) as computed_balance
                              from entry
                              group by account_id) e
                        left join account_balance b on b.account_id = e.account_id
                        where e.computed_balance <> coalesce(b.balance, 0)
                        """)
                .query(BalanceDiscrepancy.class)
                .list();
    }

    public long computeGlobalBalanceSum() {
        return this.db.sql("select coalesce(sum(amount), 0)from entry")
                        .query(Long.class)
                        .single();
    }

    public List<OrphanedBalance> findOrphanedBalances() {
        return this.db.sql("""
                select b.account_id, b.balance
                from account_balance b
                left join entry e on e.account_id = b.account_id
                where e.account_id is null and b.balance <> 0
                """)
                .query(OrphanedBalance.class)
                .list();
    }

}
