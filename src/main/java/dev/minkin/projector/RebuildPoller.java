package dev.minkin.projector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RebuildPoller {
    private static final Logger log = LoggerFactory.getLogger(RebuildPoller.class);

    private final BalanceProjector balanceProjector;
    private final long batchSize;


    public RebuildPoller(BalanceProjector balanceProjector,
                         @Value("${dev.minkin.ledger.poller.batch-size}") long batchSize) {
        this.balanceProjector = balanceProjector;
        this.batchSize = batchSize;
    }

    @Async
    public void poll() {
        int processed;
        do {
            processed = balanceProjector.projectNextBatch(batchSize);
            if (processed > 0) {
                log.debug("Projected {} entries", processed);
            }
        } while (processed == batchSize);
    }

}