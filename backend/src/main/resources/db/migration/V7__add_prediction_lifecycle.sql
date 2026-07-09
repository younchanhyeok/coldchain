-- M0 스켈레톤(prediction)을 M4 실사용 요구에 맞춰 보정.
-- lead_time_minutes는 삭제한다 — 리드타임은 두 가지 다른 의미(응답: predictedBreachAt-조회
-- 시점, 평가: breached_at-created_at)를 조회/집계 시점에 다른 컬럼으로 계산하므로 저장할
-- 단일 값이 아니다.
ALTER TABLE prediction
    DROP COLUMN lead_time_minutes,
    ADD COLUMN anchor_ts          TIMESTAMPTZ,
    ADD COLUMN anchor_temperature NUMERIC(5, 2),
    ADD COLUMN calm_streak        INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN breached_at        TIMESTAMPTZ;

ALTER TABLE prediction DROP CONSTRAINT prediction_status_check;
ALTER TABLE prediction
    ADD CONSTRAINT prediction_status_check
    CHECK (status IN ('ACTIVE', 'CANCELED', 'INVALIDATED', 'EXPIRED', 'BREACHED'));

-- 트래커당 활성 예측은 최대 1건 — anomaly_event/shipment와 같은 계열의 부분 유니크 인덱스.
CREATE UNIQUE INDEX uq_prediction_active ON prediction (tracker_id) WHERE status = 'ACTIVE';

-- 평가 지표(리드타임·오탐률)의 "실제 이탈" 원천. alert(BREACH)는 dedup(10분) 때문에
-- 누락될 수 있어 평가 원천으로 쓰기엔 부정직하다 — 이 테이블은 dedup 없이 전이(justBreached)마다
-- 그대로 기록한다.
CREATE TABLE breach_event (
    id         BIGSERIAL   PRIMARY KEY,
    tracker_id VARCHAR(32) NOT NULL REFERENCES tracker (id),
    ts         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_breach_event_tracker_ts ON breach_event (tracker_id, ts DESC);
