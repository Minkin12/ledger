package dev.minkin.projector;

import dev.minkin.ledger.types.EntryDto;
import dev.minkin.ledger.types.events.EntryEvent;
import dev.minkin.projector.types.AccountBalanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Component
public class BalanceProjector {
    private final static Logger log = LoggerFactory.getLogger(BalanceProjector.class);

    private final ProjectionRepository projectionRepository;

    public BalanceProjector(ProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    @Transactional
    public void apply(UUID eventId, EntryEvent entryEvent) {
        int row = projectionRepository.insertProcessedEvent(eventId);
        if (row == 0) {
            log.debug("duplicate event {}, skipping", eventId);
            return;
        }
        projectionRepository.upsertAccountBalance(new AccountBalanceDto(entryEvent.accountId(), entryEvent.amount(), entryEvent.entryId()));
    }

    @Transactional
    public int projectNextBatch(long batchSize) {
        long cursor = projectionRepository.getPollingCursor();
        List<EntryDto> entries = projectionRepository.batchFetchEntries(cursor, batchSize);

        List<AccountBalanceDto> deltas = netByAccount(entries);
        for (AccountBalanceDto delta : deltas) {
            projectionRepository.upsertAccountBalance(delta);
        }

        log.debug("Projected {} entries from cursor {}", entries.size(), cursor);
        return entries.size();
    }

    private List<AccountBalanceDto> netByAccount(List<EntryDto> entries) {
        Map<Long, Net> byAccount = new LinkedHashMap<>();

        for (EntryDto entry : entries) {
            Net net = byAccount.get(entry.accountId());
            if (net == null) {
                net = new Net();
                byAccount.put(entry.accountId(), net);
            }
            net.sum += entry.amount();
            net.lastEntryId = entry.id();   // entries arrive in ascending id order
        }

        List<AccountBalanceDto> deltas = new ArrayList<>(byAccount.size());
        for (Map.Entry<Long, Net> e : byAccount.entrySet()) {
            Net net = e.getValue();
            deltas.add(new AccountBalanceDto(e.getKey(), net.sum, net.lastEntryId));
        }
        return deltas;
    }

    private static final class Net {
        long sum;
        long lastEntryId;
    }

}