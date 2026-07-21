package com.coldchain.reading.dto;

import java.time.Instant;

/**
 * 차트 시계열의 한 점. 원시 조회는 temperature만 채우고 min/max는 null이다.
 * interval 다운샘플 조회(1m/5m)는 버킷 내 avg를 temperature로, min/max를 함께 채운다 —
 * 콜드체인에선 평균이 짧은 이탈을 가릴 수 있어 maxTemperature가 이탈 안전 신호다.
 */
public record ReadingPoint(
        Instant ts,
        Double temperature,
        Double minTemperature,
        Double maxTemperature,
        Double lat,
        Double lon) {

    /** 원시 리딩용 — min/max 없음. */
    public static ReadingPoint raw(Instant ts, Double temperature, Double lat, Double lon) {
        return new ReadingPoint(ts, temperature, null, null, lat, lon);
    }
}
