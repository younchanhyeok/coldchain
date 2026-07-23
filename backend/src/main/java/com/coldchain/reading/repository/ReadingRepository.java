package com.coldchain.reading.repository;

import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.domain.ReadingId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

// 키 타입이 ReadingId(복합키) — hypertable 전환(V11)으로 (id, recorded_at) PK가 됐다.
// 조회는 전부 tracker_id+recorded_at 파생 쿼리라 findById(단일 키)를 쓰는 곳이 없어 무영향.
//
// 읽기 전용 계약(M8): 쓰기는 전부 ReadingBatchWriter(JDBC)를 거친다 — Reading은 복합키+DEFAULT
// nextval 채번이라 JPA로 쓸 수 없는(읽기 전용 @Entity) 설계다. JpaRepository를 상속하면 save/delete
// 류가 표면에 노출돼 실수로 JPA 쓰기 경로가 생길 수 있으므로, 최소 read 메서드만 선언하는
// Repository 베이스로 좁혀 "이 리포지토리로는 못 쓴다"를 타입으로 보장한다.
public interface ReadingRepository extends Repository<Reading, ReadingId> {

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
