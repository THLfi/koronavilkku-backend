package fi.thl.covid19.exposurenotification.efgs;

import fi.thl.covid19.exposurenotification.ExposureNotificationApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ActiveProfiles({"dev", "test"})
@WebMvcTest(CallbackController.class)
@ContextConfiguration(classes= ExposureNotificationApplication.class)
public class CallbackControllerDisabledTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FederationGatewaySyncService federationGatewaySyncService;

    @Test
    public void callBackReturns503() throws Exception {
        mvc.perform(get("/efgs/callback")
                .param("batchTag", "tag-1")
                .param("date", "2020-11-13"))
                .andExpect(status().is(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @Test
    public void callBackInvalidDateReturnsClientError() throws Exception {
        mvc.perform(get("/efgs/callback")
                .param("batchTag", "tag-1")
                .param("date", "2020-11-13455647"))
                .andExpect(status().is4xxClientError());
    }
}
