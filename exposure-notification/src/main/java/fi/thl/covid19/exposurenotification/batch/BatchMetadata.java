package fi.thl.covid19.exposurenotification.batch;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.startSecondOf24HourInterval;
import static java.util.Objects.requireNonNull;

public class BatchMetadata {
    // Start timestamp inclusive, end timestamp exclusive
    public final long startTimestampUtcSec;
    public final long endTimestampUtcSec;
    public final String region;

    public BatchMetadata(long startTimestampUtcSec,
                         long endTimestampUtcSec,
                         String region) {
        this.startTimestampUtcSec = startTimestampUtcSec;
        this.endTimestampUtcSec = endTimestampUtcSec;
        this.region = requireNonNull(region);
    }

    public static BatchMetadata of(int intervalNumber, String region) {
        return new BatchMetadata(
                startSecondOf24HourInterval(intervalNumber),
                startSecondOf24HourInterval(intervalNumber+1),
                region);
    }
}
