package fi.thl.covid19.exposurenotification.efgs.entity;

import fi.thl.covid19.exposurenotification.efgs.util.CommonConst;

import java.time.Instant;
import java.time.LocalDate;

public class InboundOperation {
    public final long id;
    public final CommonConst.EfgsOperationState state;
    public final int keysCountTotal;
    public final int invalidSignatureCount;
    public final String batchTag;
    public final int retryCount;
    public final Instant updatedAt;
    public final LocalDate batchDate;

    public InboundOperation(long id,
                            CommonConst.EfgsOperationState state,
                            int keysCountTotal,
                            int invalidSignatureCount,
                            String batchTag,
                            int retryCount,
                            Instant updatedAt,
                            LocalDate batchDate
    ) {
        this.id = id;
        this.state = state;
        this.keysCountTotal = keysCountTotal;
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
                ", invalidSignatureCount=" + invalidSignatureCount +
                ", batchTag='" + batchTag + '\'' +
                ", updatedAt=" + updatedAt +
                ", batchDate=" + batchDate +
                '}';
    }
}
