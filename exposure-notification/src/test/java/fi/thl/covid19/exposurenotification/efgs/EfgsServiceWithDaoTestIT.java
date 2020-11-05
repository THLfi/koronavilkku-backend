package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.diagnosiskey.DiagnosisKeyDao;
import fi.thl.covid19.exposurenotification.diagnosiskey.IntervalNumber;
import fi.thl.covid19.exposurenotification.diagnosiskey.TemporaryExposureKey;
import fi.thl.covid19.exposurenotification.diagnosiskey.TestKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fi.thl.covid19.exposurenotification.efgs.FederationGatewayBatchUtil.serialize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles({"dev","test"})
public class EfgsServiceWithDaoTestIT {

    @Autowired
    private DiagnosisKeyDao dao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    FederationGatewayService federationGatewayService;

    @MockBean
    FederationGatewayClient client;

    private TestKeyGenerator keyGenerator;

    @BeforeEach
    public void setUp() {
        keyGenerator = new TestKeyGenerator(123);
        dao.deleteKeysBefore(Integer.MAX_VALUE);
    }

    @Test
    public void downloadKeys() {
        List<TemporaryExposureKey> keys = keyGenerator.someKeys(10);
        Mockito.when(client.download(anyString(), ArgumentMatchers.any())).thenReturn(generateDownloadData(keys));
        federationGatewayService.startInbound(Optional.empty());

        List<TemporaryExposureKey> dbKeys = dao.getIntervalKeys(IntervalNumber.to24HourInterval(Instant.now()));
        // TODO: add sensible assertions
        assertTrue(keys.size() == dbKeys.size());
    }

    private List<byte[]> generateDownloadData(List<TemporaryExposureKey> keys) {
        return List.of(serialize(FederationGatewayBatchUtil.transform(keys)));
    }
}
