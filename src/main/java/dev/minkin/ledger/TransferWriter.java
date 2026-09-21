package dev.minkin.ledger;

import dev.minkin.ledger.controller.exceptions.IdempotencyConflictException;
import dev.minkin.ledger.controller.exceptions.TransferNotFoundException;
import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.controller.types.EntryResponse;
import dev.minkin.ledger.controller.types.TransferResponse;
import dev.minkin.ledger.types.ResolvedEntryDto;
import dev.minkin.ledger.types.TransferDto;
import dev.minkin.ledger.types.CreateTransferRequestEntryLine;
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

    private final LedgerRepository ledger;

    public TransferWriter(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    @Transactional
    public TransferResponse write(String key, byte[] hash,
                                  CreateTransferRequest req,
                                  Map<String, Long> accounts) {

        InsertedTransfer insertedTransfer = ledger.insertTransfer(key, hash, req.reason());

        List<ResolvedEntry> resolvedEntries = req.entries().stream()
                .map(l -> new ResolvedEntry(accounts.get(l.accountId()), l.accountId(), l.amount()))
                .toList();

        List<Long> entryIds = ledger.insertEntries(insertedTransfer.id(), resolvedEntries);

        for (int i = 0; i < resolvedEntries.size(); i++){
            // This works since entries are returned in order
            ResolvedEntry resolvedEntry = resolvedEntries.get(i);
            Long entryId = entryIds.get(i);

            Long shard = resolvedEntry.accountId() % 16;
            String resolvedSubject = String.format("ledger.entry.%s.%d", shard, resolvedEntry.accountId());

            ledger.insertOutbox(UUID.randomUUID(), resolvedSubject, new EntryEvent(
                    resolvedEntry.accountId(),
                    entryId,
                    resolvedEntry.amount(),
                    EventType.ENTRY_CREATED.name(),
                    1));
        }

        return buildResponseForWrite(insertedTransfer, entryIds, req);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferResponse replay(String key, byte[] hash) {
        Optional<TransferDto> existing = ledger.findByIdempotencyKey(key);
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

    private TransferResponse buildResponseForWrite(InsertedTransfer insertedTransfer, List<Long> entryIds, CreateTransferRequest req) {
        List<EntryResponse> entries = new ArrayList<>();
        for (int i = 0; i < entryIds.size(); i++) {
            CreateTransferRequestEntryLine createTransferRequestEntryLine = req.entries().get(i);
            Long entryId = entryIds.get(i);

            entries.add(new EntryResponse(entryId, createTransferRequestEntryLine.accountId(), createTransferRequestEntryLine.amount()));
        }
        return new TransferResponse(insertedTransfer.id(),req.reason(), insertedTransfer.createdAt(), entries);
    }

    private TransferResponse buildResponseForReplay(TransferDto transferDto) {
        List<ResolvedEntryDto> resolvedEntryDtoList = ledger.findResolvedEntriesByTransferId(transferDto.id());
        List<EntryResponse> entries = new ArrayList<>();
        for (ResolvedEntryDto resolvedEntryDto : resolvedEntryDtoList) {
            entries.add(new EntryResponse(resolvedEntryDto.entryId(), resolvedEntryDto.externalId(), resolvedEntryDto.amount()));
        }
        return new TransferResponse(transferDto.id(), transferDto.reason(), transferDto.createdAt(),entries);
    }
}
