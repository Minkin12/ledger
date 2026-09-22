package dev.minkin.configuration.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("dev.minkin.ledger.nats")
public record NatsProperties(
        @DefaultValue("nats://localhost:4222") String url,
        String streamName,
        String subjects,
        @DefaultValue("16") Integer entryShardCount,
        @DefaultValue("10d") Duration streamMaxAge,
        Map<String, ConsumerProperties> consumer,
        Map<String, PublisherProperties> publisher
) {
    public record ConsumerProperties(
            String subject,
            String consumerName,
            @DefaultValue("5") int maxDeliver,
            @DefaultValue("30s") Duration ackWait
    ) {}
    public record PublisherProperties(@DefaultValue("500") Integer batchSize) {}
}
