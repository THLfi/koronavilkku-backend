package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import fi.thl.covid19.publishtoken.sms.SmsPayload;
import fi.thl.covid19.publishtoken.sms.SmsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ActiveProfiles({"dev"})
@SpringBootTest(properties = {"covid19.publish-token.sms.gateway=http://testaddress"})
@AutoConfigureMockMvc
public class SMSServiceTestIT {

    @Autowired
    private SmsService smsService;

    @MockBean
    private RestTemplate rest;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<SmsPayload>> reqCaptor;

    @BeforeEach
    public void setUp() {
        deleteStatsRows();
    }

    @AfterEach
    public void end() {
        Mockito.verifyNoMoreInteractions(rest);
    }

    @Test
    public void tokenSmsSendingViaGateway() {
        String number = "321654987";
        String token = "123456789012";

        given(rest.postForEntity(eq("http://testaddress"), reqCaptor.capture(), eq(String.class)))
                .willReturn(ResponseEntity.ok("test"));
        assertTrue(smsService.send(number, new PublishToken(token, Instant.now(), Instant.now())));
        assertSmsStatRowAdded();
        HttpEntity<SmsPayload> request = reqCaptor.getValue();
        assertNotNull(request);
        verify(rest).postForEntity(eq("http://testaddress"), any(), eq(String.class));

        assertNotNull(request.getHeaders());
        assertEquals(MediaType.APPLICATION_JSON, request.getHeaders().getContentType());
        assertEquals(List.of(MediaType.APPLICATION_JSON), request.getHeaders().getAccept());

        assertNotNull(request.getBody());
        String message = request.getBody().text;
        String recipient = request.getBody().destination.toArray()[0].toString();
        assertNotNull(message);
        // This is not probably max limit, but let's keep this in here because if we grow our message it should be tested first
        assertTrue(message.length() <= 500);
        assertTrue(message.contains(token));
        assertEquals(number, recipient);
    }

    private void assertSmsStatRowAdded() {
        String sql = "select count(*) from pt.stats_sms_send";
        Integer rows = jdbcTemplate.queryForObject(sql, Collections.emptyMap(), Integer.class);
        assertEquals(1, rows);
    }

    private void deleteStatsRows() {
        jdbcTemplate.update("delete from pt.stats_sms_send", Collections.emptyMap());
    }
}
