package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.*;

@Service
public class FederationGatewayService {
    private final FederationGatewayClient client;
    private final DiagnosisKeyDao diagnosisKeyDao;
    private final OperationDao operationDao;
    private final FederationGatewayBatchSigner signer;

    public FederationGatewayService(
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
        operationDao.getCrashed(OperationDao.EfgsOperationDirection.INBOUND);
        diagnosisKeyDao.resolveOutboundCrash();
    }

    // TODO: we'll need to keep log which batches are already fetch for retry and to avoid downloading same batch again
    public void startInbound(LocalDate date, Optional<String> batchTag) {
        String dateS = getDateString(date);
        doInbound(dateS, batchTag);
    }

    private void doInbound(String date, Optional<String> batchTag) {
        client.download(date, batchTag).forEach(this::addInboundKeys);
    }

    private void addInboundKeys(EfgsProto.DiagnosisKeyBatch batch) {
        long operationId = operationDao.startOperation(OperationDao.EfgsOperationDirection.INBOUND);
        boolean finished = false;

        try {
            List<TemporaryExposureKey> keys = transform(batch);
            diagnosisKeyDao.addInboundKeys(keys, IntervalNumber.to24HourInterval(Instant.now()));
            finished = operationDao.finishOperation(operationId, keys.size());

        } finally {
            if (!finished) {
                operationDao.markErrorOperation(operationId);
            }
        }
    }

    private void doOutbound(FederationOutboundOperation operation) {
        boolean finished = false;
        try {
            UploadResponseEntity res = handleOutbound(transform(operation.keys), operation.operationId);
            // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
            if (res.httpStatus.value() == 207) {
                Map<Integer, Integer> responseCounts = handlePartialOutbound(res.multiStatuses.orElseThrow(), operation.keys, operation.operationId);
                finished = operationDao.finishOperation(operation.operationId,
                        responseCounts.get(201) + responseCounts.get(409) + responseCounts.get(500),
                        responseCounts.get(201), responseCounts.get(409), responseCounts.get(500));
            } else {
                finished = operationDao.finishOperation(operation.operationId,
                        operation.keys.size(), operation.keys.size(), 0, 0);
            }
        } finally {
            if (!finished) {
                diagnosisKeyDao.setNotSent(operation);
            }
        }
    }

    private UploadResponseEntity handleOutbound(EfgsProto.DiagnosisKeyBatch batch, long operationId) {
        return client.upload(getBatchTag(LocalDate.now(ZoneOffset.UTC), Long.toString(operationId)), signer.sign(batch), batch);
    }

    private Map<Integer, Integer> handlePartialOutbound(Map<Integer, List<Integer>> statuses, List<TemporaryExposureKey> localKeys, long operationId) {
        List<Integer> successKeysIdx = statuses.get(201);
        List<Integer> keysIdx409 = statuses.get(409);
        List<Integer> keysIdx500 = statuses.get(500);
        List<TemporaryExposureKey> successKeys = successKeysIdx.stream().map(localKeys::get).collect(Collectors.toList());

        UploadResponseEntity resendRes = handleOutbound(transform(successKeys), operationId);

        if (resendRes.httpStatus.value() == 207)
            throw new IllegalStateException("Upload to efgs still failing after resend 207 success keys.");

        // There is not much sense to resend 409 keys again, or even 500, but for simplicity this will be done anyway for now
        List<TemporaryExposureKey> failedKeys = Stream.concat(keysIdx409.stream(), keysIdx500.stream()).map(localKeys::get).collect(Collectors.toList());
        diagnosisKeyDao.setNotSent(new FederationOutboundOperation(failedKeys, operationId));
        return Map.of(201, successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }
}
