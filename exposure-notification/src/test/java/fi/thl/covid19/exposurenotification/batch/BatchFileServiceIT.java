package fi.thl.covid19.exposurenotification.batch;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: These tests require the DB to be available and configured through ENV.
 */
@SpringBootTest
@ActiveProfiles({"test"})
@AutoConfigureMockMvc
public class BatchFileServiceIT {

    private static final BatchIntervals INTERVALS = BatchIntervals.forExport(false);

    @Autowired
    private BatchFileService fileService;

    @Autowired
    private BatchFileStorage fileStorage;

    @Autowired
    private DiagnosisKeyDao dao;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(123);
        dao.deleteKeysBefore(Integer.MAX_VALUE);
        dao.deleteVerificationsBefore(Instant.now().plus(24, HOURS));
    }

    @Test
    public void generateBatchesWorks() {
        for (int next = INTERVALS.first; next <= INTERVALS.last; next++) {
            dao.addKeys(next, "TEST" + next, next, keyGenerator.someKeys(5));
            assertFalse(fileStorage.fileExists(new BatchId(next)));
            fileService.cacheMissingBatchesBetween(INTERVALS.first, INTERVALS.last);
            assertTrue(fileStorage.fileExists(new BatchId(next)));
        }
    }
}
