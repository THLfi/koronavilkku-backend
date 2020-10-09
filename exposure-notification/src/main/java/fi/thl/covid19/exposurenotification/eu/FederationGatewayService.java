package fi.thl.covid19.exposurenotification.eu;

import com.google.protobuf.InvalidProtocolBufferException;
import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

@Service
public class FederationGatewayService {

    private final FederationGatewayClient client;
    private final BatchFileService batchFileService;

    public FederationGatewayService(
            FederationGatewayClient client,
            BatchFileService batchFileService
    ) {
        this.client = client;
        this.batchFileService = batchFileService;
    }

    public void updateTo() {
        // TODO: maybe fetch some keys and create batch
        EfgsProto.DiagnosisKeyBatch batch = EfgsProto.DiagnosisKeyBatch.getDefaultInstance();
        byte[] batchData = serialize(batch);
        // TODO: how we'll construct batchTag?
        // TODO: should we do something with partial success i.e. http status 207?
        int status = client.upload(getDateString(LocalDate.now()) + "-1", calculateBatchSignature(batchData), batchData);
    }

    public void updateFrom(Optional<String> batchTag) {
        // TODO: what date we should use?
        String date = getDateString(LocalDate.now());
        byte[] batchData = client.download(date, batchTag);
        // TODO: maybe some checks for data?
        EfgsProto.DiagnosisKeyBatch batch = deserialize(batchData);
        // TODO: do something with batch, maybe?
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
