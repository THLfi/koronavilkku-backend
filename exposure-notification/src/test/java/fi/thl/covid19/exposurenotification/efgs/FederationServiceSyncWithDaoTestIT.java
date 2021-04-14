package fi.thl.covid19.exposurenotification.efgs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import fi.thl.covid19.exposurenotification.efgs.dao.InboundOperationDao;
import fi.thl.covid19.exposurenotification.efgs.dao.OutboundOperationDao;
import fi.thl.covid19.exposurenotification.efgs.entity.AuditEntry;
import fi.thl.covid19.exposurenotification.efgs.entity.OutboundOperation;
import fi.thl.covid19.exposurenotification.efgs.signing.FederationGatewaySigningDev;
import org.bouncycastle.cert.X509CertificateHolder;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.security.*;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.DEFAULT_RISK_BUCKET;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.FINISHED;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.EfgsOperationState.ERROR;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.MAX_RETRY_COUNT;
import static fi.thl.covid19.exposurenotification.efgs.util.CommonConst.STALLED_MIN_AGE_IN_MINUTES;
import static fi.thl.covid19.exposurenotification.efgs.util.SignatureHelperUtil.getCertThumbprint;
import static fi.thl.covid19.exposurenotification.efgs.util.BatchUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayClient.BATCH_TAG_HEADER;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayClient.NEXT_BATCH_TAG_HEADER;
import static fi.thl.covid19.exposurenotification.efgs.util.SignatureHelperUtil.x509CertificateToPem;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
public class FederationServiceSyncWithDaoTestIT {

    private static final MediaType PROTOBUF_MEDIATYPE = new MediaType("application", "protobuf", 1.0);
    private static final String TEST_TAG_NAME = "test-1";

    @Autowired
    DiagnosisKeyDao diagnosisKeyDao;

    @Autowired
    OutboundOperationDao outboundOperationDao;

    @Autowired
    InboundOperationDao inboundOperationDao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    OutboundService outboundService;

    @Autowired
    InboundService inboundService;

    @Autowired
    FederationGatewaySigningDev signer;

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

    @AfterEach
    public void tearDown() {
        mockServer.verify();
    }

    @Test
    public void downloadKeys() throws Exception {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        List<TemporaryExposureKey> keys1 = transform(transform(keyGenerator.someKeys(200)), currentInterval, currentIntervalV2);
        List<TemporaryExposureKey> keys2 = transform(transform(keyGenerator.someKeys(200)), currentInterval, currentIntervalV2);
        String date = getDateString(LocalDate.now(ZoneOffset.UTC));
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + date))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders(TEST_TAG_NAME, "test-2"))
                        .body(serialize(transform(keys1)))
                );
        generateAuditResponse(date, TEST_TAG_NAME, keys1);
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + date))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders("test-2", "null"))
                        .body(serialize(transform(keys2)))
                );
        generateAuditResponse(date, "test-2", keys2);

        inboundService.startInbound(LocalDate.now(ZoneOffset.UTC), Optional.empty());

        List<TemporaryExposureKey> dbKeys = diagnosisKeyDao.getIntervalKeys(to24HourInterval(Instant.now()));
        assertTrue(keys1.size() + keys2.size() == dbKeys.size() && dbKeys.containsAll(keys1) && dbKeys.containsAll(keys2));
        assertDownloadOperationStateIsCorrect(200);
    }

    @Test
    public void downloadRetry() throws Exception {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        List<TemporaryExposureKey> keys = transform(transform(keyGenerator.someKeys(10)), currentInterval, currentIntervalV2);
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String dateS = getDateString(date);
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + dateS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + dateS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders(TEST_TAG_NAME, "null"))
                        .body(serialize(transform(keys)))
                );
        generateAuditResponse(dateS, TEST_TAG_NAME, keys);

        try {
            inboundService.startInbound(date, Optional.of(TEST_TAG_NAME));
        } catch (HttpServerErrorException e) {
            fakeStalled();
            inboundService.startInboundRetry(date);
            assertTrue(inboundOperationDao.getInboundErrors(date).stream().noneMatch(op -> op.batchTag.get().equals(TEST_TAG_NAME)));
        }
    }

    @Test
    public void downloadRetryMaxLimit() {
        mockServer.expect(ExpectedCount.times(MAX_RETRY_COUNT),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + getDateString(LocalDate.now(ZoneOffset.UTC))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        try {
            inboundService.startInbound(date, Optional.of(TEST_TAG_NAME));
        } catch (HttpServerErrorException e) {
            fakeStalled();
            assertEquals(1, inboundOperationDao.getInboundErrors(date).size());
            inboundOperationDao.resolveStarted();
        }

        for (int i = 2; i <= MAX_RETRY_COUNT; i++) {
            try {
                inboundService.startInboundRetry(date);
            } catch (HttpServerErrorException e) {
                fakeStalled();
                if (i < MAX_RETRY_COUNT) {
                    assertEquals(1, inboundOperationDao.getInboundErrors(date).size());
                    inboundOperationDao.resolveStarted();
                } else {
                    assertEquals(0, inboundOperationDao.getInboundErrors(date).size());
                }
            }
        }
    }

    @Test
    public void downloadKeysTransmissionRiskLevel() throws Exception {
        String date = getDateString(LocalDate.now(ZoneOffset.UTC));
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        List<TemporaryExposureKey> keys = transform(transform(
                List.of(
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 1, Optional.of(true), currentInterval, currentIntervalV2),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 2, Optional.of(false), currentInterval, currentIntervalV2),
                        keyGenerator.someKey(1, 0x7FFFFFFF, true, 3, currentInterval, currentIntervalV2)
                )
        ), currentInterval, currentIntervalV2);
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + date))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders(TEST_TAG_NAME, "null"))
                        .body(serialize(transform(keys)))
                );
        generateAuditResponse(date, TEST_TAG_NAME, keys);
        inboundService.startInbound(LocalDate.now(ZoneOffset.UTC), Optional.empty());

        List<TemporaryExposureKey> dbKeys = diagnosisKeyDao.getIntervalKeys(to24HourInterval(Instant.now()));
        assertEquals(dbKeys.size(), 210 + keys.size());

        TemporaryExposureKey dbKey0 = dbKeys.stream().filter(key -> key.keyData.equals(keys.get(0).getKeyData())).findAny().orElseThrow();
        TemporaryExposureKey dbKey1 = dbKeys.stream().filter(key -> key.keyData.equals(keys.get(1).getKeyData())).findAny().orElseThrow();
        TemporaryExposureKey dbKey2 = dbKeys.stream().filter(key -> key.keyData.equals(keys.get(2).getKeyData())).findAny().orElseThrow();
        assertTrue(dbKey0.daysSinceOnsetOfSymptoms.get() == 1 &&
                calculateTransmissionRisk(dayFirst10MinInterval(Instant.now().minus(1, ChronoUnit.DAYS)), 1) == dbKey0.transmissionRiskLevel);
        assertTrue(dbKey1.daysSinceOnsetOfSymptoms.isEmpty() && dbKey1.transmissionRiskLevel == DEFAULT_RISK_BUCKET);
        assertTrue(dbKey2.daysSinceOnsetOfSymptoms.isEmpty() && dbKey2.transmissionRiskLevel == DEFAULT_RISK_BUCKET);
    }

    @Test
    public void downloadValidationFailsWithWrongPublicKey() throws Exception {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        List<TemporaryExposureKey> keys = transform(transform(keyGenerator.someKeys(10)), currentInterval, currentIntervalV2);
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String dateS = getDateString(date);
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/download/" + dateS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(PROTOBUF_MEDIATYPE)
                        .headers(getDownloadResponseHeaders(TEST_TAG_NAME, "null"))
                        .body(serialize(transform(keys)))
                );
        generateAuditResponseWithWrongKey(dateS, TEST_TAG_NAME, keys);
        inboundService.startInbound(date, Optional.of(TEST_TAG_NAME));
        Map<String, Object> operation = getLatestInboundOperation();
        assertEquals(operation.get("invalid_signature_count"), 10);
        assertEquals(operation.get("keys_count_total"), 10);
        assertEquals(operation.get("keys_count_success"), 0);
        assertEquals(operation.get("keys_count_not_valid"), 0);
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
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5, to24HourInterval(now), toV2Interval(now)), 5);
        Set<Long> operationIds = outboundService.startOutbound(false);
        assertFalse(operationIds.isEmpty());
        long operationId = operationIds.stream().findFirst().get();
        assertUploadOperationStateIsCorrect(operationId, 201, 201, 0, 0);
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(true).isEmpty());
        assertCrashDoNotReturnKeys();
    }

    @Test
    public void uploadKeysPartialSuccess() throws JsonProcessingException {
        Instant now = Instant.now();
        List<TemporaryExposureKey> keys = keyGenerator.someKeys(603, to24HourInterval(now), toV2Interval(now));
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()), to24HourInterval(now), toV2Interval(now), keys, 603);

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
        Set<Long> operationIds = outboundService.startOutbound(false);
        assertFalse(operationIds.isEmpty());
        long operationId = operationIds.stream().findFirst().get();
        assertUploadOperationStateIsCorrect(operationId, 603, 200, 201, 202);
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        assertEquals(403, diagnosisKeyDao.fetchAvailableKeysForEfgs(true).get().keys.size());
        assertCrashDoNotReturnKeys();
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
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5, to24HourInterval(now), toV2Interval(now)), 5);

        try {
            outboundService.startOutbound(false);
        } catch (HttpServerErrorException e) {
            assertOutboundOperationErrorStateIsCorrect();
        }
        Set<Long> operationIds = outboundService.startOutbound(true);
        assertUploadOperationErrorStateIsFinished(operationIds.stream().findFirst().get());
        assertEquals(1, getOutboundOperationsInError().size());
        assertEquals(210, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys.size());
        assertCrashDoNotReturnKeys();
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
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(500, to24HourInterval(now), toV2Interval(now)), 500);

        doOutBound();
        IntStream.range(1, MAX_RETRY_COUNT).forEach(this::doOutBoundRetry);
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(true).isEmpty());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        assertCrashDoNotReturnKeys();
    }

    @Test
    public void uploadInBatch() {
        mockServer.expect(ExpectedCount.twice(),
                requestTo("http://localhost:8080/diagnosiskeys/upload/"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("")
                );
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(10000, to24HourInterval(now), toV2Interval(now)), 10000);

        Set<Long> operationIds = outboundService.startOutbound(false);
        assertFalse(operationIds.isEmpty());
        assertEquals(2, operationIds.size());
        operationIds.forEach(id -> assertUploadOperationStateIsCorrect(id, 5000, 5000, 0, 0));
        assertCrashDoNotReturnKeys();
    }

    @Test
    public void fetchPartialBatch() {
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5005, to24HourInterval(now), toV2Interval(now)), 5005);

        List<TemporaryExposureKey> batch1 = diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys;
        List<TemporaryExposureKey> batch2 = diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys;

        assertEquals(5000, batch1.size());
        assertEquals(201, batch2.size());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
    }

    @Test
    public void crashDoNotReturnKeys() {
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5, to24HourInterval(now), toV2Interval(now)), 5);
        assertEquals(201, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys.size());
        assertCrashDoNotReturnKeys();
    }

    @Test
    public void crashDoReturnKeys() {
        Instant now = Instant.now();
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5, to24HourInterval(now), toV2Interval(now)), 5);
        assertEquals(201, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys.size());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        fakeStalled();
        diagnosisKeyDao.resolveOutboundCrash();
        assertEquals(201, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys.size());
    }

    @Test
    public void keysWithoutConsentWillNotBeFetched() {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        diagnosisKeyDao.addKeys(1, md5DigestAsHex("test1".getBytes()),
                currentInterval, currentIntervalV2, keyGenerator.someKeys(5, currentInterval, currentIntervalV2, 5, false), 5);
        diagnosisKeyDao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                to24HourInterval(now), toV2Interval(now), keyGenerator.someKeys(5, currentInterval, currentIntervalV2, 5, true), 5);

        assertEquals(10, getAllDiagnosisKeys().size());
        OutboundOperation operation = diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get();
        List<TemporaryExposureKey> toEfgs1 = operation.keys;
        assertEquals(201, toEfgs1.size());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        diagnosisKeyDao.setNotSent(operation);
        assertEquals(210, diagnosisKeyDao.fetchAvailableKeysForEfgs(false).get().keys.size());
        List<TemporaryExposureKey> toEfgs2 = diagnosisKeyDao.fetchAvailableKeysForEfgs(true).get().keys;
        assertEquals(5, toEfgs2.size());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(true).isEmpty());
    }

    private void assertCrashDoNotReturnKeys() {
        diagnosisKeyDao.resolveOutboundCrash();
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(true).isEmpty());
        assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
    }

    private void doOutBound() {
        try {
            outboundService.startOutbound(false);
        } catch (HttpServerErrorException e) {
            assertOutboundOperationErrorStateIsCorrect();
            assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
        }
    }

    private void doOutBoundRetry(int i) {
        try {
            outboundService.startOutbound(true);
        } catch (HttpServerErrorException e) {
            assertOutboundOperationErrorStateIsCorrect();
            assertTrue(diagnosisKeyDao.fetchAvailableKeysForEfgs(false).isEmpty());
            getAllDiagnosisKeys().forEach(key ->
                    assertEquals(i + 1, key.get("retry_count"))
            );
        }
    }

    private void assertDownloadOperationStateIsCorrect(int keysCount) {
        Map<String, Object> resultSet = getLatestInboundOperation();
        assertEquals(FINISHED.name(), resultSet.get("state").toString());
        assertEquals(keysCount, resultSet.get("keys_count_total"));
    }

    private void assertUploadOperationStateIsCorrect(long operationId, int total, int c201, int c409, int c500) {
        Map<String, Object> resultSet = getOutboundOperation(operationId);
        assertEquals(FINISHED.name(), resultSet.get("state").toString());
        assertEquals(total, resultSet.get("keys_count_total"));
        assertEquals(c201, resultSet.get("keys_count_201"));
        assertEquals(c409, resultSet.get("keys_count_409"));
        assertEquals(c500, resultSet.get("keys_count_500"));
    }

    private void assertOutboundOperationErrorStateIsCorrect() {
        Map<String, Object> resultSet = getLatestOutboundOperation();
        assertEquals(ERROR.name(), resultSet.get("state").toString());
    }

    private void assertUploadOperationErrorStateIsFinished(long operationId) {
        Map<String, Object> resultSet = getOutboundOperation(operationId);
        assertEquals(FINISHED.name(), resultSet.get("state").toString());
    }

    private Map<String, Object> getLatestOutboundOperation() {
        String sql = "select * from en.efgs_outbound_operation order by updated_at desc limit 1";
        return jdbcTemplate.queryForMap(sql, Map.of());
    }

    private Map<String, Object> getOutboundOperation(long operationId) {
        String sql = "select * from en.efgs_outbound_operation where id = :id";
        return jdbcTemplate.queryForMap(sql, Map.of("id", operationId));
    }

    private Map<String, Object> getLatestInboundOperation() {
        String sql = "select * from en.efgs_inbound_operation order by updated_at desc limit 1";
        return jdbcTemplate.queryForMap(sql, Map.of());
    }

    private List<Map<String, Object>> getAllDiagnosisKeys() {
        String sql = "select * from en.diagnosis_key";
        return jdbcTemplate.queryForList(sql, Map.of());
    }

    private String generateMultiStatusUploadResponseBody() throws JsonProcessingException {
        FederationGatewayClient.UploadResponseEntityInner body = new FederationGatewayClient.UploadResponseEntityInner();
        List<Integer> successIds = IntStream.rangeClosed(0, 199).boxed().collect(Collectors.toList());
        List<Integer> ids409 = IntStream.rangeClosed(200, 400).boxed().collect(Collectors.toList());
        List<Integer> ids500 = IntStream.rangeClosed(401, 602).boxed().collect(Collectors.toList());
        body.putAll(Map.of(201, successIds, 409, ids409, 500, ids500));
        return objectMapper.writeValueAsString(body);
    }

    private HttpHeaders getDownloadResponseHeaders(String current, String next) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(NEXT_BATCH_TAG_HEADER, next);
        headers.add(BATCH_TAG_HEADER, current);
        return headers;
    }

    private void deleteOperations() {
        String outbound = "delete from en.efgs_outbound_operation";
        jdbcTemplate.update(outbound, Map.of());
        String inbound = "delete from en.efgs_inbound_operation";
        jdbcTemplate.update(inbound, Map.of());
    }

    private int calculateTransmissionRisk(int rollingStartIntervalNumber, int dsos) {
        LocalDate keyDate = utcDateOf10MinInterval(rollingStartIntervalNumber);
        return getRiskBucket(LocalDate.from(keyDate).plusDays(dsos), keyDate);
    }

    public List<Long> getOutboundOperationsInError() {
        String sql = "select id from en.efgs_outbound_operation " +
                "where state = cast(:state as en.state_t)";
        return jdbcTemplate.queryForList(sql, Map.of(
                "state", ERROR.name()
        ), Long.class);
    }

    private void fakeStalled() {
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minus(STALLED_MIN_AGE_IN_MINUTES * 2, MINUTES));
        String sql1 = "update en.efgs_inbound_operation set updated_at = :updated_at";
        jdbcTemplate.update(sql1, Map.of("updated_at", timestamp));
        String sql2 = "update en.efgs_outbound_operation set updated_at = :updated_at";
        jdbcTemplate.update(sql2, Map.of("updated_at", timestamp));
        String sql3 = "update en.diagnosis_key set efgs_sync = :efgs_sync";
        jdbcTemplate.update(sql3, Map.of("efgs_sync", timestamp));
    }

    private void generateAuditResponse(String date,
                                       String batchTag,
                                       List<TemporaryExposureKey> keys
    ) throws Exception {
        X509Certificate certificate = signer.getSignerCertificate();
        PrivateKey key = signer.getTrustAnchorPrivateKey();
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initSign(key);
        signature.update(x509CertificateToPem(certificate).getBytes());
        addAuditMockExpect(certificate, keys, signature, date, batchTag);
    }

    private void generateAuditResponseWithWrongKey(String date,
                                                   String batchTag,
                                                   List<TemporaryExposureKey> keys
    ) throws Exception {
        KeyPair trustAnchorKeyPair = signer.generateKeyPair();
        X509Certificate trustAnchorCert = signer.generateDevRootCertificate(trustAnchorKeyPair);
        KeyPair keyPair = signer.generateKeyPair();
        X509Certificate certificate = signer.generateDevCertificate(keyPair, trustAnchorKeyPair, trustAnchorCert);
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Signature signature = Signature.getInstance("SHA256withRSA", "BC");
        signature.initSign(trustAnchorKeyPair.getPrivate());
        signature.update(x509CertificateToPem(certificate).getBytes());
        addAuditMockExpect(certificate, keys, signature, date, batchTag);
    }

    private void addAuditMockExpect(
            X509Certificate certificate,
            List<TemporaryExposureKey> keys,
            Signature signature,
            String date,
            String batchTag
    ) throws Exception {
        AuditEntry auditEntry = new AuditEntry(
                "FI",
                ZonedDateTime.now(),
                "",
                "",
                getCertThumbprint(new X509CertificateHolder(certificate.getEncoded())),
                keys.size(),
                signer.sign(transform(keys)),
                "",
                new String(Base64.getEncoder().encode(signature.sign())),
                x509CertificateToPem(certificate)
        );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/audit/download/" + date + "/" + batchTag))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(List.of(auditEntry)))
                );
    }
}
