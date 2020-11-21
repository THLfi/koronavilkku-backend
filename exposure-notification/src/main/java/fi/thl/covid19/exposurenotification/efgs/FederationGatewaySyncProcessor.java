package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway", value = "enabled",
        havingValue = "true"
)
public class FederationGatewaySyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);
    private final AtomicReference<LocalDate> lastInboundSyncFromEfgs;
    private final boolean importEnabled;

    private final FederationGatewaySyncService federationGatewaySyncService;

    public FederationGatewaySyncProcessor(
            FederationGatewaySyncService federationGatewaySyncService,
            @Value("${covid19.federation-gateway.scheduled-inbound-enabled}") boolean importEnabled
    ) {
        this.federationGatewaySyncService = requireNonNull(federationGatewaySyncService);
        this.lastInboundSyncFromEfgs = new AtomicReference<>(LocalDate.now(ZoneOffset.UTC));
        this.importEnabled = importEnabled;
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedRateString = "${covid19.federation-gateway.upload-interval}")
    private void runExportToEfgs() {
        LOG.info("Starting scheduled export to efgs.");
        Set<Long> operationIds = federationGatewaySyncService.startOutbound(false);
        LOG.info("Scheduled export to efgs finished. {}", keyValue("operationId", operationIds));
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.download-interval}",
            fixedRateString = "${covid19.federation-gateway.download-interval}")
    private void runImportFromEfgs() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate previous = lastInboundSyncFromEfgs.getAndUpdate(c -> today.isAfter(c) ? today : c);
        if (importEnabled && today.isAfter(previous)) {
            try {
                LOG.info("Starting scheduled import from efgs.");
                federationGatewaySyncService.startInbound(previous, Optional.empty());
                LOG.info("Scheduled import from efgs finished.");
            } catch (Exception e) {
                lastInboundSyncFromEfgs.set(previous);
                throw e;
            }
        }
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.error-handling-interval}",
            fixedRateString = "${covid19.federation-gateway.error-handling-interval}")
    private void runErrorHandling() {
        LOG.info("Starting scheduled efgs error handling.");
        federationGatewaySyncService.resolveCrash();
        federationGatewaySyncService.startOutbound(true);
        federationGatewaySyncService.startInboundRetry(lastInboundSyncFromEfgs.get());
        LOG.info("Scheduled efgs error handling finished.");
    }
}
