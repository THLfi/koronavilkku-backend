package fi.thl.covid19.exposurenotification.eu;

import fi.thl.covid19.exposurenotification.batch.BatchFileService;
import fi.thl.covid19.proto.EfgsProto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        // TODO: how we'll construct batchTag?
        // TODO: should we do something with partial success i.e. http status 207?
        int status = client.upload("batchTag", "batchSign", batch);
    }

    public void updateFrom(Optional<String> batchTag) {
        // TODO: what date we should use?
        String date = getDateString(LocalDate.now());
        EfgsProto.DiagnosisKeyBatch batch = client.download(date, batchTag);
        // TODO: do something with batch, maybe?
    }

    private String getDateString(LocalDate date) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }
}
