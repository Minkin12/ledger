package dev.minkin.configuration;

import dev.minkin.configuration.properties.NatsProperties;
import io.nats.client.*;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class NatsConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NatsConfiguration.class);

    private static final int STREAM_NAME_ALREADY_IN_USE_ERROR_CODE = 10058;

    private final String natsUrl;
    private final String streamName;
    private final String subjects;
    private final Duration streamMaxAge;

    public NatsConfiguration(NatsProperties natsProperties) {
        this.natsUrl = natsProperties.url();
        this.streamName = natsProperties.streamName();
        this.subjects = natsProperties.subjects();
        this.streamMaxAge = natsProperties.streamMaxAge();
    }
    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .maxReconnects(-1)
                .connectionTimeout(Duration.ofSeconds(5))
                .build();
        return Nats.connect(options);
    }

    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException {
        return connection.jetStreamManagement();
    }

    @Bean
    public JetStream jetStream(Connection connection) throws IOException {
        return connection.jetStream();
    }

    @Bean
    public StreamInfo ledgerEventStream(JetStreamManagement jsm) throws Exception {

        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subjects)
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(streamMaxAge)
                .build();

        try {
            return jsm.addStream(streamConfig);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == STREAM_NAME_ALREADY_IN_USE_ERROR_CODE) {
                try {
                    return jsm.updateStream(streamConfig);
                } catch (JetStreamApiException updateError) {
                    log.error("Stream exists with incompatible config (likely retention policy) — manual reset required: {}", updateError.getMessage());
                    throw updateError;
                }
            } else {
                throw e;
            }
        }
    }
}
