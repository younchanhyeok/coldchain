package com.coldchain.alert.repository;

import com.coldchain.alert.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // alert.tracker_id는 매핑된 연관관계가 아니라 문자열 컬럼이라, tracker와의 조인은 JPQL의
    // 명시적 WHERE 조건으로 표현한다(둘 다 엔티티라 FK 매핑 없이도 조인 가능) — 화주 스코핑(M5).
    @Query("SELECT a FROM Alert a, Tracker t WHERE a.trackerId = t.id AND t.shipperId = :shipperId "
            + "AND (:trackerId IS NULL OR a.trackerId = :trackerId) ORDER BY a.createdAt DESC")
    Page<Alert> findByShipperIdAndOptionalTrackerId(
            @Param("shipperId") Long shipperId, @Param("trackerId") String trackerId, Pageable pageable);
}
