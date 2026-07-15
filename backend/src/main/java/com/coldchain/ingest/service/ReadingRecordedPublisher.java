package com.coldchain.ingest.service;

import com.coldchain.ingest.event.ReadingRecordedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * tracker_latest 갱신(UPDATED) 후의 ReadingRecordedEvent 발행 — direct 파이프라인과
 * Kafka 컨슈머가 같은 justBreached(정상→임계초과 전이) 판정을 공유한다.
 */
@Component
public class ReadingRecordedPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public ReadingRecordedPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(String trackerId, BigDecimal temperature, Point position, Instant recordedAt,
            BigDecimal thresholdTemp, BigDecimal previousTemperature) {
        boolean wasOverThreshold = previousTemperature != null && previousTemperature.compareTo(thresholdTemp) > 0;
        boolean isOverThreshold = temperature.compareTo(thresholdTemp) > 0;
        boolean justBreached = isOverThreshold && !wasOverThreshold;

        eventPublisher.publishEvent(
                new ReadingRecordedEvent(trackerId, temperature, position, recordedAt, thresholdTemp, justBreached));
    }
}
