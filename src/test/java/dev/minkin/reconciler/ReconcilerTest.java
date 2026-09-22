package dev.minkin.reconciler;

import dev.minkin.reconciler.types.BalanceDiscrepancy;
import dev.minkin.reconciler.types.OrphanedBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconcilerTest {

    private ReconciliationRepository repository;
    private Reconciler reconciler;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ReconciliationRepository.class);
        reconciler = new Reconciler(repository);
    }

    @Test
    void reconcile_runsAllThreeChecksWhenEverythingIsConsistent() {
        when(repository.findBalanceDiscrepancies()).thenReturn(List.of());
        when(repository.computeGlobalBalanceSum()).thenReturn(0L);
        when(repository.findOrphanedBalances()).thenReturn(List.of());

        assertDoesNotThrow(reconciler::reconcile);

        verify(repository).findBalanceDiscrepancies();
        verify(repository).computeGlobalBalanceSum();
        verify(repository).findOrphanedBalances();
    }

    @Test
    void reconcile_runsAllThreeChecksEvenWhenDiscrepanciesAreFound() {
        when(repository.findBalanceDiscrepancies())
                .thenReturn(List.of(new BalanceDiscrepancy(1L, 100L, 90L)));
        when(repository.computeGlobalBalanceSum()).thenReturn(10L);
        when(repository.findOrphanedBalances())
                .thenReturn(List.of(new OrphanedBalance(2L, 5L)));

        assertDoesNotThrow(reconciler::reconcile);

        verify(repository).findBalanceDiscrepancies();
        verify(repository).computeGlobalBalanceSum();
        verify(repository).findOrphanedBalances();
    }
}
