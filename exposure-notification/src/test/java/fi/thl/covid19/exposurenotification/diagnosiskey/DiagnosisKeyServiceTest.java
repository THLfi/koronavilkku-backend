package fi.thl.covid19.exposurenotification.diagnosiskey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    @Test
    public void exportedKeyCountIsCalculatedCorrectly() throws Exception {
        Method method = DiagnosisKeyService.class.getDeclaredMethod("getExportedKeyCount", List.class);
        method.setAccessible(true);
        long exportedCount = (Long) method.invoke(sut, generateCountTestKeys());
        assertEquals(3, exportedCount);
    }

    private List<TemporaryExposureKey> generateCountTestKeys() {
        List<TemporaryExposureKey> list = new ArrayList<>();
        list.add(generateAt(LocalDate.parse("2020-10-20"), 144, 0));
        list.add(generateAt(LocalDate.parse("2020-10-20"), 144, 1));
        list.add(generateAt(LocalDate.parse("2020-10-20"), 144, 2));
        list.add(generateAt(LocalDate.parse("2020-10-20"), 144, 3));
        list.add(generateAt(LocalDate.parse("2020-10-20"), 144, 7));

        return list;
    }

    private List<TemporaryExposureKey> filter(List<TemporaryExposureKey> keys, String now) {
        return sut.filter(keys, Instant.parse(now));
    }

    private TemporaryExposureKey generateAt(LocalDate keyDate) {
        return generateAt(keyDate, 144);
    }

    private TemporaryExposureKey generateAt(LocalDate keyDate, int rollingPeriod) {
        return generateAt(keyDate, rollingPeriod, 1);
    }

    private TemporaryExposureKey generateAt(LocalDate keyDate, int rollingPeriod, int transmissionRiskLevel) {
        int interval = to10MinInterval(keyDate.atStartOfDay(UTC).toInstant());
        return new TemporaryExposureKey("c9Uau9icuBlvDvtokvlNaA==", transmissionRiskLevel, interval,
                rollingPeriod, Set.of(), 0, "FI", false);
    }
}
