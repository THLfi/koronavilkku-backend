package fi.thl.covid19.exposurenotification.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExportIntervalsTest {

    @Test
    public void distributionIsRecognizedCorrectly() {
        int current = 5;
        int keep = 2;
        BatchIntervals intervals = new BatchIntervals(current, keep, false);
        assertFalse(intervals.isDistributed(0));
        assertFalse(intervals.isDistributed(2));
        assertFalse(intervals.isDistributed(5));
        assertFalse(intervals.isDistributed(6));
        assertTrue(intervals.isDistributed(3));
        assertTrue(intervals.isDistributed(4));
    }

    @Test
    public void onlyDemoModeDistributesCurrent() {
        assertFalse(new BatchIntervals(123, 2, false).isDistributed(123));
        assertTrue(new BatchIntervals(123, 2, true).isDistributed(123));
    }
}
