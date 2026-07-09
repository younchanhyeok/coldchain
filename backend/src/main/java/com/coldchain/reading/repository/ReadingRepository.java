package com.coldchain.reading.repository;

import com.coldchain.reading.domain.Reading;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    List<Reading> findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            String trackerId, Instant from, Instant to, Pageable pageable);

    List<Reading> findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            String trackerId, Instant from, Pageable pageable);

    /** L3 예측 윈도우 — 최근 30개(최신순, 호출부에서 시간순으로 뒤집어 씀). */
    List<Reading> findTop30ByTrackerIdOrderByRecordedAtDesc(String trackerId);
}
