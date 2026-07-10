package com.coldchain.shipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 뷰(v1) 집계 계약 — 어드민 키 검증(기존 metrics와 동일 패턴) + 활성 트래커 수 정합성. */
@SpringBootTest(properties = "app.auth.admin-key=test-admin-key")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
@Transactional
class AdminOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackerRepository trackerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String givenTracker(String trackerId) {
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
    void rejectsRequestWithoutAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRequestWithWrongAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview").header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_reflectsActiveTrackerCountAcrossShipmentLifecycle() throws Exception {
        long before = readActiveTrackerCount();

        String trackerId = givenTracker("TRK-ADMIN-OVERVIEW");
        Long shipmentId = createShipment(trackerId);

        // READY도 DELIVERED가 아니므로 활성으로 집계된다.
        assertActiveTrackerCount(before + 1);

        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk());
        assertActiveTrackerCount(before + 1);

        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());
        assertActiveTrackerCount(before);
    }

    private Long createShipment(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("shipmentId").asLong();
    }

    private long readActiveTrackerCount() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/overview").header("X-Admin-Key", "test-admin-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipperCount").isNumber())
                .andExpect(jsonPath("$.activeTrackerCount").isNumber())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("activeTrackerCount").asLong();
    }

    private void assertActiveTrackerCount(long expected) throws Exception {
        assertThat(readActiveTrackerCount()).isEqualTo(expected);
    }
}
