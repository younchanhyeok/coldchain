package com.coldchain.detection.listener;

import com.coldchain.detection.service.AnomalyDetectionService;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 수집 요청 스레드가 L2 분석을 기다리지 않도록 별도 스레드에서 처리한다
 * (SseBroadcaster와 동일한 이유 — 부가 기능은 수집·저장 경로를 막지 않는다).
 */
@Component
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
