package com.coldchain.ingest.event;

import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

/**
 * tracker_latest가 실제로 갱신됐을 때만(out-of-order 스킵 제외) 발행된다.
 * justBreached는 이번 리딩으로 "정상 → 임계 초과"로 막 전이됐는지를 나타낸다(계속 초과 상태 유지는 false).
 */
public record ReadingRecordedEvent(
        String trackerId,
        BigDecimal temperature,
        Point position,
        Instant recordedAt,
        BigDecimal thresholdTemp,
        boolean justBreached) {
}
