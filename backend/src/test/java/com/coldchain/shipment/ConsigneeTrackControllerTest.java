package com.coldchain.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.auth.domain.MagicLinkToken;
import com.coldchain.auth.repository.MagicLinkTokenRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 매직링크 발급(POST /shipments)·조회(GET /track/{token})·만료(DELIVERED 전이) 계약 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
@Transactional
class ConsigneeTrackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackerRepository trackerRepository;

    @Autowired
    private MagicLinkTokenRepository magicLinkTokenRepository;

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

    private record CreatedShipment(Long shipmentId, String token) {
    }

    private CreatedShipment createShipment(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createShipmentBody(trackerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.magicLink").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(body);
        Long shipmentId = json.get("shipmentId").asLong();
        String magicLink = json.get("magicLink").asText();
        String token = magicLink.substring(magicLink.lastIndexOf('/') + 1);
        return new CreatedShipment(shipmentId, token);
    }

    @Test
    void createShipment_issuesMagicLinkPointingToFrontend() throws Exception {
        CreatedShipment created = createShipment(givenTracker("TRK-ML-CREATE"));

        assertThat(created.token()).startsWith("mlk_");
        assertThat(magicLinkTokenRepository.findById(created.token())).isPresent();
    }

    @Test
    void getTrack_validToken_returnsViewWithoutTrackerIdOrPath() throws Exception {
        CreatedShipment created = createShipment(givenTracker("TRK-ML-VIEW"));

        mockMvc.perform(get("/api/v1/track/{token}", created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.productName").value("백신 A"))
                .andExpect(jsonPath("$.shipment.shipperName").value("한국제약"))
                .andExpect(jsonPath("$.shipment.status").value("READY"))
                .andExpect(jsonPath("$.thresholdTemp").value(8.0))
                // 노출 범위 최소화 — 화주 내부 표현(트래커ID·경로·이탈구간)은 필드 자체가 없어야 한다.
                .andExpect(jsonPath("$.trackerId").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.breachSegments").doesNotExist());
    }

    @Test
    void getTrack_unknownToken_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/track/{token}", "mlk_does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getTrack_expiredToken_unauthorizedWithMagicLinkExpiredCode() throws Exception {
        CreatedShipment created = createShipment(givenTracker("TRK-ML-EXPIRED"));
        MagicLinkToken token = magicLinkTokenRepository.findById(created.token()).orElseThrow();
        token.expireAt(Instant.now().minusSeconds(60));
        magicLinkTokenRepository.save(token);

        mockMvc.perform(get("/api/v1/track/{token}", created.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MAGIC_LINK_EXPIRED"));
    }

    @Test
    void deliveredTransition_setsMagicLinkExpiryToSevenDaysLater() throws Exception {
        CreatedShipment created = createShipment(givenTracker("TRK-ML-DELIVER"));

        mockMvc.perform(patch("/api/v1/shipments/{id}", created.shipmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/shipments/{id}", created.shipmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());

        MagicLinkToken token = magicLinkTokenRepository.findById(created.token()).orElseThrow();
        assertThat(token.getExpiresAt()).isNotNull();
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plusSeconds(6L * 24 * 60 * 60));
        assertThat(token.getExpiresAt()).isBefore(Instant.now().plusSeconds(8L * 24 * 60 * 60));

        // 아직 만료 시각(delivered_at+7일) 이전이므로 조회는 여전히 200이어야 한다.
        mockMvc.perform(get("/api/v1/track/{token}", created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("DELIVERED"));
    }
}
