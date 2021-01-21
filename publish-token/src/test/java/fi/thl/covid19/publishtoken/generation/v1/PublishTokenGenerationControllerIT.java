package fi.thl.covid19.publishtoken.generation.v1;


import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.publishtoken.PublishTokenDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static fi.thl.covid19.publishtoken.generation.v1.PublishTokenGenerationController.SERVICE_NAME_HEADER;
import static fi.thl.covid19.publishtoken.generation.v1.PublishTokenGenerationController.VALIDATE_ONLY_HEADER;
import static java.time.temporal.ChronoUnit.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"covid19.publish-token.validity-duration=PT10M"})
@ActiveProfiles({"dev"})
@AutoConfigureMockMvc
public class PublishTokenGenerationControllerIT {

    private static final String GENERATION_URL = "/publish-token/v1";
    private static final String TEST_SERVICE = "test.service.fi";
    private static final String TEST_USER = "test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PublishTokenDao dao;

    @BeforeEach
    public void setUp() {
        dao.deleteTokensExpiredBefore(Instant.now().plus(48, HOURS));
    }

    @Test
    public void postAndGetWorks() throws Exception {
        assertTokens(TEST_SERVICE, TEST_USER, List.of());

        Instant start = Instant.now().truncatedTo(SECONDS);
        LocalDate symptomsOnset = LocalDate.now().minus(1, DAYS);
        PublishTokenGenerationRequest request = new PublishTokenGenerationRequest(
                TEST_USER, symptomsOnset, Optional.empty(), Optional.empty(), Optional.empty());

        PublishToken generated = verifiedPost(request);
        Instant end = Instant.now().truncatedTo(SECONDS).plus(1, SECONDS);

        assertTokens(TEST_SERVICE, TEST_USER, List.of(generated));

        assertEquals(12, generated.token.length());
        assertFalse(generated.createTime.isBefore(start));
        assertFalse(generated.createTime.isAfter(end));
        // Configured to 10min in test class properties
        assertFalse(generated.validThroughTime.isBefore(start.plus(10, MINUTES)));
        assertFalse(generated.validThroughTime.isAfter(end.plus(10, MINUTES)));
    }

    @Test
    public void validateOnlyDoesntGenerateToken() throws Exception {
        assertTokens(TEST_SERVICE, TEST_USER, List.of());

        PublishTokenGenerationRequest request1 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.empty(), Optional.empty());
        PublishTokenGenerationRequest request2 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.of(false), Optional.of(false));
        PublishTokenGenerationRequest request3 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.of(true), Optional.of(true));

        PublishToken generated1 = verifiedPost(request1);
        PublishToken generated2 = verifiedPost(request2);
        PublishToken generated3 = verifiedPost(request3);

        assertNotEquals(generated1.token, generated2.token);
        assertNotEquals(generated1.token, generated3.token);
        assertNotEquals(generated2.token, generated3.token);

        // The third one is not active due to validate-only
        assertTokens(TEST_SERVICE, TEST_USER, List.of(generated1, generated2));
    }

    @Test
    public void validateOnlyHeaderDoesntGenerateToken() throws Exception {
        assertTokens(TEST_SERVICE, TEST_USER, List.of());

        PublishTokenGenerationRequest request1 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.empty(), Optional.empty());
        PublishTokenGenerationRequest request2 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.of(false), Optional.of(false));
        PublishTokenGenerationRequest request3 = new PublishTokenGenerationRequest(
                TEST_USER, LocalDate.now().minus(1, DAYS), Optional.empty(), Optional.of(true), Optional.of(true));

        PublishToken generated1 = verifiedPost(request1);
        PublishToken generated2 = verifiedPost(request2);
        PublishToken generated3 = verifiedPost(request3);
        // No header -> generated 1st and 2nd according to request
        assertTokens(TEST_SERVICE, TEST_USER, List.of(generated1, generated2));

        PublishToken validateOnly1 = verifiedValidateOnlyPost(request1, true);
        PublishToken validateOnly2 = verifiedValidateOnlyPost(request2, true);
        // Setting header to false works like no header -> uses the field
        PublishToken validateOnly3 = verifiedValidateOnlyPost(request3, false);
        assertNotEquals(generated1.token, validateOnly1.token);
        assertNotEquals(generated2.token, validateOnly2.token);
        assertNotEquals(generated3.token, validateOnly3.token);
        // All set to validateOnly -> Generate nothing new
        assertTokens(TEST_SERVICE, TEST_USER, List.of(generated1, generated2));
    }

    @Test
    public void getInvalidUserIs400() throws Exception {
        mockMvc.perform(get(GENERATION_URL + "/test-'ser")
                .header(SERVICE_NAME_HEADER, "test-service"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid user name")));
    }

    @Test
    public void getInvalidServiceIs400() throws Exception {
        mockMvc.perform(get(GENERATION_URL + "/test-user")
                .header(SERVICE_NAME_HEADER, "te'rvice"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid service name")));
    }

    @Test
    public void postInvalidUserIs400() throws Exception {
        String invalidJson = "{\"requestUser\":\"test-u'ser\",\"symptomsOnset\":\"2099-01-01T12:00:00Z\"}";
        mockMvc.perform(post(GENERATION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SERVICE_NAME_HEADER, "test-service")
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid user name")));
    }

    @Test
    public void postInvalidServiceIs400() throws Exception {
        String invalidJson = "{\"requestUser\":\"test-user\",\"symptomsOnset\":\"2020-01-01T12:00:00Z\"}";
        mockMvc.perform(post(GENERATION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SERVICE_NAME_HEADER, "te'rvice")
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid service name")));
    }

    @Test
    public void postFutureTimestampIs400() throws Exception {
        String futureJson = "{\"requestUser\":\"test-user\",\"symptomsOnset\":\"2099-01-01T12:00:00Z\"}";
        mockMvc.perform(post(GENERATION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SERVICE_NAME_HEADER, "test-service")
                .content(futureJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Symptoms onset time in the future:")));
    }

    @Test
    public void postWithoutPhoneNumberWorks() throws Exception {
        String futureJson = "{\"requestUser\":\"test-user\",\"symptomsOnset\":\"" + Instant.now().truncatedTo(SECONDS).toString() + "\"}";
        mockMvc.perform(post(GENERATION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SERVICE_NAME_HEADER, "test-service")
                .content(futureJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private PublishToken verifiedValidateOnlyPost(PublishTokenGenerationRequest request, boolean validateOnly) throws Exception {
        MvcResult post = mockMvc
                .perform(post(GENERATION_URL)
                        .header(SERVICE_NAME_HEADER, TEST_SERVICE)
                        .header(VALIDATE_ONLY_HEADER, validateOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readValue(post.getResponse().getContentAsString(), PublishToken.class);
    }

    private PublishToken verifiedPost(PublishTokenGenerationRequest request) throws Exception {
        MvcResult post = mockMvc
                .perform(post(GENERATION_URL)
                        .header(SERVICE_NAME_HEADER, TEST_SERVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readValue(post.getResponse().getContentAsString(), PublishToken.class);
    }

    private void assertTokens(String service, String user, List<PublishToken> expected) throws Exception {
        PublishTokenList list = new PublishTokenList(expected);
        mockMvc.perform(get(GENERATION_URL + "/" + user)
                .header(SERVICE_NAME_HEADER, service))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(mapper.writeValueAsString(list)));
    }
}
