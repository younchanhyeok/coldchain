package com.coldchain.ingest.pipeline;

import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.tracker.domain.Tracker;
import java.util.List;

/**
 * 수집 파이프라인 추상화(M6 PR3) — 컨트롤러는 인증·검증까지만 하고 저장 경로는 여기에 위임한다.
 * direct(M1~M5의 동기 저장) | kafka(브로커 발행 → 컨슈머 배치 처리) 두 구현을
 * app.ingest.mode 토글로 선택 — 같은 빌드에서 before/after 부하 측정(A/B)이 가능하다.
 * 202 계약은 두 모드 동일: "수신 보장"(direct는 저장까지, kafka는 브로커 영속까지).
 */
public interface IngestPipeline {

    /** 검증 통과한 단건 리딩 처리. 반환하면 컨트롤러가 202를 돌려준다. */
    void ingest(Tracker tracker, ReadingIngestRequest request);

    /** 검증 통과한 배치 리딩 처리(요소 검증 실패분은 컨트롤러가 이미 걸러냄). */
    void ingestBatch(Tracker tracker, List<ReadingIngestRequest> readings);
}
