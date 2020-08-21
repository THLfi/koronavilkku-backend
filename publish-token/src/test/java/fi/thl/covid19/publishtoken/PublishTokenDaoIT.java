package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;

import static fi.thl.covid19.publishtoken.Validation.SERVICE_NAME_MAX_LENGTH;
import static fi.thl.covid19.publishtoken.Validation.USER_NAME_MAX_LENGTH;
import static java.time.temporal.ChronoUnit.HOURS;
import static net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils.repeat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
public class PublishTokenDaoIT {

    @Autowired
    private PublishTokenDao dao;

    @BeforeEach
    public void setUp() {
        dao.deleteTokensExpiredBefore(Instant.now().plus(48, HOURS));
    }

    @Test
    public void doubleInsertSameTokenFails() {
        PublishToken token = new PublishToken("testtoken", Instant.now(), Instant.now().plus(1, HOURS));
        assertTrue(dao.storeToken(token, LocalDate.now(), "testservice", "testuser"));
        assertFalse(dao.storeToken(token, LocalDate.now(), "testservice", "testuser"));
    }

    @Test
    public void maximumLengthFieldsAreWrittenOk() {
        PublishToken token = new PublishToken("123456789012", Instant.now(), Instant.now().plus(1, HOURS));
        dao.storeToken(token, LocalDate.now(), repeat("a", SERVICE_NAME_MAX_LENGTH), repeat("a", USER_NAME_MAX_LENGTH));
    }

    @Test
    public void minimumLengthFieldsAreWrittenOk() {
        PublishToken token = new PublishToken("123456789012", Instant.now(), Instant.now().plus(1, HOURS));
        dao.storeToken(token, LocalDate.now(), "a", "a");
    }
}
