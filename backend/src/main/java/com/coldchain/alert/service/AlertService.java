package com.coldchain.alert.service;

import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.domain.AlertSeverity;
import com.coldchain.alert.domain.AlertType;
import com.coldchain.alert.event.AlertRaisedEvent;
import com.coldchain.alert.repository.AlertRepository;
import com.coldchain.detection.domain.AnomalySeverity;
import com.coldchain.detection.domain.AnomalyType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertDedupService dedupService;
    private final SlackAlertSender slackAlertSender;
    private final ApplicationEventPublisher eventPublisher;

    public AlertService(AlertRepository alertRepository, AlertDedupService dedupService,
            SlackAlertSender slackAlertSender, ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.dedupService = dedupService;
        this.slackAlertSender = slackAlertSender;
        this.eventPublisher = eventPublisher;
    }

    public void raiseBreachAlert(String trackerId, BigDecimal temperature, BigDecimal thresholdTemp) {
        if (!dedupService.tryAcquire(trackerId, AlertType.BREACH)) {
            return;
        }
        String message = String.format(Locale.ROOT, "[긴급] %s 온도 %.1f℃ — 임계 %.1f℃ 초과",
                trackerId, temperature, thresholdTemp);
        raise(trackerId, AlertType.BREACH, AlertSeverity.HIGH, temperature, message);
    }

    public void raiseAnomalyAlert(String trackerId, AnomalyType anomalyType, AnomalySeverity anomalySeverity,
            String anomalyMessage) {
        if (!dedupService.tryAcquire(trackerId, AlertType.ANOMALY)) {
            return;
        }
        AlertSeverity severity = anomalySeverity == AnomalySeverity.HIGH ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;
        String message = String.format(Locale.ROOT, "[주의] %s %s 이상 감지 — %s", trackerId, anomalyType, anomalyMessage);
        raise(trackerId, AlertType.ANOMALY, severity, null, message);
    }

    /**
     * FR-5 선제 경고 — "N분 후 이탈 예상". 경계에서의 취소↔재활성 flap 방어를 위해 BREACH/
     * ANOMALY와 동일하게 dedup을 건다(취소 시 {@link #raisePredictionCanceledAlert}가 해제).
     */
    public void raisePredictionAlert(String trackerId, Instant createdAt, Instant predictedBreachAt) {
        if (!dedupService.tryAcquire(trackerId, AlertType.PREDICTION)) {
            return;
        }
        long minutes = Duration.between(createdAt, predictedBreachAt).toMinutes();
        String message = String.format(Locale.ROOT, "[예측] %s %d분 후 이탈 예상", trackerId, minutes);
        raise(trackerId, AlertType.PREDICTION, AlertSeverity.HIGH, null, message);
    }

    /**
     * 예측 취소(추세 완화) 통보 — 알림 피로를 줄이는 FR-5의 목적 그대로, 이 알림 자체는 dedup
     * 없이 항상 보낸다(취소는 에피소드당 정확히 1회만 일어나 반복 발송 위험이 없다). 대신 다음
     * 경고를 위해 PREDICTION dedup 락을 해제한다 — 안 풀면 취소 후 10분 내 재악화 시 새 경고가
     * 조용히 억제된다.
     */
    public void raisePredictionCanceledAlert(String trackerId) {
        String message = String.format(Locale.ROOT, "[해제] %s 예측 경고 취소 — 온도 추세 완화됨", trackerId);
        raise(trackerId, AlertType.PREDICTION_CANCELED, AlertSeverity.MEDIUM, null, message);
        dedupService.release(trackerId, AlertType.PREDICTION);
    }

    /**
     * 저장(PENDING) → 발송 시도 → 최종 상태 갱신 순서. Slack 호출을 트랜잭션 안에 두지 않는다 —
     * 외부 호출 동안 DB 커넥션을 붙잡지 않기 위해 저장을 두 번(INSERT, UPDATE)으로 나눈다.
     */
    private void raise(String trackerId, AlertType type, AlertSeverity severity, BigDecimal temperatureAtEvent,
            String message) {
        Alert alert = alertRepository.save(Alert.pending(trackerId, type, severity, temperatureAtEvent, message));

        SlackAlertSender.SendResult result = slackAlertSender.send(message);
        if (result.success()) {
            alert.markSent(result.retryCount());
        } else {
            alert.markFailed(result.retryCount());
            // 실패한 발송은 dedup 억제 대상이 아니다 — 락을 풀어 다음 전이 때 재시도 가능하게 한다
            // (그대로 두면 Slack 장애 중 지속 breach가 TTL 내내 조용히 묻힌다).
            dedupService.release(trackerId, type);
        }
        alertRepository.save(alert);

        eventPublisher.publishEvent(new AlertRaisedEvent(
                alert.getId(), alert.getTrackerId(), alert.getType(), alert.getSeverity(),
                alert.getStatus(), alert.getCreatedAt()));
    }
}
