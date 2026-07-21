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

    /** 폐구간 [from, to] — 배송 시간창처럼 상한 시각의 리딩도 창에 속하는 조회용(ConsigneeTrack). */
    List<Reading> findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            String trackerId, Instant from, Instant to, Pageable pageable);

    /** 반개구간 [from, to) — readings 조회 API의 원시 경로(M6 PR5 후속). 다운샘플 경로와 경계
     *  의미를 통일해 nextBefore 커서로 다음 페이지(to=nextBefore) 요청 시 경계 리딩이 중복되지 않는다. */
    List<Reading> findByTrackerIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
            String trackerId, Instant from, Instant to, Pageable pageable);

    List<Reading> findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
            String trackerId, Instant from, Pageable pageable);

    /** L3 예측 윈도우 — 최근 30개(최신순, 호출부에서 시간순으로 뒤집어 씀). */
    List<Reading> findTop30ByTrackerIdOrderByRecordedAtDesc(String trackerId);
}
