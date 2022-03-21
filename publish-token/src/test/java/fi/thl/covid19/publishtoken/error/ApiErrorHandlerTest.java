package fi.thl.covid19.publishtoken.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"test", "nodb"})
@SpringBootTest
@AutoConfigureMockMvc
public class ApiErrorHandlerTest {

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamedParameterJdbcTemplate jdbc;

    @AfterEach
    public void end() {
        Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test
    public void getInvalidIs404() throws Exception {
        var result = mockMvc.perform(get("/nosuchpath"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse();
        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
    }

    @Test
    public void validationFailureIs400() throws Exception {
        String result = mockMvc.perform(get("/test/input-validation-failure"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertErrorObject(result, HttpStatus.BAD_REQUEST, Optional.of(TestController.FAILURE_STRING));
    }

    @Test
    public void validationFailureIs400WithValidateOnly() throws Exception {
        String result = mockMvc.perform(get("/test/input-validation-failure-validate-only"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertErrorObject(result, HttpStatus.BAD_REQUEST, Optional.of(TestController.FAILURE_STRING));
    }

    @Test
    public void pathParsingErrorIs400() throws Exception {
        String result = mockMvc.perform(get("/test/should-be-int-path/asdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertErrorObject(result, HttpStatus.BAD_REQUEST, Optional.of("Invalid request parameter"));
    }

    @Test
    public void paramParsingErrorIs400() throws Exception {
        String result = mockMvc.perform(get("/test/should-be-int-param?param=asdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertErrorObject(result, HttpStatus.BAD_REQUEST, Optional.of("Invalid request parameter"));
    }

    @Test
    public void illegalStateIsInternal() throws Exception {
        assertInternalError("/test/illegal-state");
    }

    @Test
    public void illegalArgumentIsInternal() throws Exception {
        assertInternalError("/test/illegal-state");
    }

    @Test
    public void sqlExceptionIsInternal() throws Exception {
        assertInternalError("/test/sql-exception");
    }

    public void assertInternalError(String url) throws Exception {
        String result = mockMvc.perform(get(url))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertErrorObject(result, HttpStatus.INTERNAL_SERVER_ERROR, Optional.empty());
    }

    private void assertErrorObject(String jsonResult, HttpStatus status, Optional<String> expectedMessage) throws JsonProcessingException {
        ApiError parsed = mapper.readValue(jsonResult, ApiError.class);
        assertEquals(status.value(), parsed.code);
        assertFalse(parsed.errorId.isEmpty());
        assertEquals(expectedMessage, parsed.message);
    }
}
