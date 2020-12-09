package fi.thl.covid19.exposurenotification.efgs.scheduled;

import fi.thl.covid19.exposurenotification.efgs.FederationGatewayClient;
import fi.thl.covid19.exposurenotification.efgs.entity.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway", value = "callback-initializer-enabled",
        matchIfMissing = true
)
public class CallbackInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);
    private static final String CALLBACK_ID = "1";

    private final FederationGatewayClient client;
    private final boolean enabled;
    private final String localUrl;

    private volatile Optional<LocalDate> callBackInitialized;

    public CallbackInitializer(
            FederationGatewayClient client,
            @Value("${covid19.federation-gateway.call-back.enabled}") boolean enabled,
            @Value("${covid19.federation-gateway.call-back.local-url}") String localUrl) {
        this.client = requireNonNull(client);
        this.callBackInitialized = Optional.empty();
        this.enabled = enabled;
        this.localUrl = requireNonNull(localUrl);
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 60000)
    public void initializeCallback() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        callBackInitialized.ifPresentOrElse(i -> doInit(today, i), () -> doInit(today, today.minus(1, DAYS)));
    }

    private void doInit(LocalDate today, LocalDate initialized) {
        if (initialized.isBefore(today)) {
            try {
                List<Callback> callbacks = client.fetchCallbacks();
                deleteUnknown(callbacks);
                Optional<Callback> current = callbacks.stream().filter(cb -> cb.callbackId.equals(CALLBACK_ID)).findFirst();
                if (this.enabled && !localUrl.isEmpty()) {
                    updateOrCreateCallback(current);
                    LOG.info("Callback initialized. {}", keyValue("local-url", localUrl));
                } else {
                    deleteCallback(current);
                    LOG.info("Callback disabled.");
                }
                this.callBackInitialized = Optional.of(today);
            } finally {
                callBackInitialized.ifPresentOrElse(
                        d -> {
                            if (!d.isEqual(today)) {
                                logInitFailed();
                            }
                        },
                        this::logInitFailed);
            }
        }
    }

    private void logInitFailed() {
        LOG.info("Callback initialization failed. Retry in 60 seconds. {}", keyValue("local-url", localUrl));
    }

    private void updateOrCreateCallback(Optional<Callback> callback) {
        callback.ifPresentOrElse(
                cb -> {
                    if (!localUrl.equals(cb.url)) {
                        putCallBack();
                    }
                },
                this::putCallBack
        );
    }

    private void putCallBack() {
        this.callBackInitialized.ifPresent(d -> LOG.warn("Re-initializing callback. This should not happen."));
        client.putCallback(new Callback(CALLBACK_ID, localUrl));
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
