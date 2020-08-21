package fi.thl.covid19.exposurenotification.diagnosiskey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransmissionRiskBucketsTest {

    @Test
    public void bucketsAreCalculatedCorrectly() {
        LocalDate now = LocalDate.now();
        Assertions.assertEquals(0, TransmissionRiskBuckets.getRiskBucket(LocalDate.MIN, LocalDate.MAX));
        Assertions.assertEquals(0, TransmissionRiskBuckets.getRiskBucket(now.minus(15, DAYS), now));
        Assertions.assertEquals(1, TransmissionRiskBuckets.getRiskBucket(now.minus(14, DAYS), now));
        Assertions.assertEquals(1, TransmissionRiskBuckets.getRiskBucket(now.minus(11, DAYS), now));
        Assertions.assertEquals(2, TransmissionRiskBuckets.getRiskBucket(now.minus(10, DAYS), now));
        Assertions.assertEquals(2, TransmissionRiskBuckets.getRiskBucket(now.minus(9, DAYS), now));
        Assertions.assertEquals(3, TransmissionRiskBuckets.getRiskBucket(now.minus(8, DAYS), now));
        Assertions.assertEquals(3, TransmissionRiskBuckets.getRiskBucket(now.minus(7, DAYS), now));
        Assertions.assertEquals(4, TransmissionRiskBuckets.getRiskBucket(now.minus(6, DAYS), now));
        Assertions.assertEquals(4, TransmissionRiskBuckets.getRiskBucket(now.minus(5, DAYS), now));
        Assertions.assertEquals(5, TransmissionRiskBuckets.getRiskBucket(now.minus(4, DAYS), now));
        Assertions.assertEquals(5, TransmissionRiskBuckets.getRiskBucket(now.minus(3, DAYS), now));
        Assertions.assertEquals(6, TransmissionRiskBuckets.getRiskBucket(now.minus(2, DAYS), now));
        Assertions.assertEquals(6, TransmissionRiskBuckets.getRiskBucket(now, now));
        Assertions.assertEquals(6, TransmissionRiskBuckets.getRiskBucket(now.plus(2, DAYS), now));
        Assertions.assertEquals(7, TransmissionRiskBuckets.getRiskBucket(now.plus(3, DAYS), now));
        Assertions.assertEquals(7, TransmissionRiskBuckets.getRiskBucket(LocalDate.MAX, LocalDate.MIN));
    }
}
