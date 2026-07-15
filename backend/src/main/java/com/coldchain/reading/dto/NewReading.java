package com.coldchain.reading.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 배치 저장용 원시 리딩 — ingest가 reading 도메인에 넘기는 도메인 중립 입력.
 *  trackerId를 포함해 여러 트래커의 리딩을 한 배치로 insert할 수 있다(Kafka 컨슈머 poll 단위). */
public record NewReading(String trackerId, Instant recordedAt, BigDecimal temperature, Double lat, Double lon) {
}
