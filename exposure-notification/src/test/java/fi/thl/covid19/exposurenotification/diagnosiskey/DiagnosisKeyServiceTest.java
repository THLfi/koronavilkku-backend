package fi.thl.covid19.exposurenotification.diagnosiskey;

import fi.thl.covid19.exposurenotification.diagnosiskey.v1.TemporaryExposureKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber.to10MinInterval;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ActiveProfiles({"dev","test","nodb"})
@SpringBootTest
public class DiagnosisKeyServiceTest {

    @Autowired
    private DiagnosisKeyService sut;

    @MockBean
    private NamedParameterJdbcTemplate jdbc;

    @AfterEach
    public void end() {
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    public void filterAcceptsKeyInsideRange() {
        List<TemporaryExposureKey> keys = List.of(
                generateAt(LocalDate.parse("2020-07-02")),
                generateAt(LocalDate.parse("2020-07-03")),
                generateAt(LocalDate.parse("2020-07-04")),
                generateAt(LocalDate.parse("2020-07-05")),
                generateAt(LocalDate.parse("2020-07-06")),
                generateAt(LocalDate.parse("2020-07-07")),
                generateAt(LocalDate.parse("2020-07-08")),
                generateAt(LocalDate.parse("2020-07-09")),
                generateAt(LocalDate.parse("2020-07-10")),
                generateAt(LocalDate.parse("2020-07-11")),
                generateAt(LocalDate.parse("2020-07-12")),
                generateAt(LocalDate.parse("2020-07-13")),
                generateAt(LocalDate.parse("2020-07-14")),
                generateAt(LocalDate.parse("2020-07-15")));
        assertEquals(keys, filter(keys, "2020-07-16T14:00:00Z"));
    }

    @Test
    public void filterAcceptsTodaysKeys() {
        List<TemporaryExposureKey> keys = List.of(
                generateAt(LocalDate.parse("2020-07-16"), 14*6),
                generateAt(LocalDate.parse("2020-07-16"), 144));
        assertEquals(keys, filter(keys, "2020-07-16T14:00:00Z"));
    }

    @Test
    public void filterRejectsOver14daysOldKey() {
        List<TemporaryExposureKey> keys = List.of(generateAt(LocalDate.parse("2020-07-01")));
        assertEquals(List.of(), filter(keys, "2020-07-16T14:00:00Z"));
    }

    @Test
    public void filterRejectsKeyInFuture() {
        List<TemporaryExposureKey> keys = List.of(generateAt(LocalDate.parse("2020-07-17")));
        assertEquals(List.of(), filter(keys, "2020-07-16T14:00:00Z"));
    }

    private List<TemporaryExposureKey> filter(List<TemporaryExposureKey> keys, String now) {
        return sut.filter(keys, Instant.parse(now));
    }

    private TemporaryExposureKey generateAt(LocalDate keyDate) {
        return generateAt(keyDate, 144);
    }

    private TemporaryExposureKey generateAt(LocalDate keyDate, int rollingPeriod) {
        int interval = to10MinInterval(keyDate.atStartOfDay(UTC).toInstant());
        return new TemporaryExposureKey("c9Uau9icuBlvDvtokvlNaA==", 1, interval, rollingPeriod);
    }
}
