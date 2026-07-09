package com.coldchain.prediction.listener;

import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import com.coldchain.detection.event.AnomalyDetectedEvent;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.prediction.service.PredictionService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 수집 요청 스레드가 L3 예측(외부 호출 포함)을 기다리지 않도록 별도 스레드에서 처리한다
 * (SseBroadcaster/AnomalyDetectionListener와 동일한 이유).
 */
@Component
public class PredictionListener {

    private final PredictionService predictionService;

    public PredictionListener(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Async
    @EventListener
    public void onReadingRecorded(ReadingRecordedEvent event) {
        predictionService.analyze(event);
    }

    /**
     * 급변(SUDDEN) 활성화 시에만 예측을 무효화한다 — GRADUAL은 오히려 예측이 다루는 바로 그
     * 추세이므로 무효화 대상이 아니다("안전한 실패 설계"는 선형 가정이 깨지는 이산 이벤트에만 적용).
     */
    @Async
    @EventListener
    public void onAnomalyDetected(AnomalyDetectedEvent event) {
        if (event.type() == AnomalyType.SUDDEN && event.status() == AnomalyStatus.ACTIVE) {
            predictionService.invalidate(event.trackerId(), event.ts());
        }
    }
}
