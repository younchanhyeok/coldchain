package com.coldchain.shipment;

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
 * 이 클래스는 의도적으로 @Transactional을 붙이지 않는다(IngestControllerTest와 동일한 이유) —
 * 리딩 수집(tracker_latest upsert)이 REQUIRES_NEW로 별도 트랜잭션(별도 커넥션)에서 실행되는데,
 * 테스트를 하나의 트랜잭션으로 감싸면 그 안에서 등록한 tracker가 아직 커밋되지 않아 REQUIRES_NEW
 * 트랜잭션에서는 FK가 안 보여 실제로는 성공해야 할 리딩 전송이 409(CONFLICT)로 실패한다 —
 * ConsigneeTrackControllerTest(@Transactional)에 이 테스트를 처음 넣었다가 CI에서 그대로
 * 재현돼 분리했다. 트래커ID는 유니크하므로 롤백이 없어도 다른 테스트와 간섭하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class ConsigneeTrackInTransitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TrackerRegisterResponse registerTrackerWithRealDeviceKey(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class);
    }

    private void sendReading(TrackerRegisterResponse tracker, Instant recordedAt, double lat, double lon,
            double temperature, int seq) throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": %s, "lon": %s, "recordedAt": "%s", "seq": %d}
                                """.formatted(temperature, lat, lon, recordedAt, seq)))
                .andExpect(status().isAccepted());
    }

    private record CreatedShipment(Long shipmentId, String token) {
    }

    private CreatedShipment createShipment(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackerId": "%s",
                                  "productName": "백신 A",
                                  "origin": {"lat": 37.4201, "lon": 127.1265, "name": "성남 물류센터"},
                                  "destination": {"lat": 37.5798, "lon": 127.0034, "name": "서울대병원 약제부"},
                                  "consignee": {"name": "서울대병원 약제부", "contact": "02-000-0000"},
                                  "driverContact": "010-0000-0000"
                                }
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(body);
        Long shipmentId = json.get("shipmentId").asLong();
        String magicLink = json.get("magicLink").asText();
        String token = magicLink.substring(magicLink.lastIndexOf('/') + 1);
        return new CreatedShipment(shipmentId, token);
    }

    // 이 PR3 조립 로직의 핵심 분기 — IN_TRANSIT일 때만 TrackService.getTrack()을 재사용해
    // remainingDistanceMeters·eta를 채운다. 로컬 curl로만 확인하고 CI엔 없었던 걸 리뷰에서
    // 지적받아 추가(회귀 방지: TrackService 시그니처가 바뀌면 이 재사용처가 조용히 깨지는 걸 잡음).
    @Test
    void getTrack_inTransit_populatesRemainingDistanceAndTemperatureLogFromRealReadings() throws Exception {
        TrackerRegisterResponse tracker = registerTrackerWithRealDeviceKey("TRK-ML-INTRANSIT");
        CreatedShipment created = createShipment(tracker.trackerId());

        Instant t1 = Instant.now();
        Instant t2 = t1.plusSeconds(240);
        sendReading(tracker, t1, 37.4201, 127.1265, 5.0, 1);
        sendReading(tracker, t2, 37.4221, 127.1285, 5.2, 2);

        mockMvc.perform(patch("/api/v1/shipments/{id}", created.shipmentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/track/{token}", created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.shipment.eta").isNotEmpty())
                .andExpect(jsonPath("$.currentTemperature").value(5.2))
                .andExpect(jsonPath("$.temperatureStatus").value("SAFE"))
                .andExpect(jsonPath("$.position").isNotEmpty())
                .andExpect(jsonPath("$.remainingDistanceMeters").isNumber())
                .andExpect(jsonPath("$.temperatureLog", org.hamcrest.Matchers.hasSize(2)));
    }
}
