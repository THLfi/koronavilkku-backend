package fi.thl.covid19.exposurenotification.efgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
public class FederationSyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FederationSyncProcessor.class);

    private final FederationGatewayService fgs;

    public FederationSyncProcessor(FederationGatewayService fgs) {
        this.fgs = fgs;
    }


    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedRateString = "${covid19.federation-gateway.upload-interval}")
    private void runExportToEfgs() {
        LOG.info("Starting scheduled export to efgs.");
        Optional<Long> operationId = fgs.startOutbound();
        LOG.info("Scheduled export to efgs finished. {}", keyValue("operationId", operationId.orElse(-1L)));
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.download-interval}",
            fixedRateString = "${covid19.federation-gateway.download-interval}")
    private void runImportFromEfgs() {
        LOG.info("Starting scheduled import from efgs.");
        fgs.startInbound(Optional.empty());
        LOG.info("Scheduled import from efgs finished.");
    }

    @Scheduled(initialDelayString = "${covid19.federation-gateway.error-handling-interval}",
            fixedRateString = "${covid19.federation-gateway.error-handling-interval}")
    private void runErrorHandling() {
        LOG.info("Starting scheduled efgs error handling.");
        fgs.startErronHandling();
        LOG.info("Scheduled efgs error handling finished.");
    }
}
