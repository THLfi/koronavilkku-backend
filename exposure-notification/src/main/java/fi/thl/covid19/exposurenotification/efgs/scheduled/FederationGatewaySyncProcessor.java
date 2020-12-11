package fi.thl.covid19.exposurenotification.efgs.scheduled;

import fi.thl.covid19.exposurenotification.efgs.InboundService;
import fi.thl.covid19.exposurenotification.efgs.OutboundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static java.time.temporal.ChronoUnit.DAYS;
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

    private final OutboundService outboundService;
    private final InboundService inboundService;

    public FederationGatewaySyncProcessor(
            OutboundService outboundService,
            InboundService inboundService,
            @Value("${covid19.federation-gateway.scheduled-inbound-enabled}") boolean importEnabled
    ) {
        this.outboundService = requireNonNull(outboundService);
        this.inboundService = requireNonNull(inboundService);
        this.lastInboundSyncFromEfgs = LocalDate.now(ZoneOffset.UTC).minus(1, DAYS);
        this.importEnabled = importEnabled;
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedDelayString = "${covid19.federation-gateway.upload-interval}")
    private void runExportToEfgs() {
        MDC.clear();
        LOG.info("Starting scheduled export to efgs.");
        Set<Long> operationIds = outboundService.startOutbound(false);
        LOG.info("Scheduled export to efgs finished. {}", keyValue("operationId", operationIds));
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.download-interval}",
            fixedDelayString = "${covid19.federation-gateway.download-interval}")
    private void runImportFromEfgs() {
        MDC.clear();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (importEnabled && today.isAfter(lastInboundSyncFromEfgs)) {
            LOG.info("Starting scheduled import from efgs.");
            inboundService.startInbound(lastInboundSyncFromEfgs, Optional.empty());
            lastInboundSyncFromEfgs = today;
            LOG.info("Scheduled import from efgs finished.");
        }
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.error-handling-interval}",
            fixedDelayString = "${covid19.federation-gateway.error-handling-interval}")
    private void runErrorHandling() {
        MDC.clear();
        LOG.info("Starting scheduled efgs error handling.");
        outboundService.resolveCrash();
        outboundService.startOutbound(true);
        inboundService.resolveCrash();
        inboundService.startInboundRetry(LocalDate.now(ZoneOffset.UTC).minus(1, DAYS));
        LOG.info("Scheduled efgs error handling finished.");
    }
}
