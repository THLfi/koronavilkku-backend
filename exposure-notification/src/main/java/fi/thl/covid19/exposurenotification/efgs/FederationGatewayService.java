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

    public Optional<Set<Long>> startOutbound(boolean retry) {
        List<TemporaryExposureKey> localKeys = diagnosisKeyDao.fetchAvailableKeysForEfgs(retry);
        Set<Long> operationsProcessed = new HashSet<>();

        while (!localKeys.isEmpty()) {
            long operationId = operationDao.startOperation(OperationDao.EfgsOperationDirection.OUTBOUND);
            operationsProcessed.add(operationId);
            doOutbound(localKeys, operationId);
            localKeys = diagnosisKeyDao.fetchAvailableKeysForEfgs(false);
        }

        return Optional.of(operationsProcessed);
    }

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

    private void doOutbound(List<TemporaryExposureKey> localKeys, long operationId) {
        boolean finished = false;
        try {
            UploadResponseEntity res = handleOutbound(transform(localKeys), operationId);
            // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
            if (res.httpStatus.value() == 207) {
                Map<Integer, Integer> responseCounts = handlePartialOutbound(res.multiStatuses.orElseThrow(), localKeys, operationId);
                finished = operationDao.finishOperation(operationId,
                        responseCounts.get(201) + responseCounts.get(409) + responseCounts.get(500),
                        responseCounts.get(201), responseCounts.get(409), responseCounts.get(500));
            } else {
                finished = operationDao.finishOperation(operationId,
                        localKeys.size(), localKeys.size(), 0, 0);
            }
        } finally {
            if (!finished) {
                operationDao.markErrorOperation(operationId);
                diagnosisKeyDao.setNotSend(localKeys);
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

        return Map.of(201, successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }
}
