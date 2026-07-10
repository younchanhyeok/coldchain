package com.coldchain.tracker;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
@Transactional
class TrackerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersTrackerAndReturnsDeviceKeyOnce() throws Exception {
        mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"TRK-TEST-001","productName":"백신 A","thresholdTemp":8.0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackerId").value("TRK-TEST-001"))
                .andExpect(jsonPath("$.deviceKey").value(notNullValue()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));
    }

    @Test
    void rejectsDuplicateTrackerId() throws Exception {
        String body = """
                {"trackerId":"TRK-TEST-DUP","productName":"백신 A","thresholdTemp":8.0}
                """;

        mockMvc.perform(post("/api/v1/trackers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trackers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }
}
