package fi.thl.covid19.exposurenotification.diagnosiskey;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.batch.BatchFile;
import fi.thl.covid19.exposurenotification.batch.BatchFileStorage;
import fi.thl.covid19.exposurenotification.batch.BatchId;
import fi.thl.covid19.exposurenotification.batch.BatchIntervals;
import fi.thl.covid19.exposurenotification.configuration.ConfigurationService;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.*;
import fi.thl.covid19.exposurenotification.error.InputValidationException;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerification;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.dayFirst10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.v1.DiagnosisKeyController.FAKE_REQUEST_HEADER;
import static fi.thl.covid19.exposurenotification.diagnosiskey.v1.DiagnosisKeyController.PUBLISH_TOKEN_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * NOTE: These tests require the DB to be available and configured through ENV.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
public class DiagnosisKeyControllerIT {

    private static final String BASE_URL = "/diagnosis/v1";
    private static final String CURRENT_URL = BASE_URL + "/current";
    private static final String LIST_URL = BASE_URL + "/list?previous=";
    private static final String STATUS_URL = BASE_URL + "/status?batch=";
    private static final String BATCH_URL = BASE_URL + "/batch";

    private static final BatchIntervals INTERVALS = BatchIntervals.forExport(false);

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiagnosisKeyDao dao;

    @Autowired
    private ConfigurationService configService;

    @MockBean
    private PublishTokenVerificationService tokenVerificationService;

    @Autowired
    private BatchFileStorage storage;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(123);
        storage.deleteKeyBatchesBefore(Integer.MAX_VALUE);
        dao.deleteKeysBefore(Integer.MAX_VALUE);
        dao.deleteVerificationsBefore(Instant.now().plus(24, HOURS));
    }

    @AfterEach
    public void tearDown() {
        Mockito.verifyNoMoreInteractions(tokenVerificationService);
    }

    @Test
    public void currentWithNoDataReturnsOk() throws Exception {
        assertCurrent(new BatchId(INTERVALS.last));
    }

    @Test
    public void listWithNoDataReturnsEmptyArray() throws Exception {
        assertListing(BatchId.DEFAULT, List.of());
        assertListing(new BatchId(INTERVALS.first), List.of());
        assertListing(new BatchId(INTERVALS.last), List.of());
    }

    @Test
    public void listWithKeysReturnsBatchIds() throws Exception {
        BatchId batchId1 = new BatchId(INTERVALS.last - 1);
        BatchId batchId2 = new BatchId(INTERVALS.last);

        dao.addKeys(1, md5DigestAsHex("test1".getBytes()),
                batchId1.intervalNumber, keyGenerator.someKeys(1), 1);

        assertListing(BatchId.DEFAULT, List.of(batchId1));
        assertListing(new BatchId(INTERVALS.first), List.of(batchId1));
        assertListing(batchId1, List.of());

        dao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                batchId2.intervalNumber, keyGenerator.someKeys(2), 2);

        assertListing(BatchId.DEFAULT, List.of(batchId1, batchId2));
        assertListing(new BatchId(INTERVALS.first), List.of(batchId1, batchId2));
        assertListing(batchId1, List.of(batchId2));
        assertListing(batchId2, List.of());
    }

    @Test
    public void statusWithKeysReturnsBatchIds() throws Exception {
        BatchId batchId1 = new BatchId(INTERVALS.last - 1);
        BatchId batchId2 = new BatchId(INTERVALS.last);

        dao.addKeys(1, md5DigestAsHex("test1".getBytes()),
                batchId1.intervalNumber, keyGenerator.someKeys(1), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1));
        assertStatus(new BatchId(INTERVALS.first), List.of(batchId1));
        assertStatus(batchId1, List.of());

        dao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                batchId2.intervalNumber, keyGenerator.someKeys(1), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1, batchId2));
        assertStatus(new BatchId(INTERVALS.first), List.of(batchId1, batchId2));
        assertStatus(batchId1, List.of(batchId2));
        assertStatus(batchId2, List.of());
    }

    @Test
    public void newBatchIsGeneratedFromKeys() throws Exception {
        BatchId batch = new BatchId(INTERVALS.last);
        assertNoFile(batch);
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), INTERVALS.last, keyGenerator.someKeys(1), 1);
        assertFileExists(batch);
    }

    @Test
    public void missingFileReturns404() throws Exception {
        assertNoFile(new BatchId(123456));
    }

    @Test
    public void existingFileIsReturnedFromCache() throws Exception {
        BatchId id = new BatchId(INTERVALS.last - 1);
        byte[] content = "TEST CONTENT".getBytes(UTF_8);
        assertNoFile(id);
        storage.addBatchFile(id, content);
        assertFileContent(id, content);
    }

    @Test
    public void postSucceedsWithDefaultEfgsData() throws Exception {
        processPost(Optional.empty(), Optional.empty(), true);
    }

    @Test
    public void postWithEfgsDataSucceedsAndVerifyConsentFalse() throws Exception {
        processPost(Optional.of(Map.of("DE", true, "IT", false)), Optional.of(false), true);
    }

    @Test
    public void postWithEfgsDataSucceedsAndVerifyConsentTrue() throws Exception {
        processPost(Optional.of(Map.of("DE", true, "IT", false)), Optional.of(true), true);
    }

    @Test
    public void postWithEfgsDataFailsWithInvalidCountryCode() throws Exception {
        processPost(Optional.of(Map.of("XX", true, "IT", false)), Optional.of(false), false);
    }

    @Test
    public void postWithEfgsDataFailsWithFinnishCountryCode() throws Exception {
        processPost(Optional.of(Map.of("FI", true, "IT", false)), Optional.of(false), false);
    }

    @Test
    public void batchFetchingSucceeds() throws Exception {
        int interval = to24HourInterval(Instant.now()) - 1;
        dao.addKeys(123, "TEST", interval, keyGenerator.someKeys(14), 14);
        mockMvc.perform(get("/diagnosis/v1/batch/" + interval))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    public void validRetryIsNotAnError() throws Exception {
        PublishTokenVerification verification = new PublishTokenVerification(1, LocalDate.now().minus(7, DAYS));
        given(tokenVerificationService.getVerification("123654032165")).willReturn(verification);
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.of(
                Map.of("DE", false, "IT", true)
        ),
                Optional.of(true));
        verifiedPost("123654032165", request);
        verifiedPost("123654032165", request);
        verify(tokenVerificationService, times(2)).getVerification("123654032165");
    }

    @Test
    public void reusingTokenForDifferentRequestIs403() throws Exception {
        PublishTokenVerification verification = new PublishTokenVerification(1, LocalDate.now().minus(7, DAYS));
        given(tokenVerificationService.getVerification("123654032165")).willReturn(verification);
        DiagnosisPublishRequest request1 = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        DiagnosisPublishRequest request2 = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        verifiedPost("123654032165", request1);
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "123654032165")
                .header(FAKE_REQUEST_HEADER, 0)
                .content(mapper.writeValueAsString(request2)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Publish token not accepted")));
        verify(tokenVerificationService, times(2)).getVerification("123654032165");
    }

    @Test
    public void invalidDiagnosisKeyBatchIdIsError400() throws Exception {
        mockMvc.perform(get("/diagnosis/v1/batch/asdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }

    @Test
    public void missingDiagnosisKeyBatchIs404() throws Exception {
        mockMvc.perform(get("/diagnosis/v1/batch/123456"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Batch not available:")));
    }

    @Test
    public void invalidPublishTokenIs400() throws Exception {
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "123")
                .header(FAKE_REQUEST_HEADER, 0)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid publish token")));
    }

    @Test
    public void tooFewOrTooManyKeysIsInvalidInput() {
        Assertions.assertThrows(InputValidationException.class,
                () -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(13), Optional.empty(), Optional.empty()));
        Assertions.assertDoesNotThrow(
                () -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty()));
        Assertions.assertThrows(InputValidationException.class,
                () -> new DiagnosisPublishRequest(keyGenerator.someRequestKeys(15), Optional.empty(), Optional.empty()));
    }

    @Test
    public void noKeysIsInvalidInput() {
        Assertions.assertThrows(InputValidationException.class,
                () -> new DiagnosisPublishRequest(List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    public void fakeRequestIsValidRegardlessOfToken() throws Exception {
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .header(FAKE_REQUEST_HEADER, 1)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verifyNoInteractions(tokenVerificationService);
    }

    @Test
    public void missingFakeRequestHeaderIsError400() throws Exception {
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Request binding failed")));
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .header(FAKE_REQUEST_HEADER, "")
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }

    @Test
    public void invalidFakeRequestHeaderIsError400() throws Exception {
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(keyGenerator.someRequestKeys(14), Optional.empty(), Optional.empty());
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .header(FAKE_REQUEST_HEADER, "abc")
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .header(FAKE_REQUEST_HEADER, 2)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, "098765432109")
                .header(FAKE_REQUEST_HEADER, -1)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }

    private void processPost(Optional<Map<String, Boolean>> visitedCountries,
                             Optional<Boolean> consentToShareWithEfgs,
                             boolean expectVisitedCountriesToSuccee) throws Exception {
        List<TemporaryExposureKeyRequest> keys = keyGenerator.someRequestKeys(14, 7);
        // Generator produces appropriate risk levels. Set them all to zero to verify that service calculates them OK.
        DiagnosisPublishRequest request = new DiagnosisPublishRequest(resetRiskLevelsRequest(keys, 0), visitedCountries, consentToShareWithEfgs);

        assertTrue(dao.getAvailableIntervals().isEmpty());
        PublishTokenVerification verification = new PublishTokenVerification(1, LocalDate.now().minus(7, DAYS));
        given(tokenVerificationService.getVerification("123654032165")).willReturn(verification);
        verifiedPost("123654032165", request);
        verify(tokenVerificationService).getVerification("123654032165");

        List<Integer> available = dao.getAvailableIntervals();
        assertEquals(1, available.size());
        List<TemporaryExposureKeyRequest> expectedOutput = keys.stream()
                // 0-risk keys (transmission risk level in extremes) are not distributed
                .filter(k -> k.transmissionRiskLevel > 0 && k.transmissionRiskLevel < 7)
                // Todays keys are not distributed without demo-mode
                .filter(k -> k.rollingPeriod < dayFirst10MinInterval(Instant.now()))
                .collect(Collectors.toList());
        // Filtered should be base 14 -4 due to risk levels -1 since it's current day
        assertEquals(9, expectedOutput.size());
        // Also, the order of exported keys is random -> sort here for clearer comparison
        List<TemporaryExposureKey> intervalKeys = dao.getIntervalKeys(available.get(0));
        assertEquals(sortByIntervalRequest(expectedOutput), sortByInterval(intervalKeys));
        visitedCountries.ifPresentOrElse(
                vc -> verifyVisitedCountries(intervalKeys, vc, expectVisitedCountriesToSuccee),
                () -> verifyVisitedCountries(intervalKeys, Map.of(), expectVisitedCountriesToSuccee)
        );
        consentToShareWithEfgs.ifPresentOrElse(
                c -> verifyConsentToShare(intervalKeys, c),
                () -> verifyConsentToShare(intervalKeys, false)
        );
    }

    private void verifyVisitedCountries(List<TemporaryExposureKey> keys, Map<String, Boolean> visitedCountries, boolean expectVisitedCountriesToSuccee) {
        assertEquals(expectVisitedCountriesToSuccee, keys.stream().allMatch(key ->
                key.visitedCountries.equals(
                        visitedCountries.entrySet().stream()
                                .filter(Map.Entry::getValue)
                                .map(Map.Entry::getKey).collect(Collectors.toSet())))
        );
    }

    private void verifyConsentToShare(List<TemporaryExposureKey> keys, boolean consentToShareWithEfgs) {
        assertTrue(keys.stream().allMatch(key -> key.consentToShareWithEfgs == consentToShareWithEfgs));
    }

    private void verifiedPost(String publishToken, DiagnosisPublishRequest request) throws Exception {
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PUBLISH_TOKEN_HEADER, publishToken)
                .header(FAKE_REQUEST_HEADER, 0)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void assertCurrent(BatchId expected) throws Exception {
        CurrentBatch current = new CurrentBatch(expected);
        mockMvc.perform(get(CURRENT_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=900, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(current)));
    }

    private void assertListing(BatchId previous, List<BatchId> expected) throws Exception {
        BatchList list = new BatchList(expected);
        mockMvc.perform(get(LIST_URL + previous))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(list)));
    }

    private void assertStatus(BatchId previous, List<BatchId> expected) throws Exception {
        Status status = new Status(expected,
                Optional.of(configService.getLatestAppConfig()),
                Optional.of(configService.getLatestExposureConfig()));
        mockMvc.perform(get(STATUS_URL + previous))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(status)));
    }

    private void assertNoFile(BatchId id) throws Exception {
        mockMvc.perform(get(BATCH_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    private void assertFileContent(BatchId id, byte[] expected) throws Exception {
        mockMvc.perform(get(BATCH_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=43200, public"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"" + BatchFile.batchFileName(id) + "\""))
                .andExpect(content().bytes(expected));
    }

    private void assertFileExists(BatchId id) throws Exception {
        mockMvc.perform(get(BATCH_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=43200, public"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"" + BatchFile.batchFileName(id) + "\""));
    }

    private List<TemporaryExposureKeyRequest> sortByIntervalRequest(List<TemporaryExposureKeyRequest> originals) {
        return originals.stream()
                .sorted((k1, k2) -> Integer.compare(k2.rollingStartIntervalNumber, k1.rollingStartIntervalNumber))
                .collect(Collectors.toList());
    }

    private List<TemporaryExposureKeyRequest> resetRiskLevelsRequest(List<TemporaryExposureKeyRequest> originals, int level) {
        return originals.stream()
                .map(k -> new TemporaryExposureKeyRequest(
                        k.keyData, level, k.rollingStartIntervalNumber, k.rollingPeriod))
                .collect(Collectors.toList());
    }

    private List<TemporaryExposureKeyRequest> sortByInterval(List<TemporaryExposureKey> originals) {
        return originals.stream()
                .sorted((k1, k2) -> Integer.compare(k2.rollingStartIntervalNumber, k1.rollingStartIntervalNumber))
                .map(key -> new TemporaryExposureKeyRequest(
                        key.keyData, key.transmissionRiskLevel, key.rollingStartIntervalNumber,
                        key.rollingPeriod))
                .collect(Collectors.toList());
    }
}
