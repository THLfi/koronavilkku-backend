package fi.thl.covid19.exposurenotification.efgs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.SimpleRequestExpectationManager;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
public class CallbackInitializerTestIT {

    @Autowired
    private FederationGatewayClient federationGatewayClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    @Qualifier("federationGatewayRestTemplate")
    RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build(new SimpleRequestExpectationManager());
    }

    @AfterEach
    public void tearDown() {
        mockServer.verify();
    }

    @Test
    public void initializationEnabledNoChangeNeeded() throws JsonProcessingException {
        CallbackInitializer callbackInitializer = new CallbackInitializer(federationGatewayClient, true, "http://test");
        Callback callback = new Callback("1", "http://test");
        Callback unknown = new Callback("2", "http://unknown");
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(List.of(callback, unknown)))
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback/2"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("")
                );
        callbackInitializer.initializeCallback();
    }

    @Test
    public void initializationEnabledChangeNeeded() throws JsonProcessingException {
        CallbackInitializer callbackInitializer = new CallbackInitializer(federationGatewayClient, true, "http://test");
        Callback callback = new Callback("1", "http://testold");
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(List.of(callback)))
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback/1?url=http://test"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("")
                );
        callbackInitializer.initializeCallback();
    }

    @Test
    public void initializationDisabled() throws JsonProcessingException {
        CallbackInitializer callbackInitializer = new CallbackInitializer(federationGatewayClient, false, "http://test");
        Callback callback = new Callback("1", "http://test");
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(List.of(callback)))
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback/1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("")
                );
        callbackInitializer.initializeCallback();
    }

    @Test
    public void localUrlEmpty() throws JsonProcessingException {
        CallbackInitializer callbackInitializer = new CallbackInitializer(federationGatewayClient, true, "");
        Callback callback = new Callback("1", "http://test");
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(List.of(callback)))
                );
        mockServer.expect(ExpectedCount.once(),
                requestTo("http://localhost:8080/diagnosiskeys/callback/1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("")
                );
        callbackInitializer.initializeCallback();
    }
}
