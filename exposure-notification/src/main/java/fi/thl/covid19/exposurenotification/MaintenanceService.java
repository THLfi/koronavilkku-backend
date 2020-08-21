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
        LOG.info("Initialized: tokenVerificationLifetime={}", tokenVerificationLifetime);
    }

    @Scheduled(initialDelayString = "${covid19.maintenance.interval}",
            fixedRateString = "${covid19.maintenance.interval}")
    public void runMaintenance() {
        BatchIntervals intervals = BatchIntervals.forGeneration();

        LOG.info("Cleaning keys and updating batch files: currentInterval={} firstFile={} lastFile={}",
                intervals.current, intervals.first, intervals.last);

        int removedKeys = dao.deleteKeysBefore(intervals.first);
        int removedVerifications = dao.deleteVerificationsBefore(Instant.now().minus(tokenVerificationLifetime));
        int removedBatches = batchFileStorage.deleteKeyBatchesBefore(intervals.first);
        int addedBatches = batchFileService.cacheMissingBatchesBetween(intervals.first, intervals.last);

        LOG.info("Batches updated: removedKeys={} removedVerifications={} removedBatches={} addedBatches={}",
                removedBatches, removedVerifications, removedKeys, addedBatches);
    }
}
