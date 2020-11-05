package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.EfgsOperationException;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.*;

@Service
public class FederationGatewayService {

    private static final int BATCH_MAX_SIZE = 5000;

    private final FederationGatewayClient client;
    private final BatchFileService batchFileService;
    private final DiagnosisKeyDao dd;

    public FederationGatewayService(
            FederationGatewayClient client,
            BatchFileService batchFileService,
            DiagnosisKeyDao diagnosisKeyDao
    ) {
        this.client = client;
        this.batchFileService = batchFileService;
        this.dd = diagnosisKeyDao;
    }

    public void startOutbound() {
        long operationId = dd.startOutboundOperation();

        if (operationId > 0) {
            doOutbound(operationId);
        }
    }

    public void startInbound(Optional<String> batchTag) {
        String date = getDateString(Instant.now());
        doInbound(date, batchTag);
    }

    // TODO: implement retry handler

    private void doInbound(String date, Optional<String> batchTag) {
        client.download(date, batchTag).forEach(
                d -> dd.addInboundKeys(transform(deserialize(d)), IntervalNumber.to24HourInterval(Instant.now()))
        );
    }

    private void doOutbound(long operationId) {
        boolean finished = false;
        try {
            List<List<TemporaryExposureKey>> localKeys = partitionIntoBatches(dd.fetchAvailableKeysForEfgs(operationId));

            AtomicInteger total201Count = new AtomicInteger();
            AtomicInteger total409Count = new AtomicInteger();
            AtomicInteger total500Count = new AtomicInteger();

            localKeys.forEach(
                    batch -> {
                        ResponseEntity<UploadResponseEntity> res = handleOutbound(transform(batch), operationId);
                        // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
                        if (res.getStatusCodeValue() == 207) {
                            Map<Integer, Integer> responseCounts = handlePartialOutbound(res.getBody(), batch, operationId);
                            total201Count.addAndGet(responseCounts.get(201));
                            total409Count.addAndGet(responseCounts.get(409));
                            total500Count.addAndGet(responseCounts.get(409));
                        } else {
                            total201Count.addAndGet(batch.size());
                        }
                    }
            );
            finished = dd.finishOperation(operationId, localKeys.size(), total201Count.get(), total409Count.get(), total500Count.get());
        } finally {
            if (!finished) {
                dd.markErrorOperation(operationId);
            }
        }
    }

    private List<List<TemporaryExposureKey>> partitionIntoBatches(List<TemporaryExposureKey> objs) {
        return new ArrayList<>(IntStream.range(0, objs.size()).boxed().collect(
                Collectors.groupingBy(e -> e / BATCH_MAX_SIZE, Collectors.mapping(objs::get, Collectors.toList())
                )).values());
    }

    private ResponseEntity<UploadResponseEntity> handleOutbound(EfgsProto.DiagnosisKeyBatch batch, long operationId) {
        byte[] batchData = serialize(batch);
        return client.upload(getDateString(Instant.now()) + "-" + operationId, calculateBatchSignature(batchData), batchData);
    }

    private Map<Integer, Integer> handlePartialOutbound(UploadResponseEntity body, List<TemporaryExposureKey> localKeys, long operationId) {
        List<Integer> successKeysIdx = Objects.requireNonNull(body).get(201);
        List<Integer> keysIdx409 = Objects.requireNonNull(body).get(409);
        List<Integer> keysIdx500 = Objects.requireNonNull(body).get(500);
        List<TemporaryExposureKey> successKeys = successKeysIdx.stream().map(localKeys::get).collect(Collectors.toList());

        ResponseEntity<UploadResponseEntity> resendRes = handleOutbound(transform(successKeys), operationId);

        if (resendRes.getStatusCodeValue() == 207)
            throw new EfgsOperationException("Upload to efgs still failing after resend.");

        return Map.of(201, successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }

    private String calculateBatchSignature(byte[] data) {
        return Arrays.toString(batchFileService.calculateBatchSignature(data));
    }

    private String getDateString(Instant date) {
        return DateTimeFormatter.ISO_DATE.format(LocalDate.ofInstant(date, ZoneId.of("UTC")));
    }
}