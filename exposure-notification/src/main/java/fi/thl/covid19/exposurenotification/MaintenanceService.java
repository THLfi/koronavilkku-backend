package fi.thl.covid19.exposurenotification;

import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.batch.BatchFileStorage;
import fi.thl.covid19.exposurenotification.batch.BatchIntervals;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.efgs.FederationGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Service
public class MaintenanceService {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceService.class);

    private final DiagnosisKeyDao dao;
    private final BatchFileStorage batchFileStorage;
    private final BatchFileService batchFileService;
    private final FederationGatewayService fgs;

    private final Duration tokenVerificationLifetime;

    public MaintenanceService(DiagnosisKeyDao dao,
                              BatchFileService batchFileService,
                              BatchFileStorage batchFileStorage,
                              FederationGatewayService fgs,
                              @Value("${covid19.maintenance.token-verification-lifetime}") Duration tokenVerificationLifetime) {
        this.dao = requireNonNull(dao);
        this.batchFileStorage = requireNonNull(batchFileStorage);
        this.batchFileService = requireNonNull(batchFileService);
        this.fgs = fgs;
        this.tokenVerificationLifetime = requireNonNull(tokenVerificationLifetime);
        LOG.info("Initialized: {}", keyValue("tokenVerificationLifetime", tokenVerificationLifetime));
    }

    @Scheduled(initialDelayString = "${covid19.maintenance.interval}",
            fixedRateString = "${covid19.maintenance.interval}")
    public void runMaintenance() {
        BatchIntervals intervals = BatchIntervals.forGeneration();

        LOG.info("Cleaning keys and updating batch files: {} {} {}",
                keyValue("currentInterval", intervals.current),
                keyValue("firstFile", intervals.first),
                keyValue("lastFile", intervals.last));

        int removedKeys = dao.deleteKeysBefore(intervals.first);
        int removedVerifications = dao.deleteVerificationsBefore(Instant.now().minus(tokenVerificationLifetime));
        int removedBatches = batchFileStorage.deleteKeyBatchesBefore(intervals.first);
        int addedBatches = batchFileService.cacheMissingBatchesBetween(intervals.first, intervals.last);

        LOG.info("Batches updated: {} {} {} {}",
                keyValue("removedKeys", removedBatches),
                keyValue("removedVerifications", removedVerifications),
                keyValue("removedBatches", removedKeys),
                keyValue("addedBatches", addedBatches));
    }

    // TODO: rename service or make own service for this
    @Scheduled(initialDelayString = "${covid19.federation-gateway.upload-interval}",
            fixedRateString = "${covid19.federation-gateway.upload-interval}")
    public void runExportToEfgs() {
        LOG.info("Starting scheduled export to efgs.");
        fgs.doOutbound();
        LOG.info("Scheduled export to efgs finished.");
    }
}
