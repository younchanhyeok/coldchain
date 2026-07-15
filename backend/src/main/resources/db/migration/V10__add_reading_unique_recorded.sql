-- M6 PR3: Kafka at-least-once 소비의 멱등성 기반 — 재전달된 리딩의 재삽입을
-- ON CONFLICT DO NOTHING으로 흡수하기 위한 자연키 유니크 제약.
-- recorded_at을 포함하므로 PR4의 hypertable 전환(파티션 컬럼이 유니크 제약에 필수)과도 호환.

-- 기존 dev 데이터 방어 — 지금까지는 중복 삽입을 막지 않았으므로(클라이언트 재시도 등)
-- 같은 (tracker_id, recorded_at)이 있으면 최초 행만 남긴다. 물리적으로 한 디바이스가
-- 같은 시각에 두 측정값을 가질 수 없어 정보 손실이 아니다.
DELETE FROM reading a
 USING reading b
 WHERE a.id > b.id
   AND a.tracker_id = b.tracker_id
   AND a.recorded_at = b.recorded_at;

ALTER TABLE reading
    ADD CONSTRAINT uq_reading_tracker_recorded UNIQUE (tracker_id, recorded_at);
