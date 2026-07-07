package com.coldchain.tracker.dto;

import java.util.List;

// current는 SSE 미연결 등 폴백 상황에 대비한 필드다 — 프론트는 평소엔 실시간(lastPosition)을 우선 쓴다.
// breachSegments: 연속으로 임계 초과였던 구간들(각각 GeoJSON LineString) — 점이 아니라 구간 강조용.
public record TrackResponse(
        String trackerId,
        GeoJsonLineString path,
        PositionResponse current,
        NamedPositionResponse destination,
        Double remainingDistanceMeters,
        List<GeoJsonLineString> breachSegments) {
}
