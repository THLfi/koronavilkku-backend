package fi.thl.covid19.exposurenotification.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import fi.thl.covid19.exposurenotification.configuration.v2.ExposureConfigurationV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"dev","test"})
@SpringBootTest
@AutoConfigureMockMvc
public class ConfigurationControllerIT {

    private static final String CONFIG_URL = "/exposure/configuration/v1";
    private static final String CONFIG_URL_V2 = "/exposure/configuration/v2";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfigurationDao dao;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void validConfigIsReturnedWhenFetchingWithNoVersion() throws Exception {
        String expected = objectMapper.writeValueAsString(dao.getLatestExposureConfiguration());
        mockMvc.perform(get(CONFIG_URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expected));
    }

    @Test
    public void validV2ConfigIsReturnedWhenFetchingWithNoVersion() throws Exception {
        String expected = objectMapper.writeValueAsString(dao.getLatestV2ExposureConfiguration());
        mockMvc.perform(get(CONFIG_URL_V2))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expected));
    }

    @Test
    public void validConfigIsReturnedWhenFetchingWithPreviousVersion() throws Exception {
        ExposureConfiguration latest = dao.getLatestExposureConfiguration();
        String expected = objectMapper.writeValueAsString(latest);
        mockMvc.perform(get(CONFIG_URL + "?previous=" + (latest.version - 1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expected));
    }

    @Test
    public void validV2ConfigIsReturnedWhenFetchingWithPreviousVersion() throws Exception {
        ExposureConfigurationV2 latest = dao.getLatestV2ExposureConfiguration();
        String expected = objectMapper.writeValueAsString(latest);
        mockMvc.perform(get(CONFIG_URL_V2 + "?previous=" + (latest.version - 1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(expected));
    }

    @Test
    public void nothingIsReturnedWhenFetchingWithLatestVersion() throws Exception {
        mockMvc.perform(get(CONFIG_URL + "?previous=" + dao.getLatestExposureConfiguration().version))
                .andExpect(status().isNoContent());
    }

    @Test
    public void nothingIsReturnedWhenFetchingWithLatestVersionV2() throws Exception {
        mockMvc.perform(get(CONFIG_URL_V2 + "?previous=" + dao.getLatestV2ExposureConfiguration().version))
                .andExpect(status().isNoContent());
    }

    @Test
    public void nothingIsReturnedWhenFetchingWithTooNewVersion() throws Exception {
        mockMvc.perform(get(CONFIG_URL + "?previous=5000"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void nothingIsReturnedWhenFetchingWithTooNewVersionV2() throws Exception {
        mockMvc.perform(get(CONFIG_URL_V2 + "?previous=5000"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void invalidPreviousIs400() throws Exception {
        mockMvc.perform(get(CONFIG_URL + "?previous=asdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }

    @Test
    public void invalidPreviousIs400V2() throws Exception {
        mockMvc.perform(get(CONFIG_URL_V2 + "?previous=asdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("Invalid request parameter")));
    }
}
