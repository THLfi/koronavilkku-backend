package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import static fi.thl.covid19.publishtoken.Validation.SERVICE_NAME_MAX_LENGTH;
import static fi.thl.covid19.publishtoken.Validation.USER_NAME_MAX_LENGTH;
import static java.time.temporal.ChronoUnit.HOURS;
import static net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils.repeat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"dev"})
@AutoConfigureMockMvc
public class PublishTokenDaoIT {

    private static final String TOKEN_STAT_TABLE_NAME = "stats_tokens_created";
    private static final String SMS_STAT_TABLE_NAME = "stats_sms_created";

    @Autowired
    private PublishTokenDao dao;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        dao.deleteTokensExpiredBefore(Instant.now().plus(48, HOURS));
        deleteStatsRows();
    }

    @Test
    public void doubleInsertSameTokenFails() {
        PublishToken token = new PublishToken("testtoken", Instant.now(), Instant.now().plus(1, HOURS));
        assertTrue(dao.storeToken(token, LocalDate.now(), "testservice", "testuser"));
        assertFalse(dao.storeToken(token, LocalDate.now(), "testservice", "testuser"));
        assertStatRowAdded(TOKEN_STAT_TABLE_NAME);
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

    @Test
    public void testSmsStatRowsAddedOk() {
        dao.addStatsRow(Instant.now(), SMS_STAT_TABLE_NAME);
        assertStatRowAdded(SMS_STAT_TABLE_NAME);
    }

    private void assertStatRowAdded(String tableName) {
        String sql = "select count(*) from pt." + tableName;
        Integer rows = jdbcTemplate.queryForObject(sql, Collections.emptyMap(), Integer.class);
        assertEquals(1, rows);
    }

    private void deleteStatsRows() {
        jdbcTemplate.update("delete from pt." + TOKEN_STAT_TABLE_NAME, Collections.emptyMap());
        jdbcTemplate.update("delete from pt." + SMS_STAT_TABLE_NAME, Collections.emptyMap());
    }
}
