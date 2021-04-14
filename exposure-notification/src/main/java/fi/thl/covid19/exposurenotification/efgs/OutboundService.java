package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.efgs.dao.OutboundOperationDao;
import fi.thl.covid19.exposurenotification.efgs.entity.OutboundOperation;
import fi.thl.covid19.exposurenotification.efgs.entity.UploadResponseEntity;
import fi.thl.covid19.exposurenotification.efgs.signing.FederationGatewaySigning;
import fi.thl.covid19.proto.EfgsProto;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.thl.covid19.exposurenotification.efgs.util.BatchUtil.*;
import static java.util.Objects.requireNonNull;

@Service
public class OutboundService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboundService.class);

    private final FederationGatewayClient client;
    private final DiagnosisKeyDao diagnosisKeyDao;
    private final OutboundOperationDao outboundOperationDao;
    private final FederationGatewaySigning signer;
    private final MeterRegistry meterRegistry;

    private final String efgsTotalOperationsOutbound = "efgs_total_operations_outbound";
    private final String efgsErrorOperationsOutbound = "efgs_error_operations_outbound";


    public OutboundService(
            FederationGatewayClient client,
            DiagnosisKeyDao diagnosisKeyDao,
            OutboundOperationDao outboundOperationDao,
            FederationGatewaySigning signer,
            MeterRegistry meterRegistry
    ) {
        this.client = requireNonNull(client);
        this.diagnosisKeyDao = requireNonNull(diagnosisKeyDao);
        this.outboundOperationDao = requireNonNull(outboundOperationDao);
        this.signer = requireNonNull(signer);
        this.meterRegistry = requireNonNull(meterRegistry);
        initCounters();
    }

    public Set<Long> startOutbound(boolean retry) {
        Optional<OutboundOperation> operation;
        Set<Long> operationsProcessed = new HashSet<>();

        while ((operation = diagnosisKeyDao.fetchAvailableKeyForEfgsWithDummyPadding(retry)).isPresent()) {
            long operationId = operation.get().operationId;
            MDC.put("outboundOperationId", "outbound-" + operationId);
            meterRegistry.counter(efgsTotalOperationsOutbound).increment(1.0);
            operationsProcessed.add(operationId);
            doOutbound(operation.get());
        }

        return operationsProcessed;
    }

    private void doOutbound(OutboundOperation operation) {
        boolean finished = false;
        try {
            List<TemporaryExposureKey> outboundKeys = operation.keys;
            UploadResponseEntity res = handleOutbound(transform(outboundKeys), operation.batchTag);
            // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
            if (res.httpStatus.value() == 207) {
                Map<Integer, Integer> responseCounts = handlePartialOutbound(res.multiStatuses.orElseThrow(), operation);
                finished = outboundOperationDao.finishOperation(operation,
                        responseCounts.get(201) + responseCounts.get(409) + responseCounts.get(500),
                        responseCounts.get(201), responseCounts.get(409), responseCounts.get(500));
            } else {
                finished = outboundOperationDao.finishOperation(operation,
                        operation.keys.size(), operation.keys.size(), 0, 0);
            }
        } finally {
            if (!finished) {
                meterRegistry.counter(efgsErrorOperationsOutbound).increment(1.0);
                diagnosisKeyDao.setNotSent(operation);
            }
        }
    }

    private UploadResponseEntity handleOutbound(EfgsProto.DiagnosisKeyBatch batch, String batchTag) {
        return client.upload(batchTag, signer.sign(batch), batch);
    }

    private Map<Integer, Integer> handlePartialOutbound(Map<Integer, List<Integer>> statuses, OutboundOperation operation) {
        List<Integer> successKeysIdx = statuses.get(201);
        List<Integer> keysIdx409 = statuses.get(409);
        List<Integer> keysIdx500 = statuses.get(500);
        List<TemporaryExposureKey> successKeys = successKeysIdx.stream().map(operation.keys::get).collect(Collectors.toList());

        UploadResponseEntity resendRes = handleOutbound(transform(successKeys), operation.batchTag);

        if (resendRes.httpStatus.value() == 207)
            throw new IllegalStateException("Upload to efgs still failing after resend 207 success keys.");

        // There is not much sense to resend 409 keys again, or even 500, but for simplicity this will be done anyway for now
        List<TemporaryExposureKey> failedKeys = Stream.concat(keysIdx409.stream(), keysIdx500.stream()).map(operation.keys::get).collect(Collectors.toList());
        diagnosisKeyDao.setNotSent(new OutboundOperation(failedKeys, operation.operationId));
        return Map.of(201, successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }

    public void resolveCrash() {
        diagnosisKeyDao.resolveOutboundCrash();
    }

    private void initCounters() {
        meterRegistry.counter(efgsTotalOperationsOutbound);
        meterRegistry.counter(efgsErrorOperationsOutbound);
    }
}
