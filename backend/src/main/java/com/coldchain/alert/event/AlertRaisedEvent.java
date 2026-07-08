package com.coldchain.alert.event;

import com.coldchain.alert.domain.AlertSeverity;
import com.coldchain.alert.domain.AlertStatus;
import com.coldchain.alert.domain.AlertType;
import java.time.Instant;

/**
 * 최종 상태(SENT/FAILED)가 정해진 뒤 발행 — 알림 탭 Live 배지(SSE)용. 상세 내용은
 * 프론트가 GET /alerts 폴링으로 가져오므로 이 이벤트는 "새 알림이 생겼다"는 신호로만 쓰인다.
 */
public record AlertRaisedEvent(
        Long id,
        String trackerId,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        Instant createdAt) {
}
