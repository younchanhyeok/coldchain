package com.coldchain.detection.event;

import com.coldchain.detection.domain.AnomalySeverity;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import java.time.Instant;

/**
 * 이상이 새로 활성화되거나(ACTIVE) 해제될 때만(CLEARED) 발행된다 — 활성 상태 유지 중인
 * 리딩마다 반복 발행되지 않는다(AnomalyDetectionService의 억제 로직).
 */
public record AnomalyDetectedEvent(
        String trackerId,
        AnomalyType type,
        AnomalySeverity severity,
        String message,
        Instant ts,
        AnomalyStatus status) {
}
