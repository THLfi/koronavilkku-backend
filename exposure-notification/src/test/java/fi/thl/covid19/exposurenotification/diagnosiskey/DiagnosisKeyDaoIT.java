package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.TokenValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * NOTE: These tests require the DB to be available and configured through ENV.
 */
@SpringBootTest
@ActiveProfiles({"dev","test"})
@AutoConfigureMockMvc
public class DiagnosisKeyDaoIT {

    @Autowired
    private DiagnosisKeyDao dao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(123);
        dao.deleteKeysBefore(Integer.MAX_VALUE);
        dao.deleteVerificationsBefore(Instant.now().plus(24, ChronoUnit.HOURS));
        deleteStatsRows();
    }

    @Test
    public void createReadDeleteWorks() {
        int interval = to24HourInterval(Instant.now());

        List<TemporaryExposureKey> keys = keyGenerator.someKeys(3);
        assertKeysNotStored(interval, keys);

        dao.addKeys(1, md5DigestAsHex("test".getBytes()), interval, keys, keys.size());
        assertKeysStored(interval, keys);
        assertStatRowAdded();

        dao.deleteKeysBefore(interval);
        assertKeysStored(interval, keys);
        assertStatRowAdded();

        dao.deleteKeysBefore(interval+1);
        assertKeysNotStored(interval, keys);
        assertStatRowAdded();
    }

    @Test
    public void emptyKeysListCreatesStatsRowOk() {
        int interval = to24HourInterval(Instant.now());
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), interval, Collections.emptyList(), 0);
        assertStatRowAdded();
        assertCountsAreStoredOk(0, 0);
    }

    @Test
    public void totalCountStatsStoredOk() {
        int interval = to24HourInterval(Instant.now());
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), interval, keyGenerator.someKeys(5), 4);
        assertStatRowAdded();
        assertCountsAreStoredOk(5, 4);
    }

    @Test
    public void intervalMetadataWorks() {
        assertEquals(List.of(), dao.getAvailableIntervals());
        assertEquals(0, dao.getKeyCount(1234));

        dao.addKeys(1, md5DigestAsHex("test".getBytes()), 1234, keyGenerator.someKeys(1), 1);
        assertEquals(List.of(1234), dao.getAvailableIntervals());
        assertEquals(1, dao.getKeyCount(1234));

        dao.addKeys(2, md5DigestAsHex("test2".getBytes()), 1235, keyGenerator.someKeys(2), 2);
        assertEquals(List.of(1234, 1235), dao.getAvailableIntervals());
        assertEquals(1, dao.getKeyCount(1234));
        assertEquals(2, dao.getKeyCount(1235));
        assertEquals(0, dao.getKeyCount(1236));

        dao.addKeys(3, md5DigestAsHex("test3".getBytes()), 1236, keyGenerator.someKeys(3), 3);
        assertEquals(List.of(1234, 1235, 1236), dao.getAvailableIntervals());
        assertEquals(3, dao.getKeyCount(1236));

        dao.addKeys(4, md5DigestAsHex("test4".getBytes()), 123, keyGenerator.someKeys(1), 1);
        assertEquals(List.of(123, 1234, 1235, 1236), dao.getAvailableIntervals());
        assertEquals(1, dao.getKeyCount(123));
    }

    @Test
    public void multipleInsertsWork() {
        int interval = to24HourInterval(Instant.now());

        List<TemporaryExposureKey> keys1 = keyGenerator.someKeys(3);
        List<TemporaryExposureKey> keys2 = keyGenerator.someKeys(3);

        assertKeysNotStored(interval, keys1);
        assertKeysNotStored(interval, keys2);

        assertDoesNotThrow(() -> dao.addKeys(1, md5DigestAsHex("test1".getBytes()), interval, keys1, keys1.size()));
        assertKeysStored(interval, keys1);
        assertKeysNotStored(interval, keys2);

        assertDoesNotThrow(() -> dao.addKeys(2, md5DigestAsHex("test2".getBytes()), interval, keys2, keys2.size()));
        assertKeysStored(interval, keys1);
        assertKeysStored(interval, keys2);
    }

    @Test
    public void doubleInsertWithSameVerificationIdAndHashIsOkWithNoChange() {
        int interval = to24HourInterval(Instant.now());

        List<TemporaryExposureKey> keys1 = keyGenerator.someKeys(3);
        assertDoesNotThrow(() -> dao.addKeys(1, md5DigestAsHex("test1".getBytes()), interval, keys1, keys1.size()));
        assertKeysStored(interval, keys1);

        List<TemporaryExposureKey> keys2 = keyGenerator.someKeys(3);
        assertDoesNotThrow(() -> dao.addKeys(1, md5DigestAsHex("test1".getBytes()), interval, keys2, keys2.size()));
        assertKeysNotStored(interval, keys2);
    }

    @Test
    public void doubleInsertWithSameVerificationIdAndDifferentHashFails() {
        int interval = to24HourInterval(Instant.now());

        List<TemporaryExposureKey> keys1 = keyGenerator.someKeys(3);
        assertDoesNotThrow(() -> dao.addKeys(1, "test1", interval, keys1, keys1.size()));
        assertKeysStored(interval, keys1);

        List<TemporaryExposureKey> keys2 = keyGenerator.someKeys(3);
        assertThrows(TokenValidationException.class,
                () -> dao.addKeys(1, "test2", interval, keys2, keys2.size()));
        assertKeysNotStored(interval, keys2);
    }

    @Test
    public void keysAreSorted() {
        int interval = to24HourInterval(Instant.now());
        TemporaryExposureKey key1 = new TemporaryExposureKey("c9Uau9icuBlvDvtokvlNaA==",
                2, interval, 144);
        TemporaryExposureKey key2 = new TemporaryExposureKey("0MwsNfC4Rgnl8SxV3YWrqA==",
                2, interval - 1, 144);
        TemporaryExposureKey key3 = new TemporaryExposureKey("1dm+92gI87Vy5ZABErgZJw==",
                2, interval - 2, 144);
        TemporaryExposureKey key4 = new TemporaryExposureKey("ulu19n4b2ii0BJvw5K7XjQ==",
                2, interval - 3, 144);

        // Expect ordering to be by key, not by insert order
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), interval, List.of(key1, key2, key3, key4), 4);
        List<TemporaryExposureKey> fromDb = dao.getIntervalKeys(interval);
        assertEquals(List.of(key2, key3, key1, key4), fromDb);
    }

    private void assertKeysStored(int interval, List<TemporaryExposureKey> keys) {
        List<TemporaryExposureKey> result = dao.getIntervalKeys(interval);
        for (TemporaryExposureKey key : keys) {
            assertTrue(result.stream().anyMatch(key::equals));
        }
    }

    private void assertKeysNotStored(int interval, List<TemporaryExposureKey> keys) {
        List<TemporaryExposureKey> result = dao.getIntervalKeys(interval);
        for (TemporaryExposureKey key : keys) {
            assertFalse(result.stream().anyMatch(key::equals));
        }
    }

    private void assertStatRowAdded() {
        String sql = "select count(*) from en.stats_report_keys";
        Integer rows = jdbcTemplate.queryForObject(sql, Collections.emptyMap(), Integer.class);
        assertEquals(1, rows);
    }

    private void assertCountsAreStoredOk(long totalKeyCount, long exportedKeyCount) {
        String sql = "select total_key_count, exported_key_count from en.stats_report_keys";
        Map<String, Object> resultSet = jdbcTemplate.queryForMap(sql, Collections.emptyMap());
        assertEquals(totalKeyCount, resultSet.get("total_key_count"));
        assertEquals(exportedKeyCount, resultSet.get("exported_key_count"));
    }

    private void deleteStatsRows() {
        jdbcTemplate.update("delete from en.stats_report_keys", Collections.emptyMap());
    }
}
