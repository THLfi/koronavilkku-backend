package fi.thl.covid19.exposurenotification.eu;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.EfgsOperationException;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            handleOutbound(transform(localKeys), operationId);
            dd.finishOperation(operationId);
        } catch (Throwable t) {
            dd.markErrorOperation(operationId);
            throw new EfgsOperationException("Outbound operation to efgs failed.", t);
        }
    }

    public void doInbound(Optional<String> batchTag) {
        // TODO: what date we should use?
        String date = getDateString(LocalDate.now());
        byte[] batchData = client.download(date, batchTag);
        // TODO: maybe some checks for data?
        dd.addInboundKeys(transform(deserialize(batchData)), IntervalNumber.to24HourInterval(Instant.now()));
    }

    private EfgsProto.DiagnosisKeyBatch transform(List<TemporaryExposureKey> localKeys) {
        List<EfgsProto.DiagnosisKey> euKeys = localKeys.stream().map(localKey ->
                EfgsProto.DiagnosisKey.newBuilder()
                        .setKeyData(ByteString.copyFromUtf8(localKey.keyData))
                        .setRollingStartIntervalNumber(localKey.rollingStartIntervalNumber)
                        .setRollingPeriod(localKey.rollingPeriod)
                        .setTransmissionRiskLevel(0x7FFFFFFF)
                        //.setVisitedCountries() // TODO
                        .setOrigin("FI")
                        .setReportType(EfgsProto.ReportType.CONFIRMED_TEST)
                        //.setDaysSinceOnsetOfSymptoms() // TODO
                        .build())
                .collect(Collectors.toList());

        return EfgsProto.DiagnosisKeyBatch.newBuilder().addAllKeys(euKeys).build();
    }

    // TODO: transmissionRiskLevel
    private List<TemporaryExposureKey> transform(EfgsProto.DiagnosisKeyBatch batch) {
        return batch.getKeysList().stream().map(remoteKey ->
                new TemporaryExposureKey(
                        remoteKey.getKeyData().toString(),
                        1,
                        remoteKey.getRollingStartIntervalNumber(),
                        remoteKey.getRollingPeriod()
                )).collect(Collectors.toList());
    }

    private void handleOutbound(EfgsProto.DiagnosisKeyBatch batch, long operationId) {
        byte[] batchData = serialize(batch);
        int status = client.upload(getDateString(LocalDate.now()) + "-" + operationId, calculateBatchSignature(batchData), batchData);

        if (status == 207) {
            // TODO: should we do something with partial success i.e. http status 207?
        }
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

    private String getDateString(LocalDate date) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }
}
