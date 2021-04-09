package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKeyRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;

public class TestKeyGenerator {

    private final Random rand;

    public TestKeyGenerator(int seed) {
        rand = new Random(seed);
    }

    public List<TemporaryExposureKey> someKeys(int count) {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        return someKeys(count, currentInterval, currentIntervalV2, count, true);
    }

    public List<TemporaryExposureKey> someKeys(int count, int currentInterval, int currentIntervalV2) {
        return someKeys(count, currentInterval, currentIntervalV2, count, true);
    }

    public List<TemporaryExposureKey> someKeys(int count, int currentInterval, int currentIntervalV2, int symptomsDays, boolean consentToShare) {
        ArrayList<TemporaryExposureKey> list = new ArrayList<>(14);
        for (int i = count - 1; i >= 0; i--) {
            list.add(someKey(count - i, symptomsDays, consentToShare, currentInterval, currentIntervalV2));
        }
        return list;
    }

    public TemporaryExposureKey someKey(int ageDays, int symptomsDays, boolean consentToShare, int currentInterval, int currentIntervalV2) {
        return someKey(ageDays, symptomsDays, consentToShare, 0, currentInterval, currentIntervalV2);
    }

    public TemporaryExposureKey someKey(int ageDays, int symptomsDays, boolean consentToShare, int dsos, int currentInterval, int currentIntervalV2) {
        return someKey(ageDays, symptomsDays, consentToShare, dsos, Optional.empty(), currentInterval, currentIntervalV2);
    }

    public TemporaryExposureKey someKey(int ageDays, int symptomsDays, boolean consentToShare, int dsos, Optional<Boolean> symptomsExist, int currentInterval, int currentIntervalV2) {
        byte[] bytes = new byte[16];
        rand.nextBytes(bytes);
        String keyData = Base64.getEncoder().encodeToString(bytes);
        return new TemporaryExposureKey(
                keyData,
                getRiskBucket(symptomsDays - ageDays),
                dayFirst10MinInterval(Instant.now().minus(ageDays, ChronoUnit.DAYS)),
                INTERVALS_10MIN_PER_24H,
                rand.nextBoolean() ? Set.of() : Set.of("DE", "IT"),
                Optional.of(dsos),
                "FI",
                consentToShare,
                symptomsExist,
                currentInterval,
                currentIntervalV2
        );
    }

    public List<TemporaryExposureKeyRequest> someRequestKeys(int count) {
        return someRequestKeys(count, count);
    }

    public List<TemporaryExposureKeyRequest> someRequestKeys(int count, int symptomsDays) {
        ArrayList<TemporaryExposureKeyRequest> list = new ArrayList<>(14);
        for (int i = count - 1; i >= 0; i--) {
            list.add(someRequestKey(count - i, symptomsDays));
        }
        return list;
    }

    public TemporaryExposureKeyRequest someRequestKey(int ageDays, int symptomsDays) {
        byte[] bytes = new byte[16];
        rand.nextBytes(bytes);
        String keyData = Base64.getEncoder().encodeToString(bytes);
        return new TemporaryExposureKeyRequest(
                keyData,
                getRiskBucket(symptomsDays - ageDays),
                dayFirst10MinInterval(Instant.now().minus(ageDays, ChronoUnit.DAYS)),
                INTERVALS_10MIN_PER_24H);
    }
}
