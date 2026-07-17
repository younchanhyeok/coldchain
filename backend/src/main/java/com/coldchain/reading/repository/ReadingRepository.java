package com.coldchain.reading.repository;

import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.domain.ReadingId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// 키 타입이 ReadingId(복합키) — hypertable 전환(V11)으로 (id, recorded_at) PK가 됐다.
// 조회는 전부 tracker_id+recorded_at 파생 쿼리라 findById(단일 키)를 쓰는 곳이 없어 무영향.
public interface ReadingRepository extends JpaRepository<Reading, ReadingId> {

    List<Reading> findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            String trackerId, Instant from, Instant to, Pageable pageable);

    List<Reading> findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            String trackerId, Instant from, Pageable pageable);

    /** L3 예측 윈도우 — 최근 30개(최신순, 호출부에서 시간순으로 뒤집어 씀). */
    List<Reading> findTop30ByTrackerIdOrderByRecordedAtDesc(String trackerId);
}
