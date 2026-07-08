package com.coldchain.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.shipment.dto.ShipmentResponse;
import com.coldchain.shipment.dto.SummaryResponse;
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
 * dev shipper가 고정이라 절대 개수는 다른 테스트의 영향을 받는다 — 이 테스트가 만든 shipment로
 * 인한 "증가분"만 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class).deviceKey();
    }

    private Long createShipment(String trackerId) throws Exception {
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
        return objectMapper.readValue(body, ShipmentResponse.class).shipmentId();
    }

    private void transitionShipment(Long shipmentId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private void sendReading(String trackerId, String deviceKey, double temperature) throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", trackerId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.42, "lon": 127.12, "recordedAt": "%s"}
                                """.formatted(temperature, Instant.now())))
                .andExpect(status().isAccepted());
    }

    private SummaryResponse fetchSummary() throws Exception {
        String body = mockMvc.perform(get("/api/v1/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SummaryResponse.class);
    }

    @Test
    void summaryReflectsInTransitBreachAndDeliveredCounts() throws Exception {
        SummaryResponse before = fetchSummary();

        String breachTrackerId = "TRK-SUMMARY-BREACH";
        String breachDeviceKey = registerTracker(breachTrackerId);
        Long breachShipmentId = createShipment(breachTrackerId);
        transitionShipment(breachShipmentId, "IN_TRANSIT");
        sendReading(breachTrackerId, breachDeviceKey, 9.0); // 임계 8.0 초과
        Thread.sleep(200);

        String deliveredTrackerId = "TRK-SUMMARY-DELIVERED";
        registerTracker(deliveredTrackerId);
        Long deliveredShipmentId = createShipment(deliveredTrackerId);
        transitionShipment(deliveredShipmentId, "IN_TRANSIT");
        transitionShipment(deliveredShipmentId, "DELIVERED");

        SummaryResponse after = fetchSummary();

        assertThat(after.totalShipments()).isEqualTo(before.totalShipments() + 2);
        assertThat(after.inTransit()).isEqualTo(before.inTransit() + 1);
        assertThat(after.breachCount()).isEqualTo(before.breachCount() + 1);
        assertThat(after.deliveredCount()).isEqualTo(before.deliveredCount() + 1);
        assertThat(after.rescuedByPrediction()).isEqualTo(0);
        assertThat(after.avgDeliveryMinutes()).isNotNull();
    }
}
