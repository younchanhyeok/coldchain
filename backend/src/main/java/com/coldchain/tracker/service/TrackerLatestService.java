package com.coldchain.tracker.service;

import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class TrackerLatestService {

    private static final int MAX_ATTEMPTS = 3;

    private final TrackerLatestUpsertAttempt upsertAttempt;

    public TrackerLatestService(TrackerLatestUpsertAttempt upsertAttempt) {
        this.upsertAttempt = upsertAttempt;
    }

    public TrackerLatestUpsertResult upsert(String trackerId, Instant recordedAt, BigDecimal temperature, Point position) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return upsertAttempt.execute(trackerId, recordedAt, temperature, position);
            } catch (ObjectOptimisticLockingFailureException e) {
                // 기존 행에 대한 update-update 경쟁(버전 충돌) — 다음 루프에서 최신 버전을 다시 읽는다
                if (attempt == MAX_ATTEMPTS) {
                    return TrackerLatestUpsertResult.conflict();
                }
            } catch (DataIntegrityViolationException e) {
                // 같은 트래커의 첫 리딩 두 개가 동시에 도착해 insert-insert로 경쟁하는 경우 — 재시도하면
                // 다음 루프에서 findById가 상대방이 커밋한 행을 찾아 정상적인 update 경로로 전환된다
                if (attempt == MAX_ATTEMPTS) {
                    return TrackerLatestUpsertResult.conflict();
                }
            }
        }
        return TrackerLatestUpsertResult.conflict();
    }
}
