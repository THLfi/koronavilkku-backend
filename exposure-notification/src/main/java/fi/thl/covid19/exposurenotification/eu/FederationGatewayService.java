package fi.thl.covid19.exposurenotification.eu;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.error.EfgsUpdateException;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

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

    public void updateTo() {
        long operationId = dd.startUpdateToEfgs();
        try {
            List<TemporaryExposureKey> localKeys = dd.fetchAvailableKeysForEfgs(operationId);
            handleUpdate(transformDiagnosisKeyBatch(localKeys), operationId);
            dd.finishUpdateTo(operationId);
        } catch (Throwable t) {
            dd.errorUpdateTo(operationId);
            throw new EfgsUpdateException("Update to efgs failed.", t);
        }
    }

    public void updateFrom(Optional<String> batchTag) {
        // TODO: what date we should use?
        String date = getDateString(LocalDate.now());
        byte[] batchData = client.download(date, batchTag);
        // TODO: maybe some checks for data?
        EfgsProto.DiagnosisKeyBatch batch = deserialize(batchData);
        // TODO: do something with batch, maybe?
    }

    private EfgsProto.DiagnosisKeyBatch transformDiagnosisKeyBatch(List<TemporaryExposureKey> localKeys) {
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

    private void handleUpdate(EfgsProto.DiagnosisKeyBatch batch, long operationId) {
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
