package fi.thl.covid19.exposurenotification.diagnosiskey;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.batch.*;
import fi.thl.covid19.exposurenotification.configuration.ConfigurationService;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.BatchList;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.CurrentBatch;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.Status;
import fi.thl.covid19.exposurenotification.tokenverification.PublishTokenVerificationService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.from24hourToV2Interval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.fromV2to24hourInterval;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

@SpringBootTest(properties = {"covid19.demo-mode=true"})
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
public class DiagnosisKeyControllerDemoIT {

    private static final String BASE_URL = "/diagnosis/v1";
    private static final String EN_API_VERSION_2_PARAM = "en-api-version=2";
    private static final String CURRENT_URL = BASE_URL + "/current";
    private static final String CURRENT_URL_V2 = BASE_URL + "/current?" + EN_API_VERSION_2_PARAM;
    private static final String LIST_URL = BASE_URL + "/list?previous=";
    private static final String LIST_URL_V2 = BASE_URL + "/list?" + EN_API_VERSION_2_PARAM + "&previous=";
    private static final String STATUS_URL = BASE_URL + "/status?batch=";
    private static final String STATUS_URL_V2 = BASE_URL + "/status?" + EN_API_VERSION_2_PARAM + "&batch=";
    private static final String BATCH_URL = BASE_URL + "/batch";

    private static final BatchIntervals INTERVALS = BatchIntervals.forExport(true);
    private static final BatchIntervals INTERVALS_V2 = BatchIntervals.forExportV2(true);

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DiagnosisKeyDao dao;

    @Autowired
    private ConfigurationService configService;

    @Autowired
    private BatchFileStorage storage;

    @Autowired
    private BatchFileService batchFileService;

    @MockBean
    private PublishTokenVerificationService tokenVerificationService;

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
        assertCurrent(new BatchId(INTERVALS.last, batchFileService.generateDemoTagPart(INTERVALS.last, 0)));
        assertCurrent(new BatchId(fromV2to24hourInterval(INTERVALS_V2.last), batchFileService.generateDemoTagPart(INTERVALS_V2.current, 0)), true);
    }

    @Test
    public void listWithNoDataReturnsEmptyArray() throws Exception {
        assertListing(BatchId.DEFAULT, List.of());
        assertListing(new BatchId(INTERVALS.first, Optional.of(1)), List.of());
        assertListing(new BatchId(INTERVALS.current, Optional.of(0)), List.of());

        assertListing(BatchId.DEFAULT, List.of(), true);
        assertListing(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(), true);
        assertListing(new BatchId(fromV2to24hourInterval(INTERVALS_V2.current), Optional.of(INTERVALS_V2.current)), List.of(), true);
    }

    @Test
    public void listWithKeysReturnsDemoBatchIds() throws Exception {
        dao.addKeys(1, md5DigestAsHex("test1".getBytes()), INTERVALS.current, from24hourToV2Interval(INTERVALS.current), keyGenerator.someKeys(1, INTERVALS.current, from24hourToV2Interval(INTERVALS.current)), 1);

        BatchId batchId1 = new BatchId(INTERVALS.current, Optional.of(Integer.parseInt(INTERVALS.current + "1")));
        assertListing(BatchId.DEFAULT, List.of(batchId1));
        assertListing(new BatchId(INTERVALS.first), List.of(batchId1));
        assertListing(batchId1, List.of());

        BatchId batchId2 = new BatchId(INTERVALS.current, Optional.of(Integer.parseInt(INTERVALS.current + "3")));
        dao.addKeys(2, md5DigestAsHex("test2".getBytes()), INTERVALS.current, from24hourToV2Interval(INTERVALS.current), keyGenerator.someKeys(2, INTERVALS.current, from24hourToV2Interval(INTERVALS.current)), 2);

        assertListing(BatchId.DEFAULT, List.of(batchId2));
        assertListing(new BatchId(INTERVALS.first), List.of(batchId2));
        assertListing(batchId1, List.of(batchId2));
        assertListing(batchId2, List.of());
    }

    @Test
    public void listWithKeysReturnsDemoBatchIdsV2() throws Exception {
        dao.addKeys(1, md5DigestAsHex("test1".getBytes()), fromV2to24hourInterval(INTERVALS_V2.current), INTERVALS_V2.current, keyGenerator.someKeys(1, fromV2to24hourInterval(INTERVALS_V2.current), INTERVALS_V2.current), 1);

        BatchId batchId1 = new BatchId(fromV2to24hourInterval(INTERVALS_V2.current), Optional.of(Integer.parseInt(INTERVALS_V2.current + "1")));
        assertListing(BatchId.DEFAULT, List.of(batchId1), true);
        assertListing(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(batchId1), true);
        assertListing(batchId1, List.of(), true);

        BatchId batchId2 = new BatchId(fromV2to24hourInterval(INTERVALS_V2.current), Optional.of(Integer.parseInt(INTERVALS_V2.current + "3")));
        dao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                fromV2to24hourInterval(INTERVALS_V2.current), INTERVALS_V2.current, keyGenerator.someKeys(2, fromV2to24hourInterval(INTERVALS_V2.current), INTERVALS_V2.current), 2);

        assertListing(BatchId.DEFAULT, List.of(batchId2), true);
        assertListing(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(batchId2), true);
        assertListing(batchId1, List.of(batchId2), true);
        assertListing(batchId2, List.of(), true);
    }

    @Test
    public void statusWithKeysReturnsDemoBatchIds() throws Exception {
        BatchId batchId1 = new BatchId(INTERVALS.last - 1);
        BatchId batchId2 = new BatchId(INTERVALS.last, Optional.of(Integer.parseInt(INTERVALS.last + "1")));

        dao.addKeys(1, md5DigestAsHex("test1".getBytes()),
                batchId1.intervalNumber, from24hourToV2Interval(batchId1.intervalNumber), keyGenerator.someKeys(1, batchId1.intervalNumber, from24hourToV2Interval(batchId1.intervalNumber)), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1));
        assertStatus(new BatchId(INTERVALS.first), List.of(batchId1));
        assertStatus(batchId1, List.of());

        dao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                batchId2.intervalNumber, from24hourToV2Interval(batchId2.intervalNumber), keyGenerator.someKeys(1, batchId2.intervalNumber, from24hourToV2Interval(batchId2.intervalNumber)), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1, batchId2));
        assertStatus(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(batchId1, batchId2));
        assertStatus(batchId1, List.of(batchId2));
        assertStatus(batchId2, List.of());
    }

    @Test
    public void statusWithKeysReturnsDemoBatchIdsV2() throws Exception {
        BatchId batchId1 = new BatchId(fromV2to24hourInterval(INTERVALS_V2.last), Optional.of(INTERVALS_V2.last - 1));
        BatchId batchId2 = new BatchId(fromV2to24hourInterval(INTERVALS_V2.last), Optional.of(Integer.parseInt(INTERVALS_V2.last + "1")));

        dao.addKeys(1, md5DigestAsHex("test1".getBytes()), batchId1.intervalNumber, batchId1.intervalNumberV2.get(), keyGenerator.someKeys(1, batchId1.intervalNumber, batchId1.intervalNumberV2.get()), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1), true);
        assertStatus(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(batchId1), true);
        assertStatus(batchId1, List.of(), true);

        dao.addKeys(2, md5DigestAsHex("test2".getBytes()),
                batchId2.intervalNumber, INTERVALS_V2.last, keyGenerator.someKeys(1, batchId2.intervalNumber, INTERVALS_V2.last), 1);

        assertStatus(BatchId.DEFAULT, List.of(batchId1, batchId2), true);
        assertStatus(new BatchId(fromV2to24hourInterval(INTERVALS_V2.first), Optional.of(INTERVALS_V2.first)), List.of(batchId1, batchId2), true);
        assertStatus(batchId1, List.of(batchId2), true);
        assertStatus(batchId2, List.of(), true);
    }

    @Test
    public void newDemoBatchIsGeneratedFromKeys() throws Exception {
        BatchId demoBatchWith0Keys = new BatchId(INTERVALS.last, batchFileService.generateDemoTagPart(INTERVALS.last, 0));
        BatchId demoBatchWith1Key = new BatchId(INTERVALS.last, batchFileService.generateDemoTagPart(INTERVALS.last, 2));

        assertCurrent(demoBatchWith0Keys);
        assertNoFile(demoBatchWith1Key);
        dao.addKeys(1, md5DigestAsHex("test".getBytes()), INTERVALS.last, from24hourToV2Interval(INTERVALS.last),
                keyGenerator.someKeys(1, INTERVALS.last, from24hourToV2Interval(INTERVALS.last)), 1);

        assertFileExists(demoBatchWith1Key);
    }

    @Test
    public void missingDemoFileReturns404() throws Exception {
        assertNoFile(new BatchId(123456, Optional.of(2)));
    }

    @Test
    public void demoBatchFetchingSucceeds() throws Exception {
        dao.addKeys(123, "TEST", INTERVALS.current, from24hourToV2Interval(INTERVALS.current), keyGenerator.someKeys(14, INTERVALS.current, from24hourToV2Interval(INTERVALS.current)), 14);
        mockMvc.perform(get("/diagnosis/v1/batch/" + INTERVALS.current + "_14"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    public void invalidDemoBatchIdIsError400() throws Exception {
        mockMvc.perform(get("/diagnosis/v1/batch/_"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
        mockMvc.perform(get("/diagnosis/v1/batch/a_12"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
        mockMvc.perform(get("/diagnosis/v1/batch/123456_a"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }

    @Test
    public void missingDemoBatchIs404() throws Exception {
        BatchId demoBatchId = new BatchId(INTERVALS.last, Optional.of(1));
        mockMvc.perform(get("/diagnosis/v1/batch/" + demoBatchId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Batch not available:")));
    }

    private void assertCurrent(BatchId expected) throws Exception {
        assertCurrent(expected, false);
    }

    private void assertCurrent(BatchId expected, boolean v2) throws Exception {
        CurrentBatch current = new CurrentBatch(expected);
        mockMvc.perform(get(v2 ? CURRENT_URL_V2 : CURRENT_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=900, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(current)));
    }

    private void assertListing(BatchId previous, List<BatchId> expected) throws Exception {
        assertListing(previous, expected, false);
    }

    private void assertListing(BatchId previous, List<BatchId> expected, boolean v2) throws Exception {
        BatchList list = new BatchList(expected);
        String listUrl = v2 ? LIST_URL_V2 : LIST_URL;
        mockMvc.perform(get(listUrl + previous))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(list)));
    }

    private void assertStatus(BatchId previous, List<BatchId> expected) throws Exception {
        assertStatus(previous, expected, false);
    }

    private void assertStatus(BatchId previous, List<BatchId> expected, boolean v2) throws Exception {
        String statusUrl = v2 ? STATUS_URL_V2 : STATUS_URL;
        Status status = new Status(expected,
                Optional.of(configService.getLatestAppConfig()),
                Optional.of(configService.getLatestExposureConfig()),
                Optional.of(configService.getLatestV2ExposureConfig()));
        mockMvc.perform(get(statusUrl + previous))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(status)));
    }

    private void assertNoFile(BatchId id) throws Exception {
        mockMvc.perform(get(BATCH_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    private void assertFileExists(BatchId id) throws Exception {
        mockMvc.perform(get(BATCH_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=43200, public"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"" + BatchFile.batchFileName(id) + "\""));
    }
}
