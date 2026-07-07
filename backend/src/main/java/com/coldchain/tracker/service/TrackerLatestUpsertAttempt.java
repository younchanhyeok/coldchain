package com.coldchain.tracker.service;

import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * tracker_latest upsert 한 번의 시도를 별도 트랜잭션(REQUIRES_NEW)으로 격리한다.
 * {@link TrackerLatestService}가 같은 빈 안에서 이 메서드를 직접 호출하면(self-invocation)
 * Spring AOP 프록시를 건너뛰어 REQUIRES_NEW가 무시되므로, 재시도 오케스트레이션과
 * 트랜잭션 경계를 별도 빈으로 분리했다.
 */
@Component
class TrackerLatestUpsertAttempt {

    private final TrackerLatestRepository trackerLatestRepository;

    TrackerLatestUpsertAttempt(TrackerLatestRepository trackerLatestRepository) {
        this.trackerLatestRepository = trackerLatestRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    TrackerLatestUpsertResult execute(String trackerId, Instant recordedAt, BigDecimal temperature, Point position) {
        TrackerLatest latest = trackerLatestRepository.findById(trackerId)
                .orElseGet(() -> new TrackerLatest(trackerId));

        if (latest.getLastTs() != null && !recordedAt.isAfter(latest.getLastTs())) {
            return new TrackerLatestUpsertResult(TrackerLatestUpsertOutcome.OUT_OF_ORDER, null);
        }

        BigDecimal previousTemperature = latest.getLastTemp();
        latest.applyReading(recordedAt, temperature, position);
        trackerLatestRepository.saveAndFlush(latest);
        return new TrackerLatestUpsertResult(TrackerLatestUpsertOutcome.UPDATED, previousTemperature);
    }
}
