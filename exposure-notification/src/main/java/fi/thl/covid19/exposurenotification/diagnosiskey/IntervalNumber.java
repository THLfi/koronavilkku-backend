package fi.thl.covid19.exposurenotification.diagnosiskey;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static fi.thl.covid19.exposurenotification.batch.BatchIntervals.DAILY_BATCHES_COUNT;

public final class IntervalNumber {

    private IntervalNumber() {}

    public static final int SECONDS_PER_24H = 60 * 60 * 24;
    public static final int SECONDS_PER_10MIN = 60 * 10;
    public static final int INTERVALS_10MIN_PER_24H = 6 * 24;

    public static int to24HourInterval(Instant time) {
        long value = time.getEpochSecond() / SECONDS_PER_24H;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot represent time as 24h interval: " + time);
        }
        return (int) value;
    }

    public static int toV2Interval(Instant time) {
        long value = time.getEpochSecond() / (SECONDS_PER_24H / DAILY_BATCHES_COUNT);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot represent time as 24h interval: " + time);
        }
        return (int) value;
    }

    public static int to10MinInterval(Instant time) {
        long value = time.getEpochSecond() / SECONDS_PER_10MIN;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Cannot represent time as 10min interval: " + time);
        }
        return (int) value;
    }

    public static int dayFirst10MinInterval(Instant time) {
        int dayNumber = to24HourInterval(time);
        return dayNumber * INTERVALS_10MIN_PER_24H;
    }

    public static int dayLast10MinInterval(Instant time) {
        int nextDayNumber = to24HourInterval(time) + 1;
        return nextDayNumber * INTERVALS_10MIN_PER_24H - 1;
    }

    public static long startSecondOf24HourInterval(int interval24h) {
        return (long) interval24h * SECONDS_PER_24H;
    }

    public static long startSecondOfV2Interval(int intervalV2) {
        return (long) intervalV2 / DAILY_BATCHES_COUNT * SECONDS_PER_24H;
    }

    public static LocalDate utcDateOf10MinInterval(int interval) {
        Instant startMoment = Instant.ofEpochSecond((long) interval * SECONDS_PER_10MIN);
        return startMoment.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    public static int from24hourToV2Interval(int interval24hour) {
       return interval24hour * DAILY_BATCHES_COUNT;
    }

    public static int fromV2to24hourInterval(int intervalV2) {
        return intervalV2 / DAILY_BATCHES_COUNT;
    }
}
