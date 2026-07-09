package com.coldchain.prediction.repository;

import com.coldchain.prediction.domain.BreachEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreachEventRepository extends JpaRepository<BreachEvent, Long> {

    /** 평가지표(M4 PR3) — 기간 내 전체 이탈 전이(트래커 전체, 어드민 뷰라 스코핑 없음). */
    List<BreachEvent> findByTsBetweenOrderByTsAsc(Instant from, Instant to);
}
