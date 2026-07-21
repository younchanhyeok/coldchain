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

-- refresh 정책 — 두 불변식이 맞물린다:
--  (1) start_offset >= 수집 허용 지연(IngestController.MAX_PAST_SKEW = 7일). 늦게 도착한
--      리딩(디바이스 버퍼링·배치 수집 — 이 프로젝트의 1급 시나리오)이 이미 materialize된
--      과거 버킷을 갱신할 때, 스케줄 refresh 윈도우가 그 구간까지 닿아야 invalidation이 처리된다.
--      안 그러면 실시간 집계가 stale한 materialize 값을 반환해 늦게 온 이탈(max_temp)이 묻힌다.
--  (2) start_offset < raw 보존. refresh가 이미 삭제된 원시 chunk를 읽으면 그 구간이 CAgg에서
--      빈다. 아래 raw 보존을 8일로 둬 7일 start_offset보다 크게 유지한다.
-- end_offset은 아직 채워지는 최신 버킷을 굳히지 않게 최소 1버킷 띄운다(그 구간은 실시간 집계가 커버).
-- 스케줄이 1~5분이어도 refresh는 invalidation 로그가 가리키는 구간만 다시 굳히므로(윈도우 전체를
-- 매번 재집계하지 않음) 7일 윈도우라도 정상 스트리밍에선 비용이 최신 버킷 몇 개에 그친다.
SELECT add_continuous_aggregate_policy('reading_1m',
    start_offset => INTERVAL '7 days',
    end_offset   => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => true);

SELECT add_continuous_aggregate_policy('reading_5m',
    start_offset => INTERVAL '7 days',
    end_offset   => INTERVAL '5 minutes',
    schedule_interval => INTERVAL '5 minutes',
    if_not_exists => true);

-- 계층형 보존: 원시 8일 → 1분 30일 → 5분 180일. 오래될수록 성기게 오래 보관.
-- 원시 8일인 이유: start_offset(7일)보다 커야 refresh가 살아있는 chunk만 읽는다(불변식 2).
-- CAgg는 원시 삭제 훨씬 전에 materialize되므로(늦은 데이터도 refresh 윈도우 7일 안에 잡힘)
-- 원시 삭제로 CAgg가 비지 않는다. 원시 조회는 최근 8일, 그 이전은 interval 다운샘플로만.
SELECT add_retention_policy('reading', INTERVAL '8 days', if_not_exists => true);
SELECT add_retention_policy('reading_1m', INTERVAL '30 days', if_not_exists => true);
SELECT add_retention_policy('reading_5m', INTERVAL '180 days', if_not_exists => true);
