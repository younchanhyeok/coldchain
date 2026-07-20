package com.coldchain.reading;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 다운샘플 continuous aggregate(M6 PR5) e2e. 원시 수집(direct 모드, 동기 저장) 후
 * refresh_continuous_aggregate를 수동 호출해 결정적으로 검증한다 — 백그라운드 refresh 정책을
 * 기다리지 않는다. @Transactional 미사용 이유는 IngestControllerTest 상단 주석 참고(+ refresh
 * 프로시저는 트랜잭션 밖에서만 실행 가능).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class ReadingDownsampleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private void postReading(TrackerRegisterResponse tracker, double temp, Instant recordedAt) throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.49, "lon": 127.02, "recordedAt": "%s"}
                                """.formatted(temp, recordedAt)))
                .andExpect(status().isAccepted());
    }

    @Test
    void downsampleBucketsCarryAvgMinMaxAndDoNotHideBreach() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-DS-001");
        // 두 개의 1분 버킷에 걸치도록 base를 분 경계에 맞춘다.
        Instant bucketA = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        Instant bucketB = bucketA.plus(1, ChronoUnit.MINUTES);

        // 버킷 A: 5.0 / 9.0 / 7.0 → avg 7.0, min 5.0, max 9.0 (9.0은 임계 8.0 초과 — 이탈이 max로 드러나야)
        postReading(tracker, 5.0, bucketA.plusSeconds(5));
        postReading(tracker, 9.0, bucketA.plusSeconds(15));
        postReading(tracker, 7.0, bucketA.plusSeconds(25));
        // 버킷 B: 4.0 / 6.0 → avg 5.0
        postReading(tracker, 4.0, bucketB.plusSeconds(5));
        postReading(tracker, 6.0, bucketB.plusSeconds(15));

        // 결정적 검증: 백그라운드 정책 대신 수동 refresh (트랜잭션 밖 autocommit).
        jdbcTemplate.execute("CALL refresh_continuous_aggregate('reading_1m', NULL, NULL)");

        mockMvc.perform(get("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .param("interval", "1m")
                        .param("from", bucketA.toString())
                        .param("to", bucketB.plus(1, ChronoUnit.MINUTES).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings.length()").value(2))
                // 시간순 정렬 — [0]=버킷A, [1]=버킷B
                .andExpect(jsonPath("$.readings[0].temperature").value(7.0))
                .andExpect(jsonPath("$.readings[0].minTemperature").value(5.0))
                .andExpect(jsonPath("$.readings[0].maxTemperature").value(9.0)) // 이탈이 평균에 안 묻힘
                .andExpect(jsonPath("$.readings[0].lat").value(37.49))
                .andExpect(jsonPath("$.readings[1].temperature").value(5.0));
    }

    @Test
    void rawQueryLeavesMinMaxNull() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-DS-002");
        postReading(tracker, 5.8, Instant.now().minusSeconds(30));

        // interval 없으면 원시 경로 — min/max는 null.
        mockMvc.perform(get("/api/v1/trackers/{id}/readings", tracker.trackerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings.length()").value(1))
                .andExpect(jsonPath("$.readings[0].temperature").value(5.8))
                .andExpect(jsonPath("$.readings[0].minTemperature").value(nullValue()))
                .andExpect(jsonPath("$.readings[0].maxTemperature").value(nullValue()));
    }

    @Test
    void unsupportedIntervalIsRejected() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-DS-003");

        mockMvc.perform(get("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .param("interval", "10m"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SEMANTIC_INVALID"));
    }
}
