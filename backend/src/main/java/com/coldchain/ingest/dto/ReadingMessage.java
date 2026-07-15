package com.coldchain.ingest.dto;

import java.time.Instant;

/**
 * Kafka `readings` 토픽 페이로드(M6 PR3). 파티션 키 = trackerId (트래커별 순서 보장).
 * thresholdTemp는 싣지 않는다 — 임계값은 운영 중 바뀔 수 있는 트래커 속성이라 컨슈머가
 * 소비 시점에 DB에서 읽는다(배치당 1쿼리).
 */
public record ReadingMessage(
        String trackerId,
        double temperature,
        Double lat,
        Double lon,
        Instant recordedAt,
        Long seq) {
}
