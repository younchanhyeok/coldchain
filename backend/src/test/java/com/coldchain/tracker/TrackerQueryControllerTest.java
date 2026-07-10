package com.coldchain.tracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @Transactional을 의도적으로 붙이지 않는다 — 이유는 IngestControllerTest 상단 주석 참고
 * (tracker_latest upsert의 REQUIRES_NEW가 테스트 트랜잭션에 감싸인 미커밋 tracker를 못 봄).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class TrackerQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TrackerRegisterResponse registerTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class);
    }

    private void createAndStartShipment(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackerId": "%s",
                                  "productName": "백신 A",
                                  "origin": {"lat": 37.4201, "lon": 127.1265, "name": "성남 물류센터"},
                                  "destination": {"lat": 37.5798, "lon": 127.0034, "name": "서울대병원 약제부"}
                                }
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long shipmentId = Long.valueOf(body.replaceAll(".*\"shipmentId\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_TRANSIT"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void listShowsBreachStatusWhenTemperatureExceedsThreshold() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-QUERY-001");
        createAndStartShipment(tracker.trackerId());

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 12.5, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(Instant.now())))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/trackers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.trackerId=='TRK-QUERY-001')].status").value("BREACH"))
                .andExpect(jsonPath("$.content[?(@.trackerId=='TRK-QUERY-001')].activePrediction").value((Object) null))
                .andExpect(jsonPath("$.content[?(@.trackerId=='TRK-QUERY-001')].originName").value("성남 물류센터"))
                .andExpect(jsonPath("$.content[?(@.trackerId=='TRK-QUERY-001')].destinationName").value("서울대병원 약제부"));
    }

    @Test
    void listExcludesReadyShipmentsByDefault() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-QUERY-002");
        // READY 상태로만 두고 IN_TRANSIT 전이하지 않음
        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackerId": "%s",
                                  "productName": "백신 A",
                                  "origin": {"lat": 37.4201, "lon": 127.1265, "name": "성남 물류센터"},
                                  "destination": {"lat": 37.5798, "lon": 127.0034, "name": "서울대병원 약제부"}
                                }
                                """.formatted(tracker.trackerId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/trackers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.trackerId=='TRK-QUERY-002')]").isEmpty());
    }

    @Test
    void detailReturnsShipmentSummary() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-QUERY-003");
        createAndStartShipment(tracker.trackerId());

        mockMvc.perform(get("/api/v1/trackers/{id}", tracker.trackerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackerId").value("TRK-QUERY-003"))
                .andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.activeAnomalies").isArray());
    }
}
