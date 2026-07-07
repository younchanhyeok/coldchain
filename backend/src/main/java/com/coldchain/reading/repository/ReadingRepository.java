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
}
