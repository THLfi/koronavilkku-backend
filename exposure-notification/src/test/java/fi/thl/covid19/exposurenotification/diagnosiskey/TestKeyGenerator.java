package fi.thl.covid19.exposurenotification.diagnosiskey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.INTERVALS_10MIN_PER_24H;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.dayFirst10MinInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.TransmissionRiskBuckets.getRiskBucket;

public class TestKeyGenerator {

    private final Random rand;

    public TestKeyGenerator(int seed) {
        rand = new Random(seed);
    }

    public List<TemporaryExposureKey> someKeys(int count) {
        return someKeys(count, count);
    }

    public List<TemporaryExposureKey> someKeys(int count, int symptomsDays) {
        ArrayList<TemporaryExposureKey> list = new ArrayList<>(14);
        for (int i = count-1; i >= 0; i--) {
            list.add(someKey(count-i, symptomsDays));
        }
        return list;
    }

    public TemporaryExposureKey someKey(int ageDays, int symptomsDays) {
        byte[] bytes = new byte[16];
        rand.nextBytes(bytes);
        String keyData = Base64.getEncoder().encodeToString(bytes);
        return new TemporaryExposureKey(
                keyData,
                getRiskBucket(symptomsDays-ageDays),
                dayFirst10MinInterval(Instant.now().minus(ageDays, ChronoUnit.DAYS)),
                INTERVALS_10MIN_PER_24H);
    }
}
