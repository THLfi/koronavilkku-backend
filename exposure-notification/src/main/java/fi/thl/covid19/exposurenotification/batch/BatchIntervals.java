package fi.thl.covid19.exposurenotification.batch;

import java.time.Duration;
import java.time.Instant;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to24HourInterval;

public class BatchIntervals {

    // Slight delay for batch generation to ensure that we don't generate batch while it still has data incoming
    private static final Duration GENERATION_DELAY = Duration.ofMinutes(15);
    // Larger delay for distributing the batch files, so we know it's already generated before we give it out
    private static final Duration DISTRIBUTION_DELAY = Duration.ofHours(4);

    private static final int DAYS_TO_DISTRIBUTE_BATCHES = 14;
    private static final int DAYS_TO_KEEP_BATCHES = DAYS_TO_DISTRIBUTE_BATCHES + 1;

    public final int current;
    public final int first;
    public final int last;

    public BatchIntervals(int current, int intervalsToKeep, boolean demoMode) {
        this.current = current;
        this.first = current - intervalsToKeep;
        this.last = demoMode ? current : current - 1;
    }

    public static BatchIntervals forExport(boolean demoMode) {
        Instant now = demoMode ? Instant.now() : Instant.now().minus(DISTRIBUTION_DELAY);
        return new BatchIntervals(to24HourInterval(now), DAYS_TO_DISTRIBUTE_BATCHES, demoMode);
    }

    public static BatchIntervals forGeneration() {
        return new BatchIntervals(to24HourInterval(Instant.now().minus(GENERATION_DELAY)), DAYS_TO_KEEP_BATCHES, false);
    }

    public boolean isDistributed(int interval) {
        return interval >= first && interval <= last;
    }
}
