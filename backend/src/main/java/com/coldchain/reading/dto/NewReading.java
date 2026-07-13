package com.coldchain.reading.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 배치 저장용 원시 리딩 — ingest가 reading 도메인에 넘기는 도메인 중립 입력.
 *  (M6 배치 수집 API에서 도입, PR3 Kafka 컨슈머도 같은 타입으로 배치 insert 한다.) */
public record NewReading(Instant recordedAt, BigDecimal temperature, Double lat, Double lon) {
}
