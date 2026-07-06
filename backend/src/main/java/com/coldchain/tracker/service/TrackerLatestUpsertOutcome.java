package com.coldchain.tracker.service;

public enum TrackerLatestUpsertOutcome {
    /** recordedAt이 최신이라 tracker_latest를 갱신했다. */
    UPDATED,
    /** recordedAt이 현재 저장된 last_ts보다 과거(out-of-order)라 tracker_latest는 건드리지 않았다. */
    OUT_OF_ORDER,
    /** 동시 갱신 충돌로 재시도를 모두 소진했다. */
    CONFLICT
}
