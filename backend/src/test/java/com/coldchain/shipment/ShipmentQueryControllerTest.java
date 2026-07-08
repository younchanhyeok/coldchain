package com.coldchain.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.shipment.dto.ShipmentListResponse;
import com.coldchain.shipment.dto.ShipmentResponse;
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
 * dev shipper가 고정(=1)이라 다른 테스트가 만든 shipment도 같은 목록에 섞인다 — size를 크게
 * 잡고 만든 trackerId로 찾아서 검증한다(전체 개수 단정은 하지 않음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentQueryControllerTest {

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

    private ShipmentListResponse fetchAll() throws Exception {
        String body = mockMvc.perform(get("/api/v1/shipments").param("size", "10000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ShipmentListResponse.class);
    }

    @Test
    void listIncludesInTransitShipmentWithTrackerStatus() throws Exception {
        String trackerId = "TRK-SHIPLIST-BREACH";
        String deviceKey = registerTracker(trackerId);
        Long shipmentId = createShipment(trackerId);
        transitionShipment(shipmentId, "IN_TRANSIT");
        sendReading(trackerId, deviceKey, 9.0); // BREACH(임계 8.0 초과)
        Thread.sleep(200);

        ShipmentListResponse list = fetchAll();
        var found = list.content().stream().filter(s -> s.trackerId().equals(trackerId)).findFirst().orElseThrow();

        assertThat(found.shipmentStatus().toString()).isEqualTo("IN_TRANSIT");
        assertThat(found.trackerStatus().toString()).isEqualTo("BREACH");
        assertThat(found.deliveredAt()).isNull();
    }

    @Test
    void listIncludesDeliveredShipmentWithDeliveredAtStamped() throws Exception {
        String trackerId = "TRK-SHIPLIST-DELIVERED";
        registerTracker(trackerId);
        Long shipmentId = createShipment(trackerId);
        transitionShipment(shipmentId, "IN_TRANSIT");
        transitionShipment(shipmentId, "DELIVERED");

        ShipmentListResponse list = fetchAll();
        var found = list.content().stream().filter(s -> s.trackerId().equals(trackerId)).findFirst().orElseThrow();

        assertThat(found.shipmentStatus().toString()).isEqualTo("DELIVERED");
        assertThat(found.deliveredAt()).isNotNull();
    }
}
