package dev.minkin.ledger;

import dev.minkin.configuration.properties.NatsProperties;
import dev.minkin.ledger.controller.exceptions.IdempotencyConflictException;
import dev.minkin.ledger.controller.exceptions.TransferNotFoundException;
import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.controller.types.EntryResponse;
import dev.minkin.ledger.controller.types.TransferResponse;
import dev.minkin.ledger.types.ResolvedEntryDto;
import dev.minkin.ledger.types.TransferDto;
import dev.minkin.ledger.types.CreateTransferRequestEntryLine;
import dev.minkin.ledger.types.InsertedEntry;
import dev.minkin.ledger.types.InsertedTransfer;
import dev.minkin.ledger.types.ResolvedEntry;
import dev.minkin.ledger.types.events.EntryEvent;
import dev.minkin.ledger.types.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.*;

@Component
public class TransferWriter {

    private static final Logger log = LoggerFactory.getLogger(TransferWriter.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outbox;
    private final int shardCount;

    public TransferWriter(TransferRepository transferRepository, OutboxRepository outbox, NatsProperties natsProperties) {
        this.transferRepository = transferRepository;
        this.outbox = outbox;
        this.shardCount = natsProperties.entryShardCount();
    }

    @Transactional
    public TransferResponse write(String key, byte[] hash,
                                  CreateTransferRequest req,
                                  Map<String, Long> accounts) {

        InsertedTransfer insertedTransfer = transferRepository.insertTransfer(key, hash, req.reason());

        List<ResolvedEntry> resolvedEntries = new ArrayList<>();
        for (CreateTransferRequestEntryLine line : req.entries()) {
            resolvedEntries.add(new ResolvedEntry(accounts.get(line.accountId()), line.accountId(), line.amount()));
        }

        List<InsertedEntry> insertedEntries = transferRepository.insertEntries(insertedTransfer.id(), resolvedEntries);

        for (InsertedEntry insertedEntry : insertedEntries) {
            ResolvedEntry resolvedEntry = insertedEntry.entry();

            long shard = resolvedEntry.internalAccountId() % shardCount;
            String resolvedSubject = String.format("ledger.entry.%s.%d", shard, resolvedEntry.internalAccountId());

            outbox.insertOutbox(UUID.randomUUID(), resolvedSubject, new EntryEvent(
                    resolvedEntry.internalAccountId(),
                    insertedEntry.id(),
                    resolvedEntry.amount(),
                    EventType.ENTRY_CREATED.name(),
                    1));
        }

        return buildResponseForWrite(insertedTransfer, insertedEntries, req);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferResponse replay(String key, byte[] hash) {
        Optional<TransferDto> existing = transferRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            TransferDto transferDto = existing.get();
            if (!MessageDigest.isEqual(transferDto.requestHash(), hash)) {
                throw new IdempotencyConflictException(key);
            }
        } else {
            throw new TransferNotFoundException(key);
        }
        return buildResponseForReplay(existing.get());
    }

    private TransferResponse buildResponseForWrite(InsertedTransfer insertedTransfer, List<InsertedEntry> insertedEntries, CreateTransferRequest req) {
        List<EntryResponse> entries = new ArrayList<>();
        for (InsertedEntry insertedEntry : insertedEntries) {
            ResolvedEntry resolvedEntry = insertedEntry.entry();
            entries.add(new EntryResponse(insertedEntry.id(), resolvedEntry.externalId(), resolvedEntry.amount()));
        }
        return new TransferResponse(insertedTransfer.id(), req.reason(), insertedTransfer.createdAt(), entries);
    }

    private TransferResponse buildResponseForReplay(TransferDto transferDto) {
        List<ResolvedEntryDto> resolvedEntryDtoList = transferRepository.findResolvedEntriesByTransferId(transferDto.id());
        List<EntryResponse> entries = new ArrayList<>();
        for (ResolvedEntryDto resolvedEntryDto : resolvedEntryDtoList) {
            entries.add(new EntryResponse(resolvedEntryDto.entryId(), resolvedEntryDto.externalId(), resolvedEntryDto.amount()));
        }
        return new TransferResponse(transferDto.id(), transferDto.reason(), transferDto.createdAt(),entries);
    }
}
