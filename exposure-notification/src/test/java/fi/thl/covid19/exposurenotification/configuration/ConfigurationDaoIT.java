package fi.thl.covid19.exposurenotification.configuration;

import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: These tests require the DB to be available and configured through ENV.
 */
@SpringBootTest
@ActiveProfiles({"test"})
@AutoConfigureMockMvc
public class ConfigurationDaoIT {

    @Autowired
    private ConfigurationDao dao;

    @Test
    public void exposureConfigIsReturned() {
        ExposureConfiguration config = dao.getLatestExposureConfiguration();
        assertTrue(config.version > 0);
    }
}
