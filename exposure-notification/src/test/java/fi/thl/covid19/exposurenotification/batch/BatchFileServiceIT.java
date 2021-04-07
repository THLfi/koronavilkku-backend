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
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.from24hourToV2Interval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.fromV2to24hourInterval;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: These tests require the DB to be available and configured through ENV.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
public class BatchFileServiceIT {

    private static final BatchIntervals INTERVALS = BatchIntervals.forExport(false);
    private static final BatchIntervals INTERVALS_V2 = BatchIntervals.forExportV2(false);

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
            dao.addKeys(next, "TEST" + next, next, from24hourToV2Interval(next), keyGenerator.someKeys(5, next, from24hourToV2Interval(next)), 5);
            assertFalse(fileStorage.fileExists(new BatchId(next)));
            fileService.cacheMissingBatchesBetween(INTERVALS.first, INTERVALS.last);
            assertTrue(fileStorage.fileExists(new BatchId(next)));
        }
    }

    @Test
    public void generateBatchesWorksV2() {
        for (int next = INTERVALS_V2.first; next <= INTERVALS_V2.last; next++) {
            dao.addKeys(next, "TEST" + next, fromV2to24hourInterval(next), next, keyGenerator.someKeys(5, fromV2to24hourInterval(next), next), 5);
            assertFalse(fileStorage.fileExists(new BatchId(fromV2to24hourInterval(next), Optional.of(next))));
            fileService.cacheMissingBatchesBetweenV2(INTERVALS_V2.first, INTERVALS_V2.last);
            assertTrue(fileStorage.fileExists(new BatchId(fromV2to24hourInterval(next), Optional.of(next))));
        }
    }
}
