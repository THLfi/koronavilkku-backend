package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.efgs.entity.DownloadData;
import fi.thl.covid19.exposurenotification.efgs.entity.FederationOutboundOperation;
import fi.thl.covid19.exposurenotification.efgs.entity.UploadResponseEntity;
import fi.thl.covid19.exposurenotification.efgs.signing.FederationGatewaySigning;
import fi.thl.covid19.proto.EfgsProto;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao.MAX_RETRY_COUNT;
import static fi.thl.covid19.exposurenotification.efgs.util.SignatureValidationUtil.validateSignature;
import static fi.thl.covid19.exposurenotification.efgs.util.BatchUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection.*;
import static java.util.Objects.requireNonNull;

@Service
public class FederationGatewaySyncService {
    private final FederationGatewayClient client;
    private final DiagnosisKeyDao diagnosisKeyDao;
    private final OperationDao operationDao;
    private final FederationGatewaySigning signer;
    private final MeterRegistry meterRegistry;

    public FederationGatewaySyncService(
            FederationGatewayClient client,
            DiagnosisKeyDao diagnosisKeyDao,
            OperationDao operationDao,
            FederationGatewaySigning signer,
            MeterRegistry meterRegistry
    ) {
        this.client = requireNonNull(client);
        this.diagnosisKeyDao = requireNonNull(diagnosisKeyDao);
        this.operationDao = requireNonNull(operationDao);
        this.signer = requireNonNull(signer);
        this.meterRegistry = requireNonNull(meterRegistry);
    }

    public Set<Long> startOutbound(boolean retry) {
        Optional<FederationOutboundOperation> operation;
        Set<Long> operationsProcessed = new HashSet<>();

        while ((operation = diagnosisKeyDao.fetchAvailableKeysForEfgs(retry)).isPresent()) {
            long operationId = operation.get().operationId;
            MDC.put("outboundOperationId", Long.toString(operationId));
            meterRegistry.counter("efgs_total_operations_outbound").increment(1.0);
            operationsProcessed.add(operationId);
            doOutbound(operation.get());
        }

        return operationsProcessed;
    }

    public void resolveCrash() {
        operationDao.getAndResolveCrashed(INBOUND);
        diagnosisKeyDao.resolveOutboundCrash();
    }

    @Async("callbackAsyncExecutor")
    public void startInboundAsync(LocalDate date, String batchTag) {
        MDC.clear();
        MDC.put("callbackBatchTag", batchTag);
        meterRegistry.counter("efgs_total_operations_inbound").increment(1.0);
        addInboundKeys(getDateString(date), Optional.of(batchTag));
    }

    public void startInbound(LocalDate date, Optional<String> batchTag) {
        doInbound(date, batchTag);
    }

    public void startInboundRetry(LocalDate date) {
        operationDao.getInboundErrorBatchTags(date).forEach(
                (tag, count) -> {
                    if (count < MAX_RETRY_COUNT) {
                        MDC.put("inboundRetryBatchTag", tag);
                        meterRegistry.counter("efgs_total_operations_inbound").increment(1.0);
                        addInboundKeys(getDateString(date), Optional.of(tag));
                    }
                }
        );
    }

    private void doInbound(LocalDate date, Optional<String> batchTag) {
        Optional<String> next = batchTag;
        String dateS = getDateString(date);
        do {
            MDC.put("scheduledInboundBatchTag", next.orElse(getBatchTag(date, "1")));
            meterRegistry.counter("efgs_total_operations_inbound").increment(1.0);
            next = addInboundKeys(dateS, next);
        } while (next.isPresent());
    }

    private Optional<String> addInboundKeys(String date, Optional<String> batchTag) {
        Optional<Long> operationId = operationDao.startInboundOperation(batchTag);
        return operationId.flatMap(id -> downloadAndStore(id, batchTag, date));
    }

    private Optional<String> downloadAndStore(long operationId, Optional<String> batchTag, String date) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Optional<String>> localBatchTag = new AtomicReference<>(batchTag);
        try {
            Optional<DownloadData> downloadO = client.download(date, localBatchTag.get());
            return downloadO.flatMap(download -> {
                localBatchTag.set(Optional.of(download.batchTag));
                EfgsProto.DiagnosisKeyBatch validBatch = validateSignature(
                        client.fetchAuditEntries(date, download.batchTag),
                        download,
                        signer.getTrustAnchor()
                );
                List<TemporaryExposureKey> finalKeys = transform(validBatch);
                diagnosisKeyDao.addInboundKeys(finalKeys, IntervalNumber.to24HourInterval(Instant.now()));
                int failedCount = download.keysCount() - finalKeys.size();
                meterRegistry.counter("efgs_verification_total").increment(finalKeys.size());
                meterRegistry.counter("efgs_verification_failed").increment(failedCount);
                finished.set(operationDao.finishOperation(operationId, finalKeys.size(), failedCount, localBatchTag.get()));
                return download.nextBatchTag;
            });
        } finally {
            if (!finished.get()) {
                meterRegistry.counter("efgs_error_operations_inbound").increment(1.0);
                operationDao.markErrorOperation(operationId, localBatchTag.get());
            }
        }
    }

    private void doOutbound(FederationOutboundOperation operation) {
        boolean finished = false;
        try {
            UploadResponseEntity res = handleOutbound(transform(operation.keys), operation.batchTag);
            // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
            if (res.httpStatus.value() == 207) {
                Map<Integer, Integer> responseCounts = handlePartialOutbound(res.multiStatuses.orElseThrow(), operation);
                finished = operationDao.finishOperation(operation,
                        responseCounts.get(201) + responseCounts.get(409) + responseCounts.get(500),
                        responseCounts.get(201), responseCounts.get(409), responseCounts.get(500));
            } else {
                finished = operationDao.finishOperation(operation,
                        operation.keys.size(), operation.keys.size(), 0, 0);
            }
        } finally {
            if (!finished) {
                meterRegistry.counter("efgs_error_operations_outbound").increment(1.0);
                diagnosisKeyDao.setNotSent(operation);
            }
        }
    }

    private UploadResponseEntity handleOutbound(EfgsProto.DiagnosisKeyBatch batch, String batchTag) {
        return client.upload(batchTag, signer.sign(batch), batch);
    }

    private Map<Integer, Integer> handlePartialOutbound(Map<Integer, List<Integer>> statuses, FederationOutboundOperation operation) {
        List<Integer> successKeysIdx = statuses.get(201);
        List<Integer> keysIdx409 = statuses.get(409);
        List<Integer> keysIdx500 = statuses.get(500);
        List<TemporaryExposureKey> successKeys = successKeysIdx.stream().map(operation.keys::get).collect(Collectors.toList());

        UploadResponseEntity resendRes = handleOutbound(transform(successKeys), operation.batchTag);

        if (resendRes.httpStatus.value() == 207)
            throw new IllegalStateException("Upload to efgs still failing after resend 207 success keys.");

        // There is not much sense to resend 409 keys again, or even 500, but for simplicity this will be done anyway for now
        List<TemporaryExposureKey> failedKeys = Stream.concat(keysIdx409.stream(), keysIdx500.stream()).map(operation.keys::get).collect(Collectors.toList());
        diagnosisKeyDao.setNotSent(new FederationOutboundOperation(failedKeys, operation.operationId));
        return Map.of(201, successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }
}
