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
 * @Transactional을 의도적으로 붙이지 않는다 — 이유는 IngestControllerTest 상단 주석 참고.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class TrackControllerTest {

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

    private void sendReading(String trackerId, String deviceKey, double temperature, double lat, double lon)
            throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", trackerId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": %s, "lon": %s, "recordedAt": "%s"}
                                """.formatted(temperature, lat, lon, Instant.now())))
                .andExpect(status().isAccepted());
    }

    @Test
    void returnsPathDestinationAndDistance() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-TRACK-001");
        createAndStartShipment(tracker.trackerId());

        sendReading(tracker.trackerId(), tracker.deviceKey(), 5.0, 37.4201, 127.1265);
        sendReading(tracker.trackerId(), tracker.deviceKey(), 5.2, 37.4500, 127.1000);

        mockMvc.perform(get("/api/v1/trackers/{id}/track", tracker.trackerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackerId").value("TRK-TRACK-001"))
                .andExpect(jsonPath("$.path.type").value("LineString"))
                .andExpect(jsonPath("$.path.coordinates.length()").value(2))
                .andExpect(jsonPath("$.destination.name").value("서울대병원 약제부"))
                .andExpect(jsonPath("$.remainingDistanceMeters").isNumber());
    }

    @Test
    void returnsNotFoundWhenNoActiveShipment() throws Exception {
        registerTracker("TRK-TRACK-002");

        mockMvc.perform(get("/api/v1/trackers/{id}/track", "TRK-TRACK-002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void breachSegmentsOnlyIncludeConsecutiveReadingsOverThreshold() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-TRACK-003");
        createAndStartShipment(tracker.trackerId());

        sendReading(tracker.trackerId(), tracker.deviceKey(), 5.0, 37.42, 127.12); // 임계(8.0) 이하
        sendReading(tracker.trackerId(), tracker.deviceKey(), 12.0, 37.45, 127.10); // 초과 시작
        sendReading(tracker.trackerId(), tracker.deviceKey(), 13.0, 37.46, 127.09); // 초과 지속(같은 구간)

        mockMvc.perform(get("/api/v1/trackers/{id}/track", tracker.trackerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breachSegments.length()").value(1))
                .andExpect(jsonPath("$.breachSegments[0].type").value("LineString"))
                .andExpect(jsonPath("$.breachSegments[0].coordinates.length()").value(2));
    }
}
