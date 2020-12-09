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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"dev", "test"})
@WebMvcTest(value = CallbackController.class, properties = {"covid19.federation-gateway.call-back.enabled=true"})
@ContextConfiguration(classes = ExposureNotificationApplication.class)
public class CallbackControllerEnabledTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InboundService inboundService;

    @Test
    public void callBackReturns202() throws Exception {
        mvc.perform(get("/efgs/callback")
                .param("batchTag", "tag-1")
                .param("date", "2020-11-13"))
                .andExpect(status().is(HttpStatus.ACCEPTED.value()));
    }
}
