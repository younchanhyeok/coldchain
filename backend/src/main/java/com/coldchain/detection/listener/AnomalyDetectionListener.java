package com.coldchain.detection.listener;

import com.coldchain.detection.service.AnomalyDetectionService;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 수집 요청 스레드가 L2 분석을 기다리지 않도록 별도 스레드에서 처리한다
 * (SseBroadcaster와 동일한 이유 — 부가 기능은 수집·저장 경로를 막지 않는다).
 *
 * direct 모드 한정(M6 PR3) — kafka 모드에서는 ReadingConsumer가 파티션 순서 그대로
 * analyze를 직접 호출하므로, 이 리스너까지 살아 있으면 같은 리딩을 두 번 분석한다.
 * (kafka 모드의 ReadingRecordedEvent는 collapse된 최신 1건뿐이라 이 리스너로는
 * 배치 내 나머지 리딩의 탐지가 누락되기도 한다 — 컨슈머 직접 호출이 정본.)
 */
@Component
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "direct")
public class AnomalyDetectionListener {

    private final AnomalyDetectionService anomalyDetectionService;

    public AnomalyDetectionListener(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @Async
    @EventListener
    public void onReadingRecorded(ReadingRecordedEvent event) {
        anomalyDetectionService.analyze(event);
    }
}
