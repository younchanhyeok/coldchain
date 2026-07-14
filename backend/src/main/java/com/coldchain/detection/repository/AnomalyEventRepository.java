package com.coldchain.detection.repository;

import com.coldchain.detection.domain.AnomalyEvent;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, Long> {

    Optional<AnomalyEvent> findByTrackerIdAndTypeAndStatus(String trackerId, AnomalyType type, AnomalyStatus status);

    boolean existsByTrackerIdAndStatus(String trackerId, AnomalyStatus status);

    /** 목록 화면 배치 조회 — 트래커별 exists 쿼리(N+1)를 IN 한 방으로. 활성 이상은
     *  (tracker, type)당 최대 1건 유지 규칙이라 결과 크기는 트래커 수 × 2로 유계. */
    List<AnomalyEvent> findByTrackerIdInAndStatus(Collection<String> trackerIds, AnomalyStatus status);

    List<AnomalyEvent> findByTrackerIdAndStatusOrderByTsDesc(String trackerId, AnomalyStatus status);

    List<AnomalyEvent> findByTrackerIdAndTsBetweenOrderByTsDesc(String trackerId, Instant from, Instant to);

    List<AnomalyEvent> findByTrackerIdAndTypeAndTsBetweenOrderByTsDesc(
            String trackerId, AnomalyType type, Instant from, Instant to);
}
