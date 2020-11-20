package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class FederationGatewaySyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);
    private final AtomicReference<LocalDate> lastInboundSyncFromEfgs;

    private final FederationGatewaySyncService federationGatewaySyncService;

    public FederationGatewaySyncProcessor(FederationGatewaySyncService federationGatewaySyncService) {
        this.federationGatewaySyncService = requireNonNull(federationGatewaySyncService);
        this.lastInboundSyncFromEfgs = new AtomicReference<>(LocalDate.now(ZoneOffset.UTC));
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
        LocalDate last = lastInboundSyncFromEfgs.get();
        if (today.isAfter(last)) {
            LOG.info("Starting scheduled import from efgs.");
            federationGatewaySyncService.startInbound(last, Optional.empty());
            lastInboundSyncFromEfgs.set(today);
            LOG.info("Scheduled import from efgs finished.");
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
