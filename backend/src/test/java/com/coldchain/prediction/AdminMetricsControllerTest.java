package com.coldchain.prediction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.auth.admin-key=test-admin-key")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/prediction")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRequestWithWrongAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/prediction")
                        .header("X-Admin-Key", "wrong-key")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsMetricsShapeWithValidAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics/prediction")
                        .header("X-Admin-Key", "test-admin-key")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPredictions").isNumber())
                .andExpect(jsonPath("$.truePositives").isNumber())
                .andExpect(jsonPath("$.falsePositives").isNumber())
                .andExpect(jsonPath("$.missedBreaches").isNumber())
                .andExpect(jsonPath("$.episodes").isArray());
    }
}
