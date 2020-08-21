package fi.thl.covid19.exposurenotification.error;

import fi.thl.covid19.exposurenotification.batch.BatchId;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(BatchId batchId) {
        super("Batch not available: id=" + batchId);
    }
}
