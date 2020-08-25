package fi.thl.covid19.publishtoken.verification.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.publishtoken.PublishTokenDao;
import fi.thl.covid19.publishtoken.generation.v1.PublishToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static fi.thl.covid19.publishtoken.verification.v1.PublishTokenVerificationController.TOKEN_HEADER;
import static java.time.temporal.ChronoUnit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"dev"})
@AutoConfigureMockMvc
public class PublishTokenVerificationControllerIT {

    private static final String VERIFICATION_URL = "/verification/v1";

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
    public void existingTokenIsVerified() throws Exception {
        LocalDate onset = LocalDate.now().minus(2, DAYS);
        addToken("000000000001", onset);
        expectSuccessfulVerification("000000000001", onset);
    }

    @Test
    public void missingTokenIsNotVerified() throws Exception {
        expectFailedVerification("000000000002");
    }

    @Test
    public void expiredTokenIsNotVerified() throws Exception {
        Instant created = Instant.now().minus(48, HOURS).truncatedTo(SECONDS);
        Instant expired = created.plus(24, HOURS);
        addToken("000000000003", created, expired, LocalDate.now().minus(3, DAYS));
        expectFailedVerification("000000000003");
    }

    @Test
    public void invalidPublishTokenIs400() throws Exception {
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "12345678901"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "1234567890123"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "1234567890 23"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "123456789012 "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "12345678901A"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, "12345678901a"))
                .andExpect(status().isBadRequest());
    }

    private void addToken(String token, LocalDate symptomsOnset) {
        Instant created = Instant.now().truncatedTo(SECONDS);
        addToken(token, created, created.plus(24, HOURS), symptomsOnset);
    }

    private void addToken(String token, Instant created, Instant expired, LocalDate symptomsOnset) {
        dao.storeToken(new PublishToken(token, created, expired),
                symptomsOnset, "testservice", "testuser");
    }

    private void expectFailedVerification(String token) throws Exception {
        mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, token))
                .andExpect(status().isNoContent());
    }

    private void expectSuccessfulVerification(String token, LocalDate symptomsOnset) throws Exception {
        String body = mockMvc.perform(get(VERIFICATION_URL).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        PublishTokenVerification verification = mapper.readValue(body, PublishTokenVerification.class);
        assertEquals(symptomsOnset, verification.symptomsOnset);
    }
}
