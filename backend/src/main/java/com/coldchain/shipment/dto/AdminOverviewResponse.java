package com.coldchain.shipment.dto;

/** 관리자 뷰(v1) — 시스템 전체 집계. 화주별 스코프 분리 없음(v2로 명시적 이연). */
public record AdminOverviewResponse(long shipperCount, long activeTrackerCount) {
}
