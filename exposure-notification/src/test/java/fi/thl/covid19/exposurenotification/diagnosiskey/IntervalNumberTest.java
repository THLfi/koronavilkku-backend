package fi.thl.covid19.exposurenotification.diagnosiskey;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.*;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntervalNumberTest {

    // Max moment representable via 10 min integer interval number (as is done by GAEN API)
    private static final Instant MAX_SUPPORTED_TIME = Instant.ofEpochSecond(60L*10L*Integer.MAX_VALUE);

    @Test
    public void getting24hIntervalWorks() {
        assertEquals(0, to24HourInterval(Instant.EPOCH));
        assertEquals(20, to24HourInterval(Instant.parse("1970-01-21T12:00:00Z")));
        assertEquals(Integer.MAX_VALUE/(6*24), to24HourInterval(MAX_SUPPORTED_TIME));
    }

    @Test
    public void getting10MinIntervalWorks() {
        assertEquals(0, to10MinInterval(Instant.EPOCH));
        assertEquals(2943, to10MinInterval(Instant.parse("1970-01-21T10:30:00Z")));
        assertEquals(Integer.MAX_VALUE, to10MinInterval(MAX_SUPPORTED_TIME));
    }

    @Test
    public void dayFirstIntervalWorks() {
        assertEquals(to10MinInterval(Instant.parse("2020-10-01T00:00:00Z")),
                dayFirst10MinInterval(Instant.parse("2020-10-01T12:00:00Z")));
    }

    @Test
    public void dayLastIntervalWorks() {
        assertEquals(to10MinInterval(Instant.parse("2020-10-01T23:55:00Z")),
                dayLast10MinInterval(Instant.parse("2020-10-01T12:00:00Z")));
    }

    @Test
    public void utcLocalDateWorks() {
        Instant now = Instant.now();
        assertEquals(now.atZone(UTC).toLocalDate(), utcDateOf10MinInterval(to10MinInterval(now)));
        assertEquals(Instant.EPOCH.atZone(UTC).toLocalDate(), utcDateOf10MinInterval(to10MinInterval(Instant.EPOCH)));
        assertEquals(MAX_SUPPORTED_TIME.atZone(UTC).toLocalDate(), utcDateOf10MinInterval(to10MinInterval(MAX_SUPPORTED_TIME)));
    }

    @Test
    public void secondsAreCorrectlyCalculatedFor24hInterval() {
        assertEquals(0, startSecondOf24HourInterval(to24HourInterval(Instant.EPOCH)));
        assertEquals(
                MAX_SUPPORTED_TIME.getEpochSecond() - MAX_SUPPORTED_TIME.getEpochSecond() % (24*60*60),
                startSecondOf24HourInterval(to24HourInterval(MAX_SUPPORTED_TIME)));
    }
}
