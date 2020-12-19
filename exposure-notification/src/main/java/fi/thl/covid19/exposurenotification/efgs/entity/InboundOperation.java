package fi.thl.covid19.exposurenotification.efgs.entity;

import fi.thl.covid19.exposurenotification.efgs.util.CommonConst;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class InboundOperation {
    public final long id;
    public final CommonConst.EfgsOperationState state;
    public final int keysCountTotal;
    public final int keysCountSuccess;
    public final int keysCountValidationFailed;
    public final int invalidSignatureCount;
    public final Optional<String> batchTag;
    public final int retryCount;
    public final Instant updatedAt;
    public final LocalDate batchDate;

    public InboundOperation(long id,
                            CommonConst.EfgsOperationState state,
                            int keysCountTotal,
                            int keysCountSuccess,
                            int keysCountValidationFailed,
                            int invalidSignatureCount,
                            Optional<String> batchTag,
                            int retryCount,
                            Instant updatedAt,
                            LocalDate batchDate
    ) {
        this.id = id;
        this.state = state;
        this.keysCountTotal = keysCountTotal;
        this.keysCountSuccess = keysCountSuccess;
        this.keysCountValidationFailed = keysCountValidationFailed;
        this.invalidSignatureCount = invalidSignatureCount;
        this.batchTag = batchTag;
        this.retryCount = retryCount;
        this.updatedAt = updatedAt;
        this.batchDate = batchDate;
    }

    @Override
    public String toString() {
        return "InboundOperation{" +
                "id=" + id +
                ", state=" + state +
                ", keysCountTotal=" + keysCountTotal +
                ", keysCountSuccess=" + keysCountSuccess +
                ", keysCountValidationFailed=" + keysCountValidationFailed +
                ", invalidSignatureCount=" + invalidSignatureCount +
                ", batchTag='" + batchTag + '\'' +
                ", retryCount=" + retryCount +
                ", updatedAt=" + updatedAt +
                ", batchDate=" + batchDate +
                '}';
    }
}
