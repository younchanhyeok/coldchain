package com.coldchain.shipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import java.math.BigDecimal;
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
@Import(TestcontainersConfiguration.class)
@Transactional
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackerRepository trackerRepository;

    private String givenRegisteredTracker(String trackerId) {
        trackerRepository.save(new Tracker(trackerId, 1L, "백신 A", new BigDecimal("8.0"), "hash"));
        return trackerId;
    }

    private String createShipmentBody(String trackerId) {
        return """
                {
                  "trackerId": "%s",
                  "productName": "백신 A",
                  "origin": {"lat": 37.4201, "lon": 127.1265, "name": "성남 물류센터"},
                  "destination": {"lat": 37.5798, "lon": 127.0034, "name": "서울대병원 약제부"},
                  "consignee": {"name": "서울대병원 약제부", "contact": "02-000-0000"},
                  "driverContact": "010-0000-0000"
                }
                """.formatted(trackerId);
    }

    @Test
    void createsShipmentInReadyStatus() throws Exception {
        String trackerId = givenRegisteredTracker("TRK-SHIP-001");

        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void rejectsSecondActiveShipmentForSameTracker() throws Exception {
        String trackerId = givenRegisteredTracker("TRK-SHIP-002");

        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void rejectsInvalidStatusTransition() throws Exception {
        String trackerId = givenRegisteredTracker("TRK-SHIP-003");

        String response = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long shipmentId = Long.valueOf(response.replaceAll(".*\"shipmentId\":(\\d+).*", "$1"));

        // READY -> DELIVERED는 IN_TRANSIT을 건너뛰므로 허용되지 않는다
        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEMANTIC_INVALID"));
    }

    @Test
    void allowsValidStatusTransition() throws Exception {
        String trackerId = givenRegisteredTracker("TRK-SHIP-004");

        String response = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long shipmentId = Long.valueOf(response.replaceAll(".*\"shipmentId\":(\\d+).*", "$1"));

        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_TRANSIT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }
}
