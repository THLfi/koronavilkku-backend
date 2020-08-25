package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import fi.thl.covid19.exposurenotification.batch.BatchFile;
import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.batch.BatchId;
import fi.thl.covid19.exposurenotification.batch.BatchIntervals;
import fi.thl.covid19.exposurenotification.configuration.ConfigurationService;
import fi.thl.covid19.exposurenotification.configuration.v1.AppConfiguration;
import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyService;
import fi.thl.covid19.exposurenotification.error.BatchNotFoundException;
import fi.thl.covid19.exposurenotification.error.InputValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.diagnosiskey.Validation.validatePublishToken;
import static java.util.Objects.requireNonNull;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@RestController
@RequestMapping("/diagnosis/v1")
public class DiagnosisKeyController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosisKeyController.class);

    public static final String PUBLISH_TOKEN_HEADER = "KV-Publish-Token";
    public static final String FAKE_REQUEST_HEADER = "KV-Fake-Request";

    private final Duration statusCacheDuration;
    private final Duration batchCacheDuration;

    private final DiagnosisKeyService diagnosisService;
    private final BatchFileService batchFileService;
    private final ConfigurationService configurationService;

    private final boolean demoMode;

    public DiagnosisKeyController(
            DiagnosisKeyService diagnosisService,
            BatchFileService batchFileService,
            ConfigurationService configurationService,
            @Value("${covid19.diagnosis.response-cache.status-duration}") Duration statusCacheDuration,
            @Value("${covid19.diagnosis.response-cache.batch-duration}") Duration batchCacheDuration,
            @Value("${covid19.demo-mode:false}") boolean demoMode) {
        this.diagnosisService = requireNonNull(diagnosisService);
        this.batchFileService = requireNonNull(batchFileService);
        this.configurationService = requireNonNull(configurationService);
        this.statusCacheDuration = requireNonNull(statusCacheDuration);
        this.batchCacheDuration = requireNonNull(batchCacheDuration);
        this.demoMode = demoMode;
        LOG.info("Initialized: {} {} {}",
                keyValue("demoMode", demoMode),
                keyValue("statusCacheDuration", statusCacheDuration),
                keyValue("batchCacheDuration", batchCacheDuration));
    }

    @GetMapping("/status")
    public ResponseEntity<Status> getCurrentStatus(
            @RequestParam(value = "batch") Optional<BatchId> batchId,
            @RequestParam(value = "app-config") Optional<Integer> appConfigVersion,
            @RequestParam(value = "exposure-config") Optional<Integer> exposureConfigVersion) {
        batchId.ifPresent(this::validateDemoModeVsId);
        LOG.info("Fetching full status info: {} {} {}",
                keyValue("clientBatchId", batchId),
                keyValue("clientAppConfigVersion", appConfigVersion),
                keyValue("clientExposureConfigVersion", exposureConfigVersion));

        BatchIntervals intervals = getExportIntervals();
        ExposureConfiguration exposureConfig = configurationService.getLatestExposureConfig();
        AppConfiguration appConfig = configurationService.getLatestAppConfig();
        Status result = new Status(
                batchId.map(id -> batchFileService.listBatchIdsSince(id, intervals)).orElse(List.of()),
                toLatest(appConfig, appConfig.version, appConfigVersion),
                toLatest(exposureConfig, exposureConfig.version, exposureConfigVersion));
        boolean cacheableBatchId = batchId.isEmpty() || intervals.isDistributed(batchId.get().intervalNumber);
        boolean cacheableAppConfig = appConfigVersion.isEmpty() || appConfigVersion.get().equals(appConfig.version);
        boolean cacheableExposureConfig = exposureConfigVersion.isEmpty() || exposureConfigVersion.get().equals(exposureConfig.version);

        return statusResponse(result, cacheableBatchId && cacheableAppConfig && cacheableExposureConfig);
    }

    @GetMapping("/current")
    public ResponseEntity<CurrentBatch> getCurrentDiagnosisBatchKey() {
        BatchId latest = batchFileService.getLatestBatchId(getExportIntervals());
        LOG.info("Fetching current batch ID: {}", keyValue("current", latest));
        return statusResponse(new CurrentBatch(latest), true);
    }

    @GetMapping("/list")
    public ResponseEntity<BatchList> listDiagnosisBatchesSince(@RequestParam(value = "previous") BatchId previousBatchId) {
        validateDemoModeVsId(previousBatchId);
        BatchIntervals intervals = getExportIntervals();
        List<BatchId> batches = batchFileService.listBatchIdsSince(previousBatchId, intervals);
        LOG.info("Listing diagnosis batches since: {} {}",
                keyValue("previousBatchId", previousBatchId),
                keyValue("resultBatches", batches.size()));
        return statusResponse(new BatchList(batches), intervals.isDistributed(previousBatchId.intervalNumber));
    }

    @GetMapping("/batch/{batch_id}")
    public ResponseEntity<Resource> getDiagnosisBatch(@PathVariable(value = "batch_id") BatchId batchId) {
        validateDemoModeVsId(batchId);
        LOG.info("Requesting diagnosis batch: {}", keyValue("batchId", batchId));
        if (!getExportIntervals().isDistributed(batchId.intervalNumber)) {
            throw new BatchNotFoundException(batchId);
        }
        return batchResponse(batchFileService.getBatchFile(batchId));
    }

    @PostMapping
    public void publishDiagnosis(@RequestHeader(PUBLISH_TOKEN_HEADER) String publishToken,
                                 @RequestHeader(FAKE_REQUEST_HEADER) boolean fakeRequest,
                                 @RequestBody DiagnosisPublishRequest request) {
        String validToken = validatePublishToken(requireNonNull(publishToken));
        LOG.info("Publishing new diagnosis: {} {}", keyValue("fake", fakeRequest), keyValue("keys", request.keys.size()));
        if (!fakeRequest) diagnosisService.handlePublishRequest(validToken, request.keys);
    }

    private <T> Optional<T> toLatest(T item, int version, Optional<Integer> previousVersion) {
        return (previousVersion.isEmpty() || previousVersion.get() < version) ? Optional.of(item) : Optional.empty();
    }

    private <T> ResponseEntity<T> statusResponse(T body, boolean cache) {
        return ResponseEntity.ok()
                .cacheControl(cache ? CacheControl.maxAge(statusCacheDuration).cachePublic() : CacheControl.noCache())
                .body(body);
    }

    private ResponseEntity<Resource> batchResponse(BatchFile file) {
        String nameHeader = "attachment; filename=\"" + file.getName() + "\"";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(batchCacheDuration).cachePublic())
                .contentType(APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, nameHeader)
                .body(new ByteArrayResource(file.data));
    }

    private void validateDemoModeVsId(BatchId id) {
        if (id.isDemoBatch() && !demoMode) {
            throw new InputValidationException("Invalid Batch ID: " + id);
        }
    }

    private BatchIntervals getExportIntervals() {
        return BatchIntervals.forExport(demoMode);
    }
}
