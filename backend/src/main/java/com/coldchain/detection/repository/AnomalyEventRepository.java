package com.coldchain.detection.repository;

import com.coldchain.detection.domain.AnomalyEvent;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, Long> {

    Optional<AnomalyEvent> findByTrackerIdAndTypeAndStatus(String trackerId, AnomalyType type, AnomalyStatus status);

    boolean existsByTrackerIdAndStatus(String trackerId, AnomalyStatus status);

    List<AnomalyEvent> findByTrackerIdAndTsBetweenOrderByTsDesc(String trackerId, Instant from, Instant to);

    List<AnomalyEvent> findByTrackerIdAndTypeAndTsBetweenOrderByTsDesc(
            String trackerId, AnomalyType type, Instant from, Instant to);
}
