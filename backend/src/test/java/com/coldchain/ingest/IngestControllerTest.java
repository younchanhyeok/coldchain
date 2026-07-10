package com.coldchain.ingest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 이 클래스는 의도적으로 @Transactional을 붙이지 않는다. tracker_latest upsert가
 * REQUIRES_NEW로 별도 트랜잭션(별도 커넥션)에서 실행되는데, 테스트 전체를 하나의
 * 트랜잭션으로 감싸면 그 안에서 등록한 tracker가 아직 커밋되지 않아 REQUIRES_NEW
 * 트랜잭션에서는 보이지 않게 되어(FK 위반) 실제로는 통과해야 할 케이스가 실패한다.
 * 각 테스트는 유니크한 trackerId를 쓰므로 커밋 방식이어도 서로 간섭하지 않는다.
 *
 * DefaultShipperAuthConfig를 import하는 이유: 이 클래스가 검증하는 리딩 수집(POST
 * .../readings)은 X-Device-Key만으로 permitAll이라 인증이 필요 없지만, givenRegisteredTracker
 * 픽스처가 트래커를 만들 때 쓰는 POST /api/v1/trackers는 화주 인증이 필요한 경로다(M5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TrackerRegisterResponse givenRegisteredTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class);
    }

    @Test
    void acceptsValidReading() throws Exception {
        TrackerRegisterResponse tracker = givenRegisteredTracker("TRK-INGEST-001");

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 5.8, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s", "seq": 1}
                                """.formatted(Instant.now())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void rejectsWrongDeviceKey() throws Exception {
        TrackerRegisterResponse tracker = givenRegisteredTracker("TRK-INGEST-002");

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", "dk_wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 5.8, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(Instant.now())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsUnknownTracker() throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", "TRK-NOT-EXIST")
                        .header("X-Device-Key", "dk_whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 5.8, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(Instant.now())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsTemperatureOutOfRange() throws Exception {
        TrackerRegisterResponse tracker = givenRegisteredTracker("TRK-INGEST-003");

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 200.0, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(Instant.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEMANTIC_INVALID"));
    }

    @Test
    void rejectsFutureRecordedAt() throws Exception {
        TrackerRegisterResponse tracker = givenRegisteredTracker("TRK-INGEST-004");

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 5.8, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(Instant.now().plusSeconds(3600))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEMANTIC_INVALID"));
    }

    @Test
    void ingestedReadingIsQueryable() throws Exception {
        TrackerRegisterResponse tracker = givenRegisteredTracker("TRK-INGEST-005");
        Instant recordedAt = Instant.now();

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": 5.8, "lat": 37.4979, "lon": 127.0276, "recordedAt": "%s"}
                                """.formatted(recordedAt)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/trackers/{id}/readings", tracker.trackerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackerId").value(tracker.trackerId()))
                .andExpect(jsonPath("$.readings.length()").value(1))
                .andExpect(jsonPath("$.readings[0].temperature").value(5.8));
    }
}
