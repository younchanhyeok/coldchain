package com.coldchain.tracker.service;

import java.math.BigDecimal;

/**
 * previousTemperature는 이번 upsert 이전에 tracker_latest에 저장돼 있던 온도(없었으면 null).
 * outcome이 UPDATED일 때만 의미가 있다 — 호출부가 임계 초과 전이(breach transition) 여부를
 * 판정하는 데 쓴다.
 */
public record TrackerLatestUpsertResult(TrackerLatestUpsertOutcome outcome, BigDecimal previousTemperature) {

    public static TrackerLatestUpsertResult conflict() {
        return new TrackerLatestUpsertResult(TrackerLatestUpsertOutcome.CONFLICT, null);
    }
}
