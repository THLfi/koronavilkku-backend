package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
@Profile("!dev & !test")
public class CallbackInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);
    private static final String CALLBACK_ID = "1";

    private final FederationGatewayClient client;
    private final AtomicBoolean initialized;
    private final boolean enabled;
    private final String localUrl;

    public CallbackInitializer(
            FederationGatewayClient client,
            @Value("${covid19.federation-gateway.call-back.enabled}") boolean enabled,
            @Value("${covid19.federation-gateway.call-back.local-url}") String localUrl) {
        this.client = requireNonNull(client);
        this.initialized = new AtomicBoolean(false);
        this.enabled = enabled;
        this.localUrl = requireNonNull(localUrl);
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 60000)
    public void initializeCallback() {
        if (!this.initialized.get()) {
            try {
                List<Callback> callbacks = client.fetchCallbacks();
                deleteUnknown(callbacks);
                Optional<Callback> current = callbacks.stream().filter(cb -> cb.callbackId.equals(CALLBACK_ID)).findFirst();
                if (this.enabled && !localUrl.isEmpty()) {
                    updateOrCreateCallback(current);
                } else {
                    deleteCallback(current);
                }

                LOG.info("Callback initialized. {}", keyValue("local-url", localUrl));
                this.initialized.set(true);
            } finally {
                LOG.info("Callback initialization failed. Retry in 60 seconds. {}", keyValue("local-url", localUrl));
            }
        }
    }

    private void updateOrCreateCallback(Optional<Callback> callback) {
        callback.ifPresentOrElse(
                cb -> {
                    if (!localUrl.equals(cb.url)) {
                        client.putCallback(new Callback(CALLBACK_ID, localUrl));
                    }
                },
                () -> client.putCallback(new Callback(CALLBACK_ID, localUrl))
        );
    }

    private void deleteCallback(Optional<Callback> callback) {
        callback.ifPresent(cb -> client.deleteCallback(cb.callbackId));
    }

    private void deleteUnknown(List<Callback> callbacks) {
        callbacks.stream()
                .filter(cb -> !CALLBACK_ID.equals(cb.callbackId))
                .collect(Collectors.toList())
                .forEach(cb -> client.deleteCallback(cb.callbackId));
    }

}
