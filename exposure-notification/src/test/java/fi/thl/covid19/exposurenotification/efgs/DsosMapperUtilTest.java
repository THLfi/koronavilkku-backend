package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;
import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.toV2Interval;
import static fi.thl.covid19.exposurenotification.efgs.util.DsosMapperUtil.DsosInterpretationMapper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DsosMapperUtilTest {

    private final TestKeyGenerator keyGenerator = new TestKeyGenerator(123);

    @Test
    public void mapToEfgsWorks() {
        Instant now = Instant.now();
        int currentInterval = to24HourInterval(now);
        int currentIntervalV2 = toV2Interval(now);
        TemporaryExposureKey key1 = keyGenerator.someKey(1, 0x7FFFFFFF, true, 3, Optional.of(true), currentInterval, currentIntervalV2);
        TemporaryExposureKey key2 = keyGenerator.someKey(2, 0x7FFFFFFF, true, 3, Optional.of(false), currentInterval, currentIntervalV2);
        TemporaryExposureKey key3 = keyGenerator.someKey(3, 0x7FFFFFFF, true, 3, Optional.empty(), currentInterval, currentIntervalV2);
        TemporaryExposureKey key5 = keyGenerator.someKey(50, 0x7FFFFFFF, true, 3, Optional.empty(), currentInterval, currentIntervalV2);

        assertEquals(3, mapToEfgs(key1));
        assertEquals(2998, mapToEfgs(key2));
        assertEquals(3997, mapToEfgs(key3));
        assertEquals(4000, mapToEfgs(key5));
    }

    @Test
    public void symptomsExistWorks() {
        assertEquals(Optional.empty(), symptomsExist(3986));
        assertEquals(Optional.of(true), symptomsExist(1));
        assertEquals(Optional.empty(), symptomsExist(50));
        assertEquals(Optional.of(true), symptomsExist(2001));
        assertEquals(Optional.of(false), symptomsExist(2999));
        assertEquals(Optional.of(true), symptomsExist(200));
    }

    @Test
    public void mapFromWorks() {
        assertEquals(Optional.empty(), mapFrom(3986));
        assertEquals(Optional.of(-5), mapFrom(-5));
        assertEquals(Optional.empty(), mapFrom(50));
        assertEquals(Optional.empty(), mapFrom(2001));
        assertEquals(Optional.empty(), mapFrom(2999));
        assertEquals(Optional.empty(), mapFrom(200));
        assertEquals(Optional.empty(), mapFrom(50000));
        assertEquals(Optional.empty(), mapFrom(-15));
    }
}
