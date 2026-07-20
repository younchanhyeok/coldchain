-- M6 PR5: 다운샘플 continuous aggregate 2종 + 계층형 보존 정책 (NFR-4).
-- 차트가 긴 구간을 원시 리딩(5s 주기)으로 그리면 점이 수만 개라 느리다 → 1분/5분 버킷으로
-- 미리 집계한 뷰를 두고, 조회 API의 interval 파라미터가 이 뷰를 읽는다.
--
-- 콜드체인 도메인 주의: 평균만 담으면 짧은 이탈(2분 스파이크)이 평균에 묻힌다 — min/max를 함께
-- 담아 조회 측이 "구간 내 최고온도"로 이탈을 놓치지 않게 한다(avg는 추세선, max가 안전 신호).
--
-- materialized_only = false: 실시간 집계. refresh 정책이 아직 굳히지 못한 최신 구간(끝 1~5분)도
-- 쿼리 시점에 원시에서 즉석 계산해 합쳐 보여준다 — 차트에 방금 값이 빠지지 않게(NFR-2와 정합).
--
-- 재실행 안전: 이 파일은 비트랜잭션(.conf)이라(CAgg 생성은 트랜잭션 불가) 부분 실패 후 재실행될
-- 수 있다 → IF NOT EXISTS + 정책 if_not_exists=true로 전 문장을 멱등하게.

CREATE MATERIALIZED VIEW IF NOT EXISTS reading_1m
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    tracker_id,
    time_bucket(INTERVAL '1 minute', recorded_at) AS bucket,
    avg(temperature) AS avg_temp,
    min(temperature) AS min_temp,
    max(temperature) AS max_temp,
    last(position, recorded_at) AS last_position
FROM reading
GROUP BY tracker_id, bucket
WITH NO DATA;

CREATE MATERIALIZED VIEW IF NOT EXISTS reading_5m
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    tracker_id,
    time_bucket(INTERVAL '5 minutes', recorded_at) AS bucket,
    avg(temperature) AS avg_temp,
    min(temperature) AS min_temp,
    max(temperature) AS max_temp,
    last(position, recorded_at) AS last_position
FROM reading
GROUP BY tracker_id, bucket
WITH NO DATA;

-- refresh 정책: start_offset은 반드시 raw 보존(7일)보다 짧아야 한다 — 안 그러면 refresh가
-- 이미 삭제된 원시 chunk를 읽으려다 그 구간이 CAgg에서 비게 된다. 1h/6h ≪ 7d라 안전.
-- end_offset은 아직 채워지는 중인 최신 버킷을 굳히지 않게 최소 1버킷 띄운다(그 구간은 실시간
-- 집계가 커버).
SELECT add_continuous_aggregate_policy('reading_1m',
    start_offset => INTERVAL '1 hour',
    end_offset   => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => true);

SELECT add_continuous_aggregate_policy('reading_5m',
    start_offset => INTERVAL '6 hours',
    end_offset   => INTERVAL '5 minutes',
    schedule_interval => INTERVAL '5 minutes',
    if_not_exists => true);

-- 계층형 보존: 원시 7일(최근 상세) → 1분 30일 → 5분 180일. 오래될수록 성기게 오래 보관.
-- CAgg는 원시가 삭제되기 훨씬 전에 materialize되므로(refresh 1~5분 주기) 원시 삭제로 CAgg가
-- 비지 않는다. 원시 조회는 최근 7일만 가능하고, 그 이전은 interval 다운샘플로만 조회된다.
SELECT add_retention_policy('reading', INTERVAL '7 days', if_not_exists => true);
SELECT add_retention_policy('reading_1m', INTERVAL '30 days', if_not_exists => true);
SELECT add_retention_policy('reading_5m', INTERVAL '180 days', if_not_exists => true);
