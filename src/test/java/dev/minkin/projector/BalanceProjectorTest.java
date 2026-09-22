package dev.minkin.projector;

import dev.minkin.ledger.types.EntryDto;
import dev.minkin.ledger.types.events.EntryEvent;
import dev.minkin.projector.types.AccountBalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceProjectorTest {

    private ProjectionRepository repository;
    private BalanceProjector projector;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ProjectionRepository.class);
        projector = new BalanceProjector(repository);
    }

    @Test
    void apply_upsertsBalanceForNewEvent() {
        UUID eventId = UUID.randomUUID();
        EntryEvent event = new EntryEvent(1L, 50L, 100L, "ENTRY_CREATED", 1);
        when(repository.insertProcessedEvent(eventId)).thenReturn(1);

        projector.apply(eventId, event);

        verify(repository).upsertAccountBalance(new AccountBalanceDto(1L, 100L, 50L));
    }

    @Test
    void apply_skipsAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        EntryEvent event = new EntryEvent(1L, 50L, 100L, "ENTRY_CREATED", 1);
        when(repository.insertProcessedEvent(eventId)).thenReturn(0);

        projector.apply(eventId, event);

        verify(repository, never()).upsertAccountBalance(any());
    }

    @Test
    void projectNextBatch_netsMultipleEntriesForTheSameAccount() {
        when(repository.getPollingCursor()).thenReturn(10L);
        when(repository.batchFetchEntries(10L, 500L)).thenReturn(List.of(
                new EntryDto(11L, 1L, 100L, 50L, OffsetDateTime.now()),
                new EntryDto(12L, 1L, 100L, -20L, OffsetDateTime.now()),
                new EntryDto(13L, 2L, 200L, 30L, OffsetDateTime.now())
        ));

        int processed = projector.projectNextBatch(500L);

        assertEquals(3, processed);
        verify(repository).upsertAccountBalance(new AccountBalanceDto(100L, 30L, 12L));
        verify(repository).upsertAccountBalance(new AccountBalanceDto(200L, 30L, 13L));
    }

    @Test
    void projectNextBatch_returnsZeroWhenNothingToProject() {
        when(repository.getPollingCursor()).thenReturn(10L);
        when(repository.batchFetchEntries(10L, 500L)).thenReturn(List.of());

        int processed = projector.projectNextBatch(500L);

        assertEquals(0, processed);
        verify(repository, never()).upsertAccountBalance(any());
    }
}
