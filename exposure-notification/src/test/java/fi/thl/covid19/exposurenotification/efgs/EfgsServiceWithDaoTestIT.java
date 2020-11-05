package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.serialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
public class EfgsServiceWithDaoTestIT {

    @Autowired
    private DiagnosisKeyDao dao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    FederationGatewayService federationGatewayService;

    @MockBean
    FederationGatewayClient client;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(123);
        dao.deleteKeysBefore(Integer.MAX_VALUE);
        dao.deleteVerificationsBefore(Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Test
    public void downloadKeys() {
        List<TemporaryExposureKey> keys = FederationGatewayBatchUtil.transform(FederationGatewayBatchUtil.transform(keyGenerator.someKeys(10)));
        Mockito.when(client.download(anyString(), ArgumentMatchers.any())).thenReturn(generateDownloadData(keys));
        federationGatewayService.startInbound(Optional.empty());

        List<TemporaryExposureKey> dbKeys = dao.getIntervalKeys(IntervalNumber.to24HourInterval(Instant.now()));
        assertTrue(keys.size() == dbKeys.size() && dbKeys.containsAll(keys) && keys.containsAll(dbKeys));
        assertDownloadOperationStateIsCorrect(keys.get(1), 10);
    }

    @Test
    public void uploadKeys() {
        Mockito.when(client.upload(anyString(), anyString(), ArgumentMatchers.any())).thenReturn(generateOKUploadResponse());
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(Instant.now()), keyGenerator.someKeys(5), 5);
        assertUploadOperationStateIsCorrect(federationGatewayService.startOutbound(), 5, 5, 0, 0);
    }

    //@Test
    public void uploadKeysPartialSuccess() {
        Mockito.when(client.upload(anyString(), anyString(), ArgumentMatchers.any())).thenReturn(generateMultiStatusUploadResponse());
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(Instant.now()), keyGenerator.someKeys(5), 5);
        assertUploadOperationStateIsCorrect(federationGatewayService.startOutbound(), 5, 2, 2, 1);
    }

    private void assertDownloadOperationStateIsCorrect(TemporaryExposureKey key, int keysCount) {
        String sql = "select * from en.efgs_operation where id = (select efgs_operation from en.diagnosis_key where key_data = :key_data)";
        Map<String, Object> resultSet = jdbcTemplate.queryForMap(sql, Map.of("key_data", key.keyData));
        assertEquals(DiagnosisKeyDao.EfgsOperationDirection.INBOUND.name(), resultSet.get("direction").toString());
        assertEquals(DiagnosisKeyDao.EfgsOperationState.FINISHED.name(), resultSet.get("state").toString());
        assertEquals(keysCount, resultSet.get("keys_count_total"));
    }

    private List<byte[]> generateDownloadData(List<TemporaryExposureKey> keys) {
        return List.of(serialize(FederationGatewayBatchUtil.transform(keys)));
    }

    private ResponseEntity<UploadResponseEntity> generateOKUploadResponse() {
        return new ResponseEntity<>(
                new UploadResponseEntity(),
                HttpStatus.OK
        );
    }

    private ResponseEntity<UploadResponseEntity> generateMultiStatusUploadResponse() {
        UploadResponseEntity body = new UploadResponseEntity();
        body.putAll(Map.of(201, List.of(0, 1), 409, List.of(2, 3), 500, List.of(4)));
        return new ResponseEntity<>(
                body,
                HttpStatus.MULTI_STATUS
        );
    }

    private void assertUploadOperationStateIsCorrect(long operationId, int total, int c201, int c409, int c500) {
        String sql = "select * from en.efgs_operation where id = :id";
        Map<String, Object> resultSet = jdbcTemplate.queryForMap(sql, Map.of("id", operationId));
        assertEquals(DiagnosisKeyDao.EfgsOperationDirection.OUTBOUND.name(), resultSet.get("direction").toString());
        assertEquals(DiagnosisKeyDao.EfgsOperationState.FINISHED.name(), resultSet.get("state").toString());
        assertEquals(total, resultSet.get("keys_count_total"));
        assertEquals(c201, resultSet.get("keys_count_201"));
        assertEquals(c409, resultSet.get("keys_count_409"));
        assertEquals(c500, resultSet.get("keys_count_500"));
    }
}
