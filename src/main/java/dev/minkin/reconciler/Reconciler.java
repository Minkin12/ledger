package dev.minkin.reconciler;

import dev.minkin.reconciler.types.BalanceDiscrepancy;
import dev.minkin.reconciler.types.OrphanedBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class Reconciler {
    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final ReconciliationRepository reconciliationRepository;

    public Reconciler(ReconciliationRepository reconciliationRepository) {
        this.reconciliationRepository = reconciliationRepository;
    }

    @Scheduled(fixedDelayString = "${dev.minkin.ledger.reconciler.fixed-delay-ms:60000}",
            initialDelayString = "${dev.minkin.ledger.reconciler.initial-delay-ms:10000}")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void reconcile() {
        boolean balancesEven = areAccountBalancesEven();
        boolean globalEven = areGlobalBalancesEven();
        boolean noOrphans = noOrphanedBalances();
        if (balancesEven && globalEven && noOrphans) {
            log.info("Reconciliation found no issues");
        }
    }

    private boolean areAccountBalancesEven() {
        List<BalanceDiscrepancy> balanceDiscrepancies = reconciliationRepository.findBalanceDiscrepancies();
        if (balanceDiscrepancies.isEmpty()) {
            return true;
        } else {
            log.warn("Account balance discrepancies are not empty. Affected accounts: {}", balanceDiscrepancies);
            return false;
        }
    }

    private boolean areGlobalBalancesEven() {
        long globalSum = reconciliationRepository.computeGlobalBalanceSum();
        if (globalSum == 0) {
            return true;
        } else {
            log.warn("Global sum is not 0, is: {}", globalSum);
            return false;
        }
    }

    private boolean noOrphanedBalances() {
        List<OrphanedBalance> orphanedBalances = reconciliationRepository.findOrphanedBalances();
        if (orphanedBalances.isEmpty()) {
            return true;
        } else {
            log.warn("Orphaned balances are not empty. Affected balances: {}", orphanedBalances);
            return false;
        }
    }
}
