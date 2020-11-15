package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
public class FederationGatewaySyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FederationGatewaySyncProcessor.class);

    private final FederationGatewayService federationGatewayService;

    public FederationGatewaySyncProcessor(FederationGatewayService federationGatewayService) {
        this.federationGatewayService = federationGatewayService;
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedRateString = "${covid19.federation-gateway.upload-interval}")
    private void runExportToEfgs() {
        LOG.info("Starting scheduled export to efgs.");
        Optional<Set<Long>> operationIds = federationGatewayService.startOutbound(false);
        LOG.info("Scheduled export to efgs finished. {}", keyValue("operationId", operationIds.orElse(Set.of())));
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.download-interval}",
            fixedRateString = "${covid19.federation-gateway.download-interval}")
    private void runImportFromEfgs() {
        LOG.info("Starting scheduled import from efgs.");
        federationGatewayService.startInbound(LocalDate.now(ZoneOffset.UTC), Optional.empty());
        LOG.info("Scheduled import from efgs finished.");
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.error-handling-interval}",
            fixedRateString = "${covid19.federation-gateway.error-handling-interval}")
    private void runErrorHandling() {
        LOG.info("Starting scheduled efgs error handling.");
        federationGatewayService.resolveCrash();
        federationGatewayService.startOutbound(true);
        LOG.info("Scheduled efgs error handling finished.");
    }
}
