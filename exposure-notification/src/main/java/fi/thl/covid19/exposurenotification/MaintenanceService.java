package fi.thl.covid19.exposurenotification;

import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.batch.BatchFileStorage;
import fi.thl.covid19.exposurenotification.batch.BatchIntervals;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
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

    private final Duration tokenVerificationLifetime;

    public MaintenanceService(DiagnosisKeyDao dao,
                              BatchFileService batchFileService,
                              BatchFileStorage batchFileStorage,
                              @Value("${covid19.maintenance.token-verification-lifetime}") Duration tokenVerificationLifetime) {
        this.dao = requireNonNull(dao);
        this.batchFileStorage = requireNonNull(batchFileStorage);
        this.batchFileService = requireNonNull(batchFileService);
        this.tokenVerificationLifetime = requireNonNull(tokenVerificationLifetime);
        LOG.info("Initialized: {}", keyValue("tokenVerificationLifetime", tokenVerificationLifetime));
    }

    @Scheduled(initialDelayString = "${covid19.maintenance.interval}",
            fixedRateString = "${covid19.maintenance.interval}")
    public void runMaintenance() {
        BatchIntervals intervals = BatchIntervals.forGeneration();
        BatchIntervals intervalsV2 = BatchIntervals.forGenerationV2();

        LOG.info("Cleaning keys and updating batch files: {} {} {}",
                keyValue("currentInterval", intervals.current),
                keyValue("firstFile", intervals.first),
                keyValue("lastFile", intervals.last));

        LOG.info("Cleaning keys and updating batch files for V2: {} {} {}",
                keyValue("currentInterval", intervalsV2.current),
                keyValue("firstFile", intervalsV2.first),
                keyValue("lastFile", intervalsV2.last));

        int removedKeys = dao.deleteKeysBefore(intervals.first);
        int removedVerifications = dao.deleteVerificationsBefore(Instant.now().minus(tokenVerificationLifetime));
        int removedBatches = batchFileStorage.deleteKeyBatchesBefore(intervals.first);
        int addedBatches = batchFileService.cacheMissingBatchesBetween(intervals.first, intervals.last);
        int addedBatchesV2 = batchFileService.cacheMissingBatchesBetweenV2(intervalsV2.first, intervalsV2.last);

        LOG.info("Batches updated: {} {} {} {} {}",
                keyValue("removedKeys", removedBatches),
                keyValue("removedVerifications", removedVerifications),
                keyValue("removedBatches", removedKeys),
                keyValue("addedBatches", addedBatches),
                keyValue("addedBatchesV2", addedBatchesV2));
    }
}
