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

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
@ConditionalOnProperty(
        prefix = "covid19.federation-gateway", value = "enabled",
        havingValue = "true"
)
public class FederationGatewaySyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);
    private final boolean importEnabled;
    private volatile LocalDate lastInboundSyncFromEfgs;

    private final FederationGatewaySyncService federationGatewaySyncService;

    public FederationGatewaySyncProcessor(
            FederationGatewaySyncService federationGatewaySyncService,
            @Value("${covid19.federation-gateway.scheduled-inbound-enabled}") boolean importEnabled
    ) {
        this.federationGatewaySyncService = requireNonNull(federationGatewaySyncService);
        this.lastInboundSyncFromEfgs = LocalDate.now(ZoneOffset.UTC);
        this.importEnabled = importEnabled;
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedDelayString = "${covid19.federation-gateway.upload-interval}")
    private void runExportToEfgs() {
        LOG.info("Starting scheduled export to efgs.");
        Set<Long> operationIds = federationGatewaySyncService.startOutbound(false);
        LOG.info("Scheduled export to efgs finished. {}", keyValue("operationId", operationIds));
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.download-interval}",
            fixedDelayString = "${covid19.federation-gateway.download-interval}")
    private void runImportFromEfgs() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (importEnabled && today.isAfter(lastInboundSyncFromEfgs)) {
            LOG.info("Starting scheduled import from efgs.");
            federationGatewaySyncService.startInbound(lastInboundSyncFromEfgs, Optional.empty());
            lastInboundSyncFromEfgs = today;
            LOG.info("Scheduled import from efgs finished.");
        }
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.error-handling-interval}",
            fixedDelayString = "${covid19.federation-gateway.error-handling-interval}")
    private void runErrorHandling() {
        LOG.info("Starting scheduled efgs error handling.");
        federationGatewaySyncService.resolveCrash();
        federationGatewaySyncService.startOutbound(true);
        federationGatewaySyncService.startInboundRetry(lastInboundSyncFromEfgs);
        LOG.info("Scheduled efgs error handling finished.");
    }
}