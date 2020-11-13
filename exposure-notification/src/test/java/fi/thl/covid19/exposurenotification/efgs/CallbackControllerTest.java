package fi.thl.covid19.exposurenotification.efgs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
public class CallbackControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FederationGatewayService federationGatewayService;


    @Test
    public void callBackReturnsOk() throws Exception {
        mvc.perform(get("/efgs/callback")
                .param("batchTag", "tag-1")
                .param("date", "2020-11-13"))
                .andExpect(status().isOk());
    }

    @Test
    public void callBackReturnsInvalidDate() throws Exception {
        mvc.perform(get("/efgs/callback")
                .param("batchTag", "tag-1")
                .param("date", "2020-11-13455647"))
                .andExpect(status().is4xxClientError());
    }
}
