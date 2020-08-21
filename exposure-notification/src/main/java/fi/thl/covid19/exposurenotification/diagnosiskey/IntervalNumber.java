package fi.thl.covid19.exposurenotification.diagnosiskey;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class IntervalNumber {

    public static final int SECONDS_PER_24H = 60*60*24;
    public static final int SECONDS_PER_10MIN = 60*10;
    public static final int INTERVALS_10MIN_PER_24H = 6*24;
    public static final Instant MAX_REPRESENTABLE_INSTANT =
            Instant.ofEpochSecond(Integer.MAX_VALUE * SECONDS_PER_10MIN);

    public final Instant instant;

    private IntervalNumber(Instant instant) {
        if (instant.isAfter(MAX_REPRESENTABLE_INSTANT)) {
            throw new IllegalStateException("Cannot represent time with int interval number: instant=" + instant);
        }
        this.instant = instant;
    }

    public static IntervalNumber now() {
        return ofInstant(Instant.now());
    }

    public static IntervalNumber ofInstant(Instant instant) {
        return new IntervalNumber(instant);
    }

    public static IntervalNumber of24hNumber(int number) {
        return new IntervalNumber(startMomentOf24HourInterval(number));
    }

    public static IntervalNumber of10minNumber(int number) {
        return new IntervalNumber(startMomentOf10MinInterval(number));
    }

    public IntervalNumber atStartOfDate() {
        return of24hNumber(get24HourInterval());
    }

    public Instant getInstant() {
        return instant;
    }

    public int get24HourInterval() {
        return to24HourInterval(instant);
    }

    public static int to24HourInterval(Instant time) {
        long value = time.getEpochSecond() / SECONDS_PER_24H;
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
        return to10MinInterval(startMomentOf24HourInterval(to24HourInterval(time)));
    }

    public static long startSecondOf24HourInterval(int interval24h) {
        return interval24h * SECONDS_PER_24H;
    }

    private static Instant startMomentOf24HourInterval(int interval) {
        return Instant.ofEpochSecond(SECONDS_PER_24H*interval);
    }

    private static Instant startMomentOf10MinInterval(int interval) {
        return Instant.ofEpochSecond(SECONDS_PER_10MIN*interval);
    }

    public static LocalDate utcDateOf10MinInterval(int interval) {
        return startMomentOf10MinInterval(interval).atOffset(ZoneOffset.UTC).toLocalDate();
    }
}
