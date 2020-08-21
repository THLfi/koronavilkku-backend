package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import fi.thl.covid19.publishtoken.sms.SmsService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ActiveProfiles({"test","nodb"})
@SpringBootTest(properties = { "covid19.publish-token.sms.gateway=http://testaddress" })
@AutoConfigureMockMvc
public class SMSServiceTest {

    @Autowired
    private SmsService smsService;

    @MockBean
    private RestTemplate rest;
    @MockBean
    private NamedParameterJdbcTemplate jdbc;

    @Captor
    private ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> reqCaptor;

    @AfterEach
    public void end() {
        Mockito.verifyNoMoreInteractions(jdbc);
        Mockito.verifyNoMoreInteractions(rest);
    }

    @Test
    public void tokenSmsSendingViaGateway() {
        String number = "321654987";
        String token = "123456789012";

        given(rest.postForEntity(eq("http://testaddress"), reqCaptor.capture(), eq(String.class)))
                .willReturn(ResponseEntity.ok("test"));
        assertTrue(smsService.send(number, new PublishToken(token, Instant.now(), Instant.now())));
        HttpEntity<MultiValueMap<String, String>> request = reqCaptor.getValue();
        assertNotNull(request);
        verify(rest).postForEntity(eq("http://testaddress"), any(), eq(String.class));

        assertNotNull(request.getHeaders());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, request.getHeaders().getContentType());
        assertEquals(List.of(MediaType.TEXT_PLAIN), request.getHeaders().getAccept());

        assertNotNull(request.getBody());
        String message = request.getBody().getFirst("text");
        String recipient = request.getBody().getFirst("recipient");
        assertNotNull(message);
        assertTrue(message.length() <= 160); // Single SMS
        assertTrue(message.contains(token));
        assertEquals(number, recipient);
    }
}
