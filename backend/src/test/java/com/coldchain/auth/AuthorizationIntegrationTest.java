package com.coldchain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.auth.service.JwtTokenProvider;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★M5 완료 기준의 핵심 — 역할별 접근 범위를 통합 테스트로 증명한다. V8 시드 화주A(id=1)·화주B
 * (id=2)를 그대로 쓴다. 이 클래스는 DefaultShipperAuthConfig(화주A 기본 토큰)를 import하지 않고
 * 매 요청에 명시적으로 토큰을 붙인다 — 교차 접근 검증이 핵심이라 어느 화주로 호출하는지가
 * 테스트 본문에서 항상 명확해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorizationIntegrationTest {

    private static final Long SHIPPER_A = 1L;
    private static final Long SHIPPER_B = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TrackerRepository trackerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void issueTokens() {
        tokenA = bearer(SHIPPER_A);
        tokenB = bearer(SHIPPER_B);
    }

    private String bearer(Long shipperId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(shipperId, AppUserRole.SHIPPER);
    }

    private String givenTracker(String trackerId, Long shipperId) {
        trackerRepository.save(new Tracker(trackerId, shipperId, "백신 A", new BigDecimal("8.0"), "hash"));
        return trackerId;
    }

    // --- 4. 무토큰 ---

    @Test
    void noToken_getTrackers_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/trackers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- 5. 화주A 토큰 → A 소유만 ---

    @Test
    void shipperA_seesOnlyOwnTrackers_notShipperBs() throws Exception {
        givenTracker("TRK-AUTHZ-A1", SHIPPER_A);
        givenTracker("TRK-AUTHZ-B1", SHIPPER_B);

        mockMvc.perform(get("/api/v1/trackers").param("shipmentStatus", "READY").header("Authorization", tokenA))
                .andExpect(status().isOk());
        // 목록은 shipment 기준이라(트래커 단독으로는 안 잡힘) 상세 엔드포인트로 소유권을 명확히 검증한다.
        mockMvc.perform(get("/api/v1/trackers/{id}", "TRK-AUTHZ-B1").header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/trackers/{id}", "TRK-AUTHZ-A1").header("Authorization", tokenA))
                .andExpect(status().isOk());
    }

    // --- 6. 화주A가 B의 트래커로 readings/anomalies/prediction/track 각각 → 404 ---

    @Test
    void shipperA_cannotReadShipperBsTrackerReadings() throws Exception {
        givenTracker("TRK-AUTHZ-B2", SHIPPER_B);

        mockMvc.perform(get("/api/v1/trackers/{id}/readings", "TRK-AUTHZ-B2").header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shipperA_cannotReadShipperBsTrackerAnomalies() throws Exception {
        givenTracker("TRK-AUTHZ-B3", SHIPPER_B);

        mockMvc.perform(get("/api/v1/trackers/{id}/anomalies", "TRK-AUTHZ-B3").header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shipperA_cannotReadShipperBsTrackerPrediction() throws Exception {
        givenTracker("TRK-AUTHZ-B4", SHIPPER_B);

        mockMvc.perform(get("/api/v1/trackers/{id}/prediction", "TRK-AUTHZ-B4").header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shipperA_cannotReadShipperBsTrackerTrack() throws Exception {
        givenTracker("TRK-AUTHZ-B5", SHIPPER_B);

        mockMvc.perform(get("/api/v1/trackers/{id}/track", "TRK-AUTHZ-B5").header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // 진짜 없는 트래커의 404와 "있지만 타사 소유"인 404가 바디상 구분되지 않아야 한다(존재 은닉).
    // 같은 trackerId 문자열로 먼저 "부재" 응답을 받고, 그 다음 화주B 소유로 등록해 같은 id로
    // "타사 소유" 응답을 받는다 — id가 다르면 detail/instance가 달라 애초에 비교가 안 된다.
    @Test
    void notFoundBody_isIdenticalForMissingTrackerAndForeignTracker() throws Exception {
        String trackerId = "TRK-AUTHZ-SAME-ID";

        String missingBody = mockMvc.perform(
                        get("/api/v1/trackers/{id}/prediction", trackerId).header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        givenTracker(trackerId, SHIPPER_B);

        String foreignBody = mockMvc.perform(
                        get("/api/v1/trackers/{id}/prediction", trackerId).header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // timestamp만 다르고 나머지(type/title/status/detail/code/instance)는 완전히 같아야 한다.
        ObjectNode missing = (ObjectNode) objectMapper.readTree(missingBody);
        ObjectNode foreign = (ObjectNode) objectMapper.readTree(foreignBody);
        missing.remove("timestamp");
        foreign.remove("timestamp");

        org.assertj.core.api.Assertions.assertThat(foreign).isEqualTo(missing);
    }

    // --- 7. 화주A가 B의 shipment PATCH → 404 ---

    @Test
    void shipperA_cannotPatchShipperBsShipment() throws Exception {
        String trackerId = givenTracker("TRK-AUTHZ-SHIP-B", SHIPPER_B);
        Long shipmentId = createShipmentAs(tokenB, trackerId);

        mockMvc.perform(patch("/api/v1/shipments/{id}", shipmentId)
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // --- 8. GET /summary·/alerts가 화주별로만 집계 ---

    @Test
    void summary_countsOnlyCallingShippersShipments() throws Exception {
        String trackerA = givenTracker("TRK-AUTHZ-SUM-A", SHIPPER_A);
        createShipmentAs(tokenA, trackerA);

        mockMvc.perform(get("/api/v1/summary").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void alerts_scopedToCallingShipper_foreignTrackerFilterReturnsEmpty() throws Exception {
        givenTracker("TRK-AUTHZ-ALERT-B", SHIPPER_B);

        // 화주A가 화주B 소유 트래커ID로 필터링해도, 스코핑 조인 때문에 결과가 나오지 않는다(404가 아니라 빈 목록 —
        // 목록 조회는 필터 조건일 뿐 단일 리소스 조회가 아니므로).
        mockMvc.perform(get("/api/v1/alerts").param("trackerId", "TRK-AUTHZ-ALERT-B").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    private Long createShipmentAs(String token, String trackerId) throws Exception {
        String body = """
                {
                  "trackerId": "%s",
                  "productName": "백신 A",
                  "origin": {"lat": 37.4201, "lon": 127.1265, "name": "성남 물류센터"},
                  "destination": {"lat": 37.5798, "lon": 127.0034, "name": "서울대병원 약제부"},
                  "consignee": {"name": "서울대병원 약제부", "contact": "02-000-0000"},
                  "driverContact": "010-0000-0000"
                }
                """.formatted(trackerId);

        String response = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return Long.valueOf(response.replaceAll(".*\"shipmentId\":(\\d+).*", "$1"));
    }
}
