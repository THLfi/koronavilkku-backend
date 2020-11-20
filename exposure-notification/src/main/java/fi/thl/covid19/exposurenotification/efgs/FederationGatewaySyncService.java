package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao.MAX_RETRY_COUNT;
import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.*;
import static fi.thl.covid19.exposurenotification.efgs.OperationDao.EfgsOperationDirection.*;

@Service
public class FederationGatewaySyncService {
    private final FederationGatewayClient client;
    private final DiagnosisKeyDao diagnosisKeyDao;
    private final OperationDao operationDao;
    private final FederationGatewayBatchSigner signer;

    public FederationGatewaySyncService(
            FederationGatewayClient client,
            DiagnosisKeyDao diagnosisKeyDao,
            OperationDao operationDao,
            FederationGatewayBatchSigner signer
    ) {
        this.client = client;
        this.diagnosisKeyDao = diagnosisKeyDao;
        this.operationDao = operationDao;
        this.signer = signer;
    }

    public Set<Long> startOutbound(boolean retry) {
        Optional<FederationOutboundOperation> operation;
        Set<Long> operationsProcessed = new HashSet<>();

        while ((operation = diagnosisKeyDao.fetchAvailableKeysForEfgs(retry)).isPresent()) {
            operationsProcessed.add(operation.get().operationId);
            doOutbound(operation.get());
        }

        return operationsProcessed;
    }

    public void resolveCrash() {
        operationDao.getAndResolveCrashed(INBOUND);
        diagnosisKeyDao.resolveOutboundCrash();
    }

    public void startInbound(LocalDate date, Optional<String> batchTag) {
        String dateS = getDateString(date);
        doInbound(dateS, batchTag);
    }

    public void startInboundRetry(LocalDate date) {
        operationDao.getInboundErrorBatchTags(date).forEach(
                (tag, count) -> {
                    if (count < MAX_RETRY_COUNT) {
                        addInboundKeys(getDateString(date), Optional.of(tag));
                    }
                }
        );
    }

    private void doInbound(String date, Optional<String> batchTag) {
        Optional<String> next = batchTag;
        do {
            next = addInboundKeys(date, next);
        } while (next.isPresent());
    }

    private Optional<String> addInboundKeys(String date, Optional<String> batchTag) {
        Optional<Long> operationId = operationDao.startOperation(INBOUND, batchTag);
        return operationId.flatMap(id -> downloadAndStore(id, batchTag, date));
    }

    private Optional<String> downloadAndStore(long operationId, Optional<String> batchTag, String date) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Optional<String>> localBatchTag = new AtomicReference<>(batchTag);
        try {
            Optional<DownloadData> downloadO = client.download(date, localBatchTag.get());
            return downloadO.flatMap(downloadData -> {
                DownloadData download = downloadO.get();
                localBatchTag.set(Optional.of(download.batchTag));
                List<TemporaryExposureKey> keys = transform(download.batch);
                diagnosisKeyDao.addInboundKeys(keys, IntervalNumber.to24HourInterval(Instant.now()));
                finished.set(operationDao.finishOperation(operationId, keys.size(), localBatchTag.get()));
                return download.nextBatchTag;
            });
        } finally {
            if (!finished.get()) {
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
