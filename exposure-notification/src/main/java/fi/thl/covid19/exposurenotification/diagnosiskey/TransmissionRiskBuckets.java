package fi.thl.covid19.exposurenotification.diagnosiskey;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class TransmissionRiskBuckets {
    private TransmissionRiskBuckets() {}

    public static final int DEFAULT_RISK_BUCKET = 4;
    // Risk buckets for an encounter are defined through days since the onset of symptoms.
    private static final List<Integer> INTERVAL_BUCKETS = List.of(14, 10, 8, 6, 4, 2, -3);

    public static int getRiskBucket(LocalDate symptomsOnset, LocalDate encounter) {
        return getRiskBucket(ChronoUnit.DAYS.between(symptomsOnset, encounter));
    }

    public static int getRiskBucket(long daysBetween) {
        for (int i = 0; i < INTERVAL_BUCKETS.size(); i++) {
            if (daysBetween > INTERVAL_BUCKETS.get(i)) {
                return i;
            }
        }
        return INTERVAL_BUCKETS.size();
    }
}
