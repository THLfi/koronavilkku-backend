package fi.thl.covid19.exposurenotification.efgs;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.SimpleRequestExpectationManager;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao.MAX_RETRY_COUNT;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.DEFAULT_RISK_BUCKET;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationState;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayClient.BATCH_TAG_HEADER;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayClient.NEXT_BATCH_TAG_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
public class FederationServiceWithDaoTestIT {

    private static final MediaType PROTOBUF_MEDIATYPE = new MediaType("application", "protobuf", 1.0);

    @Autowired
    DiagnosisKeyDao diagnosisKeyDao;

    @Autowired
    OperationDao operationDao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    FederationGatewayService federationGatewayService;

    @Autowired
    FederationGatewayBatchSigner signer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    @Qualifier("federationGatewayRestTemplate")
    RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build(new SimpleRequestExpectationManager());
        keyGenerator = new TestKeyGenerator(123);
        diagnosisKeyDao.deleteKeysBefore(Integer.MAX_VALUE);
        diagnosisKeyDao.deleteVerificationsBefore(Instant.now().plus(24, ChronoUnit.HOURS));
        deleteOperations();
    }

    @Test
    public void downloadKeys() {
        List<TemporaryExposureKey> keys = FederationGatewayBatchUtil.transform(FederationGatewayBatchUtil.transform(keyGenerator.someKeys(10)));
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + FederationGatewayBatchUtil.getDateString(LocalDate.now(ZoneOffset.UTC))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders())
                        .body(serialize(transform(keys)))
                );
        federationGatewayService.startInbound(LocalDate.now(ZoneOffset.UTC), Optional.empty());

        List<TemporaryExposureKey> dbKeys = diagnosisKeyDao.getIntervalKeys(IntervalNumber.to24HourInterval(Instant.now()));
        assertTrue(keys.size() == dbKeys.size() && dbKeys.containsAll(keys) && keys.containsAll(dbKeys));
        assertDownloadOperationStateIsCorrect(10);
    }

    @Test
    public void downloadKeysTransmissionRiskLevel() {
        List<TemporaryExposureKey> keys = FederationGatewayBatchUtil.transform(FederationGatewayBatchUtil.transform(
                List.of(
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 1),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 1000),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 2000),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 3000),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 4000)
                )
        ));
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + FederationGatewayBatchUtil.getDateString(LocalDate.now(ZoneOffset.UTC))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders())
                        .body(serialize(transform(keys)))
                );
        federationGatewayService.startInbound(LocalDate.now(ZoneOffset.UTC), Optional.empty());

        List<TemporaryExposureKey> dbKeys = diagnosisKeyDao.getIntervalKeys(IntervalNumber.to24HourInterval(Instant.now()));
        assertEquals(dbKeys.size(), keys.size());
        assertTrue(dbKeys.get(0).daysSinceOnsetOfSymptoms.get() == 1 &&
                calculateTransmissionRisk(dayFirst10MinInterval(Instant.now().minus(1, ChronoUnit.DAYS)), 1) == dbKeys.get(0).transmissionRiskLevel);
        assertTrue(dbKeys.get(1).daysSinceOnsetOfSymptoms.isEmpty() && dbKeys.get(1).transmissionRiskLevel == DEFAULT_RISK_BUCKET);
        assertTrue(dbKeys.get(2).daysSinceOnsetOfSymptoms.isEmpty() && dbKeys.get(2).transmissionRiskLevel == DEFAULT_RISK_BUCKET);
        assertTrue(dbKeys.get(3).daysSinceOnsetOfSymptoms.isEmpty() && dbKeys.get(3).transmissionRiskLevel == DEFAULT_RISK_BUCKET);
        assertTrue(dbKeys.get(4).daysSinceOnsetOfSymptoms.isEmpty() && dbKeys.get(4).transmissionRiskLevel == DEFAULT_RISK_BUCKET);

    }

    @Test
    public void uploadKeys() {
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(Instant.now()), keyGenerator.someKeys(5), 5);
        assertUploadOperationStateIsCorrect(federationGatewayService.startOutbound(false).orElseThrow(), 5, 5, 0, 0);
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(true).size());
    }

    @Test
    public void uploadKeysPartialSuccess() throws Exception {
        List<TemporaryExposureKey> keys = keyGenerator.someKeys(5);
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(Instant.now()), keys, 5);

        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.MULTI_STATUS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(generateMultiStatusUploadResponseBody())
                );

        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .body("")
                );
        assertUploadOperationStateIsCorrect(federationGatewayService.startOutbound(false).orElseThrow(), 5, 2, 2, 1);
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(true).size());
    }

    @Test
    public void uploadKeysErrorState() {
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(Instant.now()), keyGenerator.someKeys(5), 5);

        try {
            federationGatewayService.startOutbound(false).orElseThrow();
        } catch (Exception e) {
            assertOperationErrorStateIsCorrect(EfgsOperationDirection.OUTBOUND);
        }
        Set<Long> operationIds = federationGatewayService.startOutbound(true).orElseThrow();
        assertUploadOperationErrorStateIsFinished(operationIds.stream().findFirst().get());
        assertEquals(1, getOutboundOperationsInError().size());
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
    }

    @Test
    public void uploadKeysRetriedUntilMaxRetryCount() {
        mockServer.expect(ExpectedCount.manyTimes(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(Instant.now()), keyGenerator.someKeys(5), 5);

        doOutBound();
        IntStream.range(1, MAX_RETRY_COUNT).forEach(this::doOutBoundRetry);
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(true).size());
        assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
    }

    private void doOutBound() {
        try {
            federationGatewayService.startOutbound(false).orElseThrow();
        } catch (Exception e) {
            assertOperationErrorStateIsCorrect(EfgsOperationDirection.OUTBOUND);
            assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
        }
    }

    private void doOutBoundRetry(int i) {
        try {
            federationGatewayService.startOutbound(true).orElseThrow();
        } catch (Exception e) {
            assertOperationErrorStateIsCorrect(EfgsOperationDirection.OUTBOUND);
            assertEquals(0, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).size());
            getAllDiagnosisKeys().forEach(key ->
                    assertEquals(i + 1, key.get("retry_count"))
            );
        }
    }

    private void assertDownloadOperationStateIsCorrect(int keysCount) {
        Map<String, Object> resultSet = getLatestOperation();
        assertEquals(EfgsOperationDirection.INBOUND.name(), resultSet.get("direction").toString());
        assertEquals(EfgsOperationState.FINISHED.name(), resultSet.get("state").toString());
        assertEquals(keysCount, resultSet.get("keys_count_total"));
    }

    private void assertUploadOperationStateIsCorrect(Set<Long> operationIds, int total, int c201, int c409, int c500) {
        assertFalse(operationIds.isEmpty());
        long operationId = operationIds.stream().findFirst().get();
        Map<String, Object> resultSet = getOperation(operationId);
        assertEquals(EfgsOperationDirection.OUTBOUND.name(), resultSet.get("direction").toString());
        assertEquals(EfgsOperationState.FINISHED.name(), resultSet.get("state").toString());
        assertEquals(total, resultSet.get("keys_count_total"));
        assertEquals(c201, resultSet.get("keys_count_201"));
        assertEquals(c409, resultSet.get("keys_count_409"));
        assertEquals(c500, resultSet.get("keys_count_500"));
    }

    private void assertOperationErrorStateIsCorrect(EfgsOperationDirection direction) {
        Map<String, Object> resultSet = getLatestOperation();
        assertEquals(direction.name(), resultSet.get("direction").toString());
        assertEquals(EfgsOperationState.ERROR.name(), resultSet.get("state").toString());
    }

    private void assertUploadOperationErrorStateIsFinished(long operationId) {
        Map<String, Object> resultSet = getOperation(operationId);
        assertEquals(EfgsOperationDirection.OUTBOUND.name(), resultSet.get("direction").toString());
        assertEquals(EfgsOperationState.FINISHED.name(), resultSet.get("state").toString());
    }

    private Map<String, Object> getLatestOperation() {
        String sql = "select * from en.efgs_operation order by updated_at desc limit 1";
        return jdbcTemplate.queryForMap(sql, Map.of());
    }

    private Map<String, Object> getOperation(long operationId) {
        String sql = "select * from en.efgs_operation where id = :id";
        return jdbcTemplate.queryForMap(sql, Map.of("id", operationId));
    }

    private List<Map<String, Object>> getAllDiagnosisKeys() {
        String sql = "select * from en.diagnosis_key";
        return jdbcTemplate.queryForList(sql, Map.of());
    }

    private String generateMultiStatusUploadResponseBody() throws Exception {
        FederationGatewayClient.UploadResponseEntityInner body = new FederationGatewayClient.UploadResponseEntityInner();
        body.putAll(Map.of(201, List.of(0, 1), 409, List.of(2, 3), 500, List.of(4)));
        return objectMapper.writeValueAsString(body);
    }

    private HttpHeaders getDownloadResponseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(NEXT_BATCH_TAG_HEADER, "null");
        headers.add(BATCH_TAG_HEADER, "test-1");
        return headers;
    }

    private void deleteOperations() {
        String sql = "delete from en.efgs_operation";
        jdbcTemplate.update(sql, Map.of());
    }

    private int calculateTransmissionRisk(int rollingStartIntervalNumber, int dsos) {
        LocalDate keyDate = utcDateOf10MinInterval(rollingStartIntervalNumber);
        return getRiskBucket(LocalDate.from(keyDate).plusDays(dsos), keyDate);
    }

    public List<Long> getOutboundOperationsInError() {
        String sql = "select id from en.efgs_operation " +
                "where state = cast(:state as en.state_t) " +
                "and direction = cast(:direction as en.direction_t) ";
        return jdbcTemplate.queryForList(sql, Map.of(
                "state", EfgsOperationState.ERROR.name(),
                "direction", EfgsOperationDirection.OUTBOUND.name()
        ), Long.class);
    }
}
