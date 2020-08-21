package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import fi.thl.covid19.exposurenotification.batch.BatchId;

public class CurrentBatch {
    public final String current;

    public CurrentBatch(BatchId id) {
        this.current = id.toString();
    }
}
