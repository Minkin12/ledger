package dev.minkin.ledger;

import dev.minkin.configuration.properties.NatsProperties;
import dev.minkin.ledger.controller.exceptions.IdempotencyConflictException;
import dev.minkin.ledger.controller.exceptions.TransferNotFoundException;
import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.controller.types.TransferResponse;
import dev.minkin.ledger.types.CreateTransferRequestEntryLine;
import dev.minkin.ledger.types.InsertedEntry;
import dev.minkin.ledger.types.InsertedTransfer;
import dev.minkin.ledger.types.ResolvedEntry;
import dev.minkin.ledger.types.ResolvedEntryDto;
import dev.minkin.ledger.types.TransferDto;
import dev.minkin.ledger.types.events.EntryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferWriterTest {

    private TransferRepository transferRepository;
    private OutboxRepository outbox;
    private TransferWriter writer;

    @BeforeEach
    void setUp() {
        transferRepository = Mockito.mock(TransferRepository.class);
        outbox = Mockito.mock(OutboxRepository.class);
        NatsProperties natsProperties = new NatsProperties(
                "nats://localhost:4222", "STREAM", "ledger.>", 16, Duration.ofDays(10),
                Map.of(), Map.of());
        writer = new TransferWriter(transferRepository, outbox, natsProperties);
    }

    @Test
    void write_insertsEntriesAndOutboxRowsShardedByInternalAccountId() {
        CreateTransferRequest req = new CreateTransferRequest(
                "PAYMENT",
                List.of(
                        new CreateTransferRequestEntryLine("acct-a", 100L),
                        new CreateTransferRequestEntryLine("acct-b", -100L)
                )
        );
        Map<String, Long> accounts = Map.of("acct-a", 1L, "acct-b", 2L);
        byte[] hash = "hash".getBytes();
        InsertedTransfer inserted = new InsertedTransfer(10L, OffsetDateTime.now());

        when(transferRepository.insertTransfer("key", hash, "PAYMENT")).thenReturn(inserted);
        when(transferRepository.insertEntries(eq(10L), anyList())).thenReturn(List.of(
                new InsertedEntry(100L, new ResolvedEntry(1L, "acct-a", 100L)),
                new InsertedEntry(101L, new ResolvedEntry(2L, "acct-b", -100L))
        ));

        TransferResponse response = writer.write("key", hash, req, accounts);

        assertEquals(10L, response.transferId());
        assertEquals("PAYMENT", response.reason());
        assertEquals(2, response.entries().size());
        assertEquals("acct-a", response.entries().get(0).accountId());
        assertEquals(100L, response.entries().get(0).entryId());
        assertEquals(100L, response.entries().get(0).amount());
        assertEquals("acct-b", response.entries().get(1).accountId());
        assertEquals(101L, response.entries().get(1).entryId());
        assertEquals(-100L, response.entries().get(1).amount());

        ArgumentCaptor<List<ResolvedEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(transferRepository).insertEntries(eq(10L), entriesCaptor.capture());
        assertEquals(
                List.of(new ResolvedEntry(1L, "acct-a", 100L), new ResolvedEntry(2L, "acct-b", -100L)),
                entriesCaptor.getValue());

        verify(outbox).insertOutbox(any(UUID.class), eq("ledger.entry.1.1"),
                eq(new EntryEvent(1L, 100L, 100L, "ENTRY_CREATED", 1)));
        verify(outbox).insertOutbox(any(UUID.class), eq("ledger.entry.2.2"),
                eq(new EntryEvent(2L, 101L, -100L, "ENTRY_CREATED", 1)));
    }

    @Test
    void replay_returnsExistingTransferWhenHashMatches() {
        byte[] hash = "hash".getBytes();
        TransferDto existing = new TransferDto(5L, "key", hash, "PAYMENT", OffsetDateTime.now());
        when(transferRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(existing));
        when(transferRepository.findResolvedEntriesByTransferId(5L)).thenReturn(List.of(
                new ResolvedEntryDto(200L, "acct-a", 100L),
                new ResolvedEntryDto(201L, "acct-b", -100L)
        ));

        TransferResponse response = writer.replay("key", hash);

        assertEquals(5L, response.transferId());
        assertEquals(2, response.entries().size());
        assertEquals("acct-a", response.entries().get(0).accountId());
        assertEquals(200L, response.entries().get(0).entryId());
    }

    @Test
    void replay_throwsWhenStoredHashDiffersFromRequestHash() {
        TransferDto existing = new TransferDto(5L, "key", "other-hash".getBytes(), "PAYMENT", OffsetDateTime.now());
        when(transferRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> writer.replay("key", "hash".getBytes()));
        verify(transferRepository, never()).findResolvedEntriesByTransferId(anyLong());
    }

    @Test
    void replay_throwsWhenNoTransferExistsForKey() {
        when(transferRepository.findByIdempotencyKey("key")).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class, () -> writer.replay("key", "hash".getBytes()));
    }
}
