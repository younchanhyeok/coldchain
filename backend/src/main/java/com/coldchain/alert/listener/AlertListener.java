package com.coldchain.alert.listener;

import com.coldchain.alert.service.AlertService;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.event.AnomalyDetectedEvent;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 수집 요청 스레드가 Slack 발송(외부 호출)을 기다리지 않도록 별도 스레드에서 처리한다
 * (SseBroadcaster/AnomalyDetectionListener와 동일한 이유).
 */
@Component
public class AlertListener {

    private final AlertService alertService;

    public AlertListener(AlertService alertService) {
        this.alertService = alertService;
    }

    @Async
    @EventListener
    public void onReadingRecorded(ReadingRecordedEvent event) {
        if (event.justBreached()) {
            alertService.raiseBreachAlert(event.trackerId(), event.temperature(), event.thresholdTemp());
        }
    }

    // AnomalyDetectedEvent는 탐지 트랜잭션 안에서 발행된다 — 커밋 전에 알림이 나가면 커밋 실패 시
    // DB엔 없는 이상에 대한 유령 알림이 되므로 AFTER_COMMIT에만 실행한다. fallbackExecution은
    // 트랜잭션 밖 발행(테스트 등)에서도 동작하게 하는 안전장치.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnomalyDetected(AnomalyDetectedEvent event) {
        // 해제(CLEARED) 전이는 알림 대상이 아니다 — 활성화될 때만 알린다.
        if (event.status() == AnomalyStatus.ACTIVE) {
            alertService.raiseAnomalyAlert(event.trackerId(), event.type(), event.severity(), event.message());
        }
    }
}
