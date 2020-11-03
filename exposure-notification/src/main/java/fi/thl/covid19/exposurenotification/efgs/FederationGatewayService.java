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
import java.util.stream.Collectors;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.utcDateOf10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;

@Service
public class FederationGatewayService {

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
            List<TemporaryExposureKey> localKeys = dd.fetchAvailableKeysForEfgs(operationId);
            //TODO: maximum batch size is 5000 keys in efgs, please split
            ResponseEntity<UploadResponseEntity> res = handleOutbound(transform(localKeys), operationId);

            // 207 means partial success, due server implementation details we'll need to remove erroneous and re-send
            if (res.getStatusCodeValue() == 207) {
                handlePartialOutbound(res.getBody(), localKeys, operationId);

            } else {
                dd.finishOperation(operationId, localKeys.size());
            }
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

    private ResponseEntity<UploadResponseEntity> handleOutbound(EfgsProto.DiagnosisKeyBatch batch, long operationId) {
        byte[] batchData = serialize(batch);
        return client.upload(getDateString(Instant.now()) + "-" + operationId, calculateBatchSignature(batchData), batchData);
    }

    private void handlePartialOutbound(UploadResponseEntity body, List<TemporaryExposureKey> localKeys, long operationId) {
        List<Integer> successKeysIdx = Objects.requireNonNull(body).get(201);
        List<Integer> keysIdx409 = Objects.requireNonNull(body).get(409);
        List<Integer> keysIdx500 = Objects.requireNonNull(body).get(500);
        List<TemporaryExposureKey> successKeys = successKeysIdx.stream().map(localKeys::get).collect(Collectors.toList());

        ResponseEntity<UploadResponseEntity> resendRes = handleOutbound(transform(successKeys), operationId);

        if (resendRes.getStatusCodeValue() == 207)
            throw new EfgsOperationException("Upload to efgs still failing after resend.");

        dd.finishOperation(operationId, localKeys.size(), successKeysIdx.size(), keysIdx409.size(), keysIdx500.size());
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
