package fi.thl.covid19.exposurenotification.efgs;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.utcDateOf10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;

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

    public void doOutbound() {
        long operationId = dd.startOutboundOperation();
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

            dd.finishOperation(operationId, localKeys.size(), total201Count.get(), total409Count.get(), total500Count.get());
        } catch (Exception e) {
            // TODO: check what we really want to catch in here
            dd.markErrorOperation(operationId);
            throw new EfgsOperationException("Outbound operation to efgs failed.", e);
        }
    }

    public void doInbound(Optional<String> batchTag) {
        String date = getDateString(dd.getLatestInboundOperation());
        // TODO: maybe some checks for data?
        client.download(date, batchTag).forEach(
                d -> dd.addInboundKeys(transform(deserialize(d)), IntervalNumber.to24HourInterval(Instant.now()))
        );
    }

    private EfgsProto.DiagnosisKeyBatch transform(List<TemporaryExposureKey> localKeys) {
        List<EfgsProto.DiagnosisKey> efgsKeys = localKeys.stream().map(localKey ->
                EfgsProto.DiagnosisKey.newBuilder()
                        .setKeyData(ByteString.copyFromUtf8(localKey.keyData))
                        .setRollingStartIntervalNumber(localKey.rollingStartIntervalNumber)
                        .setRollingPeriod(localKey.rollingPeriod)
                        .setTransmissionRiskLevel(0x7FFFFFFF)
                        .addAllVisitedCountries(localKey.visitedCountries)
                        .setOrigin(localKey.origin)
                        .setReportType(EfgsProto.ReportType.CONFIRMED_TEST)
                        .setDaysSinceOnsetOfSymptoms(localKey.daysSinceOnsetOfSymptoms)
                        .build())
                .collect(Collectors.toList());

        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(efgsKeys).build();
    }

    private List<TemporaryExposureKey> transform(EfgsProto.DiagnosisKeyBatch batch) {
        return batch.getKeysList().stream().map(remoteKey ->
                new TemporaryExposureKey(
                        remoteKey.getKeyData().toString(),
                        calculateTransmissionRisk(remoteKey),
                        remoteKey.getRollingStartIntervalNumber(),
                        remoteKey.getRollingPeriod(),
                        new HashSet<>(remoteKey.getVisitedCountriesList()),
                        remoteKey.getDaysSinceOnsetOfSymptoms(),
                        remoteKey.getOrigin()
                )).collect(Collectors.toList());
    }

    private int calculateTransmissionRisk(EfgsProto.DiagnosisKey key) {
        LocalDate keyDate = utcDateOf10MinInterval(key.getRollingStartIntervalNumber());

        return getRiskBucket(LocalDate.from(keyDate).plusDays(key.getDaysSinceOnsetOfSymptoms()), keyDate);
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

        return Map.of(201,successKeysIdx.size(), 409, keysIdx409.size(), 500, keysIdx500.size());
    }

    private String calculateBatchSignature(byte[] data) {
        return Arrays.toString(batchFileService.calculateBatchSignature(data));
    }

    private byte[] serialize(EfgsProto.DiagnosisKeyBatch batch) {
        return batch.toByteArray();
    }

    private EfgsProto.DiagnosisKeyBatch deserialize(byte[] data) {
        try {
            return EfgsProto.DiagnosisKeyBatch.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Incorrect format from federation gateway.", e);
        }
    }

    private String getDateString(Instant date) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }
}
